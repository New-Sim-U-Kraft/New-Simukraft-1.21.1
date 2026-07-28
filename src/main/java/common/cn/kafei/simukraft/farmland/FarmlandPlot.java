package common.cn.kafei.simukraft.farmland;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 农田作业区域：一个以作物平面 Y 为基准的矩形。min/max 已归一化（min <= max 各分量）。
 * 该记录不可变，区域变更时整体替换，避免外部持有旧引用读到脏数据。
 */
public record FarmlandPlot(BlockPos min, BlockPos max) {
    public FarmlandPlot {
        BlockPos normalizedMin = new BlockPos(
                Math.min(min.getX(), max.getX()),
                Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ()));
        BlockPos normalizedMax = new BlockPos(
                Math.max(min.getX(), max.getX()),
                Math.max(min.getY(), max.getY()),
                Math.max(min.getZ(), max.getZ()));
        min = normalizedMin;
        max = normalizedMax;
    }

    public static FarmlandPlot square(BlockPos boxPos, int radius) {
        int clampedRadius = Math.max(1, radius);
        int cropY = boxPos.getY();
        return new FarmlandPlot(
                new BlockPos(boxPos.getX() - clampedRadius, cropY, boxPos.getZ() - clampedRadius),
                new BlockPos(boxPos.getX() + clampedRadius, cropY, boxPos.getZ() + clampedRadius));
    }

    public int width() {
        return max.getX() - min.getX() + 1;
    }

    public int depth() {
        return max.getZ() - min.getZ() + 1;
    }

    // 区域里的格子总数（含中心方块列，工作时会显式跳过箱子所在列）。
    public int cellCount() {
        return width() * depth();
    }

    // 把线性游标映射成区域格子，S型蛇形扫描：奇数行反向，减少农民来回空走。
    public BlockPos cellAt(int index) {
        int total = Math.max(1, cellCount());
        int normalized = Math.floorMod(index, total);
        int localZ = normalized / width();
        int localX = normalized % width();
        if (localZ % 2 == 1) {
            localX = width() - 1 - localX;
        }
        return new BlockPos(min.getX() + localX, min.getY(), min.getZ() + localZ);
    }

    public boolean containsColumn(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public boolean intersects(FarmlandPlot other) {
        return other != null
                && min.getX() <= other.max.getX() && max.getX() >= other.min.getX()
                && min.getY() <= other.max.getY() && max.getY() >= other.min.getY()
                && min.getZ() <= other.max.getZ() && max.getZ() >= other.min.getZ();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Min", min.asLong());
        tag.putLong("Max", max.asLong());
        return tag;
    }

    public static FarmlandPlot fromTag(CompoundTag tag) {
        if (tag == null || !tag.contains("Min") || !tag.contains("Max")) {
            return null;
        }
        return new FarmlandPlot(BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")));
    }
}
