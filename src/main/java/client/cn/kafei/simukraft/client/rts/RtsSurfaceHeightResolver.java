package client.cn.kafei.simukraft.client.rts;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
public final class RtsSurfaceHeightResolver {
    private static final long MAX_EXACT_COLUMNS = 65_536L;
    private static final int MAX_CACHED_COLUMNS = 65_536;
    private static final Predicate<BlockState> SURFACE_BLOCK_PREDICATE =
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.isOpaque();

    private RtsSurfaceHeightResolver() {
    }

    /** resolveHighestSurfaceY：扫描已加载方块列，避免客户端高度图在 Sodium 下返回错误高度。 */
    private static int resolveHighestSurfaceY(ClientLevel level, int minX, int maxX, int minZ, int maxZ, int fallbackY,
                                              ConcurrentMap<Long, Integer> columnHeights) {
        if (level == null || minX > maxX || minZ > maxZ) {
            return fallbackY;
        }
        long width = (long) maxX - minX + 1L;
        long depth = (long) maxZ - minZ + 1L;
        if (width > MAX_EXACT_COLUMNS || depth > MAX_EXACT_COLUMNS || width * depth > MAX_EXACT_COLUMNS) {
            return fallbackY;
        }
        int highestY = Integer.MIN_VALUE;
        for (int chunkX = SectionPos.blockToSectionCoord(minX); chunkX <= SectionPos.blockToSectionCoord(maxX); chunkX++) {
            int startX = Math.max(minX, SectionPos.sectionToBlockCoord(chunkX));
            int endX = Math.min(maxX, SectionPos.sectionToBlockCoord(chunkX + 1) - 1);
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ); chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                int startZ = Math.max(minZ, SectionPos.sectionToBlockCoord(chunkZ));
                int endZ = Math.min(maxZ, SectionPos.sectionToBlockCoord(chunkZ + 1) - 1);
                for (int x = startX; x <= endX; x++) {
                    for (int z = startZ; z <= endZ; z++) {
                        long columnKey = BlockPos.asLong(x, 0, z);
                        int columnX = x;
                        int columnZ = z;
                        int surfaceY = columnHeights.computeIfAbsent(columnKey,
                                unused -> resolveColumnSurfaceY(level, columnX, columnZ));
                        highestY = Math.max(highestY, surfaceY);
                    }
                }
            }
        }
        return highestY != Integer.MIN_VALUE ? highestY : fallbackY;
    }

    /** resolveSurfaceY: 返回单列已加载方块的地表上方坐标，供 RTS 光标落点使用。 */
    public static int resolveSurfaceY(ClientLevel level, int x, int z) {
        if (level == null || !level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(x),
                SectionPos.blockToSectionCoord(z))) {
            return level == null ? 0 : level.getMinBuildHeight();
        }
        return resolveColumnSurfaceY(level, x, z);
    }

    /** resolveColumnSurfaceY: 从世界顶端扫描到首个阻挡移动的非树叶方块。 */
    private static int resolveColumnSurfaceY(ClientLevel level, int x, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, level.getMaxBuildHeight() - 1, z);
        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (SURFACE_BLOCK_PREDICATE.test(state)) {
                return y + 1;
            }
        }
        return level.getMinBuildHeight();
    }

    /** areAllChunksLoaded：仅在可精确扫描的范围内检查是否已完整加载。 */
    private static boolean areAllChunksLoaded(ClientLevel level, int minX, int maxX, int minZ, int maxZ) {
        long width = (long) maxX - minX + 1L;
        long depth = (long) maxZ - minZ + 1L;
        if (width > MAX_EXACT_COLUMNS || depth > MAX_EXACT_COLUMNS || width * depth > MAX_EXACT_COLUMNS) {
            return false;
        }
        for (int chunkX = SectionPos.blockToSectionCoord(minX); chunkX <= SectionPos.blockToSectionCoord(maxX); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ); chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** SurfaceHeightCache：缓存同一投影范围的最高地表，避免鼠标静止时重复扫描。 */
    public static final class SurfaceHeightCache {
        private ClientLevel level;
        private int minX = Integer.MIN_VALUE;
        private int maxX;
        private int minZ;
        private int maxZ;
        private int highestY;
        private final ConcurrentMap<Long, Integer> columnHeights = new ConcurrentHashMap<>();

        /** resolve：仅在投影范围区块齐全时返回地表高度，防止部分高度图导致建筑下沉。 */
        public SurfaceHeight resolve(ClientLevel currentLevel, int currentMinX, int currentMaxX,
                                     int currentMinZ, int currentMaxZ, int fallbackY) {
            if (currentLevel == null || !areAllChunksLoaded(currentLevel, currentMinX, currentMaxX, currentMinZ, currentMaxZ)) {
                return new SurfaceHeight(fallbackY, false);
            }
            if (level != currentLevel || columnHeights.size() >= MAX_CACHED_COLUMNS) {
                columnHeights.clear();
            }
            if (level == currentLevel && minX == currentMinX && maxX == currentMaxX
                    && minZ == currentMinZ && maxZ == currentMaxZ) {
                return new SurfaceHeight(highestY, true);
            }
            level = currentLevel;
            minX = currentMinX;
            maxX = currentMaxX;
            minZ = currentMinZ;
            maxZ = currentMaxZ;
            highestY = resolveHighestSurfaceY(currentLevel, currentMinX, currentMaxX, currentMinZ, currentMaxZ,
                    fallbackY, columnHeights);
            return new SurfaceHeight(highestY, true);
        }

        /** clear：世界或预览结束时释放上一次投影范围缓存。 */
        public void clear() {
            level = null;
            minX = Integer.MIN_VALUE;
            columnHeights.clear();
        }
    }

    /** SurfaceHeight：封装地表高度及其投影范围是否完整加载。 */
    public record SurfaceHeight(int y, boolean complete) {
    }
}
