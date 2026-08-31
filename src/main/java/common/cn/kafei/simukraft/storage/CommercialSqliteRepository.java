package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("null")
public final class CommercialSqliteRepository {
    private final SimuSqliteDatabase database;

    public CommercialSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /**
     * saveBoxes: 把当前维度内存中的商业箱全部 upsert 进库。
     * <p>不再清空整表再重写：删除只走 {@link #deleteBox(Connection, long, String)}。
     */
    public void saveBoxes(Connection connection, CompoundTag tag, String dimensionId) throws SQLException {
        String normalized = normalizeDimensionId(dimensionId);
        ListTag boxes = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < boxes.size(); i++) {
            saveBox(connection, boxes.getCompound(i), normalized);
        }
    }

    /** upsertBox: 保存单个商业箱状态。 */
    public void upsertBox(Connection connection, CompoundTag boxTag, String dimensionId) throws SQLException {
        saveBox(connection, boxTag, normalizeDimensionId(dimensionId));
    }

    /** deleteBox: 删除指定维度的商业箱和其库存。 */
    public void deleteBox(Connection connection, long boxPosLong, String dimensionId) throws SQLException {
        String normalized = normalizeDimensionId(dimensionId);
        try (PreparedStatement stockStatement = connection.prepareStatement(
                "DELETE FROM commercial_stock WHERE dimension_id = ? AND box_pos_long = ?");
             PreparedStatement boxStatement = connection.prepareStatement(
                     "DELETE FROM commercial_boxes WHERE dimension_id = ? AND box_pos_long = ?")) {
            stockStatement.setString(1, normalized);
            stockStatement.setLong(2, boxPosLong);
            stockStatement.executeUpdate();
            boxStatement.setString(1, normalized);
            boxStatement.setLong(2, boxPosLong);
            boxStatement.executeUpdate();
        }
    }

    /** loadBoxes: 读取指定维度的商业箱状态。 */
    public synchronized CompoundTag loadBoxes(String dimensionId) {
        CompoundTag tag = new CompoundTag();
        ListTag boxes = new ListTag();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM commercial_boxes WHERE dimension_id = ? ORDER BY box_pos_long")) {
            statement.setString(1, normalizeDimensionId(dimensionId));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CompoundTag box = new CompoundTag();
                    box.putLong("BoxPos", resultSet.getLong("box_pos_long"));
                    box.putString("BuildingId", resultSet.getString("building_id"));
                    box.putString("DefinitionId", resultSet.getString("definition_id"));
                    box.putBoolean("Running", resultSet.getInt("running") != 0);
                    box.putString("StatusKey", resultSet.getString("status_key"));
                    box.putString("StatusText", resultSet.getString("status_text"));
                    box.putLong("UpdatedAt", resultSet.getLong("updated_at"));
                    boxes.add(box);
                }
            }
            tag.put("Boxes", boxes);
            return boxes.isEmpty() ? null : tag;
        } catch (SQLException exception) {
            database.markDegraded("loadBoxes(commercial)", exception);
            SimuKraft.LOGGER.error("Failed to load commercial boxes from SQLite", exception);
            return null;
        }
    }

    /**
     * saveStock: 把当前维度内存中的商业库存全部 upsert 进库。
     * <p>不再清空整表再重写，库存删除只走 {@link #deleteStockAtBox(Connection, long, String)}。
     */
    public void saveStock(Connection connection, CompoundTag tag, String dimensionId) throws SQLException {
        String normalized = normalizeDimensionId(dimensionId);
        ListTag stock = tag.getList("Stock", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < stock.size(); i++) {
            saveStockEntry(connection, stock.getCompound(i), normalized);
        }
    }

    /** upsertStockEntry: 保存单个商业库存条目。 */
    public void upsertStockEntry(Connection connection, CompoundTag stockTag, String dimensionId) throws SQLException {
        saveStockEntry(connection, stockTag, normalizeDimensionId(dimensionId));
    }

    /** deleteStockAtBox: 删除指定维度商业箱库存。 */
    public void deleteStockAtBox(Connection connection, long boxPosLong, String dimensionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM commercial_stock WHERE dimension_id = ? AND box_pos_long = ?")) {
            statement.setString(1, normalizeDimensionId(dimensionId));
            statement.setLong(2, boxPosLong);
            statement.executeUpdate();
        }
    }

    /** loadStock: 读取指定维度的商业库存。 */
    public synchronized CompoundTag loadStock(String dimensionId) {
        CompoundTag tag = new CompoundTag();
        ListTag stock = new ListTag();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM commercial_stock WHERE dimension_id = ? ORDER BY box_pos_long, item_id")) {
            statement.setString(1, normalizeDimensionId(dimensionId));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CompoundTag entry = new CompoundTag();
                    entry.putLong("BoxPos", resultSet.getLong("box_pos_long"));
                    entry.putString("ItemId", resultSet.getString("item_id"));
                    entry.putInt("CurrentStock", resultSet.getInt("current_stock"));
                    entry.putInt("MaxStock", resultSet.getInt("max_stock"));
                    entry.putLong("LastRestockGameTime", resultSet.getLong("last_restock_game_time"));
                    entry.putLong("UpdatedAt", resultSet.getLong("updated_at"));
                    stock.add(entry);
                }
            }
            tag.put("Stock", stock);
            return stock.isEmpty() ? null : tag;
        } catch (SQLException exception) {
            database.markDegraded("loadStock(commercial)", exception);
            SimuKraft.LOGGER.error("Failed to load commercial stock from SQLite", exception);
            return null;
        }
    }

    /** addDailyIncome: 累加指定城市在某个 MC 日的商业营业收入。在写线程执行并同步等待结果。 */
    public boolean addDailyIncome(UUID cityId, long incomeDay, double amount) {
        if (cityId == null || incomeDay <= 0L || amount <= 0.0D) {
            return false;
        }
        Boolean saved = database.callSync(connection -> addDailyIncome(connection, cityId, incomeDay, amount));
        return saved != null && saved;
    }

    private boolean addDailyIncome(Connection connection, UUID cityId, long incomeDay, double amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO commercial_daily_income(city_id, income_day, income, tax_collected) "
                        + "VALUES(?, ?, ?, 0) "
                        + "ON CONFLICT(city_id, income_day) DO UPDATE SET income = income + excluded.income")) {
            statement.setString(1, cityId.toString());
            statement.setLong(2, incomeDay);
            statement.setDouble(3, amount);
            return statement.executeUpdate() > 0;
        }
    }

    /** loadUntaxedIncomeBefore: 读取指定日期之前尚未上交企业税的商业收入。 */
    public synchronized Map<UUID, Double> loadUntaxedIncomeBefore(long dayExclusive) {
        if (dayExclusive <= 1L) {
            return Map.of();
        }
        Map<UUID, Double> result = new LinkedHashMap<>();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT city_id, SUM(income) AS income FROM commercial_daily_income "
                             + "WHERE income_day < ? AND tax_collected = 0 GROUP BY city_id")) {
            statement.setLong(1, dayExclusive);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.put(UUID.fromString(resultSet.getString("city_id")), resultSet.getDouble("income"));
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.error("Failed to load untaxed commercial income from SQLite", exception);
            return Map.of();
        }
        return Map.copyOf(result);
    }

    /** markIncomeTaxCollectedBefore: 标记指定城市在日期之前的商业收入已完成企业税结算。在写线程执行并同步等待结果。 */
    public boolean markIncomeTaxCollectedBefore(UUID cityId, long dayExclusive) {
        if (cityId == null || dayExclusive <= 1L) {
            return false;
        }
        Boolean marked = database.callSync(connection -> markIncomeTaxCollectedBefore(connection, cityId, dayExclusive));
        return marked != null && marked;
    }

    private boolean markIncomeTaxCollectedBefore(Connection connection, UUID cityId, long dayExclusive) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE commercial_daily_income SET tax_collected = 1 "
                        + "WHERE city_id = ? AND income_day < ? AND tax_collected = 0")) {
            statement.setString(1, cityId.toString());
            statement.setLong(2, dayExclusive);
            return statement.executeUpdate() > 0;
        }
    }

    private void saveBox(Connection connection, CompoundTag box, String dimensionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO commercial_boxes(dimension_id, box_pos_long, building_id, definition_id, running, status_key, status_text, updated_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(dimension_id, box_pos_long) DO UPDATE SET building_id = excluded.building_id, definition_id = excluded.definition_id, running = excluded.running, status_key = excluded.status_key, status_text = excluded.status_text, updated_at = excluded.updated_at")) {
            statement.setString(1, dimensionId);
            statement.setLong(2, box.getLong("BoxPos"));
            statement.setString(3, box.getString("BuildingId"));
            statement.setString(4, box.getString("DefinitionId"));
            statement.setInt(5, box.getBoolean("Running") ? 1 : 0);
            statement.setString(6, box.getString("StatusKey"));
            statement.setString(7, box.getString("StatusText"));
            statement.setLong(8, box.getLong("UpdatedAt"));
            statement.executeUpdate();
        }
    }

    private void saveStockEntry(Connection connection, CompoundTag stock, String dimensionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO commercial_stock(dimension_id, box_pos_long, item_id, current_stock, max_stock, last_restock_game_time, updated_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(dimension_id, box_pos_long, item_id) DO UPDATE SET current_stock = excluded.current_stock, max_stock = excluded.max_stock, last_restock_game_time = excluded.last_restock_game_time, updated_at = excluded.updated_at")) {
            statement.setString(1, dimensionId);
            statement.setLong(2, stock.getLong("BoxPos"));
            statement.setString(3, stock.getString("ItemId"));
            statement.setInt(4, stock.getInt("CurrentStock"));
            statement.setInt(5, stock.getInt("MaxStock"));
            statement.setLong(6, stock.getLong("LastRestockGameTime"));
            statement.setLong(7, stock.getLong("UpdatedAt"));
            statement.executeUpdate();
        }
    }

    /** normalizeDimensionId: 空维度归入主世界，和仓库层其他表口径一致。 */
    private static String normalizeDimensionId(String dimensionId) {
        return dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
    }
}
