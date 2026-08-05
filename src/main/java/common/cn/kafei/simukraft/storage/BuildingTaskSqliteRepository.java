package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingPoiDefinition;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.core.BlockPos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BuildingTaskSqliteRepository {
    private final SimuSqliteDatabase database;

    public BuildingTaskSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    public void upsert(Connection connection, BuildingTaskData task) throws SQLException {
        saveTask(connection, task);
    }

    public synchronized BuildingTaskData findByCitizen(UUID citizenId) {
        if (citizenId == null) {
            return null;
        }
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM building_tasks WHERE citizen_id = ?")) {
            statement.setString(1, citizenId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                UUID taskId = UUID.fromString(resultSet.getString("task_id"));
                return new BuildingTaskData(
                        taskId,
                        UUID.fromString(resultSet.getString("citizen_id")),
                        nullableUuid(resultSet.getString("city_id")),
                        resultSet.getString("dimension_id"),
                        new BlockPos(resultSet.getInt("build_box_x"), resultSet.getInt("build_box_y"), resultSet.getInt("build_box_z")),
                        resultSet.getString("category"),
                        resultSet.getString("building_file_name"),
                        resultSet.getString("display_name"),
                        resultSet.getString("amount"),
                        resultSet.getString("structure_file_name"),
                        new BlockPos(resultSet.getInt("origin_x"), resultSet.getInt("origin_y"), resultSet.getInt("origin_z")),
                        resultSet.getInt("rotation_degrees"),
                        resultSet.getInt("current_block_index"),
                        resultSet.getInt("total_blocks"),
                        resultSet.getString("status"),
                        resultSet.getLong("created_at"),
                        resultSet.getLong("updated_at"),
                        loadTaskPois(connection, taskId),
                        resultSet.getInt("replace_with_air") != 0
                );
            }
        } catch (SQLException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.error("Failed to load building task by citizen", exception);
            return null;
        }
    }

    public synchronized List<BuildingTaskData> findByDimension(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return List.of();
        }
        List<BuildingTaskData> tasks = new ArrayList<>();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM building_tasks WHERE dimension_id = ? ORDER BY updated_at")) {
            statement.setString(1, dimensionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID taskId = UUID.fromString(resultSet.getString("task_id"));
                    tasks.add(readTask(connection, resultSet, taskId));
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            database.markDegraded("findByDimension(buildingTasks)", exception);
            SimuKraft.LOGGER.error("Failed to load building tasks by dimension", exception);
            return List.of();
        }
        return List.copyOf(tasks);
    }

    public void deleteByCitizen(Connection connection, UUID citizenId) throws SQLException {
        if (citizenId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM building_tasks WHERE citizen_id = ?")) {
            statement.setString(1, citizenId.toString());
            statement.executeUpdate();
        }
    }

    private void saveTask(Connection connection, BuildingTaskData task) throws SQLException {
        // 冲突目标是业务唯一键 citizen_id 而不是 task_id：cancel→start 会以新 taskId 重新 upsert，
        // 写队列合并可能吞掉中间的 delete，按 citizen_id 收敛无论旧行是否还在都落库正确。
        try (PreparedStatement deleteLegacyPois = connection.prepareStatement("DELETE FROM building_task_pois WHERE task_id IN (SELECT task_id FROM building_tasks WHERE citizen_id = ?)");
             PreparedStatement taskStatement = connection.prepareStatement("INSERT INTO building_tasks(task_id, citizen_id, city_id, dimension_id, build_box_x, build_box_y, build_box_z, category, building_file_name, display_name, amount, structure_file_name, origin_x, origin_y, origin_z, rotation_degrees, current_block_index, total_blocks, status, created_at, updated_at, replace_with_air) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(citizen_id) DO UPDATE SET task_id = excluded.task_id, city_id = excluded.city_id, dimension_id = excluded.dimension_id, build_box_x = excluded.build_box_x, build_box_y = excluded.build_box_y, build_box_z = excluded.build_box_z, category = excluded.category, building_file_name = excluded.building_file_name, display_name = excluded.display_name, amount = excluded.amount, structure_file_name = excluded.structure_file_name, origin_x = excluded.origin_x, origin_y = excluded.origin_y, origin_z = excluded.origin_z, rotation_degrees = excluded.rotation_degrees, current_block_index = excluded.current_block_index, total_blocks = excluded.total_blocks, status = excluded.status, created_at = excluded.created_at, updated_at = excluded.updated_at, replace_with_air = excluded.replace_with_air");
             PreparedStatement deletePois = connection.prepareStatement("DELETE FROM building_task_pois WHERE task_id = ?");
             PreparedStatement poiStatement = connection.prepareStatement("INSERT INTO building_task_pois(task_id, poi_key, poi_type, capacity) VALUES(?, ?, ?, ?)") ) {
            // upsert 触发 citizen_id 冲突时 task_id 会被改写成新值，而 building_task_pois 的外键
            // 没有 ON UPDATE CASCADE：必须先按旧 task_id 清掉子表行，否则外键约束让整条 upsert 失败。
            deleteLegacyPois.setString(1, task.citizenId().toString());
            deleteLegacyPois.executeUpdate();

            taskStatement.setString(1, task.taskId().toString());
            taskStatement.setString(2, task.citizenId().toString());
            SqliteNbtHelper.setNullableString(taskStatement, 3, task.cityId() != null ? task.cityId().toString() : null);
            taskStatement.setString(4, task.dimensionId());
            taskStatement.setInt(5, task.buildBoxPos().getX());
            taskStatement.setInt(6, task.buildBoxPos().getY());
            taskStatement.setInt(7, task.buildBoxPos().getZ());
            taskStatement.setString(8, task.category());
            taskStatement.setString(9, task.buildingFileName());
            taskStatement.setString(10, task.displayName());
            taskStatement.setString(11, task.amount());
            taskStatement.setString(12, task.structureFileName());
            taskStatement.setInt(13, task.origin().getX());
            taskStatement.setInt(14, task.origin().getY());
            taskStatement.setInt(15, task.origin().getZ());
            taskStatement.setInt(16, task.rotationDegrees());
            taskStatement.setInt(17, task.currentBlockIndex());
            taskStatement.setInt(18, task.totalBlocks());
            taskStatement.setString(19, task.status());
            taskStatement.setLong(20, task.createdAt());
            taskStatement.setLong(21, task.updatedAt());
            taskStatement.setInt(22, task.replaceWithAir() ? 1 : 0);
            taskStatement.executeUpdate();

            deletePois.setString(1, task.taskId().toString());
            deletePois.executeUpdate();
            for (BuildingPoiDefinition poi : task.poiDefinitions()) {
                poiStatement.setString(1, task.taskId().toString());
                poiStatement.setString(2, poi.id());
                poiStatement.setString(3, poi.poiType().name());
                poiStatement.setInt(4, poi.capacity());
                poiStatement.addBatch();
            }
            poiStatement.executeBatch();
        }
    }

    private BuildingTaskData readTask(Connection connection, ResultSet resultSet, UUID taskId) throws SQLException {
        return new BuildingTaskData(
                taskId,
                UUID.fromString(resultSet.getString("citizen_id")),
                nullableUuid(resultSet.getString("city_id")),
                resultSet.getString("dimension_id"),
                new BlockPos(resultSet.getInt("build_box_x"), resultSet.getInt("build_box_y"), resultSet.getInt("build_box_z")),
                resultSet.getString("category"),
                resultSet.getString("building_file_name"),
                resultSet.getString("display_name"),
                resultSet.getString("amount"),
                resultSet.getString("structure_file_name"),
                new BlockPos(resultSet.getInt("origin_x"), resultSet.getInt("origin_y"), resultSet.getInt("origin_z")),
                resultSet.getInt("rotation_degrees"),
                resultSet.getInt("current_block_index"),
                resultSet.getInt("total_blocks"),
                resultSet.getString("status"),
                resultSet.getLong("created_at"),
                resultSet.getLong("updated_at"),
                loadTaskPois(connection, taskId),
                resultSet.getInt("replace_with_air") != 0
        );
    }

    private List<BuildingPoiDefinition> loadTaskPois(Connection connection, UUID taskId) throws SQLException {
        List<BuildingPoiDefinition> pois = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM building_task_pois WHERE task_id = ? ORDER BY poi_key")) {
            statement.setString(1, taskId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    pois.add(new BuildingPoiDefinition(resultSet.getString("poi_key"), CityPoiType.fromName(resultSet.getString("poi_type")), resultSet.getInt("capacity")));
                }
            }
        }
        return List.copyOf(pois);
    }

    private static UUID nullableUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
