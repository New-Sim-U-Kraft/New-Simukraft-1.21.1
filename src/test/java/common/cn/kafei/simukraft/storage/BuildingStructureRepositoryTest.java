package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingPoiDefinition;
import common.cn.kafei.simukraft.building.BuildingPoiInstance;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P1 回归：建筑结构库的写入经写队列落库、读取借池化连接，upsert/delete 如实报告成败。 */
class BuildingStructureRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void upsertThenDeleteRoundTripsThroughTheWriteQueue() throws Exception {
        try (BuildingStructureSqliteDatabase database = openDatabase(tempDir.resolve("buildings.sqlite"))) {
            BuildingStructureRepository repository = new BuildingStructureRepository(database);
            PlacedBuildingRecord record = record(UUID.randomUUID());

            assertEquals(BuildingStructureRepository.WriteOutcome.PERSISTED, repository.upsert(record));
            List<PlacedBuildingRecord> loaded = repository.loadByDimension("minecraft:overworld");
            assertEquals(1, loaded.size());
            PlacedBuildingRecord stored = loaded.get(0);
            assertEquals(record.buildingId(), stored.buildingId());
            assertEquals("House", stored.displayName());
            assertEquals(2, stored.blocks().size());
            assertEquals(1, stored.poiInstances().size());
            assertEquals("bed1", stored.poiInstances().get(0).key());

            assertEquals(BuildingStructureRepository.WriteOutcome.PERSISTED, repository.delete(record.buildingId()));
            assertTrue(repository.loadByDimension("minecraft:overworld").isEmpty());
        }
    }

    /**
     * 库已关闭/已降级必须报成 STORAGE_UNAVAILABLE，而不是 FAILED，也不是假装成功。
     * <p>调用方（PlacedBuildingService）据此区分两条降级路径：单条写入失败 → 不动内存缓存；
     * 整库不可用 → 仍登记进内存（降级语义是"磁盘冻结、内存权威"），否则降级后所有新建成的建筑都不生效。
     */
    @Test
    void upsertReportsStorageUnavailableAfterDatabaseClosed() throws Exception {
        BuildingStructureSqliteDatabase database = openDatabase(tempDir.resolve("buildings-closed.sqlite"));
        BuildingStructureRepository repository = new BuildingStructureRepository(database);
        database.close();

        assertEquals(BuildingStructureRepository.WriteOutcome.STORAGE_UNAVAILABLE, repository.upsert(record(UUID.randomUUID())));
        assertEquals(BuildingStructureRepository.WriteOutcome.STORAGE_UNAVAILABLE, repository.delete(UUID.randomUUID()));
    }

    /**
     * 加载失败必须返回 null 并把建筑库标记为降级，而不是静默返回部分结果：
     * 调用方（PlacedBuildingService）不缓存 null、下次访问重试；若把残缺结果当成权威缓存，
     * 一次读取故障就让全维度建筑"消失"到重启。
     */
    @Test
    void loadFailureMarksDegradedAndReturnsNull() throws Exception {
        try (BuildingStructureSqliteDatabase database = openDatabase(tempDir.resolve("buildings-broken.sqlite"))) {
            BuildingStructureRepository repository = new BuildingStructureRepository(database);
            try (java.sql.Connection connection = database.borrowConnection();
                 java.sql.Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP TABLE placed_building_blocks");
            }

            assertNull(repository.loadByDimension("minecraft:overworld"), "加载失败必须返回 null 而不是部分结果");
            assertTrue(database.isDegraded(), "加载失败必须把建筑库标记为降级");
        }
    }

    private static PlacedBuildingRecord record(UUID buildingId) {
        return new PlacedBuildingRecord(
                buildingId, null, "minecraft:overworld", "residential", "house.sk", "House", "1", "house.nbt", "north",
                new BlockPos(0, 64, 0), new BlockPos(0, 64, 0),
                new BlockPos(0, 64, 0), new BlockPos(4, 68, 4), 1000L,
                List.of(
                        new BuildingBlockData(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState(), new BlockPos(0, 0, 0)),
                        new BuildingBlockData(new BlockPos(0, 1, 0), Blocks.OAK_LOG.defaultBlockState(), new BlockPos(0, 1, 0))
                ),
                List.of(new BuildingPoiDefinition("bed1", CityPoiType.RESIDENTIAL, 2)),
                List.of(new BuildingPoiInstance("bed1", CityPoiType.RESIDENTIAL, 2, new BlockPos(1, 64, 1))),
                List.of(),
                List.of()
        );
    }

    private static BuildingStructureSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<BuildingStructureSqliteDatabase> constructor = BuildingStructureSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
