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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

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
