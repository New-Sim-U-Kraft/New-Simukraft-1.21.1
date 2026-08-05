package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.virtualvein.VirtualVeinConsumption;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinFieldKey;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinFieldProfile;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlot;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlotState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualVeinSqliteRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void fieldProfileIsIdempotentAndConcurrentConsumptionCannotExceedReserve() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("virtual-veins.sqlite"))) {
            VirtualVeinSqliteRepository repository = new VirtualVeinSqliteRepository(database);
            VirtualVeinFieldKey key = new VirtualVeinFieldKey(12, -8, 25_088, -15_744, "minecraft:forest");
            VirtualVeinFieldProfile profile = profile(key, "赤铁矿脉", 100);

            assertEquals("赤铁矿脉", repository.createIfAbsent(profile).orElseThrow().slots().getFirst().displayName());
            assertEquals("赤铁矿脉", repository.createIfAbsent(profile(key, "不会覆盖", 1)).orElseThrow().slots().getFirst().displayName());
            VirtualVeinFieldKey taigaKey = new VirtualVeinFieldKey(12, -8, 25_088, -15_744, "minecraft:taiga");
            assertEquals("针叶林矿脉", repository.createIfAbsent(profile(taigaKey, "针叶林矿脉", 60)).orElseThrow().slots().getFirst().displayName());
            assertEquals("赤铁矿脉", repository.find("minecraft:overworld", key).orElseThrow().slots().getFirst().displayName());

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<VirtualVeinConsumption> first = executor.submit(() -> repository.consume("minecraft:overworld", key, 0, 70).orElseThrow());
                Future<VirtualVeinConsumption> second = executor.submit(() -> repository.consume("minecraft:overworld", key, 0, 70).orElseThrow());
                VirtualVeinConsumption firstResult = first.get();
                VirtualVeinConsumption secondResult = second.get();
                assertEquals(100, firstResult.consumed() + secondResult.consumed());
            } finally {
                executor.shutdownNow();
            }

            VirtualVeinFieldProfile stored = repository.find("minecraft:overworld", key).orElseThrow();
            VirtualVeinSlot depletedSlot = stored.slots().getFirst();
            assertEquals(0, depletedSlot.remainingReserve());
            assertEquals(VirtualVeinSlotState.DEPLETED, depletedSlot.state());
            VirtualVeinConsumption exhausted = repository.consume("minecraft:overworld", key, 0, 1).orElseThrow();
            assertEquals(0, exhausted.consumed());
            assertTrue(exhausted.depleted());
            assertFalse(stored.slots().get(1).state() == VirtualVeinSlotState.DEPLETED);
        }
    }

    @Test
    void legacyEmptyProfileCanBeRepairedWithoutOverwritingCurrentProfiles() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("virtual-vein-repair.sqlite"))) {
            VirtualVeinSqliteRepository repository = new VirtualVeinSqliteRepository(database);
            VirtualVeinFieldKey legacyKey = new VirtualVeinFieldKey(2, 3, 5_000, 7_000, "minecraft:forest");
            VirtualVeinFieldProfile emptyLegacyProfile = new VirtualVeinFieldProfile(
                    "minecraft:overworld", legacyKey, "minecraft:forest", 10, List.of()
            );
            repository.createIfAbsent(emptyLegacyProfile).orElseThrow();
            try (Connection connection = database.borrowConnection();
                 PreparedStatement update = connection.prepareStatement("UPDATE virtual_vein_fields SET generation_version = 1 WHERE dimension_id = ? AND field_cell_x = ? AND field_cell_z = ? AND field_biome_id = ?")) {
                update.setString(1, "minecraft:overworld");
                update.setInt(2, legacyKey.cellX());
                update.setInt(3, legacyKey.cellZ());
                update.setString(4, legacyKey.biomeId());
                update.executeUpdate();
            }

            assertEquals("修复后的矿脉", repository.replaceLegacyEmptyProfile(profile(legacyKey, "修复后的矿脉", 50)).orElseThrow().slots().getFirst().displayName());
            assertEquals("修复后的矿脉", repository.replaceLegacyEmptyProfile(profile(legacyKey, "不会二次覆盖", 1)).orElseThrow().slots().getFirst().displayName());
        }
    }

    @Test
    void legacyTableMigrationPreservesExistingVeinReserve() throws Exception {
        Path databasePath = tempDir.resolve("virtual-vein-legacy.sqlite");
        createLegacyVirtualVeinTable(databasePath);

        try (SimuSqliteDatabase database = openDatabase(databasePath)) {
            VirtualVeinSqliteRepository repository = new VirtualVeinSqliteRepository(database);
            VirtualVeinFieldKey key = new VirtualVeinFieldKey(12, -8, 3_200, -2_000, "minecraft:forest");
            VirtualVeinSlot migratedSlot = repository.find("minecraft:overworld", key).orElseThrow().slots().getFirst();

            assertEquals("赤铁矿脉", migratedSlot.displayName());
            assertEquals(100, migratedSlot.initialReserve());
            assertEquals(37, migratedSlot.remainingReserve());
            assertEquals(VirtualVeinSlotState.ACTIVE, migratedSlot.state());
        }
    }

    private static VirtualVeinFieldProfile profile(VirtualVeinFieldKey key, String displayName, int reserve) {
        return new VirtualVeinFieldProfile(
                "minecraft:overworld",
                key,
                key.biomeId(),
                42,
                List.of(
                        slot("hematite_vein", displayName, reserve),
                        slot("lignite_vein", "褐煤矿脉", 80)
                )
        );
    }

    private static VirtualVeinSlot slot(String id, String displayName, int reserve) {
        return new VirtualVeinSlot(
                id,
                displayName,
                ResourceLocation.parse("minecraft:raw_iron"),
                16,
                80,
                5,
                1_200,
                reserve,
                reserve,
                VirtualVeinSlotState.ACTIVE
        );
    }

    /** createLegacyVirtualVeinTable: 构造缺少群系主键列的旧版矿区表。 */
    private static void createLegacyVirtualVeinTable(Path databasePath) throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE virtual_vein_fields("
                        + "dimension_id TEXT NOT NULL, field_cell_x INTEGER NOT NULL, field_cell_z INTEGER NOT NULL, center_x INTEGER NOT NULL, center_z INTEGER NOT NULL, center_biome_id TEXT NOT NULL, created_game_time INTEGER NOT NULL, vein_count INTEGER NOT NULL, generation_version INTEGER NOT NULL DEFAULT 1, "
                        + "slot0_vein_id TEXT NOT NULL DEFAULT '', slot0_display_name TEXT NOT NULL DEFAULT '', slot0_product_id TEXT NOT NULL DEFAULT '', slot0_min_y INTEGER NOT NULL DEFAULT 0, slot0_max_y INTEGER NOT NULL DEFAULT 0, slot0_amount INTEGER NOT NULL DEFAULT 0, slot0_period_ticks INTEGER NOT NULL DEFAULT 0, slot0_initial_reserve INTEGER NOT NULL DEFAULT 0, slot0_remaining_reserve INTEGER NOT NULL DEFAULT 0, slot0_state TEXT NOT NULL DEFAULT 'EMPTY', "
                        + "slot1_vein_id TEXT NOT NULL DEFAULT '', slot1_display_name TEXT NOT NULL DEFAULT '', slot1_product_id TEXT NOT NULL DEFAULT '', slot1_min_y INTEGER NOT NULL DEFAULT 0, slot1_max_y INTEGER NOT NULL DEFAULT 0, slot1_amount INTEGER NOT NULL DEFAULT 0, slot1_period_ticks INTEGER NOT NULL DEFAULT 0, slot1_initial_reserve INTEGER NOT NULL DEFAULT 0, slot1_remaining_reserve INTEGER NOT NULL DEFAULT 0, slot1_state TEXT NOT NULL DEFAULT 'EMPTY', "
                        + "PRIMARY KEY(dimension_id, field_cell_x, field_cell_z))");
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO virtual_vein_fields("
                    + "dimension_id, field_cell_x, field_cell_z, center_x, center_z, center_biome_id, created_game_time, vein_count, generation_version, "
                    + "slot0_vein_id, slot0_display_name, slot0_product_id, slot0_min_y, slot0_max_y, slot0_amount, slot0_period_ticks, slot0_initial_reserve, slot0_remaining_reserve, slot0_state) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, "minecraft:overworld");
                statement.setInt(2, 12);
                statement.setInt(3, -8);
                statement.setInt(4, 3_200);
                statement.setInt(5, -2_000);
                statement.setString(6, "minecraft:forest");
                statement.setLong(7, 42);
                statement.setInt(8, 1);
                statement.setInt(9, 1);
                statement.setString(10, "hematite_vein");
                statement.setString(11, "赤铁矿脉");
                statement.setString(12, "minecraft:raw_iron");
                statement.setInt(13, 16);
                statement.setInt(14, 80);
                statement.setInt(15, 5);
                statement.setInt(16, 1_200);
                statement.setInt(17, 100);
                statement.setInt(18, 37);
                statement.setString(19, VirtualVeinSlotState.ACTIVE.name());
                statement.executeUpdate();
            }
        }
    }

    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
