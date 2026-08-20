package common.cn.kafei.simukraft.city;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** CityLevelDefinitionLoader: 从数据包原子加载城市等级定义。 */
@SuppressWarnings("null")
public final class CityLevelDefinitionLoader implements PreparableReloadListener {
    public static final CityLevelDefinitionLoader INSTANCE = new CityLevelDefinitionLoader();
    private static final String DIRECTORY = "city_levels";
    private static final int MAX_DEFINITIONS = 1_024;
    private final AtomicReference<List<CityLevelDefinition>> definitions = new AtomicReference<>(List.of());

    private CityLevelDefinitionLoader() {
    }

    /** definitions: 返回当前已发布的不可变等级快照。 */
    public List<CityLevelDefinition> definitions() {
        return definitions.get();
    }

    /** definition: 按等级读取当前生效的城市等级定义。 */
    public CityLevelDefinition definition(int level) {
        for (CityLevelDefinition definition : definitions()) {
            if (definition.level() == level) {
                return definition;
            }
        }
        return null;
    }

    /** nextLevel: 查找高于当前等级的下一个数据包等级。 */
    public CityLevelDefinition nextLevel(int currentLevel) {
        int normalizedCurrent = Math.max(CityLevelDefinition.MIN_LEVEL, currentLevel);
        if (normalizedCurrent >= CityLevelDefinition.MAX_LEVEL) {
            return null;
        }
        int targetLevel = normalizedCurrent + 1;
        return definition(targetLevel);
    }

