package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.building.BuildingPoiDefinition;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.planner.PlanOperation;
import common.cn.kafei.simukraft.planner.PlanningTaskData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 回归：cancel→start 以新 taskId 直接 upsert 时必须按业务唯一键 citizen_id 收敛
 * （写队列的同 key 合并可能吞掉中间的 delete）；居民被删除时其未完成任务必须一并清除。
 */
class TaskRepositoryConvergenceTest {
    private static final String DIMENSION = "minecraft:overworld";

    @TempDir
    Path tempDir;

    @Test
    void buildingTaskUpsertWithNewTaskIdReplacesCancelledTask() throws Exception {
        UUID citizenId = UUID.randomUUID();
        UUID oldTaskId = UUID.randomUUID();
        UUID newTaskId = UUID.randomUUID();

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("building-tasks.sqlite"))) {
            BuildingTaskSqliteRepository repository = new BuildingTaskSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.upsert(connection, buildingTask(oldTaskId, citizenId, 10));
            }
            // cancelTask 的 delete 被合并吞掉后，startTask 以新 taskId 直接 upsert（旧行仍在库中）。
            // 修复前：INSERT 撞 UNIQUE(citizen_id) 失败，新任务丢失、已取消的旧任务留在表里复活。
            try (Connection connection = database.borrowConnection()) {
                repository.upsert(connection, buildingTask(newTaskId, citizenId, 20));
            }

            BuildingTaskData loaded = repository.findByCitizen(citizenId);
            assertNotNull(loaded, "新任务必须落库成功");
            assertEquals(newTaskId, loaded.taskId(), "必须收敛到新任务而不是保留已取消的旧任务");
            assertEquals(20, loaded.currentBlockIndex());
            assertEquals(1, countRows(database, "building_tasks"), "旧任务行不得残留");
            assertEquals(1, countRows(database, "building_task_pois"), "旧任务的 POI 必须清掉、新 POI 写入");
            assertEquals(1, loaded.poiDefinitions().size());
        }
    }

    @Test
    void planningTaskUpsertWithNewTaskIdReplacesCancelledTask() throws Exception {
        UUID citizenId = UUID.randomUUID();
        UUID oldTaskId = UUID.randomUUID();
        UUID newTaskId = UUID.randomUUID();

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("planning-tasks.sqlite"))) {
            PlanningTaskSqliteRepository repository = new PlanningTaskSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.upsert(connection, planningTask(oldTaskId, citizenId));
            }
            try (Connection connection = database.borrowConnection()) {
                repository.upsert(connection, planningTask(newTaskId, citizenId));
            }

            List<PlanningTaskData> loaded = repository.findByDimension(DIMENSION);
            assertEquals(1, loaded.size(), "旧任务行不得残留");
            assertEquals(newTaskId, loaded.get(0).taskId(), "必须收敛到新任务");
        }
    }

    @Test
    void deletingCitizenRemovesTheirUnfinishedTasks() throws Exception {
        UUID citizenId = UUID.randomUUID();

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("citizen-delete.sqlite"))) {
            CitizenSqliteRepository citizens = new CitizenSqliteRepository(database);
            BuildingTaskSqliteRepository buildingTasks = new BuildingTaskSqliteRepository(database);
            PlanningTaskSqliteRepository planningTasks = new PlanningTaskSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                citizens.upsert(connection, citizenTag(citizenId));
                buildingTasks.upsert(connection, buildingTask(UUID.randomUUID(), citizenId, 0));
                planningTasks.upsert(connection, planningTask(UUID.randomUUID(), citizenId));
            }

            try (Connection connection = database.borrowConnection()) {
                citizens.delete(connection, citizenId);
            }

            assertNull(buildingTasks.findByCitizen(citizenId), "居民删除后其建筑任务必须一并清除");
            assertTrue(planningTasks.findByDimension(DIMENSION).isEmpty(), "居民删除后其规划任务必须一并清除");
            assertEquals(0, countRows(database, "building_tasks"));
            assertEquals(0, countRows(database, "planning_tasks"));
        }
    }

    private static CompoundTag citizenTag(UUID citizenId) {
        CitizenData citizen = new CitizenData(citizenId);
        citizen.setName("Citizen-" + citizenId);
        return citizen.toTag();
    }

    private static BuildingTaskData buildingTask(UUID taskId, UUID citizenId, int progress) {
        return new BuildingTaskData(
                taskId, citizenId, null, DIMENSION,
                new BlockPos(1, 64, 1), "residential", "house.sk", "House", "1", "house.nbt",
                new BlockPos(0, 64, 0), 0, progress, 100, "BUILDING", 1000L, 2000L,
                List.of(new BuildingPoiDefinition("bed1", CityPoiType.RESIDENTIAL, 2)), false);
    }

    private static PlanningTaskData planningTask(UUID taskId, UUID citizenId) {
        return new PlanningTaskData(
                taskId, citizenId, null, DIMENSION,
                new BlockPos(1, 64, 1), new BlockPos(0, 64, 0), new BlockPos(9, 70, 9),
                PlanOperation.REMOVE, "", "", null, Map.of(), 0, 100, 0, 100, "RUNNING", 1000L, 2000L);
    }

    // countRows 的表名只来自本测试的常量，没有注入面。
    private static int countRows(SimuSqliteDatabase database, String table) throws Exception {
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
