package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.world.level.block.Block;

/** PublicBuildingTypes: 公共建筑类别以 JSON type 为准，和医院同一套规则。 */
public final class PublicBuildingTypes {
    private PublicBuildingTypes() {
    }

    /** of: 读取建筑包 JSON 声明的公共子类。 */
    public static BuildingCatalog.BuildingType of(PlacedBuildingRecord building) {
        if (building == null) {
            return BuildingCatalog.BuildingType.STANDARD;
        }
        return BuildingCatalog.findBuilding(building.category(), building.buildingFileName())
                .map(BuildingCatalog.BuildingDefinition::buildingType)
                .orElse(BuildingCatalog.BuildingType.STANDARD);
    }

    /** isMedical: 医院类公共建筑。 */
    public static boolean isMedical(PlacedBuildingRecord building) {
        return matches(building, BuildingCatalog.BuildingType.MEDICAL, CityPoiType.MEDICAL, ModBlocks.MEDICAL_CONTROL_BOX.get());
    }

    /** isBank: 银行类公共建筑。 */
    public static boolean isBank(PlacedBuildingRecord building) {
        return matches(building, BuildingCatalog.BuildingType.BANK, CityPoiType.BANK, ModBlocks.BANK_CONTROL_BOX.get());
    }

    /** isExchange: 交易所类公共建筑。 */
    public static boolean isExchange(PlacedBuildingRecord building) {
        return matches(building, BuildingCatalog.BuildingType.EXCHANGE, CityPoiType.EXCHANGE, ModBlocks.EXCHANGE_CONTROL_BOX.get());
    }

    private static boolean matches(PlacedBuildingRecord building,
                                   BuildingCatalog.BuildingType expected,
                                   CityPoiType poiType,
                                   Block controlBox) {
        if (building == null || expected == null) {
            return false;
        }
        BuildingCatalog.BuildingType declared = of(building);
        if (declared == expected) {
            return true;
        }
        if (declared != BuildingCatalog.BuildingType.STANDARD) {
            return false;
        }
        return building.poiDefinitions().stream().anyMatch(poi -> poi.poiType() == poiType)
                || building.poiInstances().stream().anyMatch(poi -> poi.poiType() == poiType)
                || building.blocks().stream().anyMatch(block -> block.state() != null && block.state().is(controlBox));
    }
}
