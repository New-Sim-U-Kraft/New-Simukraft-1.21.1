package common.cn.kafei.simukraft.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M7 回归：损坏的行数据不得在加载时把 IllegalArgumentException 抛向主线程，必须降级并返回 null。 */
class LogisticsSqliteRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void corruptedUuidRowFailsLoadAllInsteadOfEscaping() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("logistics-all.sqlite"))) {
            insertCorruptedWarehouse(database);

            LogisticsSqliteRepository repository = new LogisticsSqliteRepository(database);
            assertNull(repository.loadAll(), "加载失败必须返回 null 而不是抛出 IllegalArgumentException");
            assertTrue(database.isDegraded(), "加载失败必须触发降级");
        }
    }

    @Test
    void corruptedUuidRowFailsLoadDimensionInsteadOfEscaping() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("logistics-dim.sqlite"))) {
            insertCorruptedWarehouse(database);

            LogisticsSqliteRepository repository = new LogisticsSqliteRepository(database);
            assertNull(repository.loadDimension("minecraft:overworld"), "加载失败必须返回 null 而不是抛出 IllegalArgumentException");
            assertTrue(database.isDegraded(), "加载失败必须触发降级");
        }
    }

    private static void insertCorruptedWarehouse(SimuSqliteDatabase database) throws Exception {
        try (Connection connection = database.borrowConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO logistics_warehouses(warehouse_id, box_pos_long, city_id, dimension_id, updated_at) "
                    + "VALUES('not-a-uuid', 1024, NULL, 'minecraft:overworld', 0)");
        }
    }

    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
