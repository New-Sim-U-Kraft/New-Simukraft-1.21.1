package common.cn.kafei.simukraft.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

public record PathSnapshot(ResourceLocation dimensionId, BlockPos startPos, BlockPos targetPos, Long2ObjectOpenHashMap<PathCell> cells,
                           LongSet bodyPassages, Long2ByteMap horizontalBarriers,
                           int minY, int maxY, long createdAt, boolean complete) {
    public PathSnapshot(ResourceLocation dimensionId, BlockPos startPos, BlockPos targetPos, Long2ObjectOpenHashMap<PathCell> cells,
                        int minY, int maxY, long createdAt, boolean complete) {
        this(dimensionId, startPos, targetPos, cells, LongSets.EMPTY_SET, Long2ByteMaps.EMPTY_MAP,
                minY, maxY, createdAt, complete);
    }

    public PathSnapshot(ResourceLocation dimensionId, BlockPos startPos, BlockPos targetPos, Long2ObjectOpenHashMap<PathCell> cells,
                        LongSet bodyPassages, int minY, int maxY, long createdAt, boolean complete) {
        this(dimensionId, startPos, targetPos, cells, bodyPassages, Long2ByteMaps.EMPTY_MAP,
                minY, maxY, createdAt, complete);
    }

    public PathCell cell(BlockPos pos) {
        return cells.get(PathCell.key(pos));
    }

    public PathCell cell(int x, int y, int z) {
        return cells.get(PathCell.key(x, y, z));
    }

    public Collection<PathCell> allCells() {
        return cells.values();
    }

    public boolean contains(BlockPos pos) {
        return cells.containsKey(PathCell.key(pos));
    }

    public boolean bodyPassage(int x, int y, int z) {
        return bodyPassages.contains(BlockPos.asLong(x, y, z));
    }

    /** Returns whether a horizontal move crosses a captured thin wall, such as an open trapdoor. */
    public boolean blocksHorizontalBoundary(int fromX, int y, int fromZ, int toX, int toZ) {
        int dx = Integer.compare(toX - fromX, 0);
        int dz = Integer.compare(toZ - fromZ, 0);
        if (Math.abs(toX - fromX) + Math.abs(toZ - fromZ) != 1) {
            return false;
        }
        byte forward = barrierMask(dx, dz);
        byte backward = barrierMask(-dx, -dz);
        return (horizontalBarriers.get(PathCell.key(fromX, y, fromZ)) & forward) != 0
                || (horizontalBarriers.get(PathCell.key(toX, y, toZ)) & backward) != 0;
    }

    static byte barrierMask(Direction direction) {
        return barrierMask(direction.getStepX(), direction.getStepZ());
    }

    static byte barrierMask(int dx, int dz) {
        if (dx == 0 && dz < 0) return 1;
        if (dx == 0 && dz > 0) return 2;
        if (dx < 0 && dz == 0) return 4;
        if (dx > 0 && dz == 0) return 8;
        return 0;
    }
}
