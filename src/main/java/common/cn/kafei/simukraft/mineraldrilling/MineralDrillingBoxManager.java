package common.cn.kafei.simukraft.mineraldrilling;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MineralDrillingBoxManager: 管理当前维度的钻井控制箱状态。
 * SQLite 是主存储，SavedData 作为数据库不可用时的灾备。
 */
@SuppressWarnings("null")
public final class MineralDrillingBoxManager extends SavedData {
    private static final int MAX_WRITE_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 50L;
    private static final String DATA_NAME = SimuKraft.MOD_ID + "_mineral_drilling_boxes";
    private static final Factory<MineralDrillingBoxManager> FACTORY = new Factory<>(
            MineralDrillingBoxManager::new,
            MineralDrillingBoxManager::load,
            null
    );
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "simukraft-mineral-drilling-io");
        thread.setDaemon(true);
        return thread;
    });

    private final ConcurrentMap<BlockPos, MineralDrillingBoxData> boxes = new ConcurrentHashMap<>();
    private final ConcurrentMap<BlockPos, PendingWrite> pendingWrites = new ConcurrentHashMap<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private volatile boolean sqliteLoaded;
    private volatile ServerLevel level;

    /** get: 获取当前维度管理器，并首次访问时从 SQLite 懒加载。 */
    public static MineralDrillingBoxManager get(ServerLevel level) {
        MineralDrillingBoxManager manager = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        manager.level = level;
        manager.loadFromSqlite(level);
        return manager;
    }

    /** level: 返回当前管理器绑定的服务端维度。 */
    public ServerLevel level() {
        return level;
    }

    /** load: 从 SavedData 灾备 NBT 恢复当前维度的控制箱。 */
    private static MineralDrillingBoxManager load(CompoundTag tag, HolderLookup.Provider registries) {
        MineralDrillingBoxManager manager = new MineralDrillingBoxManager();
        ListTag list = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            try {
                MineralDrillingBoxData data = MineralDrillingBoxData.fromTag(list.getCompound(index), registries);
                manager.boxes.put(data.boxPos(), manager.attach(data));
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.error("Failed to load mineral drilling box {} from SavedData", index, exception);
            }
        }
        return manager;
    }

    /** save: 生成线程安全的 SavedData 灾备快照。 */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MineralDrillingBoxData data : boxes.values()) {
            try {
                list.add(data.toTag(registries));
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.error("Failed to snapshot mineral drilling box at {}", data.boxPos(), exception);
            }
        }
        tag.put("Boxes", list);
        return tag;
    }

    /** saveToSqlite: 同步写入当前维度完整快照，用于服务器保存/关闭流程。 */
    public synchronized void saveToSqlite(ServerLevel level) {
        if (level == null) {
            return;
        }
        CompoundTag snapshot = save(new CompoundTag(), level.registryAccess());
        try {
            CompletableFuture.runAsync(
                    () -> retryWrite(() -> SimuSqliteStorage.saveMineralDrillingBoxes(level, snapshot),
                            "full snapshot", level.dimension().location().toString()),
                    IO_EXECUTOR
            ).join();
        } catch (CompletionException exception) {
            SimuKraft.LOGGER.error("Failed to flush mineral drilling boxes for dimension {}",
                    level.dimension().location(), exception.getCause());
        }
    }

    /** reloadFromSqlite: 清空内存副本并重新读取当前维度状态。 */
    public synchronized void reloadFromSqlite(ServerLevel level) {
        boxes.values().forEach(data -> data.setChangeListener(null));
        boxes.clear();
        sqliteLoaded = false;
        loadFromSqlite(level);
    }

    /** loadFromSqlite: 仅首次访问时以 SQLite 快照覆盖灾备状态。 */
    private synchronized void loadFromSqlite(ServerLevel level) {
        if (sqliteLoaded) {
            return;
        }
        sqliteLoaded = true;
        CompoundTag sqliteTag = SimuSqliteStorage.loadMineralDrillingBoxes(level);
        if (sqliteTag == null || sqliteTag.isEmpty()) {
            return;
        }
        ListTag list = sqliteTag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        boxes.values().forEach(data -> data.setChangeListener(null));
        boxes.clear();
        for (int index = 0; index < list.size(); index++) {
            try {
                MineralDrillingBoxData data = MineralDrillingBoxData.fromTag(
                        list.getCompound(index), level.registryAccess());
                boxes.put(data.boxPos(), attach(data));
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.error("Failed to load mineral drilling box {} from SQLite", index, exception);
            }
        }
        // SQLite 是主存储；成功装载后同步刷新 SavedData 灾备，避免其长期停留在旧版本。
        setDirty();
    }

    /** get: 查询指定位置的控制箱状态。 */
    public MineralDrillingBoxData get(BlockPos boxPos) {
        return boxPos != null ? boxes.get(boxPos.immutable()) : null;
    }

    /** getOrCreate: 查询或创建指定位置的控制箱状态。 */
    public MineralDrillingBoxData getOrCreate(BlockPos boxPos) {
        BlockPos key = boxPos.immutable();
        return boxes.computeIfAbsent(key, position -> attach(new MineralDrillingBoxData(position)));
    }

    /** persist: 更新灾备状态，并合并排队同一位置的 SQLite 写入。 */
    public void persist(MineralDrillingBoxData data) {
        if (data == null) {
            return;
        }
        BlockPos key = data.boxPos().immutable();
        MineralDrillingBoxData previous = boxes.put(key, data);
        if (previous != data) {
            if (previous != null) {
                previous.setChangeListener(null);
            }
            attach(data);
        }
        data.touch();
        setDirty();

        ServerLevel currentLevel = level;
        if (currentLevel == null) {
            return;
        }
        try {
            CompoundTag snapshot = data.toTag(currentLevel.registryAccess());
            pendingWrites.put(key, PendingWrite.save(currentLevel, snapshot));
            scheduleDrain();
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.error("Failed to queue mineral drilling box snapshot at {}", key, exception);
        }
    }

    /** remove: 移除内存状态，并让删除操作与尚未完成的增量写保持顺序。 */
    public void remove(BlockPos boxPos) {
        if (boxPos == null) {
            return;
        }
        BlockPos key = boxPos.immutable();
        MineralDrillingBoxData removed = boxes.remove(key);
        if (removed != null) {
            removed.setChangeListener(null);
        }
        setDirty();
        ServerLevel currentLevel = level;
        if (currentLevel != null) {
            pendingWrites.put(key, PendingWrite.delete(currentLevel));
            scheduleDrain();
        }
    }

    /** all: 返回当前维度控制箱状态的不可变列表快照。 */
    public List<MineralDrillingBoxData> all() {
        return List.copyOf(boxes.values());
    }

    /** attach: 将数据变化绑定到当前管理器的合并写入入口。 */
    private MineralDrillingBoxData attach(MineralDrillingBoxData data) {
        data.setChangeListener(() -> persist(data));
        return data;
    }

    /** scheduleDrain: 保证每个管理器同时最多排队一个数据库排空任务。 */
    private void scheduleDrain() {
        if (drainScheduled.compareAndSet(false, true)) {
            IO_EXECUTOR.execute(this::drainPendingWrites);
        }
    }

    /** drainPendingWrites: 逐键提取最新操作，跳过已被新快照替换的旧操作。 */
    private void drainPendingWrites() {
        while (true) {
            boolean processed = false;
            for (var entry : pendingWrites.entrySet()) {
                BlockPos key = entry.getKey();
                PendingWrite write = entry.getValue();
                if (!pendingWrites.remove(key, write)) {
                    continue;
                }
                processed = true;
                if (write.delete()) {
                    retryWrite(() -> SimuSqliteStorage.deleteMineralDrillingBox(write.level(), key.asLong()),
                            "delete", key.toShortString());
                } else {
                    retryWrite(() -> SimuSqliteStorage.saveMineralDrillingBox(write.level(), write.snapshot()),
                            "upsert", key.toShortString());
                }
            }
            if (processed) {
                continue;
            }
            drainScheduled.set(false);
            if (pendingWrites.isEmpty() || !drainScheduled.compareAndSet(false, true)) {
                return;
            }
        }
    }

    /** retryWrite: 对短暂 SQLite 失败进行有限次数重试。 */
    private static void retryWrite(WriteOperation operation, String operationName, String target) {
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            try {
                if (operation.run()) {
                    return;
                }
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.error("Unexpected mineral drilling SQLite {} failure for {} on attempt {}",
                        operationName, target, attempt, exception);
            }
            if (attempt == MAX_WRITE_ATTEMPTS) {
                break;
            }
            try {
                Thread.sleep(RETRY_DELAY_MILLIS * attempt);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                SimuKraft.LOGGER.warn("Interrupted while retrying mineral drilling SQLite {} for {}", operationName, target);
                return;
            }
        }
        SimuKraft.LOGGER.error("Mineral drilling SQLite {} failed after {} attempts for {}",
                operationName, MAX_WRITE_ATTEMPTS, target);
    }

    @FunctionalInterface
    private interface WriteOperation {
        /** run: 执行一次数据库操作并返回是否成功。 */
        boolean run();
    }

    private record PendingWrite(ServerLevel level, CompoundTag snapshot, boolean delete) {
        /** save: 创建保留独立 NBT 快照的增量写请求。 */
        private static PendingWrite save(ServerLevel level, CompoundTag snapshot) {
            return new PendingWrite(level, snapshot, false);
        }

        /** delete: 创建按维度和位置删除记录的请求。 */
        private static PendingWrite delete(ServerLevel level) {
            return new PendingWrite(level, null, true);
        }
    }
}
