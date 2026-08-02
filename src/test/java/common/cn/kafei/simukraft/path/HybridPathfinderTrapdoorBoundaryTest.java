package common.cn.kafei.simukraft.path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class HybridPathfinderTrapdoorBoundaryTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void directionalThinWallBlocksTheCrossingEdge() {
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos target = new BlockPos(0, 64, 1);
        Long2ObjectOpenHashMap<PathCell> cells = new Long2ObjectOpenHashMap<>();
        cells.put(PathCell.key(start), new PathCell(start, 0, 64, 0, 64.0D, false, false, false, 1.0D));
        cells.put(PathCell.key(target), new PathCell(target, 0, 64, 1, 64.0D, false, false, false, 1.0D));
        LongOpenHashSet passages = new LongOpenHashSet();
        passages.add(start.asLong());
        passages.add(target.asLong());
        Long2ByteOpenHashMap barriers = new Long2ByteOpenHashMap();
        barriers.put(start.asLong(), PathSnapshot.barrierMask(0, 1));
        PathSnapshot snapshot = new PathSnapshot(DIMENSION, start, target, cells,
                LongSets.unmodifiable(passages), Long2ByteMaps.unmodifiable(barriers),
                64, 64, 0L, true);
        PathRequest request = new PathRequest(UUID.randomUUID(), DIMENSION, start,
                new Vec3(0.5D, 64.0D, 1.5D), MovementIntent.WALK, 0L);

        assertFalse(HybridPathfinder.find(request, snapshot).success());
    }

    @Test
    void thinWallForcesDiagonalRoutesToGoAroundItsCorner() {
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos target = new BlockPos(1, 64, 1);
        Long2ObjectOpenHashMap<PathCell> cells = new Long2ObjectOpenHashMap<>();
        addFloorCell(cells, 0, 64, 0);
        addFloorCell(cells, 1, 64, 0);
        addFloorCell(cells, 0, 64, 1);
        addFloorCell(cells, 1, 64, 1);
        LongOpenHashSet passages = new LongOpenHashSet();
        cells.values().forEach(cell -> passages.add(cell.pos().asLong()));
        Long2ByteOpenHashMap barriers = new Long2ByteOpenHashMap();
        barriers.put(start.asLong(), PathSnapshot.barrierMask(1, 0));
        PathSnapshot snapshot = new PathSnapshot(DIMENSION, start, target, cells,
                LongSets.unmodifiable(passages), Long2ByteMaps.unmodifiable(barriers),
                64, 64, 0L, true);
        PathRequest request = new PathRequest(UUID.randomUUID(), DIMENSION, start,
                new Vec3(1.5D, 64.0D, 1.5D), MovementIntent.WALK, 0L);

        var result = HybridPathfinder.find(request, snapshot);

        assertTrue(result.success());
        assertTrue(result.waypoints().size() > 2,
                "a route must turn around a thin-wall corner instead of cutting through it diagonally");
    }

    private static void addFloorCell(Long2ObjectOpenHashMap<PathCell> cells, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        cells.put(pos.asLong(), new PathCell(pos, x, y, z, y, false, false, false, 1.0D));
    }
}
