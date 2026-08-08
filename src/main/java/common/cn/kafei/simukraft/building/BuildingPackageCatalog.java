package common.cn.kafei.simukraft.building;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BuildingPackageCatalog {
    public static final String ROOT_DIR = "simukraftbuilding";
    public static final String OFFICIAL_PACKAGE_NAME = "official_building.zip";
    private static final String BUILDING_ROOT = "buildings/";
    private static final String MANIFEST_FILE = "_files.txt";
    private static final List<String> CATEGORIES = List.of("residential", "commercial", "industry", "public", "other");
    private static final Set<String> CATEGORY_SET = Set.copyOf(CATEGORIES);
    private static final ConcurrentHashMap<String, CatalogSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private BuildingPackageCatalog() {
    }

    /** ensurePrepared: 确保官方建筑包已复制到游戏目录并完成索引。 */
    public static CatalogSnapshot ensurePrepared() {
        return snapshot(rootDirectory());
    }

    /** snapshot: 获取指定建筑包根目录的缓存快照。 */
    public static CatalogSnapshot snapshot(Path rootDirectory) {
        Path normalizedRoot = normalizeRoot(rootDirectory);
        return SNAPSHOTS.computeIfAbsent(cacheKey(normalizedRoot), ignored -> scan(normalizedRoot, true));
    }

    /** reload: 重新扫描指定建筑包根目录。 */
    public static CatalogSnapshot reload(Path rootDirectory) {
        Path normalizedRoot = normalizeRoot(rootDirectory);
        CatalogSnapshot snapshot = scan(normalizedRoot, true);
        SNAPSHOTS.put(cacheKey(normalizedRoot), snapshot);
        return snapshot;
    }

    /** scanPackages: 只扫描给定目录内的 zip 包，不复制内置官方包。 */
    public static CatalogSnapshot scanPackages(Path rootDirectory) {
        return scan(normalizeRoot(rootDirectory), false);
    }

    /** clearCache: 清理全部建筑包索引缓存。 */
    public static void clearCache() {
        SNAPSHOTS.clear();
        BuildingBuiltinResourceService.clearCache();
    }

    public static Path rootDirectory() {
        return FMLPaths.GAMEDIR.get().resolve(ROOT_DIR);
    }

    public static Path normalizeRoot(Path rootDirectory) {
        Path root = rootDirectory != null ? rootDirectory : rootDirectory();
        return root.toAbsolutePath().normalize();
    }

    public static List<String> categories() {
        return CATEGORIES;
    }

    private static CatalogSnapshot scan(Path rootDirectory, boolean copyOfficialPackage) {
        if (copyOfficialPackage) {
            BuildingBuiltinResourceService.ensureCopied(rootDirectory);
        }
        List<Path> packages = listPackages(rootDirectory);
        Map<String, Map<String, BuildingCatalog.BuildingDefinition>> byCategory = new HashMap<>();
        for (String category : CATEGORIES) {
            byCategory.put(category, new LinkedHashMap<>());
        }

        int packageCount = 0;
        for (Path packagePath : packages) {
            packageCount++;
            scanPackage(packagePath, byCategory);
        }

        Map<String, List<BuildingCatalog.BuildingDefinition>> immutable = new HashMap<>();
        int buildingCount = 0;
        for (String category : CATEGORIES) {
            List<BuildingCatalog.BuildingDefinition> definitions = byCategory.get(category).values().stream()
                    .sorted(Comparator.comparing(definition -> definition.metaFileName().toLowerCase(Locale.ROOT)))
                    .toList();
            buildingCount += definitions.size();
            immutable.put(category, List.copyOf(definitions));
        }
        SimuKraft.LOGGER.info("Simukraft: Loaded {} building packages with {} buildings from {}", packageCount, buildingCount, rootDirectory);
        return new CatalogSnapshot(rootDirectory, immutable, packages);
    }

    private static List<Path> listPackages(Path rootDirectory) {
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to create building package directory {}", rootDirectory, exception);
            return List.of();
        }
        try (var stream = Files.list(rootDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to scan building package directory {}", rootDirectory, exception);
            return List.of();
        }
    }

    private static void scanPackage(Path packagePath, Map<String, Map<String, BuildingCatalog.BuildingDefinition>> byCategory) {
        try (ZipFile zipFile = new ZipFile(packagePath.toFile(), StandardCharsets.UTF_8)) {
            for (String category : CATEGORIES) {
                scanCategory(packagePath, zipFile, category, byCategory.get(category));
            }
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to open building package {}", packagePath, exception);
        }
    }

    private static void scanCategory(Path packagePath,
                                     ZipFile zipFile,
                                     String category,
                                     Map<String, BuildingCatalog.BuildingDefinition> target) {
        Map<String, String> packageFiles = new HashMap<>();
        List<String> metaFiles = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            Optional<String> fileName = directCategoryFileName(category, entry.getName());
            if (fileName.isEmpty() || MANIFEST_FILE.equalsIgnoreCase(fileName.get())) {
                continue;
            }
            if (!isSafePackageFileName(fileName.get())) {
                SimuKraft.LOGGER.warn("Simukraft: Ignored unsafe building package entry {} in {}", entry.getName(), packagePath);
                continue;
            }
            putPackageFile(packageFiles, fileName.get(), false);
            if (fileName.get().toLowerCase(Locale.ROOT).endsWith(".sk")) {
                metaFiles.add(fileName.get());
            }
        }

        addManifestCalibration(packagePath, zipFile, category, packageFiles);
        metaFiles.sort(String.CASE_INSENSITIVE_ORDER);

        for (String metaFile : metaFiles) {
            try {
                BuildingCatalog.BuildingDefinition definition = readDefinition(packagePath, zipFile, category, metaFile, packageFiles);
                if (definition != null) {
                    target.put(stripExtension(metaFile).toLowerCase(Locale.ROOT), definition);
                }
            } catch (IllegalArgumentException exception) {
                SimuKraft.LOGGER.warn("Simukraft: Ignored invalid building metadata {} in {}", metaFile, packagePath, exception);
            }
        }
    }

    private static void addManifestCalibration(Path packagePath,
                                               ZipFile zipFile,
                                               String category,
                                               Map<String, String> packageFiles) {
        String manifestPath = categoryPath(category, MANIFEST_FILE);
        ZipEntry manifestEntry = zipFile.getEntry(manifestPath);
        if (manifestEntry == null || manifestEntry.isDirectory()) {
            return;
        }
        try (InputStream inputStream = zipFile.getInputStream(manifestEntry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String fileName = stripUtf8Bom(line).trim();
                if (fileName.isEmpty() || fileName.startsWith("#")) {
                    continue;
                }
                if (!isSafePackageFileName(fileName)) {
                    SimuKraft.LOGGER.warn("Simukraft: Ignored unsafe building calibration entry {} in {}", fileName, packagePath);
                    continue;
                }
                String actualName = actualFileName(packageFiles, fileName);
                if (actualName == null) {
                    continue;
                }
                putPackageFile(packageFiles, actualName, true);
            }
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read building calibration file {} in {}", manifestPath, packagePath, exception);
        }
    }

    private static BuildingCatalog.BuildingDefinition readDefinition(Path packagePath,
                                                                     ZipFile zipFile,
                                                                     String category,
                                                                     String metaFile,
                                                                     Map<String, String> packageFiles) {
        Optional<String> metaText = readText(zipFile, categoryPath(category, metaFile));
        if (metaText.isEmpty()) {
            return null;
        }
        String baseName = stripExtension(metaFile);
        String displayName = findValue(metaText.get(), "name", baseName);
        String author = findValue(metaText.get(), "author", "External");
        String size = findValue(metaText.get(), "size", "-");
        String amount = findValue(metaText.get(), "amount", findValue(metaText.get(), "price", "-"));
        String description = findValue(metaText.get(), "description", findValue(metaText.get(), "desc", ""));
        int unlockLevel = readUnlockLevel(metaText.get());
        String structureFile = findValue(metaText.get(), "structure", findValue(metaText.get(), "file", ""));
        if (structureFile.isBlank()) {
            structureFile = baseName + ".nbt";
        }
        String actualStructureFile = isSafePackageFileName(structureFile) ? actualFileName(packageFiles, structureFile) : null;
        if (actualStructureFile == null) {
            SimuKraft.LOGGER.warn("Simukraft: Ignored building {} in {} because structure {} is not present in the package", metaFile, packagePath, structureFile);
            return null;
        }
        BuildingCatalog.BuildingType buildingType = readBuildingType(
                packagePath, zipFile, category, baseName, metaText.get(), packageFiles);
        return new BuildingCatalog.BuildingDefinition(
                category,
                displayName,
                size,
                amount,
                author,
                description,
                unlockLevel,
                metaFile,
                actualStructureFile,
                buildingType,
                new PackageSource(packagePath.toAbsolutePath().normalize(), packagePath.getFileName().toString(), Map.copyOf(Map.of(category, Map.copyOf(packageFiles))))
        );
    }

    /** readBuildingType: 从工业建筑的配套 JSON 读取控制箱类型声明。 */
    private static BuildingCatalog.BuildingType readBuildingType(Path packagePath,
                                                                 ZipFile zipFile,
                                                                 String category,
                                                                 String baseName,
                                                                 String metaText,
                                                                 Map<String, String> packageFiles) {
        if (!"industry".equals(normalizeCategory(category))) {
            return BuildingCatalog.BuildingType.STANDARD;
        }
        String drillingFile = findValue(metaText, "drilling", "");
        if (!drillingFile.isBlank()) {
            return readDedicatedDrillingType(packagePath, zipFile, category, drillingFile, packageFiles);
        }
        String configuredFile = findValue(metaText, "industrial", baseName + ".json");
        String actualFile = isSafePackageFileName(configuredFile)
                ? actualFileName(packageFiles, configuredFile)
                : null;
        if (actualFile == null) {
            return BuildingCatalog.BuildingType.STANDARD;
        }
        Optional<String> jsonText = readText(zipFile, categoryPath(category, actualFile));
        if (jsonText.isEmpty()) {
            return BuildingCatalog.BuildingType.STANDARD;
        }
        try {
            JsonObject root = JsonParser.parseString(jsonText.get()).getAsJsonObject();
            if (isDrillingPlatformType(root)
                    || booleanValue(root, "drillingPlatform")
                    || booleanValue(root, "drilling_platform")) {
                return BuildingCatalog.BuildingType.DRILLING_PLATFORM;
            }
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read building type from {} in {}", actualFile, packagePath, exception);
        }
        return BuildingCatalog.BuildingType.STANDARD;
    }

    /** readDedicatedDrillingType: 读取 drilling: 指向的专用钻井 JSON。 */
    private static BuildingCatalog.BuildingType readDedicatedDrillingType(Path packagePath,
                                                                            ZipFile zipFile,
                                                                            String category,
                                                                            String configuredFile,
                                                                            Map<String, String> packageFiles) {
        String actualFile = isSafePackageFileName(configuredFile)
                ? actualFileName(packageFiles, configuredFile)
                : null;
        if (actualFile == null) {
            SimuKraft.LOGGER.warn("Simukraft: Drilling definition {} is missing in {}", configuredFile, packagePath);
            return BuildingCatalog.BuildingType.STANDARD;
        }
        Optional<String> jsonText = readText(zipFile, categoryPath(category, actualFile));
        if (jsonText.isEmpty()) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read drilling definition {} in {}", actualFile, packagePath);
            return BuildingCatalog.BuildingType.STANDARD;
        }
        try {
            JsonObject root = JsonParser.parseString(jsonText.get()).getAsJsonObject();
            return isDedicatedDrillingType(root)
                    ? BuildingCatalog.BuildingType.DRILLING_PLATFORM
                    : BuildingCatalog.BuildingType.STANDARD;
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Invalid drilling definition {} in {}", actualFile, packagePath, exception);
            return BuildingCatalog.BuildingType.STANDARD;
        }
    }

    /** isDedicatedDrillingType: 判断专用 JSON 是否声明为钻井类型。 */
    private static boolean isDedicatedDrillingType(JsonObject root) {
        JsonElement element = root.get("type");
        if (element == null || !element.isJsonPrimitive()) {
            return false;
        }
        String type = element.getAsString().trim();
        return "drilling".equalsIgnoreCase(type) || "simukraft:drilling".equalsIgnoreCase(type);
    }

    /** isDrillingPlatformType: 兼容驼峰与下划线形式的钻井平台类型字段。 */
    private static boolean isDrillingPlatformType(JsonObject root) {
        JsonElement element = root.has("buildingType") ? root.get("buildingType") : root.get("building_type");
        return element != null
                && element.isJsonPrimitive()
                && "drilling_platform".equalsIgnoreCase(element.getAsString().trim());
    }

    /** booleanValue: 安全读取可选 JSON 布尔声明，非法值按 false 处理。 */
    private static boolean booleanValue(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    static Optional<InputStream> openEntry(PackageSource source, String category, String fileName) throws IOException {
        if (source == null || !source.isAllowed(category, fileName) || !isSafePackageFileName(fileName)) {
            return Optional.empty();
        }
        String normalizedCategory = normalizeCategory(category);
        String entryPath = categoryPath(normalizedCategory, source.actualFileName(normalizedCategory, fileName));
        ZipFile zipFile = new ZipFile(source.packagePath().toFile(), StandardCharsets.UTF_8);
        ZipEntry entry = zipFile.getEntry(entryPath);
        if (entry == null || entry.isDirectory()) {
            zipFile.close();
            return Optional.empty();
        }
        InputStream inputStream = zipFile.getInputStream(entry);
        return Optional.of(new ZipEntryInputStream(zipFile, inputStream));
    }

    static Optional<String> readText(BuildingCatalog.BuildingDefinition definition, String fileName) {
        try {
            return withEntry(definition, fileName, inputStream -> {
                try {
                    return Optional.of(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException exception) {
                    SimuKraft.LOGGER.warn("Simukraft: Failed to read text building entry {}", fileName, exception);
                    return Optional.empty();
                }
            });
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to open text building entry {}", fileName, exception);
            return Optional.empty();
        }
    }

    static <T> Optional<T> withEntry(BuildingCatalog.BuildingDefinition definition, String fileName, EntryReader<T> reader) {
        if (definition == null || reader == null) {
            return Optional.empty();
        }
        try (InputStream inputStream = definition.openFile(fileName).orElse(null)) {
            if (inputStream == null) {
                return Optional.empty();
            }
            return reader.read(inputStream);
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read building entry {} from {}", fileName, definition.packageName(), exception);
            return Optional.empty();
        }
    }

    private static Optional<String> readText(ZipFile zipFile, String entryPath) {
        ZipEntry entry = zipFile.getEntry(entryPath);
        if (entry == null || entry.isDirectory()) {
            return Optional.empty();
        }
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            return Optional.of(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read building package entry {}", entryPath, exception);
            return Optional.empty();
        }
    }

    private static String findValue(String text, String key, String fallback) {
        String value = findDeclaredValue(text, key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String findDeclaredValue(String text, String key) {
        if (text == null || key == null || key.isBlank()) {
            return null;
        }
        String prefix = key + ":";
        for (String line : text.split("\\R")) {
            String trimmedLine = line.trim();
            if (trimmedLine.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return trimmedLine.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    /** readUnlockLevel: 读取建筑解锁等级；仅缺失或空白表示不限制。 */
    static int readUnlockLevel(String text) {
        String value = findDeclaredValue(text, "unlockLevel");
        if (value == null || value.isBlank()) {
            value = findDeclaredValue(text, "unlock_level");
        }
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            int unlockLevel = Integer.parseInt(value.trim());
            if (unlockLevel <= 0) {
                throw new IllegalArgumentException("unlockLevel must be a positive integer when specified");
            }
            return unlockLevel;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("unlockLevel must be a positive integer when specified", exception);
        }
    }

    public static String normalizeCategory(String category) {
        String normalized = category == null ? "other" : category.toLowerCase(Locale.ROOT);
        if ("commerce".equals(normalized)) {
            normalized = "commercial";
        }
        if ("medical".equals(normalized)) {
            normalized = "public";
        }
        return CATEGORY_SET.contains(normalized) ? normalized : "other";
    }

    private static boolean isSafePackageFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return false;
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".sk") || lowerName.endsWith(".nbt") || lowerName.endsWith(".json");
    }

    private static Optional<String> directCategoryFileName(String category, String entryName) {
        if (entryName == null || entryName.contains("\\")) {
            return Optional.empty();
        }
        String prefix = BUILDING_ROOT + normalizeCategory(category) + "/";
        if (!entryName.startsWith(prefix)) {
            return Optional.empty();
        }
        String fileName = entryName.substring(prefix.length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            return Optional.empty();
        }
        return Optional.of(fileName);
    }

    private static void putPackageFile(Map<String, String> packageFiles, String fileName, boolean overwriteRelaxedAlias) {
        packageFiles.put(fileName.toLowerCase(Locale.ROOT), fileName);
        String relaxedKey = relaxedFileKey(fileName);
        if (overwriteRelaxedAlias) {
            packageFiles.put(relaxedKey, fileName);
        } else {
            packageFiles.putIfAbsent(relaxedKey, fileName);
        }
    }

    private static String actualFileName(Map<String, String> packageFiles, String fileName) {
        String actualName = packageFiles.get(fileName.toLowerCase(Locale.ROOT));
        return actualName != null ? actualName : packageFiles.get(relaxedFileKey(fileName));
    }

    private static String relaxedFileKey(String fileName) {
        String safeName = fileName != null ? fileName : "";
        return safeName.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private static String categoryPath(String category, String fileName) {
        return BUILDING_ROOT + normalizeCategory(category) + "/" + fileName;
    }

    private static String stripUtf8Bom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private static String cacheKey(Path rootDirectory) {
        return rootDirectory.toString().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    interface EntryReader<T> {
        Optional<T> read(InputStream inputStream) throws IOException;
    }

    public record CatalogSnapshot(Path rootDirectory,
                                  Map<String, List<BuildingCatalog.BuildingDefinition>> buildingsByCategory,
                                  List<Path> packages) {
        public List<BuildingCatalog.BuildingDefinition> listBuildings(String category) {
            return buildingsByCategory.getOrDefault(normalizeCategory(category), List.of());
        }
    }

    public record PackageSource(Path packagePath, String packageName, Map<String, Map<String, String>> allowedFilesByCategory) {
        public boolean isAllowed(String category, String fileName) {
            if (fileName == null || fileName.isBlank()) {
                return false;
            }
            Map<String, String> allowed = allowedFilesByCategory.getOrDefault(normalizeCategory(category), Map.of());
            return BuildingPackageCatalog.actualFileName(allowed, fileName) != null;
        }

        public String actualFileName(String category, String fileName) {
            Map<String, String> allowed = allowedFilesByCategory.getOrDefault(normalizeCategory(category), Map.of());
            String actualName = BuildingPackageCatalog.actualFileName(allowed, fileName);
            return actualName != null ? actualName : fileName;
        }
    }

    private static final class ZipEntryInputStream extends InputStream {
        private final ZipFile zipFile;
        private final InputStream delegate;

        private ZipEntryInputStream(ZipFile zipFile, InputStream delegate) {
            this.zipFile = zipFile;
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                zipFile.close();
            }
        }
    }
}
