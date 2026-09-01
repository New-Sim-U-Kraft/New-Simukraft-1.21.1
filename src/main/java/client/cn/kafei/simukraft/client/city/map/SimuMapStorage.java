package client.cn.kafei.simukraft.client.city.map;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.zip.CRC32;

/**
 * 地图 region 的本地磁盘缓存。
 */
@OnlyIn(Dist.CLIENT)
public class SimuMapStorage {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAGIC = 0x534D5200;
    /** VERSION: 文件头带存档/维度身份，旧版无身份缓存会丢弃。 */
    private static final short VERSION = 3;
    private static final String ROOT_DIR = "simukraft_mapdata";
    public static final String UNRESOLVED_WORLD_ID = "unresolved";

    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SimuMap-Save");
        thread.setDaemon(true);
        return thread;
    });

    private static final ExecutorService LOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SimuMap-Load");
        thread.setDaemon(true);
        return thread;
    });

    private SimuMapStorage() {
    }

    /**
     * 单人用存档文件夹而不是显示名，中文世界不会再全部变成同一串下划线。
     */
    public static String getCurrentWorldId() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer singleplayerServer = mc.getSingleplayerServer();
        if (singleplayerServer != null) {
            Path worldFolder = singleplayerServer.getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent();
            if (worldFolder != null) {
                return createSinglePlayerWorldId(worldFolder);
            }
        }
        ServerData currentServer = mc.getCurrentServer();
        if (currentServer != null && currentServer.ip != null && !currentServer.ip.isBlank()) {
            return createMultiplayerWorldId(currentServer.ip);
        }
        return UNRESOLVED_WORLD_ID;
    }

    /** isResolvedWorldId: 未解析的身份不能读写磁盘，避免不同世界写进同一个 unresolved 目录。 */
    public static boolean isResolvedWorldId(String worldId) {
        return worldId != null && !worldId.isBlank() && !UNRESOLVED_WORLD_ID.equals(worldId);
    }

    /** createSinglePlayerWorldId: 用文件夹名加绝对路径校验和，拷贝到别的目录也不会串档。 */
    public static String createSinglePlayerWorldId(Path worldFolder) {
        Path normalized = worldFolder.toAbsolutePath().normalize();
        String folderName = normalized.getFileName() == null ? "world" : normalized.getFileName().toString();
        return "sp_" + sanitizeForPath(folderName) + "_" + pathChecksum(normalized);
    }

    /** createMultiplayerWorldId: 按服务器地址隔离多人缓存。 */
    public static String createMultiplayerWorldId(String address) {
        return "mp_" + sanitizeForPath(address);
    }

    public static String dimensionToDir(ResourceKey<Level> dimension) {
        return sanitizeForPath(dimension.location().getNamespace() + "_" + dimension.location().getPath());
    }

    /** matchesCacheIdentity: 磁盘文件头必须与当前存档、维度一致。 */
    public static boolean matchesCacheIdentity(String storedWorldId, String storedDimension,
                                               String expectedWorldId, String expectedDimension) {
        return isResolvedWorldId(expectedWorldId)
                && expectedWorldId.equals(storedWorldId)
                && expectedDimension.equals(storedDimension);
    }

    public static Path getRegionDir(String worldId, ResourceKey<Level> dimension) {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        return gameDir.resolve(ROOT_DIR).resolve(worldId).resolve(dimensionToDir(dimension));
    }

    public static Path getRegionFile(String worldId, ResourceKey<Level> dimension, int regionX, int regionZ) {
        return getRegionDir(worldId, dimension).resolve(regionX + "_" + regionZ + ".smr");
    }

    public static void saveRegion(String worldId, ResourceKey<Level> dimension, SimuMapRegion region) {
        if (!isResolvedWorldId(worldId) || dimension == null) {
            return;
        }
        SimuMapRegionData data = region.getData();
        if (data == null || data.isEmpty() || !data.needsSave()) {
            return;
        }

        Path file = getRegionFile(worldId, dimension, region.regionX, region.regionZ);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        String dimensionId = dimensionToDir(dimension);
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(MAGIC);
                out.writeShort(VERSION);
                writeUtf(out, worldId);
                writeUtf(out, dimensionId);
                for (short height : data.height) {
                    out.writeShort(height);
                }
                for (int color : data.color) {
                    out.writeInt(color);
                }
                for (short flags : data.flags) {
                    out.writeShort(flags);
                }
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            data.markSaved();
        } catch (IOException e) {
            LOGGER.error("Simukraft: Failed to save map region ({}, {}) for world={} dim={}",
                    region.regionX, region.regionZ, worldId, dimensionId, e);
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    public static void saveAll(String worldId, ResourceKey<Level> dimension, Collection<SimuMapRegion> regions) {
        if (!isResolvedWorldId(worldId) || dimension == null) {
            return;
        }
        int saved = 0;
        for (SimuMapRegion region : regions) {
            SimuMapRegionData data = region.getData();
            if (data == null || data.isEmpty() || !data.needsSave()) {
                continue;
            }
            saveRegion(worldId, dimension, region);
            saved++;
        }
        LOGGER.debug("Simukraft: Saved {} dirty regions of {} for world={} dim={}",
                saved, regions.size(), worldId, dimensionToDir(dimension));
    }

    public static void saveAllAsync(String worldId, ResourceKey<Level> dimension,
                                    Collection<SimuMapRegion> regions, String reason) {
        saveAllAsync(worldId, dimension, regions, reason, true);
    }

    /**
     * `discardAfterSave=false` 用于周期缓存，保留内存数据继续渲染未加载区块。
     */
    public static void saveAllAsync(String worldId, ResourceKey<Level> dimension,
                                    Collection<SimuMapRegion> regions, String reason,
                                    boolean discardAfterSave) {
        if (!isResolvedWorldId(worldId) || dimension == null) {
            if (discardAfterSave) {
                for (SimuMapRegion region : regions) {
                    region.discardData();
                }
            }
            return;
        }
        List<SimuMapRegion> regionSnapshot = new ArrayList<>(regions);
        if (regionSnapshot.isEmpty()) {
            return;
        }

        SAVE_EXECUTOR.execute(() -> {
            saveAll(worldId, dimension, regionSnapshot);
            if (discardAfterSave) {
                for (SimuMapRegion region : regionSnapshot) {
                    region.discardData();
                }
            }
            LOGGER.info("Simukraft: Async-saved {} regions for world={} dim={} reason={} discardAfterSave={}",
                    regionSnapshot.size(), worldId, dimensionToDir(dimension), reason, discardAfterSave);
        });
    }

    public static void loadAll(String worldId, ResourceKey<Level> dimension, Map<Long, SimuMapRegion> regions) {
        if (!isResolvedWorldId(worldId) || dimension == null) {
            return;
        }
        Path dir = getRegionDir(worldId, dimension);
        if (!Files.isDirectory(dir)) {
            return;
        }

        try (var stream = Files.list(dir)) {
            stream.filter(path -> path.toString().endsWith(".smr")).forEach(file -> {
                String name = file.getFileName().toString();
                name = name.substring(0, name.length() - 4);
                String[] parts = name.split("_", 2);
                if (parts.length != 2) {
                    return;
                }

                try {
                    int regionX = Integer.parseInt(parts[0]);
                    int regionZ = Integer.parseInt(parts[1]);
                    SimuMapRegionData data = readRegionFile(file, worldId, dimensionToDir(dimension));
                    if (data == null) {
                        return;
                    }

                    SimuMapRegion region = new SimuMapRegion(regionX, regionZ);
                    region.setData(data);
                    data.markDirty();
                    regions.put(regionKey(regionX, regionZ), region);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Simukraft: Skipping malformed region file: {}", file.getFileName());
                }
            });
        } catch (IOException e) {
            LOGGER.error("Simukraft: Failed to list region files for world={} dim={}",
                    worldId, dimensionToDir(dimension), e);
        }

        LOGGER.debug("Simukraft: Loaded {} regions from world={} dim={}",
                regions.size(), worldId, dimensionToDir(dimension));
    }

    public static void loadAllAsync(String worldId, ResourceKey<Level> dimension, Map<Long, SimuMapRegion> regions) {
        LOAD_EXECUTOR.execute(() -> loadAll(worldId, dimension, regions));
    }

    public static void loadAllAsync(String worldId, ResourceKey<Level> dimension,
                                    Consumer<Map<Long, SimuMapRegion>> callback) {
        LOAD_EXECUTOR.execute(() -> {
            Map<Long, SimuMapRegion> loadedRegions = new ConcurrentHashMap<>();
            loadAll(worldId, dimension, loadedRegions);
            callback.accept(loadedRegions);
        });
    }

    private static SimuMapRegionData readRegionFile(Path file, String expectedWorldId, String expectedDimension) {
        boolean dropFile = false;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                LOGGER.warn("Simukraft: Invalid magic in {}", file.getFileName());
                return null;
            }

            short version = in.readShort();
            if (version != VERSION) {
                dropFile = true;
                return null;
            }

            String storedWorldId = readUtf(in);
            String storedDimension = readUtf(in);
            if (!matchesCacheIdentity(storedWorldId, storedDimension, expectedWorldId, expectedDimension)) {
                dropFile = true;
                return null;
            }

            String name = file.getFileName().toString();
            name = name.substring(0, name.length() - 4);
            String[] parts = name.split("_", 2);
            int regionX = Integer.parseInt(parts[0]);
            int regionZ = Integer.parseInt(parts[1]);

            SimuMapRegionData data = new SimuMapRegionData(regionX, regionZ);
            int filled = 0;
            for (int i = 0; i < SimuMapRegionData.AREA; i++) {
                data.height[i] = in.readShort();
                if (data.height[i] != SimuMapRegionData.HEIGHT_UNKNOWN) {
                    filled++;
                }
            }
            for (int i = 0; i < SimuMapRegionData.AREA; i++) {
                data.color[i] = in.readInt();
            }
            for (int i = 0; i < SimuMapRegionData.AREA; i++) {
                data.flags[i] = in.readShort();
            }
            data.setFilledCount(filled);
            data.markSaved();
            return data;
        } catch (IOException e) {
            LOGGER.error("Simukraft: Failed to read region file {}", file.getFileName(), e);
            return null;
        } finally {
            if (dropFile) {
                LOGGER.debug("Simukraft: Dropping foreign or obsolete map cache {}", file.getFileName());
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }

    /** sanitizeForPath: 只去掉路径非法字符，保留中文等存档名。 */
    public static String sanitizeForPath(String value) {
        if (value == null || value.isBlank()) {
            return "world";
        }
        String cleaned = value.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_");
        cleaned = cleaned.replaceAll("[. ]+$", "_");
        return cleaned.isBlank() ? "world" : cleaned;
    }

    private static String pathChecksum(Path path) {
        CRC32 crc = new CRC32();
        crc.update(path.toString().getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(crc.getValue());
    }

    private static void writeUtf(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readUtf(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Truncated UTF payload");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
