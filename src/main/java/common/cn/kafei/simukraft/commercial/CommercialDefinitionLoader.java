package common.cn.kafei.simukraft.commercial;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CommercialDefinitionLoader {
    private static final int MAX_OFFERS = 256;
    private static final int MAX_RESOURCES = 4;
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
            Path explicit = explicitCommercialPath(definition.get().metaPath());
            if (explicit != null && Files.isRegularFile(explicit)) {
                return load(explicit);
            }
            Path sibling = definition.get().metaPath().getParent().resolve(stripExtension(definition.get().metaFileName()) + ".json");
            if (Files.isRegularFile(sibling)) {
                return load(sibling);
            }
        }
        LoadResult bundled = loadBundled(stripExtension(building.buildingFileName()));
        return bundled.definition() != null ? bundled : LoadResult.missing("missing_commercial_json");
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

    /** clearCache: 清理定义缓存。 */
    public static void clearCache() {
        CACHE.clear();
    }

    private static LoadResult loadBundled(String baseName) {
        String normalized = stripExtension(baseName);
        String key = "bundled:" + normalized;
        CacheEntry cached = CACHE.get(key);
        if (cached != null) {
            return cached.result();
        }
        String resourcePath = "/assets/" + SimuKraft.MOD_ID + "/commercial/" + normalized + ".json";
        try (var input = CommercialDefinitionLoader.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return LoadResult.missing("missing_commercial_json");
            }
            LoadResult result = loadText(new String(input.readAllBytes(), StandardCharsets.UTF_8), normalized, null);
            CACHE.put(key, new CacheEntry(0L, result));
            return result;
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to load bundled commercial definition {}", resourcePath, exception);
            return LoadResult.missing("invalid_commercial_json");
        }
    }

    private static LoadResult loadText(String text, String fallbackId, @Nullable Path sourcePath) {
        List<String> errors = new ArrayList<>();
        JsonObject root = asObject(JsonParser.parseString(text));
        if (root == null) {
            return new LoadResult(null, List.of("invalid_root"), sourcePath);
        }
        String id = string(root, "id", fallbackId);
        String name = string(root, "name", id);
        CommercialDefinition.JobDefinition job = parseJob(root.getAsJsonObject("job"));
        List<CommercialOffer> offers = parseOffers(root.getAsJsonArray("offers"), errors);
        if (offers.isEmpty()) {
            errors.add("missing_offers");
        }
        CommercialDefinition definition = new CommercialDefinition(id, name, job, offers, sourcePath);
        return new LoadResult(definition, List.copyOf(errors), sourcePath);
    }

    private static CommercialDefinition.JobDefinition parseJob(@Nullable JsonObject object) {
        if (object == null) {
            return new CommercialDefinition.JobDefinition("commercial_worker", "商业员工", "");
        }
        return new CommercialDefinition.JobDefinition(
                string(object, "id", "commercial_worker"),
                string(object, "name", string(object, "id", "商业员工")),
                stringAny(object, "", "heldItem", "held_item")
        );
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
                Math.max(0L, longAny(object, 0L, "restockInterval", "restock_interval"))
        );
    }

    @Nullable
    private static Path explicitCommercialPath(Path metaPath) {
        if (metaPath == null || !Files.isRegularFile(metaPath)) {
            return null;
        }
        try {
            for (String rawLine : Files.readAllLines(metaPath, StandardCharsets.UTF_8)) {
                String line = rawLine != null ? rawLine.trim() : "";
                if (!line.regionMatches(true, 0, "commercial:", 0, "commercial:".length())) {
                    continue;
                }
                String fileName = line.substring("commercial:".length()).trim();
                if (!fileName.isBlank()) {
                    return metaPath.getParent().resolve(fileName);
                }
            }
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read commercial entry from {}", metaPath, exception);
        }
        return null;
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

    private static String stripExtension(String fileName) {
        String safeName = fileName != null ? fileName : "";
        int index = safeName.lastIndexOf('.');
        return (index > 0 ? safeName.substring(0, index) : safeName).toLowerCase(Locale.ROOT);
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
