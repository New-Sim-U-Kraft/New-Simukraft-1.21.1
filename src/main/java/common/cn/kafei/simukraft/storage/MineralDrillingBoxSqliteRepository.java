package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** MineralDrillingBoxSqliteRepository: 按维度和方块位置保存钻井控制箱状态。 */
@SuppressWarnings("null")
public final class MineralDrillingBoxSqliteRepository {
    private static final String TABLE = "mineral_drilling_boxes";
    private final SimuSqliteDatabase database;

    /** MineralDrillingBoxSqliteRepository: 绑定当前存档数据库连接工厂。 */
    public MineralDrillingBoxSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /** saveAll: 经 callSync 在写线程的单事务内替换指定维度的控制箱快照，事务边界由写队列掌握。 */
    public synchronized boolean saveAll(String dimensionId, CompoundTag tag) {
        if (dimensionId == null || dimensionId.isBlank() || tag == null) {
            return false;
        }
        Boolean saved = database.callSync(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + TABLE + " WHERE dimension_id = ?")) {
                delete.setString(1, dimensionId);
                delete.executeUpdate();
            }
            ListTag boxes = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
            for (int index = 0; index < boxes.size(); index++) {
                saveBox(connection, dimensionId, boxes.getCompound(index));
            }
            return true;
        });
        if (saved == null || !saved) {
            SimuKraft.LOGGER.error("Failed to save mineral drilling boxes for dimension {}", dimensionId);
            return false;
        }
        return true;
    }

    /** upsert: 增量写入单个控制箱，避免滑杆修改触发整表重写。 */
    public synchronized boolean upsert(String dimensionId, CompoundTag boxTag) {
        if (dimensionId == null || dimensionId.isBlank() || boxTag == null) {
            return false;
        }
        Boolean saved = database.callSync(connection -> {
            saveBox(connection, dimensionId, boxTag);
            return true;
        });
        if (saved == null || !saved) {
            SimuKraft.LOGGER.error("Failed to save mineral drilling box at {} in {}", boxTag.getLong("BoxPos"), dimensionId);
            return false;
        }
        return true;
    }

    /** delete: 删除指定维度和位置的控制箱记录。 */
    public synchronized boolean delete(String dimensionId, long boxPosLong) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return false;
        }
        Boolean deleted = database.callSync(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + TABLE + " WHERE dimension_id = ? AND box_pos_long = ?")) {
                statement.setString(1, dimensionId);
                statement.setLong(2, boxPosLong);
                statement.executeUpdate();
            }
            return true;
        });
        if (deleted == null || !deleted) {
            SimuKraft.LOGGER.error("Failed to delete mineral drilling box at {} in {}", boxPosLong, dimensionId);
            return false;
        }
        return true;
    }

    /** loadAll: 读取指定维度的全部控制箱快照。 */
    public synchronized CompoundTag loadAll(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return null;
        }
        CompoundTag result = new CompoundTag();
        ListTag boxes = new ListTag();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT box_pos_long, drill_depth, lowest_reached_depth, running, status_key, status_text, selected_vein_id, inventory_nbt, updated_at, revision "
                             + "FROM " + TABLE + " WHERE dimension_id = ? ORDER BY box_pos_long")) {
            statement.setString(1, dimensionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CompoundTag box = new CompoundTag();
                    box.putLong("BoxPos", resultSet.getLong("box_pos_long"));
                    box.putInt("DrillDepth", resultSet.getInt("drill_depth"));
                    box.putInt("LowestReachedDepth", resultSet.getInt("lowest_reached_depth"));
                    box.putBoolean("Running", resultSet.getInt("running") != 0);
                    box.putString("StatusKey", safeText(resultSet.getString("status_key")));
                    box.putString("StatusText", safeText(resultSet.getString("status_text")));
                    box.putString("SelectedVeinId", safeText(resultSet.getString("selected_vein_id")));
                    box.putLong("UpdatedAt", Math.max(0L, resultSet.getLong("updated_at")));
                    box.putLong("Revision", Math.max(0L, resultSet.getLong("revision")));
                    putInventory(box, resultSet.getString("inventory_nbt"));
                    boxes.add(box);
                }
            }
            result.put("Boxes", boxes);
            return boxes.isEmpty() ? null : result;
        } catch (SQLException exception) {
            database.markDegraded("loadAll(mineralDrillingBoxes)", exception);
            SimuKraft.LOGGER.error("Failed to load mineral drilling boxes for dimension {}", dimensionId, exception);
            return null;
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.error("Failed to load mineral drilling boxes for dimension {}", dimensionId, exception);
            return null;
        }
    }

    /** saveBox: 使用复合主键插入或更新单个控制箱。 */
    private static void saveBox(Connection connection, String dimensionId, CompoundTag box) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + TABLE + "(dimension_id, box_pos_long, drill_depth, lowest_reached_depth, running, status_key, status_text, selected_vein_id, inventory_nbt, updated_at, revision) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(dimension_id, box_pos_long) DO UPDATE SET drill_depth = excluded.drill_depth, lowest_reached_depth = excluded.lowest_reached_depth, running = excluded.running, status_key = excluded.status_key, status_text = excluded.status_text, selected_vein_id = excluded.selected_vein_id, inventory_nbt = excluded.inventory_nbt, updated_at = excluded.updated_at, revision = excluded.revision "
                        + "WHERE excluded.revision >= " + TABLE + ".revision")) {
            statement.setString(1, dimensionId);
            statement.setLong(2, box.getLong("BoxPos"));
            statement.setInt(3, box.getInt("DrillDepth"));
            statement.setInt(4, box.contains("LowestReachedDepth")
                    ? box.getInt("LowestReachedDepth") : Integer.MAX_VALUE);
            statement.setInt(5, box.getBoolean("Running") ? 1 : 0);
            statement.setString(6, safeText(box.getString("StatusKey")));
            statement.setString(7, safeText(box.getString("StatusText")));
            statement.setString(8, safeText(box.getString("SelectedVeinId")));
            statement.setString(9, inventoryText(box.getCompound("Inventory")));
            statement.setLong(10, Math.max(0L, box.getLong("UpdatedAt")));
            statement.setLong(11, Math.max(0L, box.getLong("Revision")));
            statement.executeUpdate();
        }
    }

    /** inventoryText: 将两格库存 NBT 编码为可回读的 SNBT。 */
    private static String inventoryText(CompoundTag inventory) {
        return inventory == null || inventory.isEmpty() ? "{}" : inventory.toString();
    }

    /** putInventory: 解析 SNBT；单行损坏时回退为空库存。 */
    private static void putInventory(CompoundTag box, String serialized) {
        if (serialized == null || serialized.isBlank()) {
            box.put("Inventory", new CompoundTag());
            return;
        }
        try {
            box.put("Inventory", TagParser.parseTag(serialized));
        } catch (CommandSyntaxException | RuntimeException exception) {
            // 单个控制箱的损坏槽位不应阻止同一维度其他控制箱加载。
            SimuKraft.LOGGER.warn("Invalid mineral drilling inventory NBT; loading empty inventory", exception);
            box.put("Inventory", new CompoundTag());
        }
    }

    /** safeText: 将 SQLite NULL 规范为空字符串。 */
    private static String safeText(String value) {
        return value != null ? value : "";
    }
}
