package client.cn.kafei.simukraft.client.city.map;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 客户端地图管理器。
 * 只扫描客户端已经加载的 FULL chunk，并将结果写入本地 region 缓存。
 */
@OnlyIn(Dist.CLIENT)
public class SimuMapManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAX_SCAN_RADIUS = 32;
    private static final int DEFAULT_SCAN_RADIUS = 12;
    private static final int ACTIVE_SCAN_INTERVAL_TICKS = 1;
    private static final int ACTIVE_SCAN_BUDGET = 8;
    private static final int PASSIVE_SCAN_INTERVAL_TICKS = 6;
    private static final int PASSIVE_SCAN_BUDGET = 1;
    private static final int DIRTY_RENDER_INTERVAL_TICKS = 5;
    private static final int AUTO_SAVE_INTERVAL_TICKS = 1200;
    private static final int STALE_RELEASE_INTERVAL_TICKS = 600;
    private static final long STALE_REGION_MAX_AGE_MS = 60_000L;
    private static final int ACTIVE_REFRESH_INTERVAL_TICKS = 200;

    private static SimuMapManager instance;

    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SimuMap-Render");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<Long, SimuMapRegion> regions = new ConcurrentHashMap<>();
    private final Set<Long> renderingKeys = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> scannedAtTick = new ConcurrentHashMap<>();

    private int scanRadius = DEFAULT_SCAN_RADIUS;
    private long tickCount = 0L;

    private int scanCursorDX = -DEFAULT_SCAN_RADIUS;
    private int scanCursorDZ = -DEFAULT_SCAN_RADIUS;
    private int scanCursorRadius = DEFAULT_SCAN_RADIUS;

    private volatile boolean initialized = false;
    private volatile int loadGeneration = 0;

    @Nullable
    private volatile ResourceKey<Level> currentDimension = null;

    @Nullable
    private volatile String currentWorldId = null;

    private int activeConsumers = 0;

    private SimuMapManager() {
    }

    public static SimuMapManager getInstance() {
        if (instance == null) {
            instance = new SimuMapManager();
        }
        return instance;
    }

    public static boolean isAvailable() {
        return instance != null && instance.initialized;
    }

    public static void shutdownIfPresent() {
        if (instance != null) {
            instance.shutdown();
        }
    }

    public void acquireConsumer() {
        activeConsumers++;
    }

    public void releaseConsumer() {
        activeConsumers = Math.max(0, activeConsumers - 1);
    }

    public boolean hasActiveConsumer() {
        return activeConsumers > 0;
    }

    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        SimuBlockColors.getInstance().init();
        resetScanCursor();

        Minecraft minecraft = Minecraft.getInstance();
        currentWorldId = SimuMapStorage.getCurrentWorldId();
        currentDimension = minecraft.level == null ? null : minecraft.level.dimension();

        if (currentDimension != null && SimuMapStorage.isResolvedWorldId(currentWorldId)) {
            queueRegionLoad(currentWorldId, currentDimension);
            LOGGER.info("Simukraft: Map system initialization queued for world={} dim={}.",
                    currentWorldId, SimuMapStorage.dimensionToDir(currentDimension));
        } else {
            LOGGER.info("Simukraft: Map system initialized (world identity not yet resolved).");
        }
    }

    /**
     * onBlockAtlasReloaded: 方块图集重建后清空贴图色缓存并重扫已加载区块。
     */
    @SuppressWarnings("null")
    public static void onBlockAtlasReloaded() {
        SimuBlockTextureColors.clear();
        if (instance == null || !instance.initialized) {
            return;
        }
        instance.scannedAtTick.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            instance.forceScanArea(
                    minecraft.player.chunkPosition().x,
                    minecraft.player.chunkPosition().z,
                    instance.getEffectiveScanRadius());
        }
        instance.forceRenderAll();
    }

    public void shutdown() {
        if (!initialized) {
            return;
        }
        initialized = false;
        SimuBlockTextureColors.clear();

        persistRegionsAsync(currentWorldId, currentDimension, List.copyOf(regions.values()), "shutdown");

        currentWorldId = null;
        currentDimension = null;
        loadGeneration++;

        regions.clear();
        renderingKeys.clear();
        scannedAtTick.clear();
        renderExecutor.shutdownNow();

        instance = null;
        LOGGER.info("Simukraft: Map rendering system shut down.");
    }

    public SimuMapRegion getOrCreateRegion(int regionX, int regionZ) {
        return regions.computeIfAbsent(regionKey(regionX, regionZ), key -> new SimuMapRegion(regionX, regionZ));
    }

    @Nullable
    public SimuMapRegion getRegion(int regionX, int regionZ) {
        return regions.get(regionKey(regionX, regionZ));
    }

    public Collection<SimuMapRegion> getAllRegions() {
        return regions.values();
    }

    public void tick() {
        if (!initialized) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) {
            return;
        }

        updateScope(SimuMapStorage.getCurrentWorldId(), level.dimension());

        tickCount++;
        boolean active = hasActiveConsumer();

        if (shouldScanThisTick(active)) {
            incrementalScan(active ? ACTIVE_SCAN_BUDGET : PASSIVE_SCAN_BUDGET);
        }
        if (active && tickCount % DIRTY_RENDER_INTERVAL_TICKS == 0) {
            renderDirtyRegions();
        }
        if (tickCount % AUTO_SAVE_INTERVAL_TICKS == 0) {
            autoSaveRegions();
        }
        if (tickCount % STALE_RELEASE_INTERVAL_TICKS == 0) {
            releaseStaleRegions(STALE_REGION_MAX_AGE_MS);
        }
    }

    public void forceScanArea(int centerChunkX, int centerChunkZ, int radius) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return;
        }

        int clampedRadius = Math.max(0, Math.min(radius, getEffectiveScanRadius()));
        for (int dz = -clampedRadius; dz <= clampedRadius; dz++) {
            for (int dx = -clampedRadius; dx <= clampedRadius; dx++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                if (!SimuChunkScanner.isChunkLoaded(level, chunkX, chunkZ)) {
                    continue;
                }

                scanLoadedChunk(level, chunkX, chunkZ, false);
            }
        }
    }

    public void forceRenderAll() {
        for (SimuMapRegion region : regions.values()) {
            SimuMapRegionData data = region.getData();
            if (data != null) {
                data.markDirty();
            }
        }
        renderDirtyRegions();
    }

    public void setScanRadius(int radius) {
        scanRadius = Math.max(1, Math.min(radius, MAX_SCAN_RADIUS));
    }

    public int getScanRadius() {
        return scanRadius;
    }

    public int getEffectiveScanRadius() {
        Minecraft minecraft = Minecraft.getInstance();
        int clientRadius = minecraft.options == null ? scanRadius : minecraft.options.getEffectiveRenderDistance() + 1;
        return Math.max(scanRadius, Math.min(clientRadius, MAX_SCAN_RADIUS));
    }

    /**
     * 客户端 chunk 一加载就立即采样，避免地图按螺旋慢慢补。
     */
    public void onClientChunkLoaded(Level level, ChunkAccess chunk) {
        if (!initialized || level == null || chunk == null) {
            return;
        }

        updateScope(SimuMapStorage.getCurrentWorldId(), level.dimension());
        scanLoadedChunk(level, chunk, chunk.getPos().x, chunk.getPos().z, false);
    }

    /** onClientChunkUnloaded: 卸载后允许下次加载重新采样，建筑变化能反映到地图。 */
    public void onClientChunkUnloaded(ChunkPos chunkPos) {
        if (chunkPos == null) {
            return;
        }
        scannedAtTick.remove(chunkPos.toLong());
    }

    private void updateScope(String worldId, ResourceKey<Level> dimension) {
        String previousWorldId = currentWorldId;
        ResourceKey<Level> previousDimension = currentDimension;
        if (Objects.equals(worldId, previousWorldId) && Objects.equals(dimension, previousDimension)) {
            return;
        }

        if (previousWorldId != null && previousDimension != null) {
            boolean sameDimension = previousDimension.equals(dimension);
            if (SimuMapStorage.isResolvedWorldId(previousWorldId) || !sameDimension) {
                persistRegionsAsync(previousWorldId, previousDimension, List.copyOf(regions.values()), "world_or_dimension_change");
                LOGGER.info("Simukraft: Map scope changed from world={} dim={} to world={} dim={}, queued async save for {} regions.",
                        previousWorldId, SimuMapStorage.dimensionToDir(previousDimension),
                        worldId, SimuMapStorage.dimensionToDir(dimension), regions.size());
                regions.clear();
                renderingKeys.clear();
                scannedAtTick.clear();
                resetScanCursor();
            }
        } else if (previousDimension == null) {
            LOGGER.info("Simukraft: First dimension acquired: {}.", SimuMapStorage.dimensionToDir(dimension));
        }

        currentWorldId = worldId;
        currentDimension = dimension;
        if (SimuMapStorage.isResolvedWorldId(worldId)) {
            queueRegionLoad(worldId, dimension);
        }
    }

    private boolean shouldScanThisTick(boolean active) {
        int interval = active ? ACTIVE_SCAN_INTERVAL_TICKS : PASSIVE_SCAN_INTERVAL_TICKS;
        return tickCount % interval == 0;
    }

    private void autoSaveRegions() {
        if (currentWorldId == null || currentDimension == null || regions.isEmpty()) {
            return;
        }

        SimuMapStorage.saveAllAsync(currentWorldId, currentDimension,
                List.copyOf(regions.values()), "periodic_cache", false);
    }

    private void incrementalScan(int maxChunks) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null || maxChunks <= 0) {
            return;
        }

        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;
        int scanRadiusNow = getEffectiveScanRadius();
        if (scanCursorRadius != scanRadiusNow) {
            resetScanCursor(scanRadiusNow);
        }

        int scanned = 0;
        int attempts = 0;
        int side = scanRadiusNow * 2 + 1;
        int maxAttempts = side * side;
        while (scanned < maxChunks && attempts < maxAttempts) {
            int chunkX = playerChunkX + scanCursorDX;
            int chunkZ = playerChunkZ + scanCursorDZ;

            advanceScanCursor(scanRadiusNow);
            attempts++;

            if (!SimuChunkScanner.isChunkLoaded(level, chunkX, chunkZ)) {
                continue;
            }

            if (scanLoadedChunk(level, chunkX, chunkZ, false)) {
                scanned++;
            }
        }
    }

    private boolean scanLoadedChunk(Level level, int chunkX, int chunkZ, boolean force) {
        ChunkAccess chunk = SimuChunkScanner.getLoadedChunk(level, chunkX, chunkZ);
        if (chunk == null) {
            return false;
        }
        return scanLoadedChunk(level, chunk, chunkX, chunkZ, force);
    }

    private boolean scanLoadedChunk(Level level, ChunkAccess chunk, int chunkX, int chunkZ, boolean force) {
        long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
        if (!force && !shouldScanChunk(chunkLong)) {
            return false;
        }
        SimuMapRegion region = getOrCreateRegion(chunkX >> 5, chunkZ >> 5);
        try {
            boolean scanned = SimuChunkScanner.scanChunk(level, chunk, chunkX, chunkZ, region);
            if (scanned) {
                scannedAtTick.put(chunkLong, tickCount);
            }
            return scanned;
        } catch (Exception e) {
            LOGGER.debug("Simukraft: Chunk scan failed for ({}, {}): {}", chunkX, chunkZ, e.getMessage());
            return false;
        }
    }

    /** shouldScanChunk: 未扫描过的区块立刻采；地图打开时隔一段时间才重扫。 */
    private boolean shouldScanChunk(long chunkLong) {
        Long lastScanTick = scannedAtTick.get(chunkLong);
        if (lastScanTick == null) {
            return true;
        }
        if (!hasActiveConsumer()) {
            return false;
        }
        return tickCount - lastScanTick >= ACTIVE_REFRESH_INTERVAL_TICKS;
    }

    private void resetScanCursor() {
        resetScanCursor(getEffectiveScanRadius());
    }

    private void resetScanCursor(int radius) {
        scanCursorRadius = radius;
        scanCursorDX = -radius;
        scanCursorDZ = -radius;
    }

    private void advanceScanCursor(int radius) {
        scanCursorDX++;
        if (scanCursorDX > radius) {
            scanCursorDX = -radius;
            scanCursorDZ++;
            if (scanCursorDZ > radius) {
                scanCursorDZ = -radius;
            }
        }
    }

    private void renderDirtyRegions() {
        for (Map.Entry<Long, SimuMapRegion> entry : regions.entrySet()) {
            long key = entry.getKey();
            SimuMapRegion region = entry.getValue();
            SimuMapRegionData data = region.getData();
            if (data == null || !data.isDirty() || !renderingKeys.add(key)) {
                continue;
            }

            renderExecutor.execute(() -> {
                try {
                    SimuMapRenderer.renderRegion(region);
                } catch (Exception e) {
                    LOGGER.error("Simukraft: Failed to render region {}", region, e);
                } finally {
                    renderingKeys.remove(key);
                }
            });
        }
    }

    private void releaseStaleRegions(long maxAge) {
        long now = System.currentTimeMillis();
        regions.entrySet().removeIf(entry -> {
            SimuMapRegion region = entry.getValue();
            if (now - region.getLastAccessTime() <= maxAge) {
                return false;
            }

            SimuMapRegionData data = region.getData();
            if (data == null && !region.isImageLoaded()) {
                return true;
            }
            if (region.isImageLoaded()) {
                region.releaseTexture();
                if (data != null) {
                    data.markDirty();
                }
            }
            return false;
        });
    }

    private void persistRegionsAsync(@Nullable String worldId, @Nullable ResourceKey<Level> dimension,
                                     List<SimuMapRegion> regionSnapshot, String reason) {
        if (regionSnapshot.isEmpty()) {
            return;
        }

        for (SimuMapRegion region : regionSnapshot) {
            region.releaseTexture();
        }

        if (SimuMapStorage.isResolvedWorldId(worldId) && dimension != null) {
            SimuMapStorage.saveAllAsync(worldId, dimension, regionSnapshot, reason, true);
            return;
        }

        for (SimuMapRegion region : regionSnapshot) {
            region.discardData();
        }
    }

    private void queueRegionLoad(String worldId, ResourceKey<Level> dimension) {
        if (!SimuMapStorage.isResolvedWorldId(worldId) || dimension == null) {
            return;
        }
        int currentLoadGeneration = ++loadGeneration;
        SimuMapStorage.loadAllAsync(worldId, dimension, loadedRegions -> {
            Minecraft.getInstance().execute(() -> {
                if (!initialized || currentLoadGeneration != loadGeneration) {
                    return;
                }
                if (!worldId.equals(currentWorldId) || currentDimension == null || !currentDimension.equals(dimension)) {
                    return;
                }

                loadedRegions.forEach(regions::putIfAbsent);
                LOGGER.info("Simukraft: Async-loaded {} regions for world={} dim={}.",
                        loadedRegions.size(), worldId, SimuMapStorage.dimensionToDir(dimension));
            });
        });
    }

    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }
}
