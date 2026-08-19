package common.cn.kafei.simukraft.building;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BuildingCatalog {
    private BuildingCatalog() {
    }

    /** findBuilding: 按分类和 .sk 文件名查找建筑，兼容不带扩展名的旧保存值。 */
    public static Optional<BuildingDefinition> findBuilding(String category, String buildingFileName) {
        if (buildingFileName == null || buildingFileName.isBlank()) {
            return Optional.empty();
        }
        String normalizedName = stripExtension(buildingFileName);
        for (BuildingDefinition candidate : listBuildings(category)) {
            if (stripExtension(candidate.metaFileName()).equalsIgnoreCase(normalizedName)) {
                return Optional.of(candidate);
            }
        }
        return findBuildingByStructureFile(category, buildingFileName);
    }

    /** findBuildingByStructureFile: 通过结构文件名恢复旧任务和已放置建筑。 */
    public static Optional<BuildingDefinition> findBuildingByStructureFile(String category, String structureFileName) {
        if (structureFileName == null || structureFileName.isBlank()) {
            return Optional.empty();
        }
        String normalizedName = stripExtension(structureFileName);
        for (var candidate : listBuildings(category)) {
            if (stripExtension(candidate.structureFileName()).equalsIgnoreCase(normalizedName)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static List<BuildingDefinition> listBuildings(String category) {
        return BuildingPackageCatalog.ensurePrepared().listBuildings(normalizeCategory(category));
    }

    public static void reload() {
        BuildingPackageCatalog.reload(rootDirectory());
    }

    public static void clearCache() {
        BuildingPackageCatalog.clearCache();
    }

    public static Path rootDirectory() {
        return BuildingPackageCatalog.rootDirectory();
    }

    public static String rootDirectoryText() {
        return rootDirectory().toString();
    }

    private static String normalizeCategory(String category) {
        return BuildingPackageCatalog.normalizeCategory(category);
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
                                     String description,
                                     int unlockLevel,
                                     String metaFileName,
                                     String structureFileName,
                                     BuildingType buildingType,
                                     BuildingPackageCatalog.PackageSource source) {
        /** isDrillingPlatform: 判断建筑包是否将当前工业建筑声明为矿物钻井平台。 */
        public boolean isDrillingPlatform() {
            return buildingType == BuildingType.DRILLING_PLATFORM;
        }

        public Optional<InputStream> openMeta() throws IOException {
            return openFile(metaFileName);
        }

        public Optional<InputStream> openStructure() throws IOException {
            return openFile(structureFileName);
        }

        public Optional<InputStream> openFile(String fileName) throws IOException {
            return BuildingPackageCatalog.openEntry(source, category, fileName);
        }

        public Optional<String> readFileText(String fileName) {
            return BuildingPackageCatalog.readText(this, fileName);
        }

        public boolean hasFile(String fileName) {
            return source != null && source.isAllowed(category, fileName);
        }

        public String actualFileName(String fileName) {
            return source != null ? source.actualFileName(category, fileName) : fileName;
        }

        public String packageName() {
            return source != null ? source.packageName() : "";
        }

        public Path packagePath() {
            return source != null ? source.packagePath() : null;
        }

        public String packageKey() {
            Path path = packagePath();
            return path == null ? "" : path.toString().toLowerCase(Locale.ROOT);
        }
    }

    /** BuildingType: 区分普通建筑与需要矿物钻井控制箱驱动的钻井平台。 */
    public enum BuildingType {
        STANDARD,
        DRILLING_PLATFORM
    }
}
