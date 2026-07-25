package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.SimuKraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BuildingBuiltinResourceService {
    private static final String RESOURCE_PATH = "assets/simukraft/building/" + BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME;
    private static final Set<String> COPIED_ROOTS = ConcurrentHashMap.newKeySet();

    private BuildingBuiltinResourceService() {
    }

    /** ensureCopied: 将官方建筑 zip 原样复制到建筑包目录，已存在时不覆盖。 */
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
            copyOfficialPackage(normalizedRoot);
            COPIED_ROOTS.add(rootKey);
        }
    }

    /** forceOverwrite: 强制用 jar 内置包覆写磁盘上的官方建筑 zip，无论文件是否存在。 */
    public static void forceOverwrite(Path rootDirectory) {
        if (rootDirectory == null) {
            return;
        }
        Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
        String rootKey = normalizedRoot.toString().toLowerCase(Locale.ROOT);
        synchronized (BuildingBuiltinResourceService.class) {
            try {
                Files.createDirectories(normalizedRoot);
            } catch (IOException exception) {
                SimuKraft.LOGGER.error("Simukraft: Failed to create building package directory {}", normalizedRoot, exception);
                return;
            }
            Path targetFile = normalizedRoot.resolve(BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME).normalize();
            if (!targetFile.startsWith(normalizedRoot)) {
                SimuKraft.LOGGER.error("Simukraft: Refused unsafe official building package path {}", targetFile);
                return;
            }
            try (InputStream inputStream = openResource()) {
                if (inputStream == null) {
                    SimuKraft.LOGGER.warn("Simukraft: Missing built-in official building package {}", RESOURCE_PATH);
                    return;
                }
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
                SimuKraft.LOGGER.info("Simukraft: Force-overwrote official building package at {}", targetFile);
            } catch (IOException exception) {
                SimuKraft.LOGGER.error("Simukraft: Failed to overwrite official building package at {}", targetFile, exception);
            }
            // 重置标记，下次 ensureCopied 可正常判断
            COPIED_ROOTS.remove(rootKey);
        }
    }

    /** clearCache: 清理已复制目录标记，测试和重载时允许重新检查官方包。 */
    public static void clearCache() {
        COPIED_ROOTS.clear();
    }

    private static void copyOfficialPackage(Path rootDirectory) {
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to create building package directory {}", rootDirectory, exception);
            return;
        }

        Path targetFile = rootDirectory.resolve(BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME).normalize();
        if (!targetFile.startsWith(rootDirectory)) {
            SimuKraft.LOGGER.error("Simukraft: Refused unsafe official building package path {}", targetFile);
            return;
        }
        if (Files.exists(targetFile)) {
            SimuKraft.LOGGER.info("Simukraft: Kept existing official building package {}", targetFile);
            return;
        }

        try (InputStream inputStream = openResource()) {
            if (inputStream == null) {
                SimuKraft.LOGGER.warn("Simukraft: Missing built-in official building package {}", RESOURCE_PATH);
                return;
            }
            Files.copy(inputStream, targetFile);
            SimuKraft.LOGGER.info("Simukraft: Copied official building package to {}", targetFile);
        } catch (IOException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to copy official building package to {}", targetFile, exception);
        }
    }

    private static InputStream openResource() {
        ClassLoader classLoader = BuildingBuiltinResourceService.class.getClassLoader();
        return classLoader == null
                ? ClassLoader.getSystemResourceAsStream(RESOURCE_PATH)
                : classLoader.getResourceAsStream(RESOURCE_PATH);
    }
}
