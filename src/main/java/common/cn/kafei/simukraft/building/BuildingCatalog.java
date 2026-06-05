package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.SimuKraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BuildingCatalog {
    private static final String ROOT_DIR = "simukraftbuilding";

    private BuildingCatalog() {
    }

    public static Optional<BuildingDefinition> findBuilding(String category, String buildingFileName) {
        if (buildingFileName == null || buildingFileName.isBlank()) {
            return Optional.empty();
        }
        String normalizedName = stripExtension(buildingFileName);
        return listBuildings(category).stream()
                .filter(candidate -> stripExtension(candidate.metaFileName()).equalsIgnoreCase(normalizedName))
                .findFirst();
    }

    public static List<BuildingDefinition> listBuildings(String category) {
        BuildingBuiltinResourceService.ensureCopied(rootDirectory());
        Path categoryDir = categoryDirectory(category);
        if (!Files.isDirectory(categoryDir)) {
            return List.of();
        }
        List<BuildingDefinition> buildings = new ArrayList<>();
        try (var stream = Files.list(categoryDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(BuildingCatalog::isMetaFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> {
                        BuildingDefinition definition = readDefinition(normalizeCategory(category), path);
                        if (definition != null) {
                            buildings.add(definition);
                        }
                    });
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to scan building category {}", category, exception);
            return List.of();
        }
        return List.copyOf(buildings);
    }

    public static Path rootDirectory() {
        return FMLPaths.GAMEDIR.get().resolve(ROOT_DIR);
    }

    public static Path categoryDirectory(String category) {
        return rootDirectory().resolve(normalizeCategory(category));
    }

    private static BuildingDefinition readDefinition(String category, Path metaPath) {
        try {
            String fileName = metaPath.getFileName().toString();
            String text = Files.readString(metaPath, StandardCharsets.UTF_8);
            String baseName = stripExtension(fileName);
            String displayName = findValue(text, "name", baseName);
            String author = findValue(text, "author", "External");
            String size = findValue(text, "size", "-");
            String amount = findValue(text, "amount", findValue(text, "price", "-"));
            String structureFile = findValue(text, "structure", findValue(text, "file", ""));
            if (structureFile.isBlank()) {
                structureFile = baseName + ".nbt";
            }
            Path structurePath = categoryDirectory(category).resolve(structureFile);
            return new BuildingDefinition(category, displayName, size, amount, author, fileName, structureFile, metaPath, structurePath);
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to read building meta file {}", metaPath, exception);
            return null;
        }
    }

    private static boolean isMetaFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".sk");
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

    private static String normalizeCategory(String category) {
        return category == null ? "other" : category.toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    public record BuildingDefinition(String category,
                                     String displayName,
                                     String size,
                                     String amount,
                                     String author,
                                     String metaFileName,
                                     String structureFileName,
                                     Path metaPath,
                                     Path structurePath) {
    }
}
