package common.cn.kafei.simukraft.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class PublicBuildingTypeTest {
    @Test
    void publicJsonType_selectsHospitalBankAndExchange() {
        assertEquals(BuildingCatalog.BuildingType.BANK, BuildingPackageCatalog.publicTypeFromJson(type("bank")));
        assertEquals(BuildingCatalog.BuildingType.EXCHANGE, BuildingPackageCatalog.publicTypeFromJson(type("exchange")));
        assertEquals(BuildingCatalog.BuildingType.MEDICAL, BuildingPackageCatalog.publicTypeFromJson(type("hospital")));
        assertEquals(BuildingCatalog.BuildingType.MEDICAL, BuildingPackageCatalog.publicTypeFromJson(type("medical")));
        assertEquals(BuildingCatalog.BuildingType.STANDARD, BuildingPackageCatalog.publicTypeFromJson(new JsonObject()));
    }

    @Test
    void publicJsonType_oldHospitalJsonWithoutType_usesServiceRange() {
        JsonObject root = new JsonObject();
        root.addProperty("id", "city_hospital");
        root.addProperty("serviceRangeRings", 4);
        assertEquals(BuildingCatalog.BuildingType.MEDICAL, BuildingPackageCatalog.publicTypeFromJson(root));
    }

    private static JsonObject type(String value) {
        JsonObject root = new JsonObject();
        root.addProperty("type", value);
        return root;
    }
}
