package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.citizen.CitizenData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回归：所有 saveAll 曾经先清空整表（或按维度整段删除）再重写内存快照。
 * <p>造成两类事故：
 * <ul>
 *   <li>加载失败 / 加载未完成时内存为空，关服全量保存把库清空；</li>
 *   <li>没有 dimension_id 的表（农田盒 / 工业盒 / 商业箱 / 商业库存）配上按维度的管理器，
 *       关服逐维度保存会让每个维度都清一次全表，最终只剩最后一个维度的数据。</li>
 * </ul>
 * 这里用"残缺快照"覆盖一次，断言库里已有的行必须原样保留。
 */
@SuppressWarnings("null")
class NonDestructiveSaveAllTest {
    @TempDir
    Path tempDir;

    @Test
    void citizenSaveAllKeepsCitizensMissingFromTheSnapshot() throws Exception {
        UUID keptId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("citizens.sqlite"))) {
            CitizenSqliteRepository repository = new CitizenSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, citizensTag(keptId, otherId));
            }
            assertEquals(2, citizenCount(repository));

            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, citizensTag(keptId));
            }

            assertEquals(2, citizenCount(repository));
        }
    }

    @Test
    void farmlandSaveAllKeepsBoxesMissingFromTheSnapshot() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("farmland.sqlite"))) {
            FarmlandBoxSqliteRepository repository = new FarmlandBoxSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, boxesTag("Boxes", boxTag(1, 64, 1), boxTag(2, 64, 2)));
            }
            assertEquals(2, listSize(repository.loadAll(), "Boxes"));

            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, boxesTag("Boxes", boxTag(1, 64, 1)));
            }

            assertEquals(2, listSize(repository.loadAll(), "Boxes"));
        }
    }

    @Test
    void industrialSaveAllKeepsBoxesMissingFromTheSnapshot() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("industrial.sqlite"))) {
            IndustrialBoxSqliteRepository repository = new IndustrialBoxSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, boxesTag("Boxes", industrialBoxTag(1, 64, 1), industrialBoxTag(2, 64, 2)));
            }
            assertEquals(2, listSize(repository.loadAll(), "Boxes"));

            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, boxesTag("Boxes", industrialBoxTag(1, 64, 1)));
            }

            assertEquals(2, listSize(repository.loadAll(), "Boxes"));
        }
    }

    @Test
    void commercialBoxSaveAllKeepsBoxesMissingFromTheSnapshot() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("commercial.sqlite"))) {
            CommercialSqliteRepository repository = new CommercialSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.saveBoxes(connection, boxesTag("Boxes", commercialBoxTag(1, 64, 1), commercialBoxTag(2, 64, 2)), "minecraft:overworld");
            }
            assertEquals(2, listSize(repository.loadBoxes("minecraft:overworld"), "Boxes"));

            try (Connection connection = database.borrowConnection()) {
                repository.saveBoxes(connection, boxesTag("Boxes", commercialBoxTag(1, 64, 1)), "minecraft:overworld");
            }

            assertEquals(2, listSize(repository.loadBoxes("minecraft:overworld"), "Boxes"));
        }
    }

    @Test
    void commercialStockSaveAllKeepsEntriesMissingFromTheSnapshot() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("commercial-stock.sqlite"))) {
            CommercialSqliteRepository repository = new CommercialSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.saveStock(connection, boxesTag("Stock", stockTag(1, "minecraft:bread"), stockTag(1, "minecraft:apple")), "minecraft:overworld");
            }
            assertEquals(2, listSize(repository.loadStock("minecraft:overworld"), "Stock"));

            try (Connection connection = database.borrowConnection()) {
                repository.saveStock(connection, boxesTag("Stock", stockTag(1, "minecraft:bread")), "minecraft:overworld");
            }

            assertEquals(2, listSize(repository.loadStock("minecraft:overworld"), "Stock"));
        }
    }

    private static int citizenCount(CitizenSqliteRepository repository) {
        return listSize(repository.loadAll(), "Citizens");
    }

    private static int listSize(CompoundTag tag, String key) {
        return tag == null ? 0 : tag.getList(key, CompoundTag.TAG_COMPOUND).size();
    }

    private static CompoundTag citizensTag(UUID... citizenIds) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (UUID citizenId : citizenIds) {
            CitizenData citizen = new CitizenData(citizenId);
            citizen.setName("Citizen-" + citizenId);
            list.add(citizen.toTag());
        }
        root.put("Citizens", list);
        return root;
    }

    private static CompoundTag boxesTag(String key, CompoundTag... entries) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (CompoundTag entry : entries) {
            list.add(entry);
        }
        root.put(key, list);
        return root;
    }

    private static CompoundTag boxTag(int x, int y, int z) {
        CompoundTag box = new CompoundTag();
        box.putLong("BoxPos", new BlockPos(x, y, z).asLong());
        box.putString("Crop", "minecraft:wheat");
        box.putBoolean("Running", true);
        return box;
    }

    private static CompoundTag industrialBoxTag(int x, int y, int z) {
        CompoundTag box = new CompoundTag();
        box.putLong("BoxPos", new BlockPos(x, y, z).asLong());
        box.putString("BuildingId", "building");
        box.putString("DefinitionId", "definition");
        box.putString("SelectedRecipeId", "recipe");
        box.putBoolean("Running", true);
        box.putBoolean("SpawnEntityDone", false);
        box.putInt("CurrentStep", 0);
        box.putString("StatusKey", "");
        box.putString("StatusText", "");
        box.putString("MachineState", "");
        box.putString("WorkState", "");
        box.putLong("UpdatedAt", 1L);
        return box;
    }

    private static CompoundTag commercialBoxTag(int x, int y, int z) {
        CompoundTag box = new CompoundTag();
        box.putLong("BoxPos", new BlockPos(x, y, z).asLong());
        box.putString("BuildingId", "building");
        box.putString("DefinitionId", "definition");
        box.putBoolean("Running", true);
        box.putString("StatusKey", "");
        box.putString("StatusText", "");
        box.putLong("UpdatedAt", 1L);
        return box;
    }

    private static CompoundTag stockTag(int boxX, String itemId) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("BoxPos", new BlockPos(boxX, 64, 0).asLong());
        entry.putString("ItemId", itemId);
        entry.putInt("CurrentStock", 4);
        entry.putInt("MaxStock", 16);
        entry.putLong("LastRestockGameTime", 10L);
        entry.putLong("UpdatedAt", 11L);
        return entry;
    }

    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
