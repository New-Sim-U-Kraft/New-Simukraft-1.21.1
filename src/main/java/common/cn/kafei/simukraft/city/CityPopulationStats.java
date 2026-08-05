package common.cn.kafei.simukraft.city;

import common.cn.kafei.simukraft.building.BuildingPoiInstance;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenHomeRestService;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("null")
public final class CityPopulationStats {
    private CityPopulationStats() {
    }

    /** snapshot：生成城市核心和 HUD 共用的人口/住房容量快照。 */
    public static Snapshot snapshot(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return Snapshot.EMPTY;
        }
        CityPoiManager poiManager = CityPoiManager.get(level);
        return new Snapshot(population(level, cityId, poiManager), housingCapacity(level, cityId, poiManager));
    }

    /** population：统计存活且归属该城市的市民，兼容只有住房绑定的旧数据。 */
    public static int population(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return 0;
        }
        return population(level, cityId, CityPoiManager.get(level));
    }

    private static int population(ServerLevel level, UUID cityId, CityPoiManager poiManager) {
        Set<UUID> placedHomeIds = null;
        long count = 0L;
        for (CitizenData citizen : CitizenManager.get(level).allCitizens()) {
            if (citizen.dead()) {
                continue;
            }
            if (Objects.equals(cityId, citizen.cityId())) {
                count++;
                continue;
            }
            if (citizen.cityId() != null || citizen.homeId() == null) {
                continue;
            }
            if (registeredHomeBelongsToCity(poiManager, cityId, citizen.homeId())) {
                count++;
                continue;
            }
            if (placedHomeIds == null) {
                placedHomeIds = placedResidentialHomeIds(level, cityId);
            }
            if (placedHomeIds.contains(citizen.homeId())) {
                count++;
            }
        }
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, count));
    }

    /** housingCapacity：优先使用运行时 POI，缺失时从已完成建筑记录恢复住宅床位容量。 */
    public static int housingCapacity(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return 0;
        }
        return housingCapacity(level, cityId, CityPoiManager.get(level));
    }

    private static int housingCapacity(ServerLevel level, UUID cityId, CityPoiManager poiManager) {
        int registeredCapacity = poiManager.getActiveCapacity(cityId, CityPoiType.RESIDENTIAL);
        if (registeredCapacity > 0) {
            return registeredCapacity;
        }
        long placedCapacity = 0L;
        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            if (!Objects.equals(cityId, building.cityId())) {
                continue;
            }
            for (BuildingPoiInstance poi : building.poiInstances()) {
                if (poi.poiType() == CityPoiType.RESIDENTIAL && poi.capacity() > 0 && isLiveResidentialPoi(level, poi)) {
                    placedCapacity += poi.capacity();
                }
            }
        }
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, placedCapacity));
    }

    private static boolean registeredHomeBelongsToCity(CityPoiManager poiManager, UUID cityId, UUID homeId) {
        CityPoiData home = poiManager.getPoi(homeId);
        return home != null && home.active() && home.type() == CityPoiType.RESIDENTIAL && Objects.equals(cityId, home.cityId());
    }

    private static Set<UUID> placedResidentialHomeIds(ServerLevel level, UUID cityId) {
        Set<UUID> homeIds = new HashSet<>();
        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            if (!Objects.equals(cityId, building.cityId())) {
                continue;
            }
            for (BuildingPoiInstance poi : building.poiInstances()) {
                if (poi.poiType() == CityPoiType.RESIDENTIAL && isLiveResidentialPoi(level, poi)) {
                    try {
                        homeIds.add(UUID.fromString(poi.key()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        return homeIds;
    }

    private static boolean isLiveResidentialPoi(ServerLevel level, BuildingPoiInstance poi) {
        return poi.worldPos() != null && CitizenHomeRestService.isResidentialBedHead(level.getBlockState(poi.worldPos()));
    }

    public record Snapshot(int population, int housingCapacity) {
        private static final Snapshot EMPTY = new Snapshot(0, 0);
    }
}
