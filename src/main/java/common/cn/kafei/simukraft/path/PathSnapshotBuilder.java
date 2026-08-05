package common.cn.kafei.simukraft.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.OptionalDouble;

/**
 * Samples the live world on the server thread into an immutable {@link PathSnapshot}.
 *
 * <h3>Two-phase build</h3>
 * <ol>
 *   <li>{@link #capture} — runs on the <em>server thread</em>. Eagerly reads every
 *       {@link BlockState} and {@link VoxelShape} in the bounded volume into a plain
 *       {@link ChunkDataCapture}. Context-sensitive shapes (fences, walls, bars) are computed
 *       here while the live {@link ServerLevel} is available. The result is an immutable
 *       snapshot of raw block data with no further world access needed.</li>
 *   <li>{@link #buildFromCapture} — runs on a <em>worker thread</em>. Performs all A*-relevant
 *       classification logic ({@code classify}, {@code hasBodyPassage}, clearance checks) against
 *       the pre-captured data. The live world is never read here.</li>
 * </ol>
 *
 * <p>{@link #build} is a convenience wrapper that calls both phases on the current thread and is
 * retained for the debug-path code path.
 */
@SuppressWarnings("null")
final class PathSnapshotBuilder {
    private static final int HORIZONTAL_PADDING = 12;
    private static final int VERTICAL_PADDING = 8;
    private static final double NPC_HALF_WIDTH = 0.31D;
    private static final double NPC_HEIGHT = 1.8D;
    private static final double MAX_LOW_STAND_OFFSET = 0.75D;
    // A legitimate floor support's top sits at or below the cell's own grid Y (collision height
    // <= 1.0). Anything higher is a fence/wall/closed-gate protruding into the cell, never a surface.
    // 0.0626 covers grass_path's 15/16-height top surface (0.0625 below grid)
    private static final double FLOOR_TOP_EPSILON = 0.0626D;

    private PathSnapshotBuilder() {
    }

    /**
     * Immutable block data captured on the server thread for a bounded volume.
     *
     * <p>Both maps sparsely cover {@code bounds} plus a one-block vertical fringe
     * ({@code minY-1} to {@code maxY+1}) so that {@link #buildFromCapture} can read
     * {@code pos.below()} and {@code pos.above()} without a bounds check. Missing entries mean
     * air / empty collision. The maps are written once during capture and never modified afterward,
     * so worker threads may read them freely.
     *
     * @param complete false when at least one chunk in the volume was unloaded at capture time
     */
    record ChunkDataCapture(
            Long2ObjectOpenHashMap<BlockState> states,
            Long2ObjectOpenHashMap<VoxelShape> shapes,
            Long2ObjectOpenHashMap<SectionDataCapture> sections,
            SnapshotBounds bounds,
            net.minecraft.resources.ResourceLocation dimensionId,
            long createdAt,
            boolean complete) {
        ChunkDataCapture(Long2ObjectOpenHashMap<BlockState> states,
                         Long2ObjectOpenHashMap<VoxelShape> shapes,
                         SnapshotBounds bounds,
                         net.minecraft.resources.ResourceLocation dimensionId,
                         long createdAt,
                         boolean complete) {
            this(states, shapes, null, bounds, dimensionId, createdAt, complete);
        }
    }

    /** Immutable raw data for one 16x16x16 world section. */
    record SectionDataCapture(
            Long2ObjectOpenHashMap<BlockState> states,
            Long2ObjectOpenHashMap<VoxelShape> shapes) {
    }

