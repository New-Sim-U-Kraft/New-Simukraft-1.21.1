package common.cn.kafei.simukraft.farmland;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings("null")
final class FarmlandWorkGeometry {
    static final double ACTION_REACH = 2.4D;
    private static final int WATER_STRIDE = 4;
    private static final int WATER_TROUGH_INDEX = 3;
    private static final int[][] STAND_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}
    };

    private FarmlandWorkGeometry() {
    }

    // isWaterTrough：按作物区域 Z 方向每三行留一条横向水槽。
    static boolean isWaterTrough(FarmlandPlot plot, BlockPos cropPos) {
        return Math.floorMod(cropPos.getZ() - plot.min().getZ(), WATER_STRIDE) == WATER_TROUGH_INDEX;
    }

    // groupedFarmCellCount：统计非水槽作物格，供分组游标循环扫描。
    static int groupedFarmCellCount(FarmlandPlot plot) {
        int farmRows = 0;
        for (int localZ = 0; localZ < plot.depth(); localZ++) {
            if (!isWaterLocalZ(localZ)) {
                farmRows++;
            }
        }
        return Math.max(1, farmRows * plot.width());
    }

    // groupedFarmCellAt：先完成当前水槽分隔出的作业块，块内S型蛇形扫描（奇数行反向），减少来回空走。
    static BlockPos groupedFarmCellAt(FarmlandPlot plot, int index) {
        int total = groupedFarmCellCount(plot);
        int remaining = Math.floorMod(index, total);
        int groups = Math.max(1, (plot.depth() + WATER_STRIDE - 1) / WATER_STRIDE);
        for (int group = 0; group < groups; group++) {
            int groupStartZ = group * WATER_STRIDE;
            int groupRows = Math.max(0, Math.min(WATER_TROUGH_INDEX, plot.depth() - groupStartZ));
            int groupCells = groupRows * plot.width();
            if (groupCells <= 0) {
                continue;
            }
            if (remaining < groupCells) {
                int rowInGroup = remaining / plot.width();
                int localX = remaining % plot.width();
                if (rowInGroup % 2 == 1) {
                    localX = plot.width() - 1 - localX;
                }
                int localZ = groupStartZ + rowInGroup;
                return new BlockPos(plot.min().getX() + localX, plot.min().getY(), plot.min().getZ() + localZ);
            }
            remaining -= groupCells;
        }
        return plot.cellAt(index);
    }

    static Vec3 workAnchorFor(ServerLevel level, BlockPos boxPos, BlockPos cropPos) {
        for (int[] offset : STAND_OFFSETS) {
            BlockPos feet = new BlockPos(cropPos.getX() + offset[0], cropPos.getY(), cropPos.getZ() + offset[1]);
            if (isSafeStandPos(level, feet)) {
                return Vec3.atBottomCenterOf(feet);
            }
        }
        BlockPos boxStand = boxPos.above();
        if (isSafeStandPos(level, boxStand)
                && Vec3.atBottomCenterOf(boxStand).distanceToSqr(Vec3.atCenterOf(cropPos)) <= ACTION_REACH * ACTION_REACH) {
            return Vec3.atBottomCenterOf(boxStand);
        }
        return Vec3.atBottomCenterOf(cropPos);
    }

    private static boolean isSafeStandPos(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockState foot = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        BlockState below = level.getBlockState(feet.below());
        if (below.is(Blocks.FARMLAND) || foot.getFluidState().is(FluidTags.LAVA) || head.getFluidState().is(FluidTags.LAVA)) {
            return false;
        }
        return isBodyPassable(level, feet, foot)
                && isBodyPassable(level, feet.above(), head)
                && !below.getCollisionShape(level, feet.below()).isEmpty();
    }

    private static boolean isBodyPassable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isWaterLocalZ(int localZ) {
        return Math.floorMod(localZ, WATER_STRIDE) == WATER_TROUGH_INDEX;
    }
}
