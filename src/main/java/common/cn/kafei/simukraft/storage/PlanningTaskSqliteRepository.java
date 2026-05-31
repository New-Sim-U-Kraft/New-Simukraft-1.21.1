package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.planner.PlanOperation;
import common.cn.kafei.simukraft.planner.PlanningTaskData;
import net.minecraft.core.BlockPos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 规划任务的 SQLite 仓库。任务扁平存储（区域用 min/max 的 long 表示），按维度加载用于恢复，按市民删除。
 */
@SuppressWarnings("null")
public final class PlanningTaskSqliteRepository {
    private final SimuSqliteDatabase database;

    public PlanningTaskSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    public synchronized void upsert(PlanningTaskData task) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO planning_tasks(task_id, citizen_id, city_id, dimension_id, box_long, min_long, max_long, operation, fill_block, source_block, current_index, total_blocks, status, created_at, updated_at) "
                             + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                             + "ON CONFLICT(task_id) DO UPDATE SET citizen_id = excluded.citizen_id, city_id = excluded.city_id, dimension_id = excluded.dimension_id, box_long = excluded.box_long, min_long = excluded.min_long, max_long = excluded.max_long, operation = excluded.operation, fill_block = excluded.fill_block, source_block = excluded.source_block, current_index = excluded.current_index, total_blocks = excluded.total_blocks, status = excluded.status, updated_at = excluded.updated_at")) {
            statement.setString(1, task.taskId().toString());
            statement.setString(2, task.citizenId().toString());
            statement.setString(3, task.cityId() != null ? task.cityId().toString() : null);
            statement.setString(4, task.dimensionId());
            statement.setLong(5, task.buildBoxPos().asLong());
            statement.setLong(6, task.minPos().asLong());
            statement.setLong(7, task.maxPos().asLong());
            statement.setString(8, task.operation().id());
            statement.setString(9, task.fillBlockId());
            statement.setString(10, task.sourceBlockId());
            statement.setInt(11, task.currentIndex());
            statement.setInt(12, task.totalBlocks());
            statement.setString(13, task.status());
            statement.setLong(14, task.createdAt());
            statement.setLong(15, task.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save planning task to SQLite", exception);
        }
    }

    public synchronized List<PlanningTaskData> findByDimension(String dimensionId) {
        List<PlanningTaskData> tasks = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM planning_tasks WHERE dimension_id = ?")) {
            statement.setString(1, dimensionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tasks.add(read(resultSet));
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.error("Failed to load planning tasks from SQLite", exception);
        }
        return tasks;
    }

    public synchronized void deleteByCitizen(UUID citizenId) {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM planning_tasks WHERE citizen_id = ?")) {
            statement.setString(1, citizenId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to delete planning task from SQLite", exception);
        }
    }

    private PlanningTaskData read(ResultSet resultSet) throws SQLException {
        String cityIdString = resultSet.getString("city_id");
        return new PlanningTaskData(
                UUID.fromString(resultSet.getString("task_id")),
                UUID.fromString(resultSet.getString("citizen_id")),
                cityIdString != null ? UUID.fromString(cityIdString) : null,
                resultSet.getString("dimension_id"),
                BlockPos.of(resultSet.getLong("box_long")),
                BlockPos.of(resultSet.getLong("min_long")),
                BlockPos.of(resultSet.getLong("max_long")),
                PlanOperation.fromId(resultSet.getString("operation")),
                resultSet.getString("fill_block"),
                resultSet.getString("source_block"),
                resultSet.getInt("current_index"),
                resultSet.getInt("total_blocks"),
                resultSet.getString("status"),
                resultSet.getLong("created_at"),
                resultSet.getLong("updated_at"));
    }
}