    /**
     * Phase 1 — server thread. Reads every {@link BlockState} and {@link VoxelShape} in the
     * bounded volume (plus a one-block vertical fringe) into a {@link ChunkDataCapture}; air and
     * empty collision shapes are omitted.
     * Context-sensitive shapes (fences, walls, iron bars) are resolved here while the live
     * {@link ServerLevel} is available. Returns incomplete data if any column's chunk is unloaded.
     */
    static ChunkDataCapture capture(ServerLevel level, BlockPos start, BlockPos target, int radius) {
        SnapshotBounds bounds = bounds(level, start, target, radius);
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        boolean complete = true;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int scanMinY = Math.max(level.getMinBuildHeight(), bounds.minY() - 1);
        int scanMaxY = Math.min(level.getMaxBuildHeight() - 1, bounds.maxY() + 1);
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                mutable.set(x, start.getY(), z);
                if (!hasLoadedChunk(level, mutable)) {
                    complete = false;
                    continue;
                }
                for (int y = scanMinY; y <= scanMaxY; y++) {
                    mutable.set(x, y, z);
                    captureBlock(level, mutable, states, shapes);
                }
            }
        }
        return new ChunkDataCapture(states, shapes, bounds, level.dimension().location(), level.getGameTime(), complete);
    }

    /** captureSection: 在主线程冻结一个区段的方块状态与实际碰撞形状。 */
    static SectionDataCapture captureSection(ServerLevel level, int sectionX, int sectionY, int sectionZ) {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int minX = sectionX << 4;
        int minY = sectionY << 4;
        int minZ = sectionZ << 4;
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                for (int y = minY; y < minY + 16; y++) {
                    mutable.set(x, y, z);
                    captureBlock(level, mutable, states, shapes);
                }
            }
        }
        return new SectionDataCapture(states, shapes);
    }

    /** composeCapture: 创建独立区段索引视图，避免在主线程复制区段中的每个方块。 */
    static ChunkDataCapture composeCapture(ServerLevel level, SnapshotBounds bounds,
                                           Long2ObjectOpenHashMap<SectionDataCapture> sections, boolean complete) {
        return new ChunkDataCapture(null, null, sections, bounds,
                level.dimension().location(), level.getGameTime(), complete);
    }

    /** captureBlock: 省略空气和空碰撞，保留异步构建所需的完整非空气状态。 */
    private static void captureBlock(ServerLevel level, BlockPos.MutableBlockPos pos,
                                     Long2ObjectOpenHashMap<BlockState> states,
                                     Long2ObjectOpenHashMap<VoxelShape> shapes) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        long key = pos.asLong();
        states.put(key, state);
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (!shape.isEmpty()) {
            shapes.put(key, shape);
        }
    }

    /**
     * Phase 2 — worker thread. Classifies every cell in {@code capture} into walkable
     * {@link PathCell}s and body passages. Never touches the live world.
     */
    static PathSnapshot buildFromCapture(ChunkDataCapture capture, BlockPos start, BlockPos target) {
        CaptureData data = new CaptureData(capture);
        SnapshotBounds bounds = capture.bounds();
        Long2ObjectOpenHashMap<PathCell> cells = new Long2ObjectOpenHashMap<>();
        Long2ByteOpenHashMap horizontalBarriers = new Long2ByteOpenHashMap();
        LongOpenHashSet bodyPassages = new LongOpenHashSet();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    mutable.set(x, y, z);
                    if (hasBodyPassage(data, mutable)) {
                        bodyPassages.add(mutable.asLong());
                    }
                    PathCell cell = classify(data, mutable);
                    if (cell != null) {
                        cells.put(cell.key(), cell);
                    }
                }
            }
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = bounds.minY() - 1; y <= bounds.maxY() + 1; y++) {
                    mutable.set(x, y, z);
                    addOpenTrapdoorBarriers(data, mutable, bounds, horizontalBarriers);
                }
            }
        }
        return new PathSnapshot(capture.dimensionId(), start.immutable(), target.immutable(),
                cells, LongSets.unmodifiable(bodyPassages), Long2ByteMaps.unmodifiable(horizontalBarriers),
                bounds.minY(), bounds.maxY(), capture.createdAt(), capture.complete());
    }

    /**
     * Convenience wrapper that runs both phases on the calling thread.
     * Used by the debug-path code path which already runs async.
     */
    static PathSnapshot build(ServerLevel level, BlockPos start, BlockPos target, int radius) {
        BlockPos buildStart = new BlockPos(
                Math.floorDiv(start.getX(), 16) * 16 + 8,
                start.getY(),
                Math.floorDiv(start.getZ(), 16) * 16 + 8);
        int buildRadius = radius + 16;
        ChunkDataCapture capture = capture(level, buildStart, target, buildRadius);
        return buildFromCapture(capture, start, target);
    }

    /**
     * Computes the sampled box for a request without reading any block state.
     *
     * <p>Exposed so callers can test box containment for snapshot reuse using exactly the same
     * bounds {@link #build} would produce.
     */
    static SnapshotBounds bounds(ServerLevel level, BlockPos start, BlockPos target, int radius) {
        int safeRadius = Math.max(16, radius);
        int minX = Math.max(Math.min(start.getX(), target.getX()) - HORIZONTAL_PADDING, start.getX() - safeRadius);
        int maxX = Math.min(Math.max(start.getX(), target.getX()) + HORIZONTAL_PADDING, start.getX() + safeRadius);
        int minZ = Math.max(Math.min(start.getZ(), target.getZ()) - HORIZONTAL_PADDING, start.getZ() - safeRadius);
        int maxZ = Math.min(Math.max(start.getZ(), target.getZ()) + HORIZONTAL_PADDING, start.getZ() + safeRadius);
        int minY = Math.max(level.getMinBuildHeight(), Math.min(start.getY(), target.getY()) - VERTICAL_PADDING);
        int maxY = Math.min(level.getMaxBuildHeight() - 2, Math.max(start.getY(), target.getY()) + VERTICAL_PADDING);
        return new SnapshotBounds(minX, maxX, minZ, maxZ, minY, maxY);
    }

    /**
     * Classifies a single column position into a walkable {@link PathCell}, or {@code null} when the
     * citizen cannot occupy it.
     */
    private static PathCell classify(BlockDataSource cache, BlockPos pos) {
        BlockState foot = cache.state(pos);
        BlockState head = cache.state(pos.above());
        BlockState below = cache.state(pos.below());
        if (isDangerous(foot) || isDangerous(head) || isDangerous(below)) {
            return null;
        }

        boolean footWater = foot.getFluidState().is(FluidTags.WATER);
        boolean headWater = head.getFluidState().is(FluidTags.WATER);
        boolean water = footWater || headWater;
        boolean climbable = isClimbable(foot) || isClimbable(head);
        if (water) {
            if (!isFootPassable(cache, pos, foot) || !isHeadPassable(cache, pos.above(), head)) {
                return null;
            }
            return new PathCell(pos.immutable(), pos.getX(), pos.getY(), pos.getZ(), pos.getY(), true, climbable, false, false, 5.0D);
        }
        if (climbable && isFootPassable(cache, pos, foot) && isHeadPassable(cache, pos.above(), head)) {
            boolean floorSupported = isGridFloorSupport(pos, supportTop(cache, pos.below(), below));
            return new PathCell(pos.immutable(), pos.getX(), pos.getY(), pos.getZ(), pos.getY(), false, true, false, floorSupported, 8.0D);
        }
        if (isClosedWoodenLowerDoor(foot) && isMatchingWoodenDoorHead(head)) {
            OptionalDouble doorStandY = supportTop(cache, pos.below(), below);
            if (isGridFloorSupport(pos, doorStandY) && hasNpcClearance(cache, pos, doorStandY.getAsDouble(), pos, pos.above())) {
                return new PathCell(pos.immutable(), pos.getX(), pos.getY(), pos.getZ(), doorStandY.getAsDouble(), false, false, true, true, 3.2D);
            }
        }
        if (!isFootPassable(cache, pos, foot) || !isHeadPassable(cache, pos.above(), head)) {
            OptionalDouble lowStandY = lowStandY(cache, pos, foot);
            if (lowStandY.isPresent() && isHeadPassable(cache, pos.above(), head, lowStandY.getAsDouble() - pos.getY()) && hasNpcClearance(cache, pos, lowStandY.getAsDouble(), null, null)) {
                return new PathCell(pos.immutable(), pos.getX(), pos.getY(), pos.getZ(), lowStandY.getAsDouble(), false, false, false, true, 1.05D);
            }
            return null;
        }
        OptionalDouble standY = supportTop(cache, pos.below(), below);
        // 只接受贴着当前脚部格底面的支撑面；过高是栅栏/墙，过低则属于下一格内部的薄方块。
        if (!isGridFloorSupport(pos, standY)) {
            return null;
        }
        if (!hasNpcClearance(cache, pos, standY.getAsDouble(), null, null)) {
            return null;
        }
        return new PathCell(pos.immutable(), pos.getX(), pos.getY(), pos.getZ(), standY.getAsDouble(), false, false, false, true, 1.0D);
    }

    private static boolean hasLoadedChunk(ServerLevel level, BlockPos pos) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /**
     * Returns whether the citizen's body can occupy the column at {@code pos} given its block state.
     */
    private static boolean isFootPassable(BlockDataSource cache, BlockPos pos, BlockState state) {
        return isBodyPassable(cache, pos, state, 0.0D, 1.0D);
    }

    private static boolean isHeadPassable(BlockDataSource cache, BlockPos pos, BlockState state) {
        return isBodyPassable(cache, pos, state, 0.0D, NPC_HEIGHT - 1.0D);
    }

    private static boolean isHeadPassable(BlockDataSource cache, BlockPos pos, BlockState state, double standOffset) {
        double localMinY = Math.max(0.0D, standOffset - 1.0D);
        double localMaxY = Math.max(localMinY, standOffset + NPC_HEIGHT - 1.0D);
        return isBodyPassable(cache, pos, state, localMinY, localMaxY);
    }

    private static boolean isBodyPassable(BlockDataSource cache, BlockPos pos, BlockState state, double localMinY, double localMaxY) {
        Block block = state.getBlock();
        if (isDoorLikeBlock(block)) {
            return isNpcPassableDoorLikeBlock(state, cache.shape(pos, state), localMinY, localMaxY);
        }
        return state.isAir()
                || state.getFluidState().is(FluidTags.WATER)
                || isClimbable(state)
                || clearsNpcBodySlice(cache, pos, state, localMinY, localMaxY);
    }

    /**
     * Returns whether the block's collision shape leaves the citizen's centred footprint column
     * free, i.e. a body standing at the block's centre would not intersect it.
     *
     * <p>This generalises the previous {@code shape.isEmpty()} test: a thin, face-hugging partial
     * shape the slim body actually clears — an open trapdoor pinned to one wall, a wall lever or
     * button — is now passable, which is what lets a citizen climb a ladder out through an open
     * trapdoor or stand under one. A shape that fills the footprint (a closed trapdoor on the floor,
     * a slab, a fence post, a closed gate) still reports blocking, so it is routed onto via {@link
     * #lowStandY} or jumped/avoided exactly as before. The test also checks the occupied vertical
     * slice, so upper trapdoors above the head are not treated like floor-level blockers.
     */
    private static boolean clearsNpcBodySlice(BlockDataSource cache, BlockPos pos, BlockState state, double localMinY, double localMaxY) {
        return clearsNpcBodySlice(cache.shape(pos, state), localMinY, localMaxY);
    }

    /** clearsNpcBodySlice: 检查中心脚印在指定垂直切片里是否避开碰撞体。 */
    static boolean clearsNpcBodySlice(VoxelShape shape, double localMinY, double localMaxY) {
        if (shape.isEmpty()) {
            return true;
        }
        double minX = 0.5D - NPC_HALF_WIDTH;
        double maxX = 0.5D + NPC_HALF_WIDTH;
        double minZ = 0.5D - NPC_HALF_WIDTH;
        double maxZ = 0.5D + NPC_HALF_WIDTH;
        for (AABB box : shape.toAabbs()) {
            if (box.maxX > minX && box.minX < maxX
                    && box.maxY > localMinY && box.minY < localMaxY
                    && box.maxZ > minZ && box.minZ < maxZ) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasBodyPassage(BlockDataSource cache, BlockPos pos) {
        BlockState foot = cache.state(pos);
        BlockState head = cache.state(pos.above());
        if (isDangerous(foot) || isDangerous(head)) {
            return false;
        }
        return isFootPassable(cache, pos, foot)
                && isHeadPassable(cache, pos.above(), head)
                && hasNpcClearance(cache, pos, pos.getY(), null, null);
    }

    private static void addOpenTrapdoorBarriers(BlockDataSource cache, BlockPos trapdoorPos,
                                                SnapshotBounds bounds, Long2ByteOpenHashMap barriers) {
        BlockState state = cache.state(trapdoorPos);
        if (!(state.getBlock() instanceof TrapDoorBlock)
                || !state.hasProperty(TrapDoorBlock.OPEN)
                || !state.getValue(TrapDoorBlock.OPEN)
                || !state.hasProperty(TrapDoorBlock.FACING)) {
            return;
        }
        // TrapDoorBlock's FACING points toward the hinge; its open shape occupies the opposite edge.
        Direction edge = state.getValue(TrapDoorBlock.FACING).getOpposite();
        byte mask = PathSnapshot.barrierMask(edge);
        addBarrierIfInBounds(barriers, trapdoorPos.getX(), trapdoorPos.getY(), trapdoorPos.getZ(),
                mask, bounds);
        addBarrierIfInBounds(barriers, trapdoorPos.getX(), trapdoorPos.getY() - 1, trapdoorPos.getZ(),
                mask, bounds);
    }

    private static void addBarrierIfInBounds(Long2ByteOpenHashMap barriers, int x, int y, int z,
                                              byte mask, SnapshotBounds bounds) {
        if (y < bounds.minY() || y > bounds.maxY()) {
            return;
        }
        long key = PathCell.key(x, y, z);
        barriers.put(key, (byte) (barriers.get(key) | mask));
    }

    /**
     * Returns whether the citizen's bounding box, standing at {@code standY} above {@code feet}, is
     * free of solid collision, ignoring up to two positions (used to exclude an opening door).
     */
    private static boolean hasNpcClearance(BlockDataSource cache, BlockPos feet, double standY, BlockPos ignoredA, BlockPos ignoredB) {
        double centerX = feet.getX() + 0.5D;
        double centerZ = feet.getZ() + 0.5D;
        AABB npcBox = new AABB(
                centerX - NPC_HALF_WIDTH,
                standY,
                centerZ - NPC_HALF_WIDTH,
                centerX + NPC_HALF_WIDTH,
                standY + NPC_HEIGHT,
                centerZ + NPC_HALF_WIDTH);
        int minX = (int) Math.floor(npcBox.minX);
        int minY = (int) Math.floor(npcBox.minY) - 1;
        int minZ = (int) Math.floor(npcBox.minZ);
        int maxX = (int) Math.floor(npcBox.maxX);
        int maxY = (int) Math.floor(npcBox.maxY);
        int maxZ = (int) Math.floor(npcBox.maxZ);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    if (mutable.equals(ignoredA) || mutable.equals(ignoredB)) {
                        continue;
                    }
                    BlockState state = cache.state(mutable);
                    VoxelShape shape = cache.shape(mutable, state);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    for (AABB box : shape.toAabbs()) {
                        if (box.maxX + x > npcBox.minX && box.minX + x < npcBox.maxX
                                && box.maxY + y > npcBox.minY && box.minY + y < npcBox.maxY
                                && box.maxZ + z > npcBox.minZ && box.minZ + z < npcBox.maxZ) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Returns the world-space top surface of a supporting block, or an empty {@link OptionalDouble}
     * when it has no collision to stand on.
     */
    private static OptionalDouble supportTop(BlockDataSource cache, BlockPos supportPos, BlockState supportState) {
        return supportTop(supportPos, cache.shape(supportPos, supportState));
    }

    /** supportTop: 返回能接触 NPC 脚印的最高支撑面，避免竖直薄板被误当成地板。 */
    static OptionalDouble supportTop(BlockPos supportPos, VoxelShape shape) {
        if (shape.isEmpty()) {
            return OptionalDouble.empty();
        }
        double top = Double.NEGATIVE_INFINITY;
        for (AABB box : shape.toAabbs()) {
            if (!touchesNpcSupportFootprint(box)) {
                continue;
            }
            top = Math.max(top, supportPos.getY() + box.maxY);
        }
        if (!Double.isFinite(top)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(top);
    }

    /** isGridFloorSupport: 判断支撑面是否正好承托当前脚部格，而不是上一层或下一层。 */
    static boolean isGridFloorSupport(BlockPos pos, OptionalDouble standY) {
        return standY.isPresent() && Math.abs(standY.getAsDouble() - pos.getY()) <= FLOOR_TOP_EPSILON;
    }

    private static boolean touchesNpcSupportFootprint(AABB box) {
        double minX = 0.5D - NPC_HALF_WIDTH;
        double maxX = 0.5D + NPC_HALF_WIDTH;
        double minZ = 0.5D - NPC_HALF_WIDTH;
        double maxZ = 0.5D + NPC_HALF_WIDTH;
        return box.maxX > minX && box.minX < maxX
                && box.maxZ > minZ && box.minZ < maxZ;
    }

    /** isNpcPassableDoorLikeBlock: 门、栅栏门、活板门仅在当前陆地寻路状态可通过时放行。 */
    static boolean isNpcPassableDoorLikeBlock(BlockState state) {
        Block block = state.getBlock();
        return isDoorLikeBlock(block) && state.isPathfindable(PathComputationType.LAND);
    }

    static boolean isNpcPassableDoorLikeBlock(BlockState state, VoxelShape shape, double localMinY, double localMaxY) {
        Block block = state.getBlock();
        if (!isDoorLikeBlock(block)) {
            return false;
        }
        return state.isPathfindable(PathComputationType.LAND)
                || clearsNpcBodySlice(shape, localMinY, localMaxY);
    }

    private static boolean isDoorLikeBlock(Block block) {
        return block instanceof DoorBlock || block instanceof FenceGateBlock || block instanceof TrapDoorBlock;
    }

    private static boolean isClosedWoodenLowerDoor(BlockState state) {
        return DoorBlock.isWoodenDoor(state)
                && state.getBlock() instanceof DoorBlock
                && state.hasProperty(DoorBlock.OPEN)
                && !state.getValue(DoorBlock.OPEN)
                && state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private static boolean isMatchingWoodenDoorHead(BlockState state) {
        return DoorBlock.isWoodenDoor(state)
                && state.getBlock() instanceof DoorBlock
                && state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    private static boolean isClimbable(BlockState state) {
        // 原版梯子必须独立识别；标签仍保留给藤蔓和模组追加的可攀爬方块。
        return state.is(Blocks.LADDER) || state.is(BlockTags.CLIMBABLE) || state.is(Blocks.SCAFFOLDING);
    }

    // lowStandY：识别半砖、地毯等低矮碰撞面，避免把半格台阶误判为上一层跳跃。
    private static OptionalDouble lowStandY(BlockDataSource cache, BlockPos pos, BlockState state) {
        OptionalDouble standY = supportTop(cache, pos, state);
        if (standY.isEmpty()) {
            return OptionalDouble.empty();
        }
        double offset = standY.getAsDouble() - pos.getY();
        return offset > 0.0D && offset <= MAX_LOW_STAND_OFFSET ? standY : OptionalDouble.empty();
    }

    private static boolean isDangerous(BlockState state) {
        Block block = state.getBlock();
        return state.getFluidState().is(FluidTags.LAVA)
                || block == Blocks.LAVA
                || block == Blocks.FIRE
                || block == Blocks.SOUL_FIRE
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CACTUS
                || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.WITHER_ROSE;
    }

    /**
     * Inclusive integer bounds of a sampled snapshot box.
     */
    record SnapshotBounds(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        /**
         * Returns whether this box fully contains {@code other} on all three axes.
         */
        boolean contains(SnapshotBounds other) {
            return minX <= other.minX && maxX >= other.maxX
                    && minZ <= other.minZ && maxZ >= other.maxZ
                    && minY <= other.minY && maxY >= other.maxY;
        }
    }

    /** Common block-data access used by classify and clearance checks. */
    private interface BlockDataSource {
        BlockState state(BlockPos pos);
        VoxelShape shape(BlockPos pos, BlockState state);
    }

    /**
     * Pre-captured source: reads from maps populated by {@link #capture} on the server thread.
     * Safe to use on worker threads — the maps are never modified after capture completes.
     * Missing positions (unloaded chunks) return air / empty shape.
     */
    private static final class CaptureData implements BlockDataSource {
        private static final BlockState AIR = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        private static final VoxelShape EMPTY = net.minecraft.world.phys.shapes.Shapes.empty();
        private final Long2ObjectOpenHashMap<BlockState> states;
        private final Long2ObjectOpenHashMap<VoxelShape> shapes;
        private final Long2ObjectOpenHashMap<SectionDataCapture> sections;

        private CaptureData(ChunkDataCapture capture) {
            this.states = capture.states();
            this.shapes = capture.shapes();
            this.sections = capture.sections();
        }

        @Override
        public BlockState state(BlockPos pos) {
            BlockState s;
            if (sections == null) {
                s = states.get(pos.asLong());
            } else {
                SectionDataCapture section = sections.get(sectionKey(pos));
                s = section == null ? null : section.states().get(pos.asLong());
            }
            return s != null ? s : AIR;
        }

        @Override
        public VoxelShape shape(BlockPos pos, BlockState state) {
            VoxelShape s;
            if (sections == null) {
                s = shapes.get(pos.asLong());
            } else {
                SectionDataCapture section = sections.get(sectionKey(pos));
                s = section == null ? null : section.shapes().get(pos.asLong());
            }
            return s != null ? s : EMPTY;
        }

        /** sectionKey: 根据方块坐标定位只读的 16x16x16 快照区段。 */
        private static long sectionKey(BlockPos pos) {
            return SectionPos.asLong(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getY()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
        }
    }
}
