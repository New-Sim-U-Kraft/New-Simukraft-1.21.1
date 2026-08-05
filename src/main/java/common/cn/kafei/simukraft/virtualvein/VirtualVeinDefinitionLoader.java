package common.cn.kafei.simukraft.virtualvein;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/** VirtualVeinDefinitionLoader: 加载虚拟矿脉数据包定义。 */
@SuppressWarnings("null")
public final class VirtualVeinDefinitionLoader implements PreparableReloadListener {
    public static final VirtualVeinDefinitionLoader INSTANCE = new VirtualVeinDefinitionLoader();
    private static final String DIRECTORY = "virtual_veins";
    private final AtomicReference<List<VirtualVeinDefinition>> definitions = new AtomicReference<>(List.of());

    private VirtualVeinDefinitionLoader() {
    }

    public List<VirtualVeinDefinition> definitions() {
        return definitions.get();
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
                .thenAcceptAsync(loaded -> {
                    definitions.set(loaded);
                    VirtualVeinService.clearCachedFields();
                }, gameExecutor);
    }

    private List<VirtualVeinDefinition> load(ResourceManager resourceManager) {
        List<VirtualVeinDefinition> loaded = new ArrayList<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json"));
        resources.forEach((resourceId, resource) -> {
            try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                loaded.add(parse(resourceId, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception exception) {
                SimuKraft.LOGGER.error("Failed to load virtual vein definition {}", resourceId, exception);
            }
        });
        loaded.sort(Comparator.comparingInt(VirtualVeinDefinition::priority).reversed().thenComparing(VirtualVeinDefinition::id));
        SimuKraft.LOGGER.info("Loaded {} virtual vein definitions", loaded.size());
        return List.copyOf(loaded);
    }

    /** parse: 校验并解析单份矿脉 JSON 定义。 */
    static VirtualVeinDefinition parse(ResourceLocation resourceId, JsonObject root) {
        String id = text(root, "id");
        String displayName = text(root, "display_name");
        int priority = integer(root, "priority");
        JsonObject conditions = requiredObject(root, "conditions");
        int[] yRange = integerRange(root, "y_range");
        ResourceLocation productId = ResourceLocation.parse(text(root, "product"));
        if (!BuiltInRegistries.ITEM.containsKey(productId)) {
            throw new IllegalArgumentException("Unknown item " + productId + " in " + resourceId);
        }
        JsonObject production = requiredObject(root, "production");
        return new VirtualVeinDefinition(
                id,
                displayName,
                priority,
                decimalRange(conditions, "continentalness"),
                decimalRange(conditions, "erosion"),
                decimalRange(conditions, "depth"),
                decimalRange(conditions, "temperature"),
                decimalRange(conditions, "humidity"),
                decimalRange(conditions, "weirdness"),
                yRange[0],
                yRange[1],
                productId,
                integer(production, "min_amount"),
                integer(production, "max_amount"),
                integer(production, "period_ticks")
        );
    }

    private static VirtualVeinRange decimalRange(JsonObject object, String key) {
        JsonArray values = requiredArray(object, key);
        if (values.size() != 2) {
            throw new IllegalArgumentException(key + " must contain exactly two values");
        }
        return new VirtualVeinRange(values.get(0).getAsDouble(), values.get(1).getAsDouble());
    }

    private static int[] integerRange(JsonObject object, String key) {
        JsonArray values = requiredArray(object, key);
        if (values.size() != 2) {
            throw new IllegalArgumentException(key + " must contain exactly two values");
        }
        return new int[]{values.get(0).getAsInt(), values.get(1).getAsInt()};
    }

    private static JsonObject requiredObject(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("Missing object " + key);
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requiredArray(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Missing array " + key);
        }
        return value.getAsJsonArray();
    }

    private static String text(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("Missing text " + key);
        }
        return value.getAsString();
    }

    private static int integer(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing integer " + key);
        }
        return value.getAsInt();
    }
}
