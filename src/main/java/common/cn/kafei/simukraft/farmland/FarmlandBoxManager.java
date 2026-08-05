package common.cn.kafei.simukraft.farmland;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 农田盒配置存储。主持久化是 SQLite（farmland_boxes 表），SavedData 作为兼容兜底。
 * 写法对齐 {@code CityPoiManager}：get(level) 时从 SQLite 懒加载，增量改动 upsert/delete，
 * 周期性整表 saveToSqlite 由服务端 tick/关服流程调用。
 */

@SuppressWarnings("null")
public final class FarmlandBoxManager extends SavedData {
    private static final String DATA_NAME = SimuKraft.MOD_ID + "_farmland_boxes";
    private static final Factory<FarmlandBoxManager> FACTORY = new Factory<>(FarmlandBoxManager::new, FarmlandBoxManager::load, null);

    private final ConcurrentMap<BlockPos, FarmlandBoxData> boxes = new ConcurrentHashMap<>();
    private volatile boolean sqliteLoaded;
    private volatile ServerLevel level;

    public static FarmlandBoxManager get(ServerLevel level) {
        FarmlandBoxManager manager = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        manager.level = level;
        manager.loadFromSqlite(level);
        return manager;
    }

    private static FarmlandBoxManager load(CompoundTag tag, HolderLookup.Provider registries) {
        FarmlandBoxManager manager = new FarmlandBoxManager();
        ListTag list = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            FarmlandBoxData data = FarmlandBoxData.fromTag(list.getCompound(i));
            manager.boxes.put(data.boxPos(), data);
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        boxes.values().forEach(data -> list.add(data.toTag()));
        tag.put("Boxes", list);
        return tag;
    }

    public synchronized void saveToSqlite(ServerLevel level) {
        if (level != null) {
            SimuSqliteStorage.saveFarmlandBoxes(level, save(new CompoundTag(), level.registryAccess()));
        }
    }

    public synchronized void reloadFromSqlite(ServerLevel level) {
        boxes.clear();
        sqliteLoaded = false;
        loadFromSqlite(level);
    }

    private synchronized void loadFromSqlite(ServerLevel level) {
        if (sqliteLoaded) {
            return;
        }
        sqliteLoaded = true;
        CompoundTag sqliteTag = SimuSqliteStorage.loadFarmlandBoxes(level);
        if (sqliteTag == null || sqliteTag.isEmpty()) {
            return;
        }
        FarmlandBoxManager loaded = load(sqliteTag, level.registryAccess());
        boxes.clear();
        boxes.putAll(loaded.boxes);
    }

    public FarmlandBoxData get(BlockPos boxPos) {
        return boxPos == null ? null : boxes.get(boxPos.immutable());
    }

    public FarmlandBoxData getOrCreate(BlockPos boxPos) {
        return boxes.computeIfAbsent(boxPos.immutable(), FarmlandBoxData::new);
    }

    // 配置改动后调用：内存更新 + SavedData 脏标记 + SQLite 增量写。
    // 写入的合并与排序由存储层写队列负责，upsert 与 remove 走同一条队列。
    public void persist(FarmlandBoxData data) {
        if (data == null) return;
        boxes.put(data.boxPos(), data);
        setDirty();
        ServerLevel lv = level;
        if (lv == null) return;
        SimuSqliteStorage.saveFarmlandBox(lv, data.toTag());
    }

    public void remove(BlockPos boxPos) {
        if (boxPos == null) {
            return;
        }
        BlockPos key = boxPos.immutable();
        if (boxes.remove(key) != null) {
            setDirty();
            if (level != null) {
                SimuSqliteStorage.deleteFarmlandBox(level, key.asLong());
            }
        }
    }

    public List<FarmlandBoxData> all() {
        return List.copyOf(boxes.values());
    }
}
