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

    public synchronized void saveAll(CompoundTag tag) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            SqliteNbtHelper.clearTables(connection, "finance_transactions", "city_members", "cities");
            try {
                ListTag cityTags = tag.getList("Cities", CompoundTag.TAG_COMPOUND);
                for (int i = 0; i < cityTags.size(); i++) {
                    saveCity(connection, cityTags.getCompound(i));
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save cities to SQLite", exception);
        }
    }

    public synchronized void upsert(CompoundTag cityTag) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                saveCity(connection, cityTag);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save city to SQLite", exception);
        }
    }

    public synchronized void delete(java.util.UUID cityId) {
        if (cityId == null) {
            return;
        }
        try (Connection connection = database.openConnection();
             PreparedStatement deleteBuildingTasks = connection.prepareStatement("DELETE FROM building_tasks WHERE city_id = ?");
             PreparedStatement deletePlanningTasks = connection.prepareStatement("DELETE FROM planning_tasks WHERE city_id = ?");
             PreparedStatement statement = connection.prepareStatement("DELETE FROM cities WHERE city_id = ?")) {
            deleteBuildingTasks.setString(1, cityId.toString());
            deleteBuildingTasks.executeUpdate();
            deletePlanningTasks.setString(1, cityId.toString());
            deletePlanningTasks.executeUpdate();
            statement.setString(1, cityId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to delete city from SQLite", exception);
        }
    }

    public synchronized CompoundTag loadAll() {
        CompoundTag tag = new CompoundTag();
        ListTag cities = new ListTag();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM cities ORDER BY city_id");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String cityId = resultSet.getString("city_id");
                CompoundTag cityTag = new CompoundTag();
                cityTag.putUUID("CityId", java.util.UUID.fromString(cityId));
                cityTag.putString("CityName", resultSet.getString("city_name"));
                cityTag.putInt("CoreX", resultSet.getInt("core_x"));
                cityTag.putInt("CoreY", resultSet.getInt("core_y"));
                cityTag.putInt("CoreZ", resultSet.getInt("core_z"));
                cityTag.putDouble("Funds", resultSet.getDouble("funds"));
                cityTag.putInt("CityLevel", resultSet.getInt("city_level"));
                cityTag.put("Members", loadCityMembers(connection, cityId));
                cityTag.put("FinanceTransactions", loadFinanceTransactions(connection, cityId));
                cities.add(cityTag);
            }
            tag.put("Cities", cities);
            return cities.isEmpty() ? null : tag;
        } catch (SQLException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.error("Failed to load cities from SQLite", exception);
            return null;
        }
    }

    private void saveCity(Connection connection, CompoundTag cityTag) throws SQLException {
        String cityId = cityTag.getUUID("CityId").toString();
        try (PreparedStatement cityStatement = connection.prepareStatement("INSERT INTO cities(city_id, city_name, core_x, core_y, core_z, funds, city_level) VALUES(?, ?, ?, ?, ?, ?, ?) ON CONFLICT(city_id) DO UPDATE SET city_name = excluded.city_name, core_x = excluded.core_x, core_y = excluded.core_y, core_z = excluded.core_z, funds = excluded.funds, city_level = excluded.city_level");
             PreparedStatement deleteMembers = connection.prepareStatement("DELETE FROM city_members WHERE city_id = ?");
             PreparedStatement deleteFinances = connection.prepareStatement("DELETE FROM finance_transactions WHERE city_id = ?");
             PreparedStatement memberStatement = connection.prepareStatement("INSERT INTO city_members(city_id, player_id, player_name, permission_level) VALUES(?, ?, ?, ?)");
             PreparedStatement financeStatement = connection.prepareStatement("INSERT INTO finance_transactions(city_id, sort_index, time, actor_id, actor_name, amount, balance_after, type, reason) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            cityStatement.setString(1, cityId);
            cityStatement.setString(2, cityTag.getString("CityName"));
            cityStatement.setInt(3, cityTag.getInt("CoreX"));
            cityStatement.setInt(4, cityTag.getInt("CoreY"));
            cityStatement.setInt(5, cityTag.getInt("CoreZ"));
            cityStatement.setDouble(6, cityTag.getDouble("Funds"));
            cityStatement.setInt(7, cityTag.getInt("CityLevel"));
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

    private ListTag loadCityMembers(Connection connection, String cityId) throws SQLException {
        ListTag members = new ListTag();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM city_members WHERE city_id = ? ORDER BY player_id")) {
            statement.setString(1, cityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CompoundTag member = new CompoundTag();
                    member.putUUID("PlayerId", java.util.UUID.fromString(resultSet.getString("player_id")));
                    member.putString("PlayerName", resultSet.getString("player_name"));
                    member.putString("PermissionLevel", resultSet.getString("permission_level"));
                    members.add(member);
                }
            }
        }
        return members;
    }

    private ListTag loadFinanceTransactions(Connection connection, String cityId) throws SQLException {
        ListTag finances = new ListTag();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM finance_transactions WHERE city_id = ? ORDER BY sort_index")) {
            statement.setString(1, cityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CompoundTag finance = new CompoundTag();
                    finance.putLong("Time", resultSet.getLong("time"));
                    SqliteNbtHelper.putNullableUuid(finance, "ActorId", resultSet.getString("actor_id"));
                    finance.putString("ActorName", resultSet.getString("actor_name"));
                    finance.putDouble("Amount", resultSet.getDouble("amount"));
                    finance.putDouble("BalanceAfter", resultSet.getDouble("balance_after"));
                    finance.putString("Type", resultSet.getString("type"));
                    finance.putString("Reason", resultSet.getString("reason"));
                    finances.add(finance);
                }
            }
        }
        return finances;
    }
}
