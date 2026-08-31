package common.cn.kafei.simukraft.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommercialSqliteRepositoryTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @TempDir
    Path tempDir;

    /** accumulatesIncomeAndMarksEnterpriseTaxCollected: 验证商业日收入累加后只结算一次企业税。 */
    @Test
    void accumulatesIncomeAndMarksEnterpriseTaxCollected() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("commercial.sqlite"))) {
            CommercialSqliteRepository repository = new CommercialSqliteRepository(database);
            UUID cityId = UUID.randomUUID();
            insertCity(database, cityId);

            assertTrue(repository.addDailyIncome(cityId, 1L, 10.0D));
            assertTrue(repository.addDailyIncome(cityId, 1L, 6.0D));
            assertTrue(repository.addDailyIncome(cityId, 2L, 4.0D));

            Map<UUID, Double> dayTwoDue = repository.loadUntaxedIncomeBefore(2L);
            assertEquals(16.0D, dayTwoDue.get(cityId), 0.001D);

            assertTrue(repository.markIncomeTaxCollectedBefore(cityId, 2L));
            assertTrue(repository.loadUntaxedIncomeBefore(2L).isEmpty());

            Map<UUID, Double> dayThreeDue = repository.loadUntaxedIncomeBefore(3L);
            assertEquals(4.0D, dayThreeDue.get(cityId), 0.001D);
        }
    }

    /** samePositionDifferentDimensionsStayIsolated: 同坐标不同维度的商业箱和库存互不影响。 */
    @Test
    void samePositionDifferentDimensionsStayIsolated() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("commercial-dimension.sqlite"))) {
            CommercialSqliteRepository repository = new CommercialSqliteRepository(database);
            CompoundTag overworldBox = commercialBoxTag(8, 64, 8, "overworld-shop");
            CompoundTag netherBox = commercialBoxTag(8, 64, 8, "nether-shop");
            CompoundTag overworldStock = stockTag(8, "minecraft:bread", 4);
            CompoundTag netherStock = stockTag(8, "minecraft:bread", 16);

            try (Connection connection = database.borrowConnection()) {
                repository.upsertBox(connection, overworldBox, OVERWORLD);
                repository.upsertBox(connection, netherBox, NETHER);
                repository.upsertStockEntry(connection, overworldStock, OVERWORLD);
                repository.upsertStockEntry(connection, netherStock, NETHER);
            }

            assertEquals("overworld-shop", firstBox(repository.loadBoxes(OVERWORLD)).getString("DefinitionId"));
            assertEquals("nether-shop", firstBox(repository.loadBoxes(NETHER)).getString("DefinitionId"));
            assertEquals(4, firstStock(repository.loadStock(OVERWORLD)).getInt("CurrentStock"));
            assertEquals(16, firstStock(repository.loadStock(NETHER)).getInt("CurrentStock"));

            try (Connection connection = database.borrowConnection()) {
                repository.deleteBox(connection, overworldBox.getLong("BoxPos"), OVERWORLD);
            }

            assertNull(repository.loadBoxes(OVERWORLD));
            assertNull(repository.loadStock(OVERWORLD));
            assertEquals("nether-shop", firstBox(repository.loadBoxes(NETHER)).getString("DefinitionId"));
            assertEquals(16, firstStock(repository.loadStock(NETHER)).getInt("CurrentStock"));
        }
    }

    /** legacyBoxesMigrateToOverworld: 旧库没有 dimension_id 的商业箱升级后归入主世界。 */
    @Test
    void legacyBoxesMigrateToOverworld() throws Exception {
        Path databasePath = tempDir.resolve("commercial-legacy.sqlite");
        long boxPos = new BlockPos(3, 64, 3).asLong();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE commercial_boxes("
                    + "box_pos_long INTEGER PRIMARY KEY, building_id TEXT NOT NULL DEFAULT '', definition_id TEXT NOT NULL DEFAULT '', "
                    + "running INTEGER NOT NULL DEFAULT 1, status_key TEXT NOT NULL DEFAULT '', status_text TEXT NOT NULL DEFAULT '', "
                    + "updated_at INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("INSERT INTO commercial_boxes(box_pos_long, building_id, definition_id, running, status_key, status_text, updated_at) "
                    + "VALUES(" + boxPos + ", 'building', 'legacy-shop', 1, '', '', 1)");
            statement.executeUpdate("CREATE TABLE commercial_stock("
                    + "box_pos_long INTEGER NOT NULL, item_id TEXT NOT NULL, current_stock INTEGER NOT NULL DEFAULT 0, "
                    + "max_stock INTEGER NOT NULL DEFAULT 0, last_restock_game_time INTEGER NOT NULL DEFAULT 0, "
                    + "updated_at INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(box_pos_long, item_id))");
            statement.executeUpdate("INSERT INTO commercial_stock(box_pos_long, item_id, current_stock, max_stock, last_restock_game_time, updated_at) "
                    + "VALUES(" + boxPos + ", 'minecraft:bread', 7, 16, 10, 11)");
        }

        try (SimuSqliteDatabase database = openDatabase(databasePath)) {
            CommercialSqliteRepository repository = new CommercialSqliteRepository(database);
            assertEquals("legacy-shop", firstBox(repository.loadBoxes(OVERWORLD)).getString("DefinitionId"));
            assertEquals(7, firstStock(repository.loadStock(OVERWORLD)).getInt("CurrentStock"));
            assertNull(repository.loadBoxes(NETHER));
            assertNull(repository.loadStock(NETHER));
        }
    }

    /** openDatabase: 通过反射创建测试用 SQLite 数据库实例。 */
    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }

    /** insertCity: 为外键约束准备测试城市。 */
    private static void insertCity(SimuSqliteDatabase database, UUID cityId) throws Exception {
        try (var connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO cities(city_id, city_name, core_x, core_y, core_z, funds, city_level) VALUES(?, ?, 0, 64, 0, 20.0, 0)")) {
            statement.setString(1, cityId.toString());
            statement.setString(2, "Test City");
            statement.executeUpdate();
        }
    }

    private static CompoundTag commercialBoxTag(int x, int y, int z, String definitionId) {
        CompoundTag box = new CompoundTag();
        box.putLong("BoxPos", new BlockPos(x, y, z).asLong());
        box.putString("BuildingId", "building");
        box.putString("DefinitionId", definitionId);
        box.putBoolean("Running", true);
        box.putString("StatusKey", "");
        box.putString("StatusText", "");
        box.putLong("UpdatedAt", 1L);
        return box;
    }

    private static CompoundTag stockTag(int boxX, String itemId, int currentStock) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("BoxPos", new BlockPos(boxX, 64, 8).asLong());
        entry.putString("ItemId", itemId);
        entry.putInt("CurrentStock", currentStock);
        entry.putInt("MaxStock", 16);
        entry.putLong("LastRestockGameTime", 10L);
        entry.putLong("UpdatedAt", 11L);
        return entry;
    }

    private static CompoundTag firstBox(CompoundTag tag) {
        ListTag boxes = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        return boxes.getCompound(0);
    }

    private static CompoundTag firstStock(CompoundTag tag) {
        ListTag stock = tag.getList("Stock", CompoundTag.TAG_COMPOUND);
        return stock.getCompound(0);
    }
}
