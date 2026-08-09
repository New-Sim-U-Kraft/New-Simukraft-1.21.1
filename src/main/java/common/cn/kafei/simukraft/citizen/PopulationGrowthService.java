package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityRuntimeService;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PopulationGrowthService {
    private static final long GROWTH_CHECK_TIME = 6_000L;
    private static final long TICKS_PER_DAY = 24_000L;
    private static final ConcurrentMap<String, Long> LAST_GROWTH_DAY_BY_LEVEL = new ConcurrentHashMap<>();

    private PopulationGrowthService() {
    }

    public static int tick(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return 0;
        }
        long dayTime = level.getDayTime();
        long currentDay = Math.floorDiv(dayTime, TICKS_PER_DAY);
        String levelKey = SaveScopedCacheKey.levelKey(level);
        long lastGrowthDay = LAST_GROWTH_DAY_BY_LEVEL.getOrDefault(levelKey, Long.MIN_VALUE);
        // 使用昼夜时间并允许跨过 6000 tick，避免 /time 或高速 tick 跳过精确时刻。
        if (!shouldRunGrowth(dayTime, lastGrowthDay)) {
            return 0;
        }
        LAST_GROWTH_DAY_BY_LEVEL.put(levelKey, currentDay);
        int totalSpawned = 0;
        for (CityData city : CityService.allCities(level)) {
            if (!CityRuntimeService.isCityActive(level, city.cityId())) {
                continue;
            }
            CitizenHousingService.fillVacantHomes(level, city.cityId());
            // 每个活跃城市每天固定尝试引进一名居民。
            totalSpawned += CitizenHousingService.spawnCitizensForVacantHomes(
                    level, city.cityId(), city.cityCorePos().above(), 1);
        }
        return totalSpawned;
    }

    /** shouldRunGrowth：判断当天是否首次达到每日人口增长时刻。 */
    static boolean shouldRunGrowth(long dayTime, long lastGrowthDay) {
        long currentDay = Math.floorDiv(dayTime, TICKS_PER_DAY);
        return currentDay != lastGrowthDay && Math.floorMod(dayTime, TICKS_PER_DAY) >= GROWTH_CHECK_TIME;
    }
}
