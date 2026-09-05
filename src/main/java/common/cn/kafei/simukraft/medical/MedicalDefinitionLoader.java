package common.cn.kafei.simukraft.medical;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 医疗 JSON 读取与缓存，避免每次医疗调度重复解析建筑包。 */
public final class MedicalDefinitionLoader {
    private static final ConcurrentMap<String, LoadResult> CACHE = new ConcurrentHashMap<>();

    private MedicalDefinitionLoader() {
    }

    /** loadForBuilding：加载已建成医疗建筑的业务 JSON。 */
    public static LoadResult loadForBuilding(PlacedBuildingRecord building) {
        if (building == null) {
            return LoadResult.missing("missing_building");
        }
        Optional<BuildingCatalog.BuildingDefinition> definition =
                BuildingCatalog.findBuilding(building.category(), building.buildingFileName());
        if (definition.isEmpty()) {
            return new LoadResult(MedicalDefinition.defaultFor(building.buildingFileName(), null), false, "missing_catalog");
        }
        String fileName = MedicalDefinitionSourceResolver.explicitMedicalFileName(definition.get());
        if (fileName == null) {
            fileName = MedicalDefinitionSourceResolver.siblingMedicalFileName(definition.get());
        }
        if (fileName == null) {
            return new LoadResult(MedicalDefinition.defaultFor(building.buildingFileName(), definition.get().packagePath()), true, "");
        }
        return load(definition.get(), fileName);
    }

    /** clearCache：建筑包刷新时释放医疗 JSON 缓存。 */
    public static void clearCache() {
        CACHE.clear();
    }

    private static LoadResult load(BuildingCatalog.BuildingDefinition definition, String fileName) {
        String key = "package:" + definition.packageKey() + ":" + definition.category() + "/" + fileName.toLowerCase(Locale.ROOT);
        return CACHE.computeIfAbsent(key, ignored -> parse(definition.readFileText(fileName).orElse(null),
                stripExtension(fileName), definition.packagePath()));
    }

    static LoadResult parse(@Nullable String text, String fallbackId, Path sourcePath) {
        if (text == null || text.isBlank()) {
            return new LoadResult(MedicalDefinition.defaultFor(fallbackId, sourcePath), true, "missing_medical_json");
        }
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            String id = string(root, "id", fallbackId);
            String name = string(root, "name", id);
            int requestedRings = integer(root, "serviceRangeRings", MedicalDefinition.DEFAULT_SERVICE_RANGE_RINGS);
            int safeRings = Math.clamp(requestedRings, 1, MedicalDefinition.MAX_SERVICE_RANGE_RINGS);
            if (requestedRings != safeRings) {
                SimuKraft.LOGGER.warn("Simukraft: Medical definition {} serviceRangeRings {} was clamped to {}", id, requestedRings, safeRings);
            }
            return new LoadResult(new MedicalDefinition(id, name, safeRings, sourcePath), true, "");
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to parse medical definition {}", fallbackId, exception);
            return new LoadResult(MedicalDefinition.defaultFor(fallbackId, sourcePath), false, "invalid_medical_json");
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            String value = object.get(key).getAsString();
            return value != null && !value.isBlank() ? value.trim() : fallback;
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception exception) {
            return fallback;
        }
    }

    
    private static String stripExtension(String fileName) {
        int dot = fileName != null ? fileName.lastIndexOf('.') : -1;
        return dot > 0 ? fileName.substring(0, dot) : "hospital";
    }

    public record LoadResult(MedicalDefinition definition, boolean valid, String error) {
        /** missing：构造缺少建筑时的无定义结果。 */
        public static LoadResult missing(String error) {
            return new LoadResult(null, false, error);
        }
    }
}
