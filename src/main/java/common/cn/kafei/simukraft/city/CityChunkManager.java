package common.cn.kafei.simukraft.city;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class CityChunkManager extends SavedData {
    private static final String DATA_NAME = SimuKraft.MOD_ID + "_city_chunks";
    private static final Factory<CityChunkManager> FACTORY = new Factory<>(CityChunkManager::new, CityChunkManager::load, null);

    // cityChunks 方便按城市取领地，chunkCityIndex 方便按区块反查归属。
    private final ConcurrentMap<UUID, Set<Long>> cityChunks = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, UUID> chunkCityIndex = new ConcurrentHashMap<>();
    private volatile boolean sqliteLoaded;
    private volatile ServerLevel level;

    public static CityChunkManager get(ServerLevel level) {
        CityChunkManager manager = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        manager.level = level;
        manager.loadFromSqlite(level);
        return manager;
    }

    private static CityChunkManager load(CompoundTag tag, HolderLookup.Provider registries) {
        CityChunkManager manager = new CityChunkManager();
        ListTag cityTags = tag.getList("CityChunks", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < cityTags.size(); i++) {
            CompoundTag cityTag = cityTags.getCompound(i);
            UUID cityId = cityTag.getUUID("CityId");
            Set<Long> chunks = ConcurrentHashMap.newKeySet();
            ListTag chunkTags = cityTag.getList("Chunks", LongTag.TAG_LONG);
            for (int j = 0; j < chunkTags.size(); j++) {
                long chunkLong = ((LongTag) chunkTags.get(j)).getAsLong();
                chunks.add(chunkLong);
                manager.chunkCityIndex.put(chunkLong, cityId);
            }
            manager.cityChunks.put(cityId, chunks);
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag cityTags = new ListTag();
        cityChunks.forEach((cityId, chunks) -> {
            CompoundTag cityTag = new CompoundTag();
            cityTag.putUUID("CityId", cityId);
            ListTag chunkTags = new ListTag();
            chunks.forEach(chunk -> chunkTags.add(LongTag.valueOf(chunk)));
            cityTag.put("Chunks", chunkTags);
            cityTags.add(cityTag);
        });
        tag.put("CityChunks", cityTags);
        return tag;
    }

    public synchronized void saveToSqlite(ServerLevel level) {
        if (level == null) {
            return;
        }
        SimuSqliteStorage.saveCityChunks(level, save(new CompoundTag(), level.registryAccess()));
    }

    public synchronized void reloadFromSqlite(ServerLevel level) {
        CompoundTag existing = SimuSqliteStorage.loadCityChunks(level);
        if ((existing == null || existing.isEmpty()) && !cityChunks.isEmpty()) {
            saveToSqlite(level);
        }
        cityChunks.clear();
        chunkCityIndex.clear();
        sqliteLoaded = false;
        loadFromSqlite(level);
    }

    private synchronized void loadFromSqlite(ServerLevel level) {
        if (sqliteLoaded) {
            return;
        }
        sqliteLoaded = true;
        CompoundTag sqliteTag = SimuSqliteStorage.loadCityChunks(level);
        if (sqliteTag == null || sqliteTag.isEmpty()) {
            return;
        }
        CityChunkManager loaded = load(sqliteTag, level.registryAccess());
        cityChunks.clear();
        chunkCityIndex.clear();
        cityChunks.putAll(loaded.cityChunks);
        chunkCityIndex.putAll(loaded.chunkCityIndex);
    }

    private void saveChunkIncremental(UUID cityId, long chunkLong) {
        ServerLevel targetLevel = level;
        if (targetLevel != null && cityId != null) {
            SimuSqliteStorage.saveCityChunk(targetLevel, cityId, chunkLong);
        }
    }

    private void deleteChunkIncremental(UUID cityId, long chunkLong) {
        ServerLevel targetLevel = level;
        if (targetLevel != null && cityId != null) {
            SimuSqliteStorage.deleteCityChunk(targetLevel, cityId, chunkLong);
        }
    }

    private void deleteCityChunksIncremental(UUID cityId) {
        ServerLevel targetLevel = level;
        if (targetLevel != null && cityId != null) {
            SimuSqliteStorage.deleteCityChunks(targetLevel, cityId);
        }
    }

    public boolean isAreaAvailable(ChunkPos centerChunk) {
        if (centerChunk == null) {
            return false;
        }
        // 建城初始领地固定检查 3x3，任意一个 chunk 已占用就不能创建。
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (chunkCityIndex.containsKey(ChunkPos.asLong(centerChunk.x + x, centerChunk.z + z))) {
                    return false;
                }
            }
        }
        return true;
    }

    public synchronized boolean assignInitialArea(UUID cityId, ChunkPos centerChunk) {
        if (cityId == null || !isAreaAvailable(centerChunk)) {
            return false;
        }
        Set<Long> chunks = cityChunks.computeIfAbsent(cityId, id -> ConcurrentHashMap.newKeySet());
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                long chunkLong = ChunkPos.asLong(centerChunk.x + x, centerChunk.z + z);
                chunks.add(chunkLong);
                chunkCityIndex.put(chunkLong, cityId);
                saveChunkIncremental(cityId, chunkLong);
            }
        }
        setDirty();
        return true;
    }

    public UUID getChunkOwner(long chunkLong) {
        return chunkCityIndex.get(chunkLong);
    }

    public Set<Long> getCityChunks(UUID cityId) {
        Set<Long> chunks = cityChunks.get(cityId);
        return chunks == null ? Collections.emptySet() : Collections.unmodifiableSet(chunks);
    }

    public synchronized boolean claimChunk(UUID cityId, long chunkLong) {
        if (cityId == null || chunkCityIndex.containsKey(chunkLong)) {
            return false;
        }
        Set<Long> chunks = cityChunks.computeIfAbsent(cityId, id -> ConcurrentHashMap.newKeySet());
        chunks.add(chunkLong);
        chunkCityIndex.put(chunkLong, cityId);
        saveChunkIncremental(cityId, chunkLong);
        setDirty();
        return true;
    }

    public synchronized boolean unclaimChunk(UUID cityId, long chunkLong) {
        if (cityId == null || !cityId.equals(chunkCityIndex.get(chunkLong))) {
            return false;
        }
        Set<Long> chunks = cityChunks.get(cityId);
        if (chunks == null) {
            return false;
        }
        chunks.remove(chunkLong);
        chunkCityIndex.remove(chunkLong);
        deleteChunkIncremental(cityId, chunkLong);
        setDirty();
        return true;
    }

    public boolean isAdjacentToCity(UUID cityId, long chunkLong) {
        Set<Long> chunks = cityChunks.get(cityId);
        if (chunks == null || chunks.isEmpty()) {
            return false;
        }
        ChunkPos chunkPos = new ChunkPos(chunkLong);
        // 扩张只允许四向相邻，斜角接触不算连通领地。
        return chunks.contains(ChunkPos.asLong(chunkPos.x + 1, chunkPos.z))
                || chunks.contains(ChunkPos.asLong(chunkPos.x - 1, chunkPos.z))
                || chunks.contains(ChunkPos.asLong(chunkPos.x, chunkPos.z + 1))
                || chunks.contains(ChunkPos.asLong(chunkPos.x, chunkPos.z - 1));
    }

    public synchronized void releaseCity(UUID cityId) {
        Set<Long> chunks = cityChunks.remove(cityId);
        if (chunks == null) {
            return;
        }
        chunks.forEach(chunkCityIndex::remove);
        deleteCityChunksIncremental(cityId);
        setDirty();
    }
}
