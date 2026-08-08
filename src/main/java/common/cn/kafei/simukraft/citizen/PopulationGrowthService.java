package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityRuntimeService;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.server.level.ServerLevel;

public final class PopulationGrowthService {
    private PopulationGrowthService() {
    }

    public static int tick(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return 0;
        }
        // 每游戏日中午检查一次（6000 tick = 中午12点）
        if (level.getGameTime() % 24_000L != 6_000L) {
            return 0;
        }
        int timesPerWeek = ServerConfig.populationGrowthTimesPerWeek();
        int maxPerInterval = ServerConfig.populationGrowthMaxPerInterval();
        if (maxPerInterval <= 0 || timesPerWeek <= 0) {
            return 0;
        }
        int totalSpawned = 0;
        for (CityData city : CityService.allCities(level)) {
            if (!CityRuntimeService.isCityActive(level, city.cityId())) {
                continue;
            }
            // 每城市独立掷骰，互不影响：一周7天随机命中 timesPerWeek 次
            if (level.random.nextInt(7) >= timesPerWeek) {
                continue;
            }
            CitizenHousingService.fillVacantHomes(level, city.cityId());
            // 每城市有独立的配额，不与其他城市共享
            totalSpawned += CitizenHousingService.spawnCitizensForVacantHomes(
                    level, city.cityId(), city.cityCorePos().above(), maxPerInterval);
        }
        return totalSpawned;
    }
}
