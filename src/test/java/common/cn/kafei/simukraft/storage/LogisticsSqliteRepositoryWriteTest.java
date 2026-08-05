package common.cn.kafei.simukraft.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
/** 物流写入回归：同位置顶替的预清理必须覆盖所有保存路径，端口必须按数值后缀序加载。 */
class LogisticsSqliteRepositoryWriteTest {
    @TempDir
    Path tempDir;

    @Test
    void saveAllReplacesStaleWarehouseAtSamePositionInsteadOfFailing() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("logistics-replace.sqlite"))) {
            LogisticsSqliteRepository repository = new LogisticsSqliteRepository(database);
            UUID staleId = UUID.randomUUID();
            CompoundTag before = new CompoundTag();
            ListTag warehouses = new ListTag();
            warehouses.add(warehouseTag(staleId, 1024L));
            before.put("Warehouses", warehouses);
            ListTag channels = new ListTag();
            channels.add(channelTag(UUID.randomUUID(), staleId));
            before.put("Channels", channels);
            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, before);
            }

            UUID replacementId = UUID.randomUUID();
            CompoundTag after = new CompoundTag();
            ListTag replacement = new ListTag();
            replacement.add(warehouseTag(replacementId, 1024L));
            after.put("Warehouses", replacement);
            try (Connection connection = database.borrowConnection()) {
                // 修复前：批量路径没有位置预清理，INSERT 撞 UNIQUE(dimension_id, box_pos_long)，整条写入被丢弃。
                repository.saveAll(connection, after);
            }

            CompoundTag loaded = repository.loadAll();
            assertNotNull(loaded);
            ListTag loadedWarehouses = loaded.getList("Warehouses", CompoundTag.TAG_COMPOUND);
            assertEquals(1, loadedWarehouses.size(), "同位置旧仓库必须被顶替而不是并存");
            assertEquals(replacementId, loadedWarehouses.getCompound(0).getUUID("WarehouseId"));
            assertTrue(loaded.getList("Channels", CompoundTag.TAG_COMPOUND).isEmpty(), "旧仓库的通道必须一并清理");
            assertFalse(database.isDegraded(), "正常顶替不得触发降级");
        }
    }

    @Test
    void warehouseContainersLoadBackInNumericSuffixOrder() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("logistics-ports.sqlite"))) {
            LogisticsSqliteRepository repository = new LogisticsSqliteRepository(database);
            CompoundTag tag = new CompoundTag();
            ListTag warehouses = new ListTag();
            CompoundTag warehouse = warehouseTag(UUID.randomUUID(), 2048L);
            ListTag containers = new ListTag();
            for (int i = 0; i < 12; i++) {
                CompoundTag container = new CompoundTag();
                container.putLong("Pos", 10_000L + i);
                containers.add(container);
            }
            warehouse.put("Containers", containers);
            warehouses.add(warehouse);
            tag.put("Warehouses", warehouses);
            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, tag);
            }

            CompoundTag loaded = repository.loadAll();
            assertNotNull(loaded);
            ListTag loadedContainers = loaded.getList("Warehouses", CompoundTag.TAG_COMPOUND)
                    .getCompound(0).getList("Containers", CompoundTag.TAG_COMPOUND);
            assertEquals(12, loadedContainers.size());
            for (int i = 0; i < 12; i++) {
                // 修复前按 port_id 字典序加载，container_10 会排到 container_2 前面。
                assertEquals(10_000L + i, loadedContainers.getCompound(i).getLong("Pos"),
                        "容器必须按保存顺序（数值后缀序）加载回来");
            }
        }
    }

    private static CompoundTag warehouseTag(UUID warehouseId, long boxPos) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("WarehouseId", warehouseId);
        tag.putLong("BoxPos", boxPos);
        tag.putString("DimensionId", "minecraft:overworld");
        tag.putLong("UpdatedAt", 1L);
        return tag;
    }

    private static CompoundTag channelTag(UUID channelId, UUID warehouseId) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ChannelId", channelId);
        tag.putUUID("WarehouseId", warehouseId);
        tag.putString("Direction", "export");
        tag.putString("Name", "");
        tag.putBoolean("Enabled", true);
        tag.put("Filters", new ListTag());
        tag.putLong("UpdatedAt", 1L);
        return tag;
    }

    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
