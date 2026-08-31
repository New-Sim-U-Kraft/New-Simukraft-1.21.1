package common.cn.kafei.simukraft.commercial;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CommercialDefinitionLoader {
    private static final int MAX_OFFERS = 256;
    private static final int MAX_RESOURCES = 4;
    private static final int MAX_POSITIONS = 64;
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private CommercialDefinitionLoader() {
    }

    /** loadForBuilding: 根据已完成建筑加载商业定义。 */
    public static LoadResult loadForBuilding(PlacedBuildingRecord building) {
        if (building == null) {
            return LoadResult.missing("missing_building");
        }
        Optional<BuildingCatalog.BuildingDefinition> definition = BuildingCatalog.findBuilding(building.category(), building.buildingFileName());
        if (definition.isPresent()) {
            String explicit = CommercialDefinitionSourceResolver.explicitCommercialFileName(definition.get());
            if (explicit != null) {
                return load(definition.get(), explicit);
            }
            String sibling = CommercialDefinitionSourceResolver.siblingCommercialFileName(definition.get());
            if (sibling != null) {
                return load(definition.get(), sibling);
            }
        }
        return LoadResult.missing("missing_commercial_json");
    }

    /** load: 从磁盘文件加载商业定义。 */
    public static LoadResult load(Path path) {
        if (path == null) {
            return LoadResult.missing("missing_path");
        }
        try {
            long modified = Files.getLastModifiedTime(path).toMillis();
            String key = path.toAbsolutePath().normalize().toString();
            CacheEntry cached = CACHE.get(key);
            if (cached != null && cached.modified() == modified) {
                return cached.result();
            }
            LoadResult result = loadText(Files.readString(path, StandardCharsets.UTF_8), stripExtension(path.getFileName().toString()), path);
            CACHE.put(key, new CacheEntry(modified, result));
            return result;
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to load commercial definition {}", path, exception);
            return LoadResult.missing("invalid_commercial_json");
        }
    }

    /** load: 从建筑包内 JSON 文本加载商业定义。 */
    private static LoadResult load(BuildingCatalog.BuildingDefinition definition, String fileName) {
        if (definition == null || fileName == null || fileName.isBlank()) {
            return LoadResult.missing("missing_path");
        }
        String key = "package:" + definition.packageKey() + ":" + definition.category() + "/" + fileName.toLowerCase(Locale.ROOT);
        long modified = packageModified(definition);
        CacheEntry cached = CACHE.get(key);
        if (cached != null && cached.modified() == modified) {
            return cached.result();
        }
        try {
            String text = definition.readFileText(fileName).orElse(null);
            if (text == null) {
                return LoadResult.missing("missing_commercial_json");
            }
            LoadResult result = loadText(text, stripExtension(fileName), definition.packagePath());
            CACHE.put(key, new CacheEntry(modified, result));
            return result;
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to load commercial definition {} from {}", fileName, definition.packageName(), exception);
            return LoadResult.missing("invalid_commercial_json");
        }
    }

    /** clearCache: 清理定义缓存。 */
    public static void clearCache() {
        CACHE.clear();
        CommercialDefinitionSourceResolver.clearCache();
    }

    private static LoadResult loadText(String text, String fallbackId, @Nullable Path sourcePath) {
        List<String> errors = new ArrayList<>();
        JsonObject root = asObject(JsonParser.parseString(text));
        if (root == null) {
            return new LoadResult(null, List.of("invalid_root"), sourcePath);
        }
        String id = stringAny(root, fallbackId, "id", "buildingId", "building_id");
        String name = stringAny(root, id, "name", "buildingName", "building_name");
        CommercialDefinition.JobDefinition job = parseJob(root);
        CommercialDefinition.WorkTime workTime = parseWorkTime(root.getAsJsonObject("workTime"));
        Map<String, CommercialDefinition.ContainerDefinition> containers = parseContainers(root.getAsJsonObject("containers"), errors);
        List<CommercialOffer> offers = parseOffers(root.getAsJsonArray("offers"), errors);
        if (offers.isEmpty()) {
            offers = CommercialLegacyDefinitionParser.parse(root, errors, MAX_OFFERS, MAX_RESOURCES);
        }
        if (offers.isEmpty()) {
            errors.add("missing_offers");
        }
        CommercialDefinition definition = new CommercialDefinition(id, name, job, workTime, containers, offers, sourcePath);
        return new LoadResult(definition, List.copyOf(errors), sourcePath);
    }

    private static CommercialDefinition.JobDefinition parseJob(@Nullable JsonObject root) {
        JsonObject object = root != null ? root.getAsJsonObject("job") : null;
        if (object == null) {
            return new CommercialDefinition.JobDefinition(
                    stringAny(root, "commercial_worker", "jobType", "job_type"),
                    stringAny(root, "商业员工", "jobName", "job_name"),
                    stringAny(root, "", "heldItem", "held_item")
            );
        }
        return new CommercialDefinition.JobDefinition(
                string(object, "id", "commercial_worker"),
                string(object, "name", string(object, "id", "商业员工")),
                stringAny(object, "", "heldItem", "held_item")
        );
    }

    private static CommercialDefinition.WorkTime parseWorkTime(@Nullable JsonObject object) {
        if (object == null) {
            return CommercialDefinition.WorkTime.always();
        }
        return new CommercialDefinition.WorkTime(
                Math.floorMod(integer(object, "start", 0), 24000),
                Math.floorMod(integer(object, "end", 0), 24000)
        );
    }

    /** parseContainers: 解析商业建筑显式物流容器声明。 */
    private static Map<String, CommercialDefinition.ContainerDefinition> parseContainers(@Nullable JsonObject object, List<String> errors) {
        Map<String, CommercialDefinition.ContainerDefinition> containers = new LinkedHashMap<>();
        if (object == null) {
            return containers;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String id = entry.getKey();
            JsonObject containerObject = asObject(entry.getValue());
            if (containerObject == null) {
                errors.add("invalid_container:" + id);
                continue;
            }
            List<BlockPos> positions = parsePositions(containerObject, errors, "container:" + id);
            if (!positions.isEmpty()) {
                containers.put(id, new CommercialDefinition.ContainerDefinition(id, string(containerObject, "type", "structure_pos"), positions));
            }
        }
        return Map.copyOf(containers);
    }

    private static List<CommercialOffer> parseOffers(@Nullable JsonArray array, List<String> errors) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<CommercialOffer> offers = new ArrayList<>();
        int limit = Math.min(array.size(), MAX_OFFERS);
        for (int i = 0; i < limit; i++) {
            JsonObject object = asObject(array.get(i));
            if (object == null) {
                errors.add("invalid_offer:" + i);
                continue;
            }
            String id = string(object, "id", "offer_" + i);
            CommercialOffer offer = new CommercialOffer(
                    id,
                    CommercialVisibility.fromName(stringAny(object, "player", "visibleTo", "visible_to", "visibility")),
                    parseResources(arrayAny(object, "cost", "costs"), errors, id + ":cost"),
                    parseResources(arrayAny(object, "result", "results"), errors, id + ":result"),
                    parseStock(object.getAsJsonObject("stock"))
            );
            if (!offer.valid()) {
                errors.add("invalid_offer:" + id);
                continue;
            }
            offers.add(offer);
        }
        return List.copyOf(offers);
    }

    private static List<CommercialResource> parseResources(@Nullable JsonArray array, List<String> errors, String context) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<CommercialResource> resources = new ArrayList<>();
        int limit = Math.min(array.size(), MAX_RESOURCES);
        for (int i = 0; i < limit; i++) {
            JsonObject object = asObject(array.get(i));
            if (object == null) {
                errors.add("invalid_resource:" + context + ":" + i);
                continue;
            }
            CommercialResource resource = parseResource(object);
            if (resource.valid()) {
                resources.add(resource);
            } else {
                errors.add("invalid_resource:" + context + ":" + i);
            }
        }
        return List.copyOf(resources);
    }

    private static CommercialResource parseResource(JsonObject object) {
        if (object.has("money")) {
            return CommercialResource.money(decimal(object, "money", 0.0D));
        }
        String itemId = string(object, "item", "");
        int count = Math.max(1, integer(object, "count", integer(object, "amount", 1)));
        return CommercialResource.item(itemId, count);
    }

    private static CommercialOffer.StockRule parseStock(@Nullable JsonObject object) {
        if (object == null) {
            return null;
        }
        return new CommercialOffer.StockRule(
                string(object, "item", ""),
                Math.max(0, integer(object, "max", 0)),
                Math.max(0, integer(object, "initial", 0)),
                Math.max(0, integerAny(object, 0, "restockAmount", "restock_amount")),
                Math.max(0L, longAny(object, 0L, "restockInterval", "restock_interval")),
                parseMaterials(arrayAny(object, "materials", "requiredMaterials", "required_materials"))
        );
    }

    private static List<CommercialOffer.MaterialRequirement> parseMaterials(@Nullable JsonArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<CommercialOffer.MaterialRequirement> materials = new ArrayList<>();
        int limit = Math.min(array.size(), MAX_RESOURCES);
        for (int i = 0; i < limit; i++) {
            JsonObject object = asObject(array.get(i));
            if (object == null) {
                continue;
            }
            CommercialOffer.MaterialRequirement requirement = new CommercialOffer.MaterialRequirement(
                    string(object, "item", ""),
                    Math.max(1, integer(object, "count", integer(object, "amount", 1)))
            );
            if (requirement.valid()) {
                materials.add(requirement);
            }
        }
        return List.copyOf(materials);
    }

    /** parsePositions: 读取结构内相对坐标数组，错误只进入定义错误列表。 */
    private static List<BlockPos> parsePositions(JsonObject object, List<String> errors, String context) {
        List<BlockPos> positions = new ArrayList<>();
        if (object == null) {
            errors.add("missing_positions:" + context);
            return List.of();
        }
        if (object.has("pos")) {
            BlockPos pos = parsePositionArray(object.get("pos"));
            if (pos != null) {
                positions.add(pos);
            }
        }
        if (object.has("positions") && object.get("positions").isJsonArray()) {
            JsonArray array = object.getAsJsonArray("positions");
            int limit = Math.min(array.size(), MAX_POSITIONS);
            for (int i = 0; i < limit; i++) {
                BlockPos pos = parsePositionArray(array.get(i));
                if (pos != null) {
                    positions.add(pos);
                }
            }
        }
        if (positions.isEmpty()) {
            errors.add("missing_positions:" + context);
        }
        return List.copyOf(positions);
    }

    @Nullable
    private static BlockPos parsePositionArray(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() < 3) {
            return null;
        }
        try {
            return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
        } catch (Exception exception) {
            return null;
        }
    }

    @Nullable
    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    @Nullable
    private static JsonArray arrayAny(JsonObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonArray()) {
                return object.getAsJsonArray(key);
            }
        }
        return null;
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static String stringAny(JsonObject object, String fallback, String... keys) {
        for (String key : keys) {
            String value = string(object, key, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback;
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

    private static int integerAny(JsonObject object, int fallback, String... keys) {
        for (String key : keys) {
            if (object != null && object.has(key)) {
                return integer(object, key, fallback);
            }
        }
        return fallback;
    }

    private static long longAny(JsonObject object, long fallback, String... keys) {
        for (String key : keys) {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
                continue;
            }
            try {
                return object.get(key).getAsLong();
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double decimal(JsonObject object, String key, double fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsDouble();
        } catch (Exception exception) {
            return fallback;
        }
    }

    /** packageModified: 用建筑包 ZIP 的修改时间让替换后的 JSON 重新加载。 */
    private static long packageModified(BuildingCatalog.BuildingDefinition definition) {
        Path path = definition != null ? definition.packagePath() : null;
        if (path == null) {
            return 0L;
        }
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception exception) {
            return 0L;
        }
    }

    private static String stripExtension(String fileName) {
        return stripExtensionPreserveCase(fileName).toLowerCase(Locale.ROOT);
    }

    private static String stripExtensionPreserveCase(String fileName) {
        String safeName = fileName != null ? fileName : "";
        int index = safeName.lastIndexOf('.');
        return index > 0 ? safeName.substring(0, index) : safeName;
    }

    public record LoadResult(CommercialDefinition definition, List<String> errors, Path path) {
        /** missing: 创建缺失定义结果。 */
        public static LoadResult missing(String error) {
            return new LoadResult(null, List.of(error), null);
        }

        /** valid: 判断定义是否无错误可用。 */
        public boolean valid() {
            return definition != null && errors.isEmpty();
        }
    }

    private record CacheEntry(long modified, LoadResult result) {
    }
}
