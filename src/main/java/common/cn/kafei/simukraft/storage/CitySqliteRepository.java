package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@SuppressWarnings("null")
public final class CitySqliteRepository {
    private final SimuSqliteDatabase database;

    public CitySqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /**
     * saveAll: 把内存中的城市全部 upsert 进库。
     * <p>刻意不做 "DELETE WHERE dimension_id" 再重写：内存快照可能因加载失败或加载未完成而不完整，
     * 一旦按它清库就会连带级联删掉 city_members / finance_transactions / commercial_daily_income。
     * 城市的真正删除只走 {@link #delete(Connection, java.util.UUID)}。
     */
    public void saveAll(Connection connection, CompoundTag tag, String dimensionId) throws SQLException {
        ListTag cityTags = tag.getList("Cities", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < cityTags.size(); i++) {
            saveCity(connection, cityTags.getCompound(i));
        }
    }

    public void upsert(Connection connection, CompoundTag cityTag) throws SQLException {
        saveCity(connection, cityTag);
    }

    public void delete(Connection connection, java.util.UUID cityId) throws SQLException {
        if (cityId == null) {
            return;
        }
        try (PreparedStatement deleteBuildingTasks = connection.prepareStatement("DELETE FROM building_tasks WHERE city_id = ?");
             PreparedStatement deletePlanningTasks = connection.prepareStatement("DELETE FROM planning_tasks WHERE city_id = ?");
             PreparedStatement statement = connection.prepareStatement("DELETE FROM cities WHERE city_id = ?")) {
            deleteBuildingTasks.setString(1, cityId.toString());
            deleteBuildingTasks.executeUpdate();
            deletePlanningTasks.setString(1, cityId.toString());
            deletePlanningTasks.executeUpdate();
            statement.setString(1, cityId.toString());
            statement.executeUpdate();
        }
    }

