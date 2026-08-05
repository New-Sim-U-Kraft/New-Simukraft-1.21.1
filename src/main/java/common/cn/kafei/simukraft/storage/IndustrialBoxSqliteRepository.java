package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@SuppressWarnings("null")
public final class IndustrialBoxSqliteRepository {
    private static final String UPSERT_SQL =
            "INSERT INTO industrial_boxes(box_pos_long, building_id, definition_id, selected_recipe_id, running, spawn_entity_done, current_step, status_key, status_text, machine_state, work_state, updated_at, step_elapsed_ticks, worker_work_pos_long) "
                    + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(box_pos_long) DO UPDATE SET building_id = excluded.building_id, definition_id = excluded.definition_id, selected_recipe_id = excluded.selected_recipe_id, running = excluded.running, spawn_entity_done = excluded.spawn_entity_done, current_step = excluded.current_step, status_key = excluded.status_key, status_text = excluded.status_text, machine_state = excluded.machine_state, work_state = excluded.work_state, updated_at = excluded.updated_at, step_elapsed_ticks = excluded.step_elapsed_ticks, worker_work_pos_long = excluded.worker_work_pos_long";

    private final SimuSqliteDatabase database;

    public IndustrialBoxSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /**
     * saveAll: 把内存中的工业盒全部 upsert 进库。
     * <p>不再清空整表再重写：该表没有 dimension_id，而管理器是按维度的，
     * 关服时逐维度保存会让每个维度都清空全表，最终只剩最后一个维度的数据。删除只走 {@link #delete(Connection, long)}。
     */
    public void saveAll(Connection connection, CompoundTag tag) throws SQLException {
        ListTag boxes = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        if (boxes.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            for (int i = 0; i < boxes.size(); i++) {
                bindBox(statement, boxes.getCompound(i));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public void upsert(Connection connection, CompoundTag boxTag) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            bindBox(statement, boxTag);
            statement.executeUpdate();
        }
    }

    public void delete(Connection connection, long boxPosLong) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM industrial_boxes WHERE box_pos_long = ?")) {
            statement.setLong(1, boxPosLong);
            statement.executeUpdate();
        }
    }

    public synchronized CompoundTag loadAll() {
        CompoundTag tag = new CompoundTag();
        ListTag boxes = new ListTag();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM industrial_boxes ORDER BY box_pos_long");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                CompoundTag box = new CompoundTag();
                box.putLong("BoxPos", resultSet.getLong("box_pos_long"));
                box.putString("BuildingId", resultSet.getString("building_id"));
                box.putString("DefinitionId", resultSet.getString("definition_id"));
                box.putString("SelectedRecipeId", resultSet.getString("selected_recipe_id"));
                box.putBoolean("Running", resultSet.getInt("running") != 0);
                box.putBoolean("SpawnEntityDone", resultSet.getInt("spawn_entity_done") != 0);
                box.putInt("CurrentStep", resultSet.getInt("current_step"));
                box.putString("StatusKey", resultSet.getString("status_key"));
                box.putString("StatusText", resultSet.getString("status_text"));
                box.putString("MachineState", resultSet.getString("machine_state"));
                box.putString("WorkState", resultSet.getString("work_state"));
                box.putLong("UpdatedAt", resultSet.getLong("updated_at"));
                box.putLong("StepElapsedTicks", resultSet.getLong("step_elapsed_ticks"));
                box.putLong("WorkerWorkPos", resultSet.getLong("worker_work_pos_long"));
                boxes.add(box);
            }
            tag.put("Boxes", boxes);
            return boxes.isEmpty() ? null : tag;
        } catch (SQLException exception) {
            database.markDegraded("loadAll(industrialBoxes)", exception);
            SimuKraft.LOGGER.error("Failed to load industrial boxes from SQLite", exception);
            return null;
        }
    }

    private static void bindBox(PreparedStatement statement, CompoundTag box) throws SQLException {
        statement.setLong(1, box.getLong("BoxPos"));
        statement.setString(2, box.getString("BuildingId"));
        statement.setString(3, box.getString("DefinitionId"));
        statement.setString(4, box.getString("SelectedRecipeId"));
        statement.setInt(5, box.getBoolean("Running") ? 1 : 0);
        statement.setInt(6, box.getBoolean("SpawnEntityDone") ? 1 : 0);
        statement.setInt(7, box.getInt("CurrentStep"));
        statement.setString(8, box.getString("StatusKey"));
        statement.setString(9, box.getString("StatusText"));
        statement.setString(10, box.getString("MachineState"));
        statement.setString(11, box.getString("WorkState"));
        statement.setLong(12, box.getLong("UpdatedAt"));
        statement.setLong(13, box.getLong("StepElapsedTicks"));
        statement.setLong(14, box.contains("WorkerWorkPos") ? box.getLong("WorkerWorkPos") : Long.MIN_VALUE);
    }
}
