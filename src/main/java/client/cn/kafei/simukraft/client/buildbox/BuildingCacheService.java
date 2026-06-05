package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingBuiltinResourceService;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class BuildingCacheService {
    private static final String ROOT_DIR = "simukraftbuilding";
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
            initialized = true;
        }
    }

    public static void reload() {
        synchronized (BuildingCacheService.class) {
            BuildingBuiltinResourceService.ensureCopied(rootDirectory());
            CACHE.clear();
            for (String category : categories()) {
                CACHE.put(category, scanCategory(category));
            }
            initialized = true;
            SimuKraft.LOGGER.info("Simukraft: Reloaded building cache from {}", rootDirectory());
        }
    }

    public static List<BuildingMeta> getBuildings(String category) {
        ensureInitialized();
        return CACHE.getOrDefault(normalizeCategory(category), List.of());
    }

    public static Path rootDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(ROOT_DIR);
    }

    public static Path categoryDirectory(String category) {
        return rootDirectory().resolve(normalizeCategory(category));
    }

    private static List<BuildingMeta> scanCategory(String category) {
        Path categoryDir = categoryDirectory(category);
        if (!Files.isDirectory(categoryDir)) {
            return List.of();
        }
        List<BuildingMeta> buildings = new ArrayList<>();
        try (var stream = Files.list(categoryDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(BuildingCacheService::isMetaFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> {
                        BuildingMeta meta = readMetaFile(category, path);
                        if (meta != null) {
                            buildings.add(meta);
                        }
                    });
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to scan building category {}", category, exception);
            return List.of();
        }
        return List.copyOf(buildings);
    }

    private static boolean isMetaFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".sk");
    }

    private static BuildingMeta readMetaFile(String category, Path path) {
        try {
            String fileName = path.getFileName().toString();
            String text = Files.readString(path, StandardCharsets.UTF_8);
            String baseName = stripExtension(fileName);
            String displayName = findValue(text, "name", baseName);
            String author = findValue(text, "author", "External");
            String size = findValue(text, "size", "-");
            String amount = findValue(text, "amount", findValue(text, "price", "-"));
            String structureFile = findValue(text, "structure", findValue(text, "file", ""));
            if (structureFile.isBlank()) {
                structureFile = baseName + ".nbt";
            }
            return new BuildingMeta(category, displayName, size, amount, author, fileName, structureFile);
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to read building meta file {}", path, exception);
            return null;
        }
    }

    private static String findValue(String text, String key, String fallback) {
        String prefix = key + ":";
        for (String line : text.split("\\R")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            String value = trimmedLine.substring(prefix.length()).trim();
            return value.isEmpty() ? fallback : value;
        }
        return fallback;
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private static List<String> categories() {
        return List.of("residential", "commercial", "industry", "public", "other");
    }

    private static String normalizeCategory(String category) {
        return category == null ? "other" : category.toLowerCase(Locale.ROOT);
    }

    public record BuildingMeta(String category, String name, String size, String amount, String author, String metaFileName, String structureFileName) {
    }
}
