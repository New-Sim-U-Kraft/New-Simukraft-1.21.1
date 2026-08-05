package common.cn.kafei.simukraft.city.poi;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.job.CityJobAssignmentService;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class CityPoiManager extends SavedData {
    private static final String DATA_NAME = SimuKraft.MOD_ID + "_city_pois";
    private static final Factory<CityPoiManager> FACTORY = new Factory<>(CityPoiManager::new, CityPoiManager::load, null);
    private static final ConcurrentHashMap<UUID, CityPoiData> GLOBAL_POI_CACHE = new ConcurrentHashMap<>();

    public static CityPoiData lookupPoi(UUID poiId) {
        return poiId == null ? null : GLOBAL_POI_CACHE.get(poiId);
    }

    public static void clearGlobalCache() {
        GLOBAL_POI_CACHE.clear();
    }

    private final ConcurrentMap<UUID, CityPoiData> pois = new ConcurrentHashMap<>();
    // cityPoiIndex 和 posIndex 是查询加速索引，真实数据仍以 pois 为准。
    private final ConcurrentMap<UUID, Set<UUID>> cityPoiIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<BlockPos, UUID> posIndex = new ConcurrentHashMap<>();
    private volatile boolean sqliteLoaded;
    private volatile ServerLevel level;

    public static CityPoiManager get(ServerLevel level) {
        CityPoiManager manager = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        manager.level = level;
        manager.loadFromSqlite(level);
        return manager;
    }

    private static CityPoiManager load(CompoundTag tag, HolderLookup.Provider registries) {
        CityPoiManager manager = new CityPoiManager();
        ListTag poiTags = tag.getList("Pois", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < poiTags.size(); i++) {
            CityPoiData poi = CityPoiData.fromTag(poiTags.getCompound(i));
            manager.putLoaded(poi);
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag poiTags = new ListTag();
        pois.values().forEach(poi -> poiTags.add(poi.toTag()));
        tag.put("Pois", poiTags);
        return tag;
    }

    public synchronized void saveToSqlite(ServerLevel level) {
        if (level == null) {
            return;
        }
        SimuSqliteStorage.saveCityPois(level, save(new CompoundTag(), level.registryAccess()));
    }

    public synchronized void reloadFromSqlite(ServerLevel level) {
        pois.forEach((id, poi) -> GLOBAL_POI_CACHE.remove(id));
        pois.clear();
        cityPoiIndex.clear();
        posIndex.clear();
        sqliteLoaded = false;
        loadFromSqlite(level);
    }

    /*
     * 必须同步加载：旧实现用 CompletableFuture.runAsync 且先把 sqliteLoaded 置 true，
     * 加载还没完成时若发生一次整表保存，内存里的空 POI 集合就会被当成权威写回数据库。
     * POI 数量在几千级，一次性查询远快于一个 tick，没有异步的必要。
     */
    private synchronized void loadFromSqlite(ServerLevel level) {
        if (sqliteLoaded) {
            return;
        }
        CompoundTag sqliteTag = SimuSqliteStorage.loadCityPois(level);
        // 加载失败（null）同样置位，否则每次 get(level) 都会重试一次失败查询；写入由存储层的降级开关拦住。
        sqliteLoaded = true;
        if (sqliteTag == null || sqliteTag.isEmpty()) {
            return;
        }
        CityPoiManager loaded = load(sqliteTag, level.registryAccess());
        loaded.pois.forEach(pois::putIfAbsent);
        loaded.cityPoiIndex.forEach((cityId, ids) ->
                cityPoiIndex.computeIfAbsent(cityId, id -> ConcurrentHashMap.newKeySet()).addAll(ids));
        loaded.posIndex.forEach(posIndex::putIfAbsent);
        loaded.pois.forEach(GLOBAL_POI_CACHE::putIfAbsent);
    }

    private void savePoiIncremental(CityPoiData poi) {
        ServerLevel targetLevel = level;
        if (targetLevel != null && poi != null) {
            SimuSqliteStorage.saveCityPoi(targetLevel, poi.toTag());
        }
    }

    private void deletePoiIncremental(UUID poiId) {
        ServerLevel targetLevel = level;
        if (targetLevel != null && poiId != null) {
            SimuSqliteStorage.deleteCityPoi(targetLevel, poiId);
        }
    }

    private void deleteCityPoisIncremental(UUID cityId) {
        ServerLevel targetLevel = level;
        if (targetLevel != null && cityId != null) {
            SimuSqliteStorage.deleteCityPois(targetLevel, cityId);
        }
    }

    public synchronized CityPoiData registerPoi(UUID cityId, BlockPos pos, CityPoiType type, int capacity) {
        return registerPoi(null, cityId, pos, type, capacity);
    }

    public synchronized CityPoiData registerPoi(UUID requestedPoiId, UUID cityId, BlockPos pos, CityPoiType type, int capacity) {
        BlockPos immutablePos = pos.immutable();
        // 建筑恢复时会传入稳定 UUID；手动注册时按坐标复用旧 POI。
        UUID existingPoiId = requestedPoiId != null && pois.containsKey(requestedPoiId) ? requestedPoiId : posIndex.get(immutablePos);
        if (existingPoiId != null) {
            CityPoiData existing = pois.get(existingPoiId);
            if (existing != null) {
                CityPoiData updated = new CityPoiData(requestedPoiId != null ? requestedPoiId : existing.poiId(), cityId, immutablePos, type, Math.max(0, capacity), true, existing.unitId());
                replacePoi(existing, updated);
                CityJobAssignmentService.invalidate(existing.cityId());
                CityJobAssignmentService.invalidate(cityId);
                GLOBAL_POI_CACHE.put(updated.poiId(), updated);
                savePoiIncremental(updated);
                setDirty();
                return updated;
            }
        }
        CityPoiData poi = new CityPoiData(requestedPoiId != null ? requestedPoiId : UUID.randomUUID(), cityId, immutablePos, type, Math.max(0, capacity), true, null);
        putLoaded(poi);
        CityJobAssignmentService.invalidate(cityId);
        GLOBAL_POI_CACHE.put(poi.poiId(), poi);
        savePoiIncremental(poi);
        setDirty();
        return poi;
    }

    public synchronized boolean deactivatePoi(BlockPos pos) {
        UUID poiId = posIndex.get(pos.immutable());
        if (poiId == null) {
            return false;
        }
        CityPoiData poi = pois.get(poiId);
        if (poi == null || !poi.active()) {
            return false;
        }
        pois.put(poiId, poi.withActive(false));
        GLOBAL_POI_CACHE.put(poiId, pois.get(poiId));
        if (level != null) {
            SimuSqliteStorage.saveCityPoi(level, pois.get(poiId).toTag());
        }
        CityJobAssignmentService.invalidate(poi.cityId());
        setDirty();
        return true;
    }

    public synchronized boolean deactivatePoi(UUID poiId) {
        CityPoiData poi = poiId != null ? pois.get(poiId) : null;
        if (poi == null || !poi.active()) {
            return false;
        }
        pois.put(poiId, poi.withActive(false));
        GLOBAL_POI_CACHE.put(poiId, pois.get(poiId));
        if (level != null) {
            SimuSqliteStorage.saveCityPoi(level, pois.get(poiId).toTag());
        }
        CityJobAssignmentService.invalidate(poi.cityId());
        setDirty();
        return true;
    }

    public synchronized void releaseCity(UUID cityId) {
        Set<UUID> poiIds = cityPoiIndex.remove(cityId);
        if (poiIds == null) {
            return;
        }
        poiIds.forEach(poiId -> {
            CityPoiData poi = pois.remove(poiId);
            if (poi != null) {
                posIndex.remove(poi.pos(), poiId);
                GLOBAL_POI_CACHE.remove(poiId);
            }
        });
        CityJobAssignmentService.invalidate(cityId);
        deleteCityPoisIncremental(cityId);
        setDirty();
    }

    public List<CityPoiData> getCityPois(UUID cityId) {
        Set<UUID> poiIds = cityPoiIndex.get(cityId);
        if (poiIds == null || poiIds.isEmpty()) {
            return List.of();
        }
        return poiIds.stream().map(pois::get).filter(poi -> poi != null && poi.active()).toList();
    }

    public List<CityPoiData> getCityPois(UUID cityId, CityPoiType type) {
        return getCityPois(cityId).stream().filter(poi -> poi.type() == type).toList();
    }

    public CityPoiData getPoi(UUID poiId) {
        return poiId == null ? null : pois.get(poiId);
    }

    public synchronized void updatePoiUnitId(UUID poiId, UUID unitId) {
        CityPoiData poi = poiId != null ? pois.get(poiId) : null;
        if (poi == null) return;
        CityPoiData updated = poi.withUnitId(unitId);
        pois.put(poiId, updated);
        GLOBAL_POI_CACHE.put(poiId, updated);
        savePoiIncremental(updated);
        setDirty();
    }

    public CityPoiData getPoiAt(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        UUID poiId = posIndex.get(pos.immutable());
        return poiId != null ? pois.get(poiId) : null;
    }

    public int getActiveCapacity(UUID cityId, CityPoiType type) {
        return getCityPois(cityId, type).stream().mapToInt(CityPoiData::capacity).sum();
    }

    public Collection<CityPoiData> allPois() {
        return List.copyOf(pois.values());
    }

    private void putLoaded(CityPoiData poi) {
        pois.put(poi.poiId(), poi);
        cityPoiIndex.computeIfAbsent(poi.cityId(), id -> ConcurrentHashMap.newKeySet()).add(poi.poiId());
        posIndex.put(poi.pos().immutable(), poi.poiId());
    }

    private void replacePoi(CityPoiData oldPoi, CityPoiData newPoi) {
        // 替换时必须同步维护三个索引，否则容量统计和按坐标查询会读到旧数据。
        Set<UUID> oldCityPois = cityPoiIndex.get(oldPoi.cityId());
        if (oldCityPois != null) {
            oldCityPois.remove(oldPoi.poiId());
        }
        posIndex.remove(oldPoi.pos(), oldPoi.poiId());
        if (!oldPoi.poiId().equals(newPoi.poiId())) {
            /*
             * 换了 poiId 就必须把旧 id 从全局缓存和数据库里一起清掉。saveCityPois 只做 upsert
             * （不再按维度清表重写），残留的旧行会在下次进档被读回：它和新 POI 同坐标、同城市，
             * posIndex 只留一个，但 cityPoiIndex 会同时含两者，getActiveCapacity 因此双计。
             */
            pois.remove(oldPoi.poiId());
            GLOBAL_POI_CACHE.remove(oldPoi.poiId());
            deletePoiIncremental(oldPoi.poiId());
        }
        pois.put(newPoi.poiId(), newPoi);
        cityPoiIndex.computeIfAbsent(newPoi.cityId(), id -> ConcurrentHashMap.newKeySet()).add(newPoi.poiId());
        posIndex.put(newPoi.pos().immutable(), newPoi.poiId());
    }
}
