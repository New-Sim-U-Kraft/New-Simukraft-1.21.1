package common.cn.kafei.simukraft.city.poi;

public enum CityPoiType {
    RESIDENTIAL,
    COMMERCIAL,
    INDUSTRIAL,
    OTHER,
    GATHERING,
    STORAGE,
    LOGISTICS,
    FARMLAND,
    DEFENSE,
    MEDICAL,
    BANK,
    EXCHANGE;

    public static CityPoiType fromName(String name) {
        for (CityPoiType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return OTHER;
    }
}
