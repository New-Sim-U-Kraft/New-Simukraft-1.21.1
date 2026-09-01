package client.cn.kafei.simukraft.client.city.map;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.slf4j.Logger;

import java.util.Objects;

@OnlyIn(Dist.CLIENT)
public class SimuChunkScanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SimuChunkScanner() {
    }

    public static boolean scanChunk(int chunkX, int chunkZ, SimuMapRegion region) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return false;

        ChunkAccess chunk = getLoadedChunk(level, chunkX, chunkZ);
        if (chunk == null) return false;

        return scanChunk(level, chunk, chunkX, chunkZ, region);
    }

    public static boolean scanChunk(Level level, ChunkAccess chunk, int chunkX, int chunkZ, SimuMapRegion region) {
        if (level == null || chunk == null) return false;

        SimuMapRegionData data = region.getOrCreateData();
        SimuBlockColors colors = SimuBlockColors.getInstance();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        int regOriginX = region.regionX * 512;
        int regOriginZ = region.regionZ * 512;
        int minBuild = level.getMinBuildHeight();

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;

                int regionLocalX = worldX - regOriginX;
                int regionLocalZ = worldZ - regOriginZ;

                if (regionLocalX < 0 || regionLocalX >= 512 || regionLocalZ < 0 || regionLocalZ >= 512) {
                    continue;
                }

                int topY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
                ColumnSample sample = sampleColumn(level, chunk, colors, pos, worldX, worldZ, topY, minBuild);
                data.setData(regionLocalX, regionLocalZ, sample.height(), sample.color(), sample.water(), sample.light());
            }
        }

        return true;
    }

    /**
     * sampleColumn: 从地表向下跳过花草，采样可见方块色，水面会与水底混合。
     */
    private static ColumnSample sampleColumn(Level level, ChunkAccess chunk, SimuBlockColors colors,
                                             BlockPos.MutableBlockPos pos, int worldX, int worldZ,
                                             int topY, int minBuild) {
        int y = topY;
        pos.set(worldX, y, worldZ);
        BlockState state = chunk.getBlockState(pos);
        while ((state.isAir() || shouldSkipSurfaceBlock(state)) && y > minBuild) {
            y--;
            pos.setY(y);
            state = chunk.getBlockState(pos);
        }

        FluidState fluidState = state.getFluidState();
        boolean liquidSurface = isLiquidSurface(state, fluidState);
        if (liquidSurface) {
            return sampleLiquidColumn(level, chunk, colors, pos, worldX, worldZ, y, minBuild, state, fluidState);
        }

        int color = colors.getBlockColor(state, level, pos);
        if (shouldBlendWithBelow(state) && y > minBuild) {
            pos.setY(y - 1);
            BlockState below = chunk.getBlockState(pos);
            if (!below.isAir() && !shouldSkipSurfaceBlock(below)) {
                int belowColor = colors.getBlockColor(below, level, pos);
                color = SimuBlockColors.blendColors(belowColor, (color & 0x00FFFFFF) | 0xA0000000);
            }
            pos.setY(y);
        }
        return new ColumnSample((short) y, color, false, readLight(level, pos, worldX, y, worldZ));
    }

    /**
     * sampleLiquidColumn: 把水体与水底色按深度混合，浅水能透出河床。
     */
    private static ColumnSample sampleLiquidColumn(Level level, ChunkAccess chunk, SimuBlockColors colors,
                                                   BlockPos.MutableBlockPos pos, int worldX, int worldZ,
                                                   int surfaceY, int minBuild, BlockState surfaceState,
                                                   FluidState fluidState) {
        boolean lava = fluidState.is(Fluids.LAVA) || surfaceState.is(Blocks.LAVA);
        BlockState liquidState = lava ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState();
        pos.set(worldX, surfaceY, worldZ);
        int liquidColor = colors.getBlockColor(liquidState, level, pos);

        int depth = 0;
        int floorY = surfaceY;
        BlockState floorState = surfaceState;
        int y = surfaceY;
        while (y > minBuild && depth < 24) {
            y--;
            pos.setY(y);
            BlockState below = chunk.getBlockState(pos);
            if (shouldSkipSurfaceBlock(below)) {
                continue;
            }
            if (isLiquidSurface(below, below.getFluidState())) {
                depth++;
                continue;
            }
            floorState = below;
            floorY = y;
            break;
        }

        int color = liquidColor;
        if (!lava) {
            pos.setY(floorY);
            int floorColor = colors.getBlockColor(floorState, level, pos);
            int waterAlpha = Math.min(0x48 + depth * 18, 0xC8);
            color = SimuBlockColors.blendColors(floorColor, (liquidColor & 0x00FFFFFF) | (waterAlpha << 24));
            if (depth > 2) {
                color = SimuBlockColors.adjustBrightness(color, -Math.min(0.22f, depth * 0.018f));
            }
        }
        return new ColumnSample((short) surfaceY, color, !lava, readLight(level, pos, worldX, surfaceY, worldZ));
    }

    /** shouldSkipSurfaceBlock: 花草等装饰不作为地图地表，避免把草地盖成杂色噪点。 */
    private static boolean shouldSkipSurfaceBlock(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty() && isLiquidSurface(state, state.getFluidState())) {
            return false;
        }
        return (state.is(BlockTags.REPLACEABLE) && state.getFluidState().isEmpty())
                || state.is(BlockTags.SMALL_FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.getBlock() instanceof TallGrassBlock;
    }

    /** isLiquidSurface: 真正的水面/岩浆面，不含含水箱子这类方块。 */
    private static boolean isLiquidSurface(BlockState state, FluidState fluidState) {
        if (fluidState.isEmpty()) {
            return false;
        }
        return state.getBlock() instanceof LiquidBlock
                || state.is(Blocks.BUBBLE_COLUMN)
                || state.isAir();
    }

    /** shouldBlendWithBelow: 玻璃叠在下层地形上，树叶保持不透明以形成树冠。 */
    private static boolean shouldBlendWithBelow(BlockState state) {
        return state.is(BlockTags.IMPERMEABLE);
    }

    private static int readLight(Level level, BlockPos.MutableBlockPos pos, int worldX, int y, int worldZ) {
        try {
            pos.set(worldX, y + 1, worldZ);
            return Math.max(level.getBrightness(LightLayer.SKY, pos), level.getBrightness(LightLayer.BLOCK, pos));
        } catch (RuntimeException ignored) {
            return 15;
        }
    }

    private record ColumnSample(short height, int color, boolean water, int light) {
    }

    /** 判断客户端是否已经持有指定 FULL chunk。 */
    public static boolean isChunkLoaded(Level level, int chunkX, int chunkZ) {
        return getLoadedChunk(level, chunkX, chunkZ) != null;
    }

    /** 获取客户端缓存中的 FULL chunk，不触发新 chunk 加载。 */
    public static ChunkAccess getLoadedChunk(Level level, int chunkX, int chunkZ) {
        try {
            return level.getChunk(chunkX, chunkZ, Objects.requireNonNull(ChunkStatus.FULL), false);
        } catch (RuntimeException exception) {
            LOGGER.debug("Simukraft: Failed to query loaded client chunk ({}, {}): {}", chunkX, chunkZ, exception.getMessage());
            return null;
        }
    }

    public static void scanAroundPlayer(SimuMapManager manager, int radius) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = playerChunkX + dx;
                int cz = playerChunkZ + dz;

                if (!isChunkLoaded(level, cx, cz)) continue;

                int regionX = cx >> 5;
                int regionZ = cz >> 5;
                SimuMapRegion region = manager.getOrCreateRegion(regionX, regionZ);

                try {
                    scanChunk(cx, cz, region);
                } catch (Exception e) {
                    LOGGER.debug("Simukraft: Failed to scan chunk ({}, {}): {}", cx, cz, e.getMessage());
                }
            }
        }
    }
}
