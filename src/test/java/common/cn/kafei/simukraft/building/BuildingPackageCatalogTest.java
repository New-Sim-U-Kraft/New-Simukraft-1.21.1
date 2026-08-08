package common.cn.kafei.simukraft.building;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class BuildingPackageCatalogTest {
    @Test
    void missingOrBlankUnlockLevelIsUnrestricted() {
        assertEquals(0, BuildingPackageCatalog.readUnlockLevel("name: house\n"));
        assertEquals(0, BuildingPackageCatalog.readUnlockLevel("unlockLevel:   \n"));
        assertEquals(4, BuildingPackageCatalog.readUnlockLevel("unlockLevel:\nunlock_level: 4\n"));
    }

    @Test
    void unlockLevelSupportsCamelCaseAndSnakeCaseFields() {
        assertEquals(3, BuildingPackageCatalog.readUnlockLevel("unlockLevel: 3\n"));
        assertEquals(4, BuildingPackageCatalog.readUnlockLevel("unlock_level: 4\n"));
    }

    @Test
    void invalidUnlockLevelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BuildingPackageCatalog.readUnlockLevel("unlockLevel: 0\n"));
        assertThrows(IllegalArgumentException.class, () -> BuildingPackageCatalog.readUnlockLevel("unlock_level: -2\n"));
        assertThrows(IllegalArgumentException.class, () -> BuildingPackageCatalog.readUnlockLevel("unlockLevel: nope\n"));
    }

    @Test
    void packageScanPropagatesBlankAndValidUnlockLevels(@TempDir Path tempDir) throws IOException {
        Path packageFile = tempDir.resolve("unlock-levels.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(packageFile), StandardCharsets.UTF_8)) {
            writeEntry(output, "buildings/other/unrestricted.sk", "name: Unrestricted\nunlockLevel:\n");
            writeEntry(output, "buildings/other/unrestricted.nbt", "");
            writeEntry(output, "buildings/other/locked.sk", "name: Locked\nunlock_level: 3\n");
            writeEntry(output, "buildings/other/locked.nbt", "");
        }

        Map<String, BuildingCatalog.BuildingDefinition> definitions = BuildingPackageCatalog.scanPackages(tempDir)
                .listBuildings("other")
                .stream()
                .collect(Collectors.toMap(BuildingCatalog.BuildingDefinition::metaFileName, Function.identity()));
        assertEquals(0, definitions.get("unrestricted.sk").unlockLevel());
        assertEquals(3, definitions.get("locked.sk").unlockLevel());
    }

    @Test
    void invalidUnlockLevelDoesNotAbortOtherBuildings(@TempDir Path tempDir) throws IOException {
        Path packageFile = tempDir.resolve("invalid-unlock-level.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(packageFile), StandardCharsets.UTF_8)) {
            writeEntry(output, "buildings/other/valid.sk", "name: Valid\nunlockLevel:\n");
            writeEntry(output, "buildings/other/valid.nbt", "");
            writeEntry(output, "buildings/other/invalid.sk", "name: Invalid\nunlockLevel: nope\n");
            writeEntry(output, "buildings/other/invalid.nbt", "");
        }

        Map<String, BuildingCatalog.BuildingDefinition> definitions = BuildingPackageCatalog.scanPackages(tempDir)
                .listBuildings("other")
                .stream()
                .collect(Collectors.toMap(BuildingCatalog.BuildingDefinition::metaFileName, Function.identity()));
        assertEquals(1, definitions.size());
        assertFalse(definitions.containsKey("invalid.sk"));
    }

    @Test
    void officialPackageDeclaresBlankUnlockLevelForEveryBuilding() throws IOException {
        InputStream packageStream = BuildingPackageCatalogTest.class.getResourceAsStream(
                "/assets/simukraft/building/official_building.zip");
        assertNotNull(packageStream, "official_building.zip must be available on the test classpath");

        int metadataCount = 0;
        try (packageStream; ZipInputStream zip = new ZipInputStream(packageStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()
                        || !entry.getName().startsWith("buildings/")
                        || !entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".sk")) {
                    continue;
                }
                metadataCount++;
                String entryName = entry.getName();
                String metadata = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(hasUnlockField(metadata), () -> "Missing unlockLevel in " + entryName);
                assertEquals(0, BuildingPackageCatalog.readUnlockLevel(metadata),
                        () -> "Expected a blank unlockLevel in " + entryName);
            }
        }
        assertEquals(113, metadataCount, "Unexpected official building metadata count");
    }

    /** hasUnlockField: 判断建筑元数据是否声明规范或兼容解锁字段。 */
    private static boolean hasUnlockField(String metadata) {
        return metadata.lines()
                .map(String::trim)
                .anyMatch(line -> line.regionMatches(true, 0, "unlockLevel:", 0, "unlockLevel:".length())
                        || line.regionMatches(true, 0, "unlock_level:", 0, "unlock_level:".length()));
    }

    /** writeEntry: 向测试建筑包写入单个 UTF-8 条目。 */
    private static void writeEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
