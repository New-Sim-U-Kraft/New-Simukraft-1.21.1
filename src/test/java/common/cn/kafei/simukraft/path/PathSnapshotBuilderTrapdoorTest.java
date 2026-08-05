package common.cn.kafei.simukraft.path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@SuppressWarnings("null")
class PathSnapshotBuilderTrapdoorTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void trapdoorHalfAndOpenStateDetermineTheOccupiableLayer() {
        BlockState closedTop = trapdoor(false, Half.TOP);
        BlockState closedBottom = trapdoor(false, Half.BOTTOM);
        BlockState openTop = trapdoor(true, Half.TOP);

        assertTrue(snapshot(closedTop).cell(0, 64, 0) != null,
                "a closed top trapdoor should support the upper grid layer");
        assertFalse(snapshot(closedBottom).cell(0, 64, 0) != null,
                "a closed bottom trapdoor should not be lifted into the upper grid layer");
        assertFalse(snapshot(openTop).cell(0, 64, 0) != null,
                "an open trapdoor should leave no false floor above its shaft");

        assertTrue(snapshot(closedBottom).cell(0, 63, 0) != null,
                "a closed bottom trapdoor should remain a low step on the lower layer");
        assertTrue(snapshot(openTop).cell(0, 63, 0) != null,
                "an open trapdoor with a clear hinge edge should remain traversable");
        assertFalse(snapshot(closedTop).cell(0, 63, 0) != null,
                "a closed top trapdoor should block the lower cell rather than become a step");
    }

    @Test
    void openTrapdoorBlocksItsActualThinWallEdge() {
        PathSnapshot snapshot = twoCellSnapshot(trapdoor(true, Half.BOTTOM));

        assertTrue(snapshot.blocksHorizontalBoundary(0, 64, 0, 0, 1),
                "an open north-facing trapdoor occupies the south edge of its block");
        assertTrue(snapshot.blocksHorizontalBoundary(0, 64, 1, 0, 0),
                "the thin wall must block the crossing in both directions");
        assertFalse(snapshot.blocksHorizontalBoundary(0, 64, 0, 0, -1),
                "the hinge edge remains clear");
    }

    @Test
    void scaffoldingCellRetainsFloorSupport() {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        for (int y = 63; y <= 65; y++) {
            BlockPos pos = new BlockPos(0, y, 0);
            BlockState state = y == 63 ? Blocks.STONE.defaultBlockState()
                    : y == 64 ? Blocks.SCAFFOLDING.defaultBlockState() : Blocks.AIR.defaultBlockState();
            states.put(pos.asLong(), state);
            shapes.put(pos.asLong(), state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
        }
        PathSnapshotBuilder.ChunkDataCapture capture = new PathSnapshotBuilder.ChunkDataCapture(
                states, shapes, new PathSnapshotBuilder.SnapshotBounds(0, 0, 0, 0, 64, 64),
                DIMENSION, 0L, true);

        PathCell cell = PathSnapshotBuilder.buildFromCapture(capture,
                new BlockPos(0, 64, 0), new BlockPos(0, 64, 0)).cell(0, 64, 0);

        assertTrue(cell != null && cell.climbable(), "scaffolding column should remain a climbable cell");
        assertTrue(cell != null && cell.floorSupported(), "scaffolding above a full block must retain its landing support");
    }

    /** 活板门薄地板必须能进入相邻下一格梯子，并继续向下爬行。 */
    @Test
    void bottomTrapdoorFloorCanEnterAdjacentLadder() {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        for (int x = 0; x <= 1; x++) {
            for (int y = 61; y <= 65; y++) {
                BlockPos pos = new BlockPos(x, y, 0);
                BlockState state = x == 0 && y == 62 ? Blocks.STONE.defaultBlockState()
                        : x == 0 && y == 63 ? trapdoor(false, Half.BOTTOM)
                        : x == 1 && (y == 62 || y == 61) ? Blocks.LADDER.defaultBlockState()
                        : Blocks.AIR.defaultBlockState();
                states.put(pos.asLong(), state);
                shapes.put(pos.asLong(), state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
            }
        }
        PathSnapshotBuilder.ChunkDataCapture capture = new PathSnapshotBuilder.ChunkDataCapture(
                states, shapes, new PathSnapshotBuilder.SnapshotBounds(0, 1, 0, 0, 62, 64),
                DIMENSION, 0L, true);
        BlockPos start = new BlockPos(0, 63, 0);
        BlockPos target = new BlockPos(1, 62, 0);
        PathSnapshot snapshot = PathSnapshotBuilder.buildFromCapture(capture, start, target);

        PathCell floor = snapshot.cell(start);
        assertTrue(floor != null && Math.abs(floor.standY() - 63.1875D) < 1.0E-6D,
                "closed bottom trapdoor must retain its 0.1875 standing height");
        assertTrue(snapshot.cell(target) != null && snapshot.cell(target).climbable(),
                "the first lower ladder cell must remain climbable");

        PathResult result = HybridPathfinder.find(new PathRequest(UUID.randomUUID(), DIMENSION, start,
                new Vec3(1.5D, 62.0D, 0.5D), MovementIntent.WALK, 0L), snapshot);

        assertTrue(result.success(), "bottom trapdoor floor must not disconnect the adjacent ladder");
    }

    private static BlockState trapdoor(boolean open, Half half) {
        return Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, open)
                .setValue(TrapDoorBlock.HALF, half)
                .setValue(TrapDoorBlock.FACING, Direction.NORTH)
                .setValue(TrapDoorBlock.WATERLOGGED, false);
    }

    private static PathSnapshot snapshot(BlockState trapdoor) {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        for (int y = 62; y <= 65; y++) {
            BlockPos pos = new BlockPos(0, y, 0);
            BlockState state = y == 62 ? Blocks.STONE.defaultBlockState()
                    : y == 63 ? trapdoor : Blocks.AIR.defaultBlockState();
            states.put(pos.asLong(), state);
            shapes.put(pos.asLong(), state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
        }
        PathSnapshotBuilder.ChunkDataCapture capture = new PathSnapshotBuilder.ChunkDataCapture(
                states, shapes, new PathSnapshotBuilder.SnapshotBounds(0, 0, 0, 0, 63, 64),
                DIMENSION, 0L, true);
        return PathSnapshotBuilder.buildFromCapture(capture,
                new BlockPos(0, 64, 0), new BlockPos(0, 63, 0));
    }

    private static PathSnapshot twoCellSnapshot(BlockState trapdoor) {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        for (int z = 0; z <= 1; z++) {
            for (int y = 62; y <= 65; y++) {
                BlockPos pos = new BlockPos(0, y, z);
                BlockState state = y == 63 ? Blocks.STONE.defaultBlockState()
                        : y == 64 && z == 0 ? trapdoor : Blocks.AIR.defaultBlockState();
                states.put(pos.asLong(), state);
                shapes.put(pos.asLong(), state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
            }
        }
        PathSnapshotBuilder.ChunkDataCapture capture = new PathSnapshotBuilder.ChunkDataCapture(
                states, shapes, new PathSnapshotBuilder.SnapshotBounds(0, 0, 0, 1, 64, 64),
                DIMENSION, 0L, true);
        return PathSnapshotBuilder.buildFromCapture(capture,
                new BlockPos(0, 64, 0), new BlockPos(0, 64, 1));
    }
}
