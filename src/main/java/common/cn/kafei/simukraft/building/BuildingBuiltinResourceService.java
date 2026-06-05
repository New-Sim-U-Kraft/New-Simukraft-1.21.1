package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.SimuKraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BuildingBuiltinResourceService {
    private static final String RESOURCE_ROOT = "assets/simukraft/building";
    private static final String MANIFEST_FILE = "_files.txt";
    private static final List<String> CATEGORIES = List.of("residential", "commercial", "industry", "public", "other");
    private static final Set<String> COPIED_ROOTS = ConcurrentHashMap.newKeySet();

    private BuildingBuiltinResourceService() {
    }

    /**
     * ensureCopied: 按资源清单复制内置建筑文件，供现有文件目录扫描读取。
     */
    public static void ensureCopied(Path rootDirectory) {
        if (rootDirectory == null) {
            return;
        }
        Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
        String rootKey = normalizedRoot.toString().toLowerCase(Locale.ROOT);
        if (COPIED_ROOTS.contains(rootKey)) {
            return;
        }
        synchronized (BuildingBuiltinResourceService.class) {
            if (COPIED_ROOTS.contains(rootKey)) {
                return;
            }
            copyAllCategories(normalizedRoot);
            COPIED_ROOTS.add(rootKey);
        }
    }

    /**
     * copyAllCategories: 逐类复制清单中的缺失建筑文件。
     */
    private static void copyAllCategories(Path rootDirectory) {
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to create building root directory {}", rootDirectory, exception);
            return;
        }

        int copied = 0;
        int kept = 0;
        for (String category : CATEGORIES) {
            CopyStats stats = copyCategory(rootDirectory, category);
            copied += stats.copied();
            kept += stats.kept();
        }
        SimuKraft.LOGGER.info("Simukraft: Prepared built-in building files at {} (copied {}, kept {})", rootDirectory, copied, kept);
    }

    /**
     * copyCategory: 复制单个建筑分类的清单文件。
     */
    private static CopyStats copyCategory(Path rootDirectory, String category) {
        Path categoryDirectory = rootDirectory.resolve(category).normalize();
        try {
            Files.createDirectories(categoryDirectory);
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to create building category directory {}", categoryDirectory, exception);
            return new CopyStats(0, 0);
        }

        int copied = 0;
        int kept = 0;
        for (String fileName : loadManifest(category)) {
            if (!isSafeManifestFileName(fileName)) {
                SimuKraft.LOGGER.warn("Simukraft: Ignored unsafe building resource name {} in {}", fileName, category);
                continue;
            }
            Path targetFile = categoryDirectory.resolve(fileName).normalize();
            if (!targetFile.startsWith(categoryDirectory)) {
                SimuKraft.LOGGER.warn("Simukraft: Ignored escaped building resource path {} in {}", fileName, category);
                continue;
            }
            if (Files.exists(targetFile)) {
                kept++;
                continue;
            }
            try (InputStream inputStream = openResource(category, fileName)) {
                if (inputStream == null) {
                    SimuKraft.LOGGER.warn("Simukraft: Missing built-in building resource {}/{}", category, fileName);
                    continue;
                }
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (IOException exception) {
                SimuKraft.LOGGER.error("Simukraft: Failed to copy built-in building resource {}/{}", category, fileName, exception);
            }
        }
        return new CopyStats(copied, kept);
    }

    /**
     * loadManifest: 读取旧版列表法的 _files.txt 清单。
     */
    private static List<String> loadManifest(String category) {
        try (InputStream inputStream = openResource(category, MANIFEST_FILE)) {
            if (inputStream == null) {
                SimuKraft.LOGGER.warn("Simukraft: Missing building resource manifest for {}", category);
                return List.of();
            }
            List<String> fileNames = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String fileName = stripUtf8Bom(line).trim();
                    if (!fileName.isEmpty() && !fileName.startsWith("#")) {
                        fileNames.add(fileName);
                    }
                }
            }
            return List.copyOf(fileNames);
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to read building resource manifest for {}", category, exception);
            return List.of();
        }
    }

    /**
     * openResource: 从模组资源中打开建筑文件。
     */
    private static InputStream openResource(String category, String fileName) {
        String resourcePath = RESOURCE_ROOT + "/" + category + "/" + fileName;
        ClassLoader classLoader = BuildingBuiltinResourceService.class.getClassLoader();
        return classLoader == null
                ? ClassLoader.getSystemResourceAsStream(resourcePath)
                : classLoader.getResourceAsStream(resourcePath);
    }

    /**
     * isSafeManifestFileName: 限制清单文件名，避免资源路径逃逸。
     */
    private static boolean isSafeManifestFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return false;
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".sk") || lowerName.endsWith(".nbt") || lowerName.endsWith(".json");
    }

    /**
     * stripUtf8Bom: 清理清单首行可能存在的 UTF-8 BOM。
     */
    private static String stripUtf8Bom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private record CopyStats(int copied, int kept) {
    }
}
