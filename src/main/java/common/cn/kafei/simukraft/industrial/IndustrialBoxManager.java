package common.cn.kafei.simukraft.industrial;

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

@SuppressWarnings("null")
public final class IndustrialBoxManager extends SavedData {
    private static final String DATA_NAME = SimuKraft.MOD_ID + "_industrial_boxes";
    private static final Factory<IndustrialBoxManager> FACTORY = new Factory<>(IndustrialBoxManager::new, IndustrialBoxManager::load, null);

    private final ConcurrentMap<BlockPos, IndustrialBoxData> boxes = new ConcurrentHashMap<>();
    private volatile boolean sqliteLoaded;
    private volatile ServerLevel level;

    public static IndustrialBoxManager get(ServerLevel level) {
        IndustrialBoxManager manager = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        manager.level = level;
        manager.loadFromSqlite(level);
        return manager;
    }

    private static IndustrialBoxManager load(CompoundTag tag, HolderLookup.Provider registries) {
        IndustrialBoxManager manager = new IndustrialBoxManager();
        ListTag list = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            IndustrialBoxData data = IndustrialBoxData.fromTag(list.getCompound(i));
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
            SimuSqliteStorage.saveIndustrialBoxes(level, save(new CompoundTag(), level.registryAccess()));
        }
    }

    /** level: 返回当前管理器绑定的服务端维度，用于状态视图同步。 */
    public ServerLevel level() {
        return level;
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
        CompoundTag sqliteTag = SimuSqliteStorage.loadIndustrialBoxes(level);
        if (sqliteTag == null || sqliteTag.isEmpty()) {
            return;
        }
        IndustrialBoxManager loaded = load(sqliteTag, level.registryAccess());
        boxes.clear();
        boxes.putAll(loaded.boxes);
    }

    public IndustrialBoxData get(BlockPos boxPos) {
        return boxPos == null ? null : boxes.get(boxPos.immutable());
    }

    public IndustrialBoxData getOrCreate(BlockPos boxPos) {
        return boxes.computeIfAbsent(boxPos.immutable(), IndustrialBoxData::new);
    }

    // 写入的合并与排序由存储层写队列负责，upsert 与 remove 走同一条队列。
    public void persist(IndustrialBoxData data) {
        if (data == null) return;
        data.touch();
        boxes.put(data.boxPos(), data);
        setDirty();
        ServerLevel lv = level;
        if (lv == null) return;
        SimuSqliteStorage.saveIndustrialBox(lv, data.toTag());
    }

    public void remove(BlockPos boxPos) {
        if (boxPos == null) {
            return;
        }
        BlockPos key = boxPos.immutable();
        if (boxes.remove(key) != null) {
            setDirty();
            if (level != null) {
                SimuSqliteStorage.deleteIndustrialBox(level, key.asLong());
            }
        }
    }

    public List<IndustrialBoxData> all() {
        return List.copyOf(boxes.values());
    }
}
