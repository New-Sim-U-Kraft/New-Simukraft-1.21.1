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

class IndustrialBoxSqliteRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void workStateRoundTripsThroughRepository() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("industrial.sqlite"))) {
            IndustrialBoxSqliteRepository repository = new IndustrialBoxSqliteRepository(database);
            String workState = "{\"items\":[{\"nbt\":\"{id:\\\"minecraft:oak_log\\\",count:1}\"}]}";

            CompoundTag root = new CompoundTag();
            ListTag boxes = new ListTag();
            CompoundTag box = new CompoundTag();
            box.putLong("BoxPos", new BlockPos(4, 64, 5).asLong());
            box.putString("BuildingId", "building");
            box.putString("DefinitionId", "definition");
            box.putString("SelectedRecipeId", "recipe");
            box.putBoolean("Running", true);
            box.putBoolean("SpawnEntityDone", true);
            box.putInt("CurrentStep", 2);
            box.putString("StatusKey", "status");
            box.putString("StatusText", "text");
            box.putString("MachineState", "machine");
            box.putString("WorkState", workState);
            box.putLong("UpdatedAt", 42L);
            boxes.add(box);
            root.put("Boxes", boxes);

            try (java.sql.Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, root);
            }
            CompoundTag loaded = repository.loadAll();

            assertNotNull(loaded);
            CompoundTag loadedBox = loaded.getList("Boxes", CompoundTag.TAG_COMPOUND).getCompound(0);
            assertEquals(workState, loadedBox.getString("WorkState"));
            assertEquals("machine", loadedBox.getString("MachineState"));
        }
    }

    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