    public synchronized CompoundTag loadAll(String dimensionId) {
        CompoundTag tag = new CompoundTag();
        ListTag cities = new ListTag();
        try (Connection connection = database.borrowConnection()) {
            // Bulk-load all members, group by city_id
            java.util.Map<String, ListTag> membersByCity = new java.util.HashMap<>();
            try (PreparedStatement s = connection.prepareStatement("SELECT * FROM city_members ORDER BY city_id, player_id");
                 ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    CompoundTag member = new CompoundTag();
                    member.putUUID("PlayerId", java.util.UUID.fromString(rs.getString("player_id")));
                    member.putString("PlayerName", rs.getString("player_name"));
                    member.putString("PermissionLevel", rs.getString("permission_level"));
                    membersByCity.computeIfAbsent(rs.getString("city_id"), k -> new ListTag()).add(member);
                }
            }
            // Bulk-load all finance transactions, group by city_id
            java.util.Map<String, ListTag> financesByCity = new java.util.HashMap<>();
            try (PreparedStatement s = connection.prepareStatement("SELECT * FROM finance_transactions ORDER BY city_id, sort_index");
                 ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    CompoundTag finance = new CompoundTag();
                    finance.putLong("Time", rs.getLong("time"));
                    SqliteNbtHelper.putNullableUuid(finance, "ActorId", rs.getString("actor_id"));
                    finance.putString("ActorName", rs.getString("actor_name"));
                    finance.putDouble("Amount", rs.getDouble("amount"));
                    finance.putDouble("BalanceAfter", rs.getDouble("balance_after"));
                    finance.putString("Type", rs.getString("type"));
                    finance.putString("Reason", rs.getString("reason"));
                    financesByCity.computeIfAbsent(rs.getString("city_id"), k -> new ListTag()).add(finance);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM cities WHERE dimension_id = ? ORDER BY city_id")) {
                statement.setString(1, normalizeDimensionId(dimensionId));
                try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String cityId = resultSet.getString("city_id");
                    CompoundTag cityTag = new CompoundTag();
                    cityTag.putUUID("CityId", java.util.UUID.fromString(cityId));
                    cityTag.putString("CityName", resultSet.getString("city_name"));
                    cityTag.putString("DimensionId", normalizeDimensionId(resultSet.getString("dimension_id")));
                    cityTag.putInt("CoreX", resultSet.getInt("core_x"));
                    cityTag.putInt("CoreY", resultSet.getInt("core_y"));
                    cityTag.putInt("CoreZ", resultSet.getInt("core_z"));
                    cityTag.putDouble("Funds", resultSet.getDouble("funds"));
                    cityTag.putInt("CityLevel", resultSet.getInt("city_level"));
                    cityTag.put("Members", membersByCity.getOrDefault(cityId, new ListTag()));
                    cityTag.put("FinanceTransactions", financesByCity.getOrDefault(cityId, new ListTag()));
                    cities.add(cityTag);
                }
                }
            }
            tag.put("Cities", cities);
            return cities.isEmpty() ? null : tag;
        } catch (SQLException | IllegalArgumentException exception) {
            // 加载失败必须置降级：否则内存留空，随后的保存会把库里真实存在的数据当成"已删除"。
            database.markDegraded("loadAll(cities)", exception);
            SimuKraft.LOGGER.error("Failed to load cities from SQLite", exception);
            return null;
        }
    }

    private void saveCity(Connection connection, CompoundTag cityTag) throws SQLException {
        String cityId = cityTag.getUUID("CityId").toString();
        try (PreparedStatement cityStatement = connection.prepareStatement("INSERT INTO cities(city_id, city_name, dimension_id, core_x, core_y, core_z, funds, city_level) VALUES(?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(city_id) DO UPDATE SET city_name = excluded.city_name, dimension_id = excluded.dimension_id, core_x = excluded.core_x, core_y = excluded.core_y, core_z = excluded.core_z, funds = excluded.funds, city_level = excluded.city_level");
             PreparedStatement deleteMembers = connection.prepareStatement("DELETE FROM city_members WHERE city_id = ?");
             PreparedStatement deleteFinances = connection.prepareStatement("DELETE FROM finance_transactions WHERE city_id = ?");
             PreparedStatement memberStatement = connection.prepareStatement("INSERT INTO city_members(city_id, player_id, player_name, permission_level) VALUES(?, ?, ?, ?)");
             PreparedStatement financeStatement = connection.prepareStatement("INSERT INTO finance_transactions(city_id, sort_index, time, actor_id, actor_name, amount, balance_after, type, reason) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            cityStatement.setString(1, cityId);
            cityStatement.setString(2, cityTag.getString("CityName"));
            cityStatement.setString(3, normalizeDimensionId(cityTag.getString("DimensionId")));
            cityStatement.setInt(4, cityTag.getInt("CoreX"));
            cityStatement.setInt(5, cityTag.getInt("CoreY"));
            cityStatement.setInt(6, cityTag.getInt("CoreZ"));
            cityStatement.setDouble(7, cityTag.getDouble("Funds"));
            cityStatement.setInt(8, cityTag.getInt("CityLevel"));
            cityStatement.executeUpdate();
            deleteMembers.setString(1, cityId);
            deleteMembers.executeUpdate();
            deleteFinances.setString(1, cityId);
            deleteFinances.executeUpdate();
            ListTag members = cityTag.getList("Members", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < members.size(); i++) {
                CompoundTag member = members.getCompound(i);
                memberStatement.setString(1, cityId);
                memberStatement.setString(2, member.getUUID("PlayerId").toString());
                memberStatement.setString(3, member.getString("PlayerName"));
                memberStatement.setString(4, member.getString("PermissionLevel"));
                memberStatement.addBatch();
            }
            ListTag finances = cityTag.getList("FinanceTransactions", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < finances.size(); i++) {
                CompoundTag finance = finances.getCompound(i);
                financeStatement.setString(1, cityId);
                financeStatement.setInt(2, i);
                financeStatement.setLong(3, finance.getLong("Time"));
                SqliteNbtHelper.setNullableString(financeStatement, 4, finance.hasUUID("ActorId") ? finance.getUUID("ActorId").toString() : null);
                financeStatement.setString(5, finance.getString("ActorName"));
                financeStatement.setDouble(6, finance.getDouble("Amount"));
                financeStatement.setDouble(7, finance.getDouble("BalanceAfter"));
                financeStatement.setString(8, finance.getString("Type"));
                financeStatement.setString(9, finance.getString("Reason"));
                financeStatement.addBatch();
            }
            memberStatement.executeBatch();
            financeStatement.executeBatch();
        }
    }

    private static String normalizeDimensionId(String dimensionId) {
        return dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
    }

}