    /** futureLevels: 返回客户端等级列表使用的有限只读快照。 */
    public List<CityLevelDefinition> futureLevels(int currentLevel, int limit) {
        int normalizedCurrent = Math.max(CityLevelDefinition.MIN_LEVEL, currentLevel);
        int safeLimit = Math.max(0, Math.min(MAX_DEFINITIONS, limit));
        return definitions().stream()
                .filter(definition -> definition.level() > normalizedCurrent)
                .limit(safeLimit)
                .toList();
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier,
                                          ResourceManager resourceManager,
                                          ProfilerFiller preparationsProfiler,
                                          ProfilerFiller reloadProfiler,
                                          Executor backgroundExecutor,
                                          Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> load(resourceManager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(definitions::set, gameExecutor);
    }

    private List<CityLevelDefinition> load(ResourceManager resourceManager) {
        Map<Integer, CityLevelDefinition> byLevel = new LinkedHashMap<>();
        AtomicInteger definitionCount = new AtomicInteger();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                DIRECTORY, path -> path.getPath().endsWith(".json"));
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    try (Reader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                        List<CityLevelDefinition> parsed = parseDefinitions(entry.getKey(), JsonParser.parseReader(reader));
                        for (CityLevelDefinition definition : parsed) {
                            if (definitionCount.get() >= MAX_DEFINITIONS) {
                                SimuKraft.LOGGER.warn("Ignoring city level definition {} because the {} definition limit was reached", entry.getKey(), MAX_DEFINITIONS);
                                break;
                            }
                            definitionCount.incrementAndGet();
                            if (byLevel.putIfAbsent(definition.level(), definition) != null) {
                                SimuKraft.LOGGER.warn("Duplicate city level {} in {}, keeping the first definition", definition.level(), entry.getKey());
                            }
                        }
                    } catch (Exception exception) {
                        SimuKraft.LOGGER.error("Failed to load city level definition {}", entry.getKey(), exception);
                    }
                });
        List<CityLevelDefinition> loaded = new ArrayList<>(byLevel.values());
        loaded.sort(Comparator.comparingInt(CityLevelDefinition::level));
        SimuKraft.LOGGER.info("Loaded {} city level definitions", loaded.size());
        return List.copyOf(loaded);
    }

    /** parseDefinitions: 解析单对象、根数组或 levels 包装格式的等级 JSON。 */
    static List<CityLevelDefinition> parseDefinitions(ResourceLocation resourceId, JsonElement root) {
        if (root == null || root.isJsonNull()) {
            throw new IllegalArgumentException("City level root must not be null");
        }
        if (root.isJsonArray()) {
            JsonArray levels = root.getAsJsonArray();
            if (levels.size() > MAX_DEFINITIONS) {
                throw new IllegalArgumentException("Too many city level definitions in " + resourceId);
            }
            List<CityLevelDefinition> definitions = new ArrayList<>(levels.size());
            for (JsonElement level : levels) {
                if (!level.isJsonObject()) {
                    throw new IllegalArgumentException("City level entry must be an object in " + resourceId);
                }
                definitions.add(parse(resourceId, level.getAsJsonObject()));
            }
            return List.copyOf(definitions);
        }
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("City level root must be an object or array");
        }
        JsonObject object = root.getAsJsonObject();
        if (!object.has("levels")) {
            return List.of(parse(resourceId, object));
        }
        JsonElement levelsValue = object.get("levels");
        if (!levelsValue.isJsonArray()) {
            throw new IllegalArgumentException("levels must be an array in " + resourceId);
        }
        JsonArray levels = levelsValue.getAsJsonArray();
        if (levels.size() > MAX_DEFINITIONS) {
            throw new IllegalArgumentException("Too many city level definitions in " + resourceId);
        }
        List<CityLevelDefinition> definitions = new ArrayList<>(levels.size());
        for (JsonElement level : levels) {
            if (!level.isJsonObject()) {
                throw new IllegalArgumentException("City level entry must be an object in " + resourceId);
            }
            definitions.add(parse(resourceId, level.getAsJsonObject()));
        }
        return List.copyOf(definitions);
    }

    /** parse: 校验并解析单个城市等级 JSON。 */
    static CityLevelDefinition parse(ResourceLocation resourceId, JsonObject root) {
        if (root == null) {
            throw new IllegalArgumentException("City level root must be an object");
        }
        int level = integer(root, "level", -1);
        if (level < CityLevelDefinition.MIN_LEVEL) {
            throw new IllegalArgumentException("Missing or invalid level in " + resourceId);
        }
        String displayName = text(root, "display_name", "Lv" + level);
        int durationTicks = integer(root, "duration_ticks",
                level == CityLevelDefinition.MIN_LEVEL ? 0 : CityLevelDefinition.DEFAULT_UPGRADE_DURATION_TICKS);
        JsonObject requirements = optionalObject(root, "requirements");
        double funds = decimal(requirements, "funds", 0.0D);
        int population = integer(requirements, "population", 0);
        JsonObject unlocks = optionalObject(root, "unlocks");
        int chunks = integer(unlocks, "chunks", CityLevelDefinition.UNLIMITED);
        int enclaves = integer(unlocks, "enclaves", CityLevelDefinition.DEFAULT_UNLOCKED_ENCLAVES);
        if (!Double.isFinite(funds) || funds < 0.0D || population < 0
                || chunks < CityLevelDefinition.UNLIMITED || chunks > CityLevelDefinition.MAX_UNLOCKED_CHUNKS
                || enclaves < CityLevelDefinition.UNLIMITED || enclaves > CityLevelDefinition.MAX_UNLOCKED_ENCLAVES
                || durationTicks < 0 || durationTicks > CityLevelDefinition.MAX_UPGRADE_DURATION_TICKS
                || (level > CityLevelDefinition.MIN_LEVEL && durationTicks <= 0)) {
            throw new IllegalArgumentException("Invalid city level requirements or unlocks in " + resourceId);
        }
        List<CityLevelDefinition.ItemRequirement> items = parseItems(requirements, resourceId);
        return new CityLevelDefinition(level, displayName, funds, population, chunks, enclaves, items, durationTicks);
    }

    private static List<CityLevelDefinition.ItemRequirement> parseItems(JsonObject requirements, ResourceLocation resourceId) {
        JsonElement value = requirements == null ? null : requirements.get("items");
        if (value == null) {
            return List.of();
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() > CityLevelDefinition.MAX_ITEMS) {
            throw new IllegalArgumentException("Invalid items array in " + resourceId);
        }
        Map<String, CityLevelDefinition.ItemRequirement> totals = new LinkedHashMap<>();
        JsonArray array = value.getAsJsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Item requirement must be an object in " + resourceId);
            }
            JsonObject itemObject = element.getAsJsonObject();
            String itemValue = text(itemObject, "item", "");
            String tagValue = text(itemObject, "tag", "");
            if (itemValue.isBlank() == tagValue.isBlank()) {
                throw new IllegalArgumentException("Item requirement must define exactly one item or tag in " + resourceId);
            }
            boolean tag = !tagValue.isBlank() || itemValue.stripLeading().startsWith("#");
            String serializedId = (tagValue.isBlank() ? itemValue : tagValue).stripLeading();
            if (serializedId.startsWith("#")) {
                serializedId = serializedId.substring(1);
            }
            ResourceLocation resource = ResourceLocation.parse(serializedId);
            if (!tag && !BuiltInRegistries.ITEM.containsKey(resource)) {
                throw new IllegalArgumentException("Unknown item " + resource + " in " + resourceId);
            }
            int count = integer(itemObject, "count", -1);
            if (count <= 0 || count > CityLevelDefinition.MAX_ITEM_COUNT) {
                throw new IllegalArgumentException("Invalid item count for " + resource + " in " + resourceId);
            }
            String displayIconValue = text(itemObject, "display_icon", "");
            ResourceLocation displayIcon = null;
            if (!displayIconValue.isBlank()) {
                displayIcon = ResourceLocation.parse(displayIconValue);
                if (!BuiltInRegistries.ITEM.containsKey(displayIcon)) {
                    throw new IllegalArgumentException("Unknown display icon " + displayIcon + " in " + resourceId);
                }
            }
            String displayName = text(itemObject, "display_name", "");
            String key = (tag ? "tag:" : "item:") + resource;
            CityLevelDefinition.ItemRequirement existing = totals.get(key);
            long total = (long) (existing == null ? 0 : existing.count()) + count;
            if (total > CityLevelDefinition.MAX_ITEM_COUNT) {
                throw new IllegalArgumentException("Combined item count is too large for " + resource + " in " + resourceId);
            }
            if (existing != null) {
                if (existing.displayIcon() != null && displayIcon != null && !existing.displayIcon().equals(displayIcon)
                        || !existing.displayName().isBlank() && !displayName.isBlank() && !existing.displayName().equals(displayName)) {
                    throw new IllegalArgumentException("Conflicting item display configuration for " + resource + " in " + resourceId);
                }
                displayIcon = existing.displayIcon() != null ? existing.displayIcon() : displayIcon;
                displayName = !existing.displayName().isBlank() ? existing.displayName() : displayName;
            }
            totals.put(key, new CityLevelDefinition.ItemRequirement(
                    tag ? null : resource,
                    tag ? resource : null,
                    (int) total,
                    displayIcon,
                    displayName));
            if (totals.size() > CityLevelDefinition.MAX_ITEMS) {
                throw new IllegalArgumentException("Too many distinct item requirements in " + resourceId);
            }
        }
        return List.copyOf(totals.values());
    }

    private static JsonObject optionalObject(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null) {
            return null;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String text(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        JsonElement element = root.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be text");
        }
        String value = element.getAsString();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int integer(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            JsonElement element = root.get(key);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(key + " must be an integer");
            }
            return element.getAsBigDecimal().intValueExact();
        } catch (Exception exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static double decimal(JsonObject root, String key, double fallback) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            JsonElement element = root.get(key);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(key + " must be a number");
            }
            double value = element.getAsDouble();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(key + " must be finite");
            }
            return value;
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException(key + " must be a number", exception);
        }
    }
}
