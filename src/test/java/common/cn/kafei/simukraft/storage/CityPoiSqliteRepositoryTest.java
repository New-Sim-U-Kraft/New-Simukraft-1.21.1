package common.cn.kafei.simukraft.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("null")
class CityPoiSqliteRepositoryTest {
    private static final String DIMENSION = "minecraft:overworld";

    @TempDir
    Path tempDir;

    /**
     * 回归：saveAll 曾经先 "DELETE FROM city_pois WHERE dimension_id = ?" 再重写内存快照，
     * 加载未完成时会把整个维度的 POI 清空。现在只做 upsert。
     */
    @Test
    void saveAllKeepsPoisMissingFromTheSnapshot() throws Exception {
        UUID cityId = UUID.randomUUID();
        UUID keptId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("pois.sqlite"))) {
            CityPoiSqliteRepository repository = new CityPoiSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, poisTag(poiTag(keptId, cityId, 1), poiTag(otherId, cityId, 2)), DIMENSION);
            }
            assertEquals(2, loadedPoiIds(repository).size());

            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, poisTag(poiTag(keptId, cityId, 1)), DIMENSION);
            }

            assertEquals(2, loadedPoiIds(repository).size(), "保存不得删除快照里缺失的 POI");
        }
    }

    /**
     * 回归：saveAll 改成纯 upsert 之后，内存里换掉一个 poiId（CityPoiManager.replacePoi）就再没有
     * 任何路径能清掉旧行。旧行会在下次进档被读回，和替换它的新 POI 一起进城市索引让容量双计。
     */
    @Test
    void deleteRemovesOnlyTheTargetPoi() throws Exception {
        UUID cityId = UUID.randomUUID();
        UUID staleId = UUID.randomUUID();
        UUID replacementId = UUID.randomUUID();

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("pois-delete.sqlite"))) {
            CityPoiSqliteRepository repository = new CityPoiSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                // 同一坐标先后由两个 poiId 占用，正是 replacePoi 的场景。
                repository.upsert(connection, poiTag(staleId, cityId, 7), DIMENSION);
                repository.upsert(connection, poiTag(replacementId, cityId, 7), DIMENSION);
            }
            assertEquals(2, loadedPoiIds(repository).size());

            try (Connection connection = database.borrowConnection()) {
                repository.delete(connection, staleId);
            }

            assertEquals(List.of(replacementId), loadedPoiIds(repository));
        }
    }

    @Test
    void deleteCityRemovesEveryPoiOfThatCity() throws Exception {
        UUID removedCityId = UUID.randomUUID();
        UUID keptCityId = UUID.randomUUID();
        UUID keptPoiId = UUID.randomUUID();

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("pois-delete-city.sqlite"))) {
            CityPoiSqliteRepository repository = new CityPoiSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, poisTag(
                        poiTag(UUID.randomUUID(), removedCityId, 1),
                        poiTag(UUID.randomUUID(), removedCityId, 2),
                        poiTag(keptPoiId, keptCityId, 3)), DIMENSION);
            }

            try (Connection connection = database.borrowConnection()) {
                repository.deleteCity(connection, removedCityId);
            }

            assertEquals(List.of(keptPoiId), loadedPoiIds(repository));
        }
    }

    @Test
    void loadAllReturnsNullForAnEmptyDimension() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("pois-empty.sqlite"))) {
            assertNull(new CityPoiSqliteRepository(database).loadAll(DIMENSION));
        }
    }

    private static List<UUID> loadedPoiIds(CityPoiSqliteRepository repository) {
        CompoundTag loaded = repository.loadAll(DIMENSION);
        if (loaded == null) {
            return List.of();
        }
        ListTag pois = loaded.getList("Pois", CompoundTag.TAG_COMPOUND);
        List<UUID> ids = new ArrayList<>(pois.size());
        for (int i = 0; i < pois.size(); i++) {
            ids.add(pois.getCompound(i).getUUID("PoiId"));
        }
        return ids;
    }

    private static CompoundTag poisTag(CompoundTag... pois) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (CompoundTag poi : pois) {
            list.add(poi);
        }
        root.put("Pois", list);
        return root;
    }

    private static CompoundTag poiTag(UUID poiId, UUID cityId, int x) {
        CompoundTag poi = new CompoundTag();
        poi.putUUID("PoiId", poiId);
        poi.putUUID("CityId", cityId);
        poi.putLong("Pos", new BlockPos(x, 64, 0).asLong());
        poi.putString("Type", "RESIDENTIAL");
        poi.putInt("Capacity", 2);
        poi.putBoolean("Active", true);
        return poi;
    }

    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
