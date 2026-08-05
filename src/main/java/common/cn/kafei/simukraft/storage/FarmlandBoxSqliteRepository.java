package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * 农田盒配置的 SQLite 仓库。读写格式与 FarmlandBoxData.toTag/fromTag 对齐：
 * 列里的 NULL 表示"未设置"，作物/区域/箱子缺省时不写对应键，保证视图能正确显示"未配置"。
 */
public final class FarmlandBoxSqliteRepository {
    private final SimuSqliteDatabase database;

    public FarmlandBoxSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /**
     * saveAll: 把内存中的农田盒全部 upsert 进库。
     * <p>不再清空整表再重写：该表没有 dimension_id，而管理器是按维度的，
     * 关服时逐维度保存会让每个维度都清空全表，最终只剩最后一个维度的数据。删除只走 {@link #delete(Connection, long)}。
     */
    public void saveAll(Connection connection, CompoundTag tag) throws SQLException {
        ListTag boxes = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        if (!boxes.isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO farmland_boxes(box_pos_long, crop, plot_min_long, plot_max_long, chest_pos_long, running) VALUES(?, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT(box_pos_long) DO UPDATE SET crop = excluded.crop, plot_min_long = excluded.plot_min_long, plot_max_long = excluded.plot_max_long, chest_pos_long = excluded.chest_pos_long, running = excluded.running")) {
                for (int i = 0; i < boxes.size(); i++) {
                    CompoundTag box = boxes.getCompound(i);
                    statement.setLong(1, box.getLong("BoxPos"));
                    if (box.contains("Crop")) { statement.setString(2, box.getString("Crop")); }
                    else { statement.setNull(2, java.sql.Types.VARCHAR); }
                    if (box.contains("Plot")) {
                        CompoundTag plot = box.getCompound("Plot");
                        statement.setLong(3, plot.getLong("Min"));
                        statement.setLong(4, plot.getLong("Max"));
                    } else {
                        statement.setNull(3, java.sql.Types.INTEGER);
                        statement.setNull(4, java.sql.Types.INTEGER);
                    }
                    if (box.contains("ChestPos")) { statement.setLong(5, box.getLong("ChestPos")); }
                    else { statement.setNull(5, java.sql.Types.INTEGER); }
                    statement.setInt(6, box.getBoolean("Running") ? 1 : 0);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }

    public void upsert(Connection connection, CompoundTag boxTag) throws SQLException {
        saveBox(connection, boxTag);
    }

    public void delete(Connection connection, long boxPosLong) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM farmland_boxes WHERE box_pos_long = ?")) {
            statement.setLong(1, boxPosLong);
            statement.executeUpdate();
        }
    }

    public synchronized CompoundTag loadAll() {
        CompoundTag tag = new CompoundTag();
        ListTag boxes = new ListTag();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM farmland_boxes ORDER BY box_pos_long");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                CompoundTag box = new CompoundTag();
                box.putLong("BoxPos", resultSet.getLong("box_pos_long"));
                String crop = resultSet.getString("crop");
                if (crop != null && !crop.isBlank()) {
                    box.putString("Crop", crop);
                }
                long plotMin = resultSet.getLong("plot_min_long");
                boolean plotMinPresent = !resultSet.wasNull();
                long plotMax = resultSet.getLong("plot_max_long");
                boolean plotMaxPresent = !resultSet.wasNull();
                if (plotMinPresent && plotMaxPresent) {
                    CompoundTag plot = new CompoundTag();
                    plot.putLong("Min", plotMin);
                    plot.putLong("Max", plotMax);
                    box.put("Plot", plot);
                }
                long chest = resultSet.getLong("chest_pos_long");
                if (!resultSet.wasNull()) {
                    box.putLong("ChestPos", chest);
                }
                box.putBoolean("Running", resultSet.getInt("running") != 0);
                boxes.add(box);
            }
            tag.put("Boxes", boxes);
            return boxes.isEmpty() ? null : tag;
        } catch (SQLException exception) {
            database.markDegraded("loadAll(farmlandBoxes)", exception);
            SimuKraft.LOGGER.error("Failed to load farmland boxes from SQLite", exception);
            return null;
        }
    }

    private void saveBox(Connection connection, CompoundTag box) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO farmland_boxes(box_pos_long, crop, plot_min_long, plot_max_long, chest_pos_long, running) VALUES(?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(box_pos_long) DO UPDATE SET crop = excluded.crop, plot_min_long = excluded.plot_min_long, plot_max_long = excluded.plot_max_long, chest_pos_long = excluded.chest_pos_long, running = excluded.running")) {
            statement.setLong(1, box.getLong("BoxPos"));
            if (box.contains("Crop")) {
                statement.setString(2, box.getString("Crop"));
            } else {
                statement.setNull(2, Types.VARCHAR);
            }
            if (box.contains("Plot")) {
                CompoundTag plot = box.getCompound("Plot");
                statement.setLong(3, plot.getLong("Min"));
                statement.setLong(4, plot.getLong("Max"));
            } else {
                statement.setNull(3, Types.INTEGER);
                statement.setNull(4, Types.INTEGER);
            }
            if (box.contains("ChestPos")) {
                statement.setLong(5, box.getLong("ChestPos"));
            } else {
                statement.setNull(5, Types.INTEGER);
            }
            statement.setInt(6, box.getBoolean("Running") ? 1 : 0);
            statement.executeUpdate();
        }
    }
}
