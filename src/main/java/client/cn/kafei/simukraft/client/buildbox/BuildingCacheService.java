package client.cn.kafei.simukraft.client.buildbox;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.BuildingPackageCatalog;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class BuildingCacheService {
    private static final Map<String, List<BuildingMeta>> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean initialized;

    private BuildingCacheService() {
    }

    public static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (BuildingCacheService.class) {
            if (initialized) {
                return;
            }
            reload();
        }
    }

    /** reload: 重新扫描 simukraftbuilding 下的 zip 建筑包。 */
    public static void reload() {
        synchronized (BuildingCacheService.class) {
            BuildingCatalog.reload();
            CACHE.clear();
            for (String category : BuildingPackageCatalog.categories()) {
                CACHE.put(category, BuildingCatalog.listBuildings(category).stream()
                        .map(BuildingCacheService::toMeta)
                        .toList());
            }
            initialized = true;
            SimuKraft.LOGGER.info("Simukraft: Reloaded building cache from {}", rootDirectory());
        }
    }

    public static List<BuildingMeta> getBuildings(String category) {
        ensureInitialized();
        return CACHE.getOrDefault(BuildingPackageCatalog.normalizeCategory(category), List.of());
    }

    public static Path rootDirectory() {
        return BuildingCatalog.rootDirectory();
    }

    public static String rootDirectoryText() {
        return BuildingCatalog.rootDirectoryText();
    }

    private static BuildingMeta toMeta(BuildingCatalog.BuildingDefinition definition) {
        return new BuildingMeta(
                definition.category(),
                definition.displayName(),
                definition.size(),
                definition.amount(),
                definition.author(),
                definition.description(),
                definition.unlockLevel(),
                definition.metaFileName(),
                definition.structureFileName(),
                definition.packageName()
        );
    }

    public record BuildingMeta(String category,
                               String name,
                               String size,
                               String amount,
                               String author,
                               String description,
                               int unlockLevel,
                               String metaFileName,
                               String structureFileName,
                               String packageName) {
    }
}
