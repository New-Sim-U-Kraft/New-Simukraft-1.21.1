package common.cn.kafei.simukraft.exchange;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;
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

/** ExchangeCompanyLoader: 加载 exchange_companies 数据包，缺省回落到内置九家公司。 */
public final class ExchangeCompanyLoader implements PreparableReloadListener {
    public static final ExchangeCompanyLoader INSTANCE = new ExchangeCompanyLoader();
    private static final String DIRECTORY = "exchange_companies";
    private final AtomicReference<List<ExchangeCompany>> companies = new AtomicReference<>(defaults());

    private ExchangeCompanyLoader() {
    }

    public List<ExchangeCompany> companies() {
        List<ExchangeCompany> loaded = companies.get();
        return loaded == null || loaded.isEmpty() ? defaults() : loaded;
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
                .thenAcceptAsync(loaded -> companies.set(loaded.isEmpty() ? defaults() : loaded), gameExecutor);
    }

    private List<ExchangeCompany> load(ResourceManager resourceManager) {
        List<ExchangeCompany> loaded = new ArrayList<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json"));
        resources.forEach((resourceId, resource) -> {
            try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                loaded.add(parse(resourceId, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception exception) {
                SimuKraft.LOGGER.error("Failed to load exchange company {}", resourceId, exception);
            }
        });
        loaded.sort(Comparator.comparing(ExchangeCompany::id));
        SimuKraft.LOGGER.info("Loaded {} exchange companies", loaded.size());
        return List.copyOf(loaded);
    }

    static ExchangeCompany parse(ResourceLocation resourceId, JsonObject root) {
        String id = text(root, "id", resourceId.getPath());
        String name = text(root, "display_name", id);
        String sector = text(root, "sector", "other");
        double price = number(root, "base_price", 1.0D);
        double volatility = number(root, "volatility", 0.02D);
        return new ExchangeCompany(id, name, sector, price, volatility);
    }

    /** defaults: 内置九家公司。 */
    public static List<ExchangeCompany> defaults() {
        return List.of(
                new ExchangeCompany("xiaoliang_media", "小亮传媒", "media", 1.20D, 0.028D),
                new ExchangeCompany("lapis_industry", "青金石工业", "industry", 1.10D, 0.022D),
                new ExchangeCompany("redstone_medical", "红石医疗", "medical", 1.15D, 0.020D),
                new ExchangeCompany("laozhang_mining", "老张矿业", "mining", 1.05D, 0.030D),
                new ExchangeCompany("mochen_dairy", "沐尘奶业", "agriculture", 0.95D, 0.018D),
                new ExchangeCompany("gunmu_network", "棍木网络", "tech", 1.25D, 0.032D),
                new ExchangeCompany("pastor_music", "老牧师音乐", "entertainment", 0.90D, 0.026D),
                new ExchangeCompany("suqing_construction", "苏庆建筑", "construction", 1.00D, 0.021D),
                new ExchangeCompany("xu_realty", "许氏地产", "realty", 1.30D, 0.024D)
        );
    }

    private static String text(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key) || !root.get(key).isJsonPrimitive()) {
            return fallback;
        }
        String value = root.get(key).getAsString();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static double number(JsonObject root, String key, double fallback) {
        if (root == null || !root.has(key) || !root.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return root.get(key).getAsDouble();
        } catch (Exception exception) {
            return fallback;
        }
    }
}
