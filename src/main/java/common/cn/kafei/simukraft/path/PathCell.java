package common.cn.kafei.simukraft.path;

import net.minecraft.core.BlockPos;

public record PathCell(BlockPos pos, int x, int y, int z, double standY, boolean water, boolean climbable,
                       boolean woodenDoor, boolean floorSupported, double cost) {
    /** PathCell: 兼容未区分梯子下方支撑面的旧调用，默认视为无支撑。 */
    public PathCell(BlockPos pos, int x, int y, int z, double standY, boolean water, boolean climbable,
                    boolean woodenDoor, double cost) {
        this(pos, x, y, z, standY, water, climbable, woodenDoor, false, cost);
    }

    public long key() {
        return key(x, y, z);
    }

    public MovementMode defaultMode(MovementIntent intent) {
        if (water) {
            return MovementMode.SWIM;
        }
        if (climbable) {
            return MovementMode.CLIMB;
        }
        return intent == MovementIntent.RUN ? MovementMode.RUN : MovementMode.WALK;
    }

    public static long key(BlockPos pos) {
        return key(pos.getX(), pos.getY(), pos.getZ());
    }

    public static long key(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }
}
