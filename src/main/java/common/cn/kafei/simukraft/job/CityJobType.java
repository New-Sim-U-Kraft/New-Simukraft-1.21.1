package common.cn.kafei.simukraft.job;

import common.cn.kafei.simukraft.city.poi.CityPoiType;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum CityJobType {
    UNEMPLOYED,
    RESIDENT,
    BUILDER,
    PLANNER,
    COMMERCIAL_WORKER,
    INDUSTRIAL_WORKER,
    FARMER,
    LOGISTICS_WORKER,
    STORAGE_WORKER,
    GUARD,
    GATHERER,
    OTHER,
    DOCTOR,
    BANKER,
    BROKER;

    private static final Map<CityPoiType, CityJobType> POI_MAPPING = new EnumMap<>(CityPoiType.class);

    static {
        POI_MAPPING.put(CityPoiType.COMMERCIAL, COMMERCIAL_WORKER);
        POI_MAPPING.put(CityPoiType.INDUSTRIAL, INDUSTRIAL_WORKER);
        POI_MAPPING.put(CityPoiType.FARMLAND, FARMER);
        POI_MAPPING.put(CityPoiType.LOGISTICS, LOGISTICS_WORKER);
        POI_MAPPING.put(CityPoiType.STORAGE, STORAGE_WORKER);
        POI_MAPPING.put(CityPoiType.DEFENSE, GUARD);
        POI_MAPPING.put(CityPoiType.GATHERING, GATHERER);
        POI_MAPPING.put(CityPoiType.OTHER, OTHER);
    }

    public static CityJobType fromName(String name) {
        for (CityJobType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return UNEMPLOYED;
    }

    public static Optional<CityJobType> fromPoiType(CityPoiType type) {
        return Optional.ofNullable(POI_MAPPING.get(type));
    }

    public static Optional<CityPoiType> primaryPoiType(CityJobType jobType) {
        for (Map.Entry<CityPoiType, CityJobType> entry : POI_MAPPING.entrySet()) {
            if (entry.getValue() == jobType) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public String translationKey() {
        return "job." + name().toLowerCase(Locale.ROOT);
    }
}
