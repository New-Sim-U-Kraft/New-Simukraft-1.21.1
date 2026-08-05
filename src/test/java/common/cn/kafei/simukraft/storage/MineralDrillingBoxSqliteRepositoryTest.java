package common.cn.kafei.simukraft.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class MineralDrillingBoxSqliteRepositoryTest {
    @TempDir
    Path tempDir;

    /** persistsDimensionScopedInventoryAndRejectsStaleRevision: 验证维度隔离、NBT 回读与版本防倒退。 */
    @Test
    void persistsDimensionScopedInventoryAndRejectsStaleRevision() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("mineral-drilling.sqlite"))) {
            MineralDrillingBoxSqliteRepository repository = new MineralDrillingBoxSqliteRepository(database);
            long boxPos = new BlockPos(10, 72, -4).asLong();

            assertTrue(repository.upsert("minecraft:overworld", box(boxPos, -20, 2L, "overworld")));
            assertTrue(repository.upsert("minecraft:the_nether", box(boxPos, 30, 1L, "nether")));
            assertTrue(repository.upsert("minecraft:overworld", box(boxPos, 64, 1L, "stale")));

            CompoundTag overworld = repository.loadAll("minecraft:overworld");
            CompoundTag nether = repository.loadAll("minecraft:the_nether");
            assertNotNull(overworld);
            assertNotNull(nether);
            CompoundTag overworldBox = overworld.getList("Boxes", CompoundTag.TAG_COMPOUND).getCompound(0);
            CompoundTag netherBox = nether.getList("Boxes", CompoundTag.TAG_COMPOUND).getCompound(0);
            assertEquals(-20, overworldBox.getInt("DrillDepth"));
            assertEquals(-30, overworldBox.getInt("LowestReachedDepth"));
            assertEquals("overworld", overworldBox.getCompound("Inventory").getString("Marker"));
            assertEquals(30, netherBox.getInt("DrillDepth"));

            assertTrue(repository.saveAll("minecraft:overworld", emptyRoot()));
            assertNull(repository.loadAll("minecraft:overworld"));
            assertEquals(1, repository.loadAll("minecraft:the_nether")
                    .getList("Boxes", CompoundTag.TAG_COMPOUND).size());
        }
    }

    /** box: 构造 repository 使用的最小控制箱 NBT 快照。 */
    private static CompoundTag box(long boxPos, int depth, long revision, String marker) {
        CompoundTag inventory = new CompoundTag();
        inventory.putString("Marker", marker);
        CompoundTag box = new CompoundTag();
        box.putLong("BoxPos", boxPos);
        box.putInt("DrillDepth", depth);
        box.putInt("LowestReachedDepth", depth - 10);
        box.putBoolean("Running", false);
        box.putString("StatusKey", "gui.simukraft.mineral_drilling.status.idle");
        box.putString("StatusText", "");
        box.putString("SelectedVeinId", "");
        box.put("Inventory", inventory);
        box.putLong("UpdatedAt", revision);
        box.putLong("Revision", revision);
        return box;
    }

    /** emptyRoot: 构造指定维度的空整表快照。 */
    private static CompoundTag emptyRoot() {
        CompoundTag root = new CompoundTag();
        root.put("Boxes", new ListTag());
        return root;
    }

    /** openDatabase: 复用生产数据库初始化流程创建临时 SQLite。 */
    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
