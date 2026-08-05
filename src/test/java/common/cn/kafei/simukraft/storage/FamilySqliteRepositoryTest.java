package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.citizen.family.FamilyData;
import common.cn.kafei.simukraft.citizen.family.FamilyStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilySqliteRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void familyRoundTripsThroughSqlite() throws Exception {
        UUID familyId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        UUID husbandId = UUID.randomUUID();
        UUID wifeId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("families.sqlite"))) {
            FamilySqliteRepository repository = new FamilySqliteRepository(database);
            FamilyData family = new FamilyData(familyId, cityId);
            family.setHusbandId(husbandId);
            family.setWifeId(wifeId);
            family.setGeneration(1);
            family.setStatus(FamilyStatus.ACTIVE);
            family.addChild(childId);

            try (Connection connection = database.borrowConnection()) {
                repository.upsert(connection, family);
            }

            List<FamilyData> loaded = repository.loadAll();
            assertNotNull(loaded);
            assertEquals(1, loaded.size());
            FamilyData stored = loaded.get(0);
            assertEquals(familyId, stored.familyId());
            assertEquals(cityId, stored.cityId());
            assertEquals(husbandId, stored.husbandId());
            assertEquals(wifeId, stored.wifeId());
            assertEquals(FamilyStatus.ACTIVE, stored.status());
            assertEquals(List.of(childId), stored.childIds());
        }
    }

    /** P2 回归：加载失败必须返回 null 并降级，不得把部分结果交给调用方。 */
    @Test
    void corruptedRowFailsTheLoadInsteadOfReturningPartialData() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("families-corrupt.sqlite"))) {
            try (Connection connection = database.borrowConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO families(family_id, city_id, status) VALUES('not-a-uuid', 'not-a-uuid', 'ACTIVE')");
            }

            FamilySqliteRepository repository = new FamilySqliteRepository(database);
            assertNull(repository.loadAll(), "加载失败必须返回 null 而不是部分结果");
            assertTrue(database.isDegraded(), "加载失败必须触发降级");
        }
    }

    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
