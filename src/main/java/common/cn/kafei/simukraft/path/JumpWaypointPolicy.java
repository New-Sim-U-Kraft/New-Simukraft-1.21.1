package common.cn.kafei.simukraft.path;

import net.minecraft.world.phys.Vec3;

/** 为一格高跳跃节点计算稳定的起跳预备点。 */
final class JumpWaypointPolicy {
    private static final double BLOCK_EDGE_DISTANCE = 0.5D;
    private static final double LIP_CLEARANCE = 0.05D;
    private static final double MIN_LAUNCH_DISTANCE = 0.05D;
    private static final double LAUNCH_ARRIVAL_TOLERANCE = 0.025D;
    private static final double MAX_LATERAL_OFFSET = 0.20D;
    private static final double MAX_FLUID_LATERAL_OFFSET = 0.45D;
    private static final double MAX_VERTICAL_OFFSET = 0.75D;
    private static final double MIN_HORIZONTAL_LENGTH = 1.0E-4D;

    private JumpWaypointPolicy() {
    }

    /** launchTarget: 返回台阶边缘前的水平起跳预备点，避免连续台阶在前一格中心提前起跳。 */
    static Vec3 launchTarget(PathWaypoint start, PathWaypoint landing, double bodyWidth) {
        Vec3 startPosition = start.position();
        Vec3 landingPosition = landing.position();
        double dx = landingPosition.x - startPosition.x;
        double dz = landingPosition.z - startPosition.z;
        double horizontalLength = Math.sqrt(dx * dx + dz * dz);
        if (horizontalLength < MIN_HORIZONTAL_LENGTH) {
            return startPosition;
        }
        double distance = launchDistance(horizontalLength, bodyWidth);
        double factor = distance / horizontalLength;
        return new Vec3(
                startPosition.x + dx * factor,
                startPosition.y,
                startPosition.z + dz * factor);
    }

    /** isAtLaunchPoint: 判断 NPC 是否已沿当前跳跃方向走到可安全起跳的位置。 */
    static boolean isAtLaunchPoint(Vec3 position, PathWaypoint start, PathWaypoint landing, double bodyWidth) {
        return isAtLaunchPoint(position, start, landing, bodyWidth, MAX_LATERAL_OFFSET);
    }

    /** isAtFluidLaunchPoint: 水流横向偏移较大时放宽对齐范围，仍要求先推进到台阶边缘。 */
    static boolean isAtFluidLaunchPoint(Vec3 position, PathWaypoint start, PathWaypoint landing, double bodyWidth) {
        return isAtLaunchPoint(position, start, landing, bodyWidth, MAX_FLUID_LATERAL_OFFSET);
    }

    private static boolean isAtLaunchPoint(Vec3 position,
                                           PathWaypoint start,
                                           PathWaypoint landing,
                                           double bodyWidth,
                                           double maxLateralOffset) {
        if (position == null || start == null || landing == null) {
            return false;
        }
        Vec3 startPosition = start.position();
        Vec3 landingPosition = landing.position();
        double dx = landingPosition.x - startPosition.x;
        double dz = landingPosition.z - startPosition.z;
        double horizontalLength = Math.sqrt(dx * dx + dz * dz);
        if (horizontalLength < MIN_HORIZONTAL_LENGTH) {
            return Math.abs(position.y - startPosition.y) <= MAX_VERTICAL_OFFSET;
        }
        double unitX = dx / horizontalLength;
        double unitZ = dz / horizontalLength;
        double relativeX = position.x - startPosition.x;
        double relativeZ = position.z - startPosition.z;
        double forwardDistance = relativeX * unitX + relativeZ * unitZ;
        double lateralDistance = Math.abs(relativeX * unitZ - relativeZ * unitX);
        return forwardDistance + LAUNCH_ARRIVAL_TOLERANCE >= launchDistance(horizontalLength, bodyWidth)
                && lateralDistance <= maxLateralOffset
                && Math.abs(position.y - startPosition.y) <= MAX_VERTICAL_OFFSET;
    }

    /** launchDistance: 按实体碰撞箱宽度预留台阶边缘安全距离。 */
    private static double launchDistance(double horizontalLength, double bodyWidth) {
        double safeBodyWidth = Math.max(0.0D, bodyWidth);
        double desiredDistance = BLOCK_EDGE_DISTANCE - safeBodyWidth * 0.5D - LIP_CLEARANCE;
        double maxDistance = horizontalLength * 0.5D;
        return Math.min(maxDistance, Math.max(MIN_LAUNCH_DISTANCE, desiredDistance));
    }
}
