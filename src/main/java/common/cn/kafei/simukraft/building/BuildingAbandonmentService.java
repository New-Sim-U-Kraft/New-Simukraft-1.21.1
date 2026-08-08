package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.city.CityRuntimeService;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BuildingAbandonmentService {
    // [buildingId] -> {abandonmentIndex, lastTickDay}
    private static final ConcurrentMap<String, int[]> CACHE = new ConcurrentHashMap<>();
    private static final java.util.Set<String> LOADED_KEYS = ConcurrentHashMap.newKeySet();

    private BuildingAbandonmentService() {
    }

    // ── 对外 API ──────────────────────────────────────────────────────────────

    public static int get(ServerLevel level, UUID buildingId) {
        ensureLoaded(level);
        int[] entry = CACHE.get(key(level, buildingId));
        return entry != null ? entry[0] : 0;
    }

    // 房主死亡：+30；全城最小建筑每日：+5；自然恢复：-1
    public static void add(ServerLevel level, UUID buildingId, UUID cityId, int delta) {
        if (buildingId == null) return;
        ensureLoaded(level);
        String k = key(level, buildingId);
        CACHE.compute(k, (ignored, existing) -> {
            int cur = existing != null ? existing[0] : 0;
            long day = existing != null ? existing[1] : 0L;
            return new int[]{ Math.clamp(cur + delta, 0, 100), (int) day };
        });
        persist(level, buildingId, cityId);
    }

    // 新房主认领：重置为 0
    public static void reset(ServerLevel level, UUID buildingId, UUID cityId) {
        if (buildingId == null) return;
        ensureLoaded(level);
        CACHE.put(key(level, buildingId), new int[]{ 0, 0 });
        persist(level, buildingId, cityId);
    }

    // 每游戏日 tick：自然恢复 −1；全城最小建筑 +5
    public static void tickDaily(ServerLevel level, long currentDay) {
        ensureLoaded(level);
        var buildings = PlacedBuildingService.getBuildings(level);
        if (buildings.isEmpty()) return;

        // 找全城最小建筑（按minPos~maxPos体积）
        UUID smallestId = findSmallestBuilding(buildings);

        for (var building : buildings) {
            if (building.cityId() == null) continue;
            // 城市休眠时跳过废弃度更新
            if (!CityRuntimeService.isCityActive(level, building.cityId())) continue;
            String k = key(level, building.buildingId());
            int[] entry = CACHE.getOrDefault(k, new int[]{ 0, 0 });
            long lastDay = entry[1];
            if (lastDay >= currentDay) continue; // 今日已处理

            int cur = entry[0];
            cur = Math.max(0, cur - 1); // 自然恢复
            if (building.buildingId().equals(smallestId)) {
                cur = Math.min(100, cur + 5); // 全城最小惩罚
            }
            CACHE.put(k, new int[]{ cur, (int) currentDay });
            SimuSqliteStorage.saveBuildingAbandonment(level, building.buildingId(), building.cityId(), cur, currentDay);
        }
    }

    /** forget: 建筑被拆除后清掉它的废弃度（内存与数据库）。 */
    public static void forget(ServerLevel level, UUID buildingId) {
        if (level == null || buildingId == null) return;
        CACHE.remove(key(level, buildingId));
        SimuSqliteStorage.deleteBuildingAbandonment(level, buildingId);
    }

    public static void clearCache(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        String serverKey = common.cn.kafei.simukraft.util.SaveScopedCacheKey.serverKey(server);
        CACHE.keySet().removeIf(k -> k.startsWith(serverKey + "|"));
        LOADED_KEYS.removeIf(k -> k.startsWith(serverKey + "|"));
    }

    // ── 内部工具 ──────────────────────────────────────────────────────────────

    private static void ensureLoaded(ServerLevel level) {
        String loadedKey = common.cn.kafei.simukraft.util.SaveScopedCacheKey.levelKey(level);
        if (!LOADED_KEYS.add(loadedKey)) return;
        SimuSqliteStorage.loadBuildingAbandonment(level).forEach((buildingId, arr) ->
                CACHE.put(key(level, buildingId), arr));
    }

    private static String key(ServerLevel level, UUID buildingId) {
        return common.cn.kafei.simukraft.util.SaveScopedCacheKey.levelKey(level) + "|" + buildingId;
    }

    private static void persist(ServerLevel level, UUID buildingId, UUID cityId) {
        int[] entry = CACHE.get(key(level, buildingId));
        int idx = entry != null ? entry[0] : 0;
        long day = entry != null ? entry[1] : 0L;
        SimuSqliteStorage.saveBuildingAbandonment(level, buildingId, cityId, idx, day);
    }

    private static UUID findSmallestBuilding(java.util.List<PlacedBuildingRecord> buildings) {
        UUID smallest = null;
        long minVol = Long.MAX_VALUE;
        for (var b : buildings) {
            if (b.minPos() == null || b.maxPos() == null) continue;
            long vol = (long) Math.abs(b.maxPos().getX() - b.minPos().getX() + 1)
                    * Math.abs(b.maxPos().getY() - b.minPos().getY() + 1)
                    * Math.abs(b.maxPos().getZ() - b.minPos().getZ() + 1);
            if (vol < minVol) {
                minVol = vol;
                smallest = b.buildingId();
            }
        }
        return smallest;
    }
}
