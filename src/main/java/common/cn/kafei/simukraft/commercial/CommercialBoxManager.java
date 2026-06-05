package common.cn.kafei.simukraft.commercial;

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
public final class CommercialBoxManager extends SavedData {
    private static final String DATA_NAME = SimuKraft.MOD_ID + "_commercial_boxes";
    private static final Factory<CommercialBoxManager> FACTORY = new Factory<>(CommercialBoxManager::new, CommercialBoxManager::load, null);

    private final ConcurrentMap<BlockPos, CommercialBoxData> boxes = new ConcurrentHashMap<>();
    private volatile boolean sqliteLoaded;
    private volatile ServerLevel level;

    /** get: 获取当前维度的商业箱管理器。 */
    public static CommercialBoxManager get(ServerLevel level) {
        CommercialBoxManager manager = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        manager.level = level;
        manager.loadFromSqlite(level);
        return manager;
    }

    private static CommercialBoxManager load(CompoundTag tag, HolderLookup.Provider registries) {
        CommercialBoxManager manager = new CommercialBoxManager();
        ListTag list = tag.getList("Boxes", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CommercialBoxData data = CommercialBoxData.fromTag(list.getCompound(i));
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

    /** saveToSqlite: 将商业箱状态写入 SQLite。 */
    public synchronized void saveToSqlite(ServerLevel level) {
        if (level != null) {
            SimuSqliteStorage.saveCommercialBoxes(level, save(new CompoundTag(), level.registryAccess()));
        }
    }

    private synchronized void loadFromSqlite(ServerLevel level) {
        if (sqliteLoaded) {
            return;
        }
        sqliteLoaded = true;
        CompoundTag sqliteTag = SimuSqliteStorage.loadCommercialBoxes(level);
        if (sqliteTag == null || sqliteTag.isEmpty()) {
            return;
        }
        CommercialBoxManager loaded = load(sqliteTag, level.registryAccess());
        boxes.clear();
        boxes.putAll(loaded.boxes);
    }

    /** get: 查找商业箱状态。 */
    public CommercialBoxData get(BlockPos boxPos) {
        return boxPos == null ? null : boxes.get(boxPos.immutable());
    }

    /** getOrCreate: 获取或创建商业箱状态。 */
    public CommercialBoxData getOrCreate(BlockPos boxPos) {
        return boxes.computeIfAbsent(boxPos.immutable(), CommercialBoxData::new);
    }

    /** persist: 持久化单个商业箱状态。 */
    public void persist(CommercialBoxData data) {
        if (data == null) {
            return;
        }
        data.touch();
        boxes.put(data.boxPos(), data);
        setDirty();
        if (level != null) {
            SimuSqliteStorage.saveCommercialBox(level, data.toTag());
        }
    }

    /** remove: 删除商业箱状态。 */
    public void remove(BlockPos boxPos) {
        if (boxPos == null) {
            return;
        }
        BlockPos key = boxPos.immutable();
        if (boxes.remove(key) != null) {
            setDirty();
            if (level != null) {
                SimuSqliteStorage.deleteCommercialBox(level, key.asLong());
            }
        }
    }

    /** all: 返回商业箱状态快照。 */
    public List<CommercialBoxData> all() {
        return List.copyOf(boxes.values());
    }
}
