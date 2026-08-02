package common.cn.kafei.simukraft.path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

class PathSnapshotBuilderSparseCaptureTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    /** missingEntriesRetainAirSemantics: verify sparse captures still treat omitted entries as air. */
    @Test
    void missingEntriesRetainAirSemantics() {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        BlockPos floor = new BlockPos(0, 63, 0);
        BlockState floorState = Blocks.STONE.defaultBlockState();
        states.put(floor.asLong(), floorState);
        shapes.put(floor.asLong(), floorState.getCollisionShape(EmptyBlockGetter.INSTANCE, floor));

        PathSnapshotBuilder.ChunkDataCapture capture = new PathSnapshotBuilder.ChunkDataCapture(
                states, shapes, new PathSnapshotBuilder.SnapshotBounds(0, 0, 0, 0, 64, 64),
                DIMENSION, 0L, true);
        PathSnapshot snapshot = PathSnapshotBuilder.buildFromCapture(capture,
                new BlockPos(0, 64, 0), new BlockPos(0, 64, 0));

        assertNotNull(snapshot.cell(0, 64, 0));
        assertTrue(snapshot.bodyPassage(0, 64, 0));
    }

    /** sectionBackedCaptureRetainsPathSemantics: 验证异步区段视图与直接快照判定一致。 */
    @Test
    void sectionBackedCaptureRetainsPathSemantics() {
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
        BlockPos floor = new BlockPos(0, 63, 0);
        BlockState floorState = Blocks.STONE.defaultBlockState();
        states.put(floor.asLong(), floorState);
        shapes.put(floor.asLong(), floorState.getCollisionShape(EmptyBlockGetter.INSTANCE, floor));
        Long2ObjectOpenHashMap<PathSnapshotBuilder.SectionDataCapture> sections = new Long2ObjectOpenHashMap<>();
        sections.put(SectionPos.asLong(0, 3, 0), new PathSnapshotBuilder.SectionDataCapture(states, shapes));

        PathSnapshotBuilder.ChunkDataCapture capture = new PathSnapshotBuilder.ChunkDataCapture(
                null, null, sections, new PathSnapshotBuilder.SnapshotBounds(0, 0, 0, 0, 64, 64),
                DIMENSION, 0L, true);
        PathSnapshot snapshot = PathSnapshotBuilder.buildFromCapture(capture,
                new BlockPos(0, 64, 0), new BlockPos(0, 64, 0));

        assertNotNull(snapshot.cell(0, 64, 0));
        assertTrue(snapshot.bodyPassage(0, 64, 0));
    }
}
