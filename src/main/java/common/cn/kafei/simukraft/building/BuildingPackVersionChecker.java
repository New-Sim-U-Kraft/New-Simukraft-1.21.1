package common.cn.kafei.simukraft.building;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 建筑包版本比较工具
 * 比较 JAR 内置 official_building.zip 与本地 simukraftbuilding/official_building.zip 的版本
 */
public final class BuildingPackVersionChecker {
    private static final String PACK_JSON = "pack.json";

    private BuildingPackVersionChecker() {
    }

    /** 读取 JAR 内置 official_building.zip 中的 pack.json version 字段 */
    public static Optional<String> builtinVersion() {
        String resourcePath = "assets/simukraft/building/" + BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME;
        ClassLoader cl = BuildingPackVersionChecker.class.getClassLoader();
        try (InputStream zipStream = cl != null ? cl.getResourceAsStream(resourcePath)
                : ClassLoader.getSystemResourceAsStream(resourcePath)) {
            if (zipStream == null) {
                return Optional.empty();
            }
            return readVersionFromZip(zipStream);
        } catch (IOException e) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read builtin building pack version", e);
            return Optional.empty();
        }
    }

    /** 读取本地 simukraftbuilding/official_building.zip 中的 pack.json version 字段 */
    public static Optional<String> localVersion() {
        Path localZip = BuildingPackageCatalog.rootDirectory().resolve(BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME);
        if (!Files.exists(localZip)) {
            return Optional.empty();
        }
        try (InputStream zipStream = Files.newInputStream(localZip)) {
            return readVersionFromZip(zipStream);
        } catch (IOException e) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read local building pack version", e);
            return Optional.empty();
        }
    }

    /** 本地版本是否低于 JAR 内版本（语义化版本比较） */
    public static boolean isLocalOutdated() {
        Optional<String> local = localVersion();
        Optional<String> builtin = builtinVersion();
        if (local.isEmpty() || builtin.isEmpty()) {
            return false;
        }
        return isOlderThan(local.get(), builtin.get());
    }

    /** 从 ZIP 输入流中读取 pack.json 的 version 字段 */
    private static Optional<String> readVersionFromZip(InputStream zipStream) {
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (PACK_JSON.equals(entry.getName())) {
                    byte[] bytes = zis.readAllBytes();
                    String jsonText = new String(bytes, StandardCharsets.UTF_8);
                    JsonObject json = JsonParser.parseString(jsonText).getAsJsonObject();
                    if (json.has("version")) {
                        return Optional.of(json.get("version").getAsString());
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to parse pack.json from building pack", e);
        }
        return Optional.empty();
    }

    /** 语义化版本比较：a < b 返回 true */
    public static boolean isOlderThan(String a, String b) {
        int[] va = parseVersion(a);
        int[] vb = parseVersion(b);
        for (int i = 0; i < Math.min(va.length, vb.length); i++) {
            if (va[i] < vb[i]) return true;
            if (va[i] > vb[i]) return false;
        }
        return va.length < vb.length;
    }

    /** 解析版本号为整数数组（"2.1.0" → [2, 1, 0]） */
    private static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }
}
