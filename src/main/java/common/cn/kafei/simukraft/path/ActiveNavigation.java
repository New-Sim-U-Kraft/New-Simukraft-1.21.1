package common.cn.kafei.simukraft.path;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/** Drives a single citizen's per-tick movement along an already-computed {@link PathResult}. */
final class ActiveNavigation {
    private static final double PASSED_WAYPOINT_DOT_EPSILON = 1.05D;
    private static final double PASSED_WAYPOINT_LATERAL_TOLERANCE = 0.45D;
    private static final double WALK_PASSED_VERTICAL_TOLERANCE = 1.0D;
    private static final double ACTION_PASSED_VERTICAL_TOLERANCE = 2.25D;
    private static final double TURN_DOT_THRESHOLD = 0.906D;
    private static final double STALLED_SOFT_SKIP_DISTANCE = 2.25D;
    private static final double ACTION_START_DISTANCE = 0.65D;
    private static final double CLIMB_VERTICAL_ARRIVAL_DISTANCE = 0.03D;
    private static final double CLIMB_VERTICAL_ASSIST_DISTANCE = 0.75D;
    private static final double CLIMB_VERTICAL_SPEED = 0.16D;
    private static final double CLIMB_VERTICAL_SPEED_FACTOR = 0.22D;
    private static final double CLIMB_EXIT_DETACH_HORIZONTAL_SPEED = 0.09D;
    private static final double CLIMB_EXIT_DROP_SPEED = -0.12D;
    private static final double CORNER_ARRIVAL_DISTANCE = 0.30D;
    private static final double SEGMENT_LOOKAHEAD_BLOCKS = 1.15D;
    private static final double CORNER_LOOKAHEAD_BLOCKS = 0.55D;
    private static final int STALLED_SOFT_SKIP_TICKS = 20;
    private static final int STALLED_REPATH_TICKS = 80;
    private static final int MAX_CROWD_YIELD_TICKS = 45;

    final Vec3 target;
    final MovementIntent intent;
    final List<PathWaypoint> waypoints;
    int waypointIndex;
    private int stalledTicks;
    private int crowdYieldTicks;
    private int actionWaypointIndex = -1;
    private boolean jumpTriggered;
    private double lastDistance = Double.MAX_VALUE;
    private final boolean[] turnFlags;
    private final double[] segmentLengths;

    ActiveNavigation(PathResult result) {
        this.target = result.target();
        this.intent = result.intent();
        this.waypoints = result.waypoints();
        this.waypointIndex = waypoints.size() > 1 ? 1 : 0;
        int n = waypoints.size();
        this.turnFlags = new boolean[n];
        for (int i = 1; i < n - 1; i++) {
            Vec3 prev = waypoints.get(i - 1).position();
            Vec3 cur  = waypoints.get(i).position();
            Vec3 nxt  = waypoints.get(i + 1).position();
            double inX = cur.x - prev.x, inZ = cur.z - prev.z;
            double outX = nxt.x - cur.x,  outZ = nxt.z - cur.z;
            double inLen = Math.sqrt(inX * inX + inZ * inZ);
            double outLen = Math.sqrt(outX * outX + outZ * outZ);
            if (inLen >= 1.0E-4D && outLen >= 1.0E-4D) {
                turnFlags[i] = (inX * outX + inZ * outZ) / (inLen * outLen) < TURN_DOT_THRESHOLD;
            }
        }
        this.segmentLengths = new double[n];
        for (int i = 1; i < n; i++) {
            Vec3 from = waypoints.get(i - 1).position();
            Vec3 to   = waypoints.get(i).position();
            double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
            this.segmentLengths[i] = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    boolean sameTarget(Vec3 other) {
        return other != null && target.distanceToSqr(other) <= 4.0D;
    }

    String debugStatus() {
        if (crowdYieldTicks > 0) {
            return "crowd_yield";
        }
        if (stalledTicks > STALLED_SOFT_SKIP_TICKS) {
            return "stalled";
        }
        return "running";
    }

    ActiveTickResult tick(ServerLevel level, CitizenEntity citizen, Map<Long, CitizenDoorService.OpenedDoor> openedDoors) {
        if (citizen.isSleeping()) {
            return ActiveTickResult.COMPLETE;
        }
        if (waypoints.isEmpty()) {
            return ActiveTickResult.COMPLETE;
        }
        citizen.getNavigation().stop();
        resetActionStateIfNeeded();
        advanceReachedWaypoints(citizen);
        if (waypointIndex >= waypoints.size()) {
            return ActiveTickResult.COMPLETE;
        }
        resetActionStateIfNeeded();
        PathWaypoint waypoint = waypoints.get(waypointIndex);
        double distance = citizen.position().distanceTo(waypoint.position());
        if (shouldAdvanceWaypoint(citizen, citizen.position(), waypointIndex, waypoint)) {
            advanceWaypoint();
            advanceReachedWaypoints(citizen);
            if (waypointIndex >= waypoints.size()) {
                return ActiveTickResult.COMPLETE;
            }
            resetActionStateIfNeeded();
            waypoint = waypoints.get(waypointIndex);
            distance = citizen.position().distanceTo(waypoint.position());
        }

        PathWaypoint commandWaypoint = waypoint;
        Vec3 commandTarget = commandTarget(citizen.position(), waypointIndex, waypoint, commandWaypoint);
        MovementMode commandMode = commandMode(commandTarget, commandWaypoint);
        CitizenDoorService.tryOpenWoodenDoor(level, citizen, waypoint, openedDoors);
        if (ClimbWaypointPolicy.isLandingAfterDescendingClimb(waypoints, waypointIndex)) {
            if (shouldApplyClimbExitDetach(citizen, waypoint)) {
                Vec3 detachDirection = ClimbWaypointPolicy.landingDetachDirection(waypoints, waypointIndex,
                        climbExitFallbackDirection(level, waypoints.get(waypointIndex - 1)));
                citizen.getMoveControl().setWantedPosition(citizen.getX(), citizen.getY(), citizen.getZ(), 0.0D);
                applyClimbExitDetach(citizen, detachDirection);
                stalledTicks = 0;
                lastDistance = distance;
                return ActiveTickResult.RUNNING;
            }
        }
        PathCrowdCoordinator.record(level, citizen.getUUID(), citizen.position(), commandTarget);
        boolean crowdYieldTimedOut = false;
        if (!isActionMode(waypoint.mode()) && PathCrowdCoordinator.shouldYield(level, citizen, commandTarget)) {
            crowdYieldTicks++;
            if (crowdYieldTicks <= MAX_CROWD_YIELD_TICKS) {
                Vec3 motion = citizen.getDeltaMovement();
                citizen.setDeltaMovement(motion.x * 0.2D, motion.y, motion.z * 0.2D);
                citizen.getMoveControl().setWantedPosition(citizen.getX(), citizen.getY(), citizen.getZ(), 0.0D);
                stalledTicks = 0;
                lastDistance = distance;
                return ActiveTickResult.RUNNING;
            }
            crowdYieldTimedOut = true;
        } else {
            crowdYieldTicks = 0;
        }

        if (lastDistance - distance > 0.04D) {
            stalledTicks = 0;
            lastDistance = distance;
        } else {
            stalledTicks++;
        }
        if (!isActionMode(waypoint.mode()) && !requiresWaypointCentering(waypointIndex, waypoint.mode()) && stalledTicks > STALLED_SOFT_SKIP_TICKS && distance <= STALLED_SOFT_SKIP_DISTANCE) {
            advanceWaypoint();
            return ActiveTickResult.RUNNING;
        }
        if (stalledTicks > STALLED_REPATH_TICKS) {
            return ActiveTickResult.REPATH;
        }

        double speed = speedFor(intent, commandMode);
        if (crowdYieldTimedOut) {
            speed *= 0.55D;
        }
        citizen.getMoveControl().setWantedPosition(commandTarget.x, commandTarget.y, commandTarget.z, speed);
        applyClimbMotion(citizen, commandTarget, commandMode);
        if (shouldTriggerJump(citizen, waypointIndex, waypoint)) {
            citizen.getJumpControl().jump();
            jumpTriggered = true;
        }
        level.getGameTime();
        return ActiveTickResult.RUNNING;
    }

    private void advanceReachedWaypoints(CitizenEntity citizen) {
        while (waypointIndex < waypoints.size()) {
            PathWaypoint waypoint = waypoints.get(waypointIndex);
            if (!shouldAdvanceWaypoint(citizen, citizen.position(), waypointIndex, waypoint)) {
                return;
            }
            advanceWaypoint();
        }
    }

    private void advanceWaypoint() {
        waypointIndex++;
        stalledTicks = 0;
        crowdYieldTicks = 0;
        lastDistance = Double.MAX_VALUE;
        actionWaypointIndex = -1;
        jumpTriggered = false;
    }

    private void resetActionStateIfNeeded() {
        if (actionWaypointIndex != waypointIndex) {
            actionWaypointIndex = waypointIndex;
            jumpTriggered = false;
        }
    }

    private boolean shouldAdvanceWaypoint(CitizenEntity citizen, Vec3 position, int index, PathWaypoint waypoint) {
        if (waypoint.mode() == MovementMode.CLIMB) {
            return ClimbWaypointPolicy.isReached(position, waypoints, index);
        }
        if (ClimbWaypointPolicy.isLandingAfterClimb(waypoints, index)) {
            return ClimbWaypointPolicy.isLandingReached(position, waypoints, index, citizen.onGround());
        }
        double arrivalDistance = arrivalDistance(index, waypoint.mode());
        if (position.distanceToSqr(waypoint.position()) <= arrivalDistance * arrivalDistance) {
            if (waypoint.mode() == MovementMode.JUMP && jumpRequiresLiftoff(index, waypoint) && (!jumpTriggered || !citizen.onGround())) {
                return false;
            }
            return true;
        }
        if (isActionMode(waypoint.mode())) {
            return false;
        }
        if (requiresWaypointCentering(index, waypoint.mode())) {
            return false;
        }
        return hasPassedWaypoint(position, index, waypoint);
    }

    private Vec3 commandTarget(Vec3 position, int index, PathWaypoint waypoint, PathWaypoint commandWaypoint) {
        if (waypoint.mode() == MovementMode.JUMP && index > 0 && !jumpTriggered && !isNearActionStart(position, index)) {
            return waypoints.get(index - 1).position();
        }
        if (waypoint.mode() == MovementMode.CLIMB) {
            return ClimbWaypointPolicy.commandTarget(position, waypoints, index);
        }
        if (!isActionMode(waypoint.mode()) && index > 0) {
            return segmentFollowTarget(position, index, waypoint);
        }
        return commandWaypoint.position();
    }

    private MovementMode commandMode(Vec3 commandTarget, PathWaypoint commandWaypoint) {
        if (isActionMode(commandWaypoint.mode()) && commandTarget != commandWaypoint.position()) {
            return MovementMode.WALK;
        }
        return commandWaypoint.mode();
    }

    private Vec3 segmentFollowTarget(Vec3 position, int index, PathWaypoint waypoint) {
        Vec3 from = waypoints.get(index - 1).position();
        Vec3 to = waypoint.position();
        double segmentX = to.x - from.x;
        double segmentY = to.y - from.y;
        double segmentZ = to.z - from.z;
        double segmentLengthSqr = segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ;
        if (segmentLengthSqr < 0.0001D) {
            return to;
        }
        double progress = ((position.x - from.x) * segmentX + (position.y - from.y) * segmentY + (position.z - from.z) * segmentZ) / segmentLengthSqr;
        double segmentLength = segmentLengths[index];
        double lookahead = requiresWaypointCentering(index, waypoint.mode()) ? CORNER_LOOKAHEAD_BLOCKS : SEGMENT_LOOKAHEAD_BLOCKS;
        double targetProgress = clamp(progress, 0.0D, 1.0D) + lookahead / segmentLength;
        targetProgress = clamp(targetProgress, 0.0D, 1.0D);
        return new Vec3(
                from.x + segmentX * targetProgress,
                from.y + segmentY * targetProgress,
                from.z + segmentZ * targetProgress
        );
    }

    private boolean shouldTriggerJump(CitizenEntity citizen, int index, PathWaypoint waypoint) {
        return waypoint.mode() == MovementMode.JUMP
                && index > 0
                && !jumpTriggered
                && citizen.onGround()
                && isNearActionStart(citizen.position(), index)
                && waypoint.position().y > waypoints.get(index - 1).position().y + 0.25D;
    }

    /**
     * Returns whether a JUMP waypoint actually needs a manual lift-off. A rise within the auto
     * step band ({@code <= 0.25}) never satisfies {@link #shouldTriggerJump}, so the body climbs
     * it by stepping and {@code jumpTriggered} stays false; gating advancement on that flag would
     * deadlock the citizen under the waypoint. Such tiny JUMP edges therefore advance on arrival
     * like a walk, while genuine jumps still wait for the jump to fire and the body to land.
     */
    private boolean jumpRequiresLiftoff(int index, PathWaypoint waypoint) {
        return index > 0 && waypoint.position().y - waypoints.get(index - 1).position().y > 0.25D;
    }

    /**
     * Vanilla only grants a mob its automatic ladder-climb boost when {@code horizontalCollision
     * && onClimbable()} (see {@code LivingEntity.handleRelativeFrictionAndCalculateMovement}) —
     * i.e. the entity must be actively pushing into a wall-mounted ladder. A citizen centred on
     * the ladder column via {@code MoveControl.setWantedPosition} has near-zero horizontal delta
     * and never triggers that collision, so vanilla's climb assist never fires. This directly
     * drives the vertical component (leaving X/Z from vanilla's own tick untouched) as the only
     * way to move a path-driven mob up/down a ladder at all.
     */
    private void applyClimbMotion(CitizenEntity citizen, Vec3 commandTarget, MovementMode commandMode) {
        if (commandMode != MovementMode.CLIMB) {
            return;
        }
        double dx = commandTarget.x - citizen.getX();
        double dz = commandTarget.z - citizen.getZ();
        double horizontalSqr = dx * dx + dz * dz;
        if (horizontalSqr > CLIMB_VERTICAL_ASSIST_DISTANCE * CLIMB_VERTICAL_ASSIST_DISTANCE) {
            return;
        }
        double dy = commandTarget.y - citizen.getY();
        if (Math.abs(dy) <= CLIMB_VERTICAL_ARRIVAL_DISTANCE) {
            return;
        }
        Vec3 motion = citizen.getDeltaMovement();
        double verticalSpeed = Math.max(-CLIMB_VERTICAL_SPEED, Math.min(CLIMB_VERTICAL_SPEED, dy * CLIMB_VERTICAL_SPEED_FACTOR));
        citizen.setDeltaMovement(motion.x, verticalSpeed, motion.z);
        citizen.fallDistance = 0.0F;
    }

    /** applyClimbExitDetach: 离梯下落阶段只轻微离墙并压低竖直速度，避免触发爬梯上行。 */
    private void applyClimbExitDetach(CitizenEntity citizen, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 motion = citizen.getDeltaMovement();
        citizen.setDeltaMovement(
                direction.x * CLIMB_EXIT_DETACH_HORIZONTAL_SPEED,
                Math.min(motion.y, CLIMB_EXIT_DROP_SPEED),
                direction.z * CLIMB_EXIT_DETACH_HORIZONTAL_SPEED);
        citizen.fallDistance = 0.0F;
    }

    /** shouldApplyClimbExitDetach: 只在未落地的陆地下梯阶段施加脱离力。 */
    private boolean shouldApplyClimbExitDetach(CitizenEntity citizen, PathWaypoint waypoint) {
        return waypoint.mode() != MovementMode.SWIM && !citizen.onGround();
    }

    /** climbExitFallbackDirection: 路径没有水平出口时，真实梯子才按方块朝向兜底离墙。 */
    private Vec3 climbExitFallbackDirection(ServerLevel level, PathWaypoint climbWaypoint) {
        BlockState state = level.getBlockState(climbWaypoint.blockPos());
        if (state.getBlock() instanceof LadderBlock && state.hasProperty(LadderBlock.FACING)) {
            net.minecraft.core.Direction facing = state.getValue(LadderBlock.FACING);
            return new Vec3(facing.getStepX(), 0.0D, facing.getStepZ());
        }
        return Vec3.ZERO;
    }

    private boolean isNearActionStart(Vec3 position, int index) {
        if (index <= 0) {
            return true;
        }
        Vec3 start = waypoints.get(index - 1).position();
        double dx = position.x - start.x;
        double dz = position.z - start.z;
        return dx * dx + dz * dz <= ACTION_START_DISTANCE * ACTION_START_DISTANCE
                && Math.abs(position.y - start.y) <= 0.75D;
    }

    private boolean isActionMode(MovementMode mode) {
        return mode == MovementMode.JUMP || mode == MovementMode.SWIM || mode == MovementMode.CLIMB || mode == MovementMode.FALL;
    }

    private boolean hasPassedWaypoint(Vec3 position, int index, PathWaypoint waypoint) {
        if (index <= 0) {
            return false;
        }
        Vec3 from = waypoints.get(index - 1).position();
        Vec3 to = waypoint.position();
        double segmentX = to.x - from.x;
        double segmentZ = to.z - from.z;
        double segmentLengthSqr = segmentX * segmentX + segmentZ * segmentZ;
        double verticalTolerance = isActionMode(waypoint.mode())
                ? ACTION_PASSED_VERTICAL_TOLERANCE
                : WALK_PASSED_VERTICAL_TOLERANCE;
        if (segmentLengthSqr < 0.0001D || Math.abs(position.y - to.y) > verticalTolerance) {
            return false;
        }
        double progressX = position.x - from.x;
        double progressZ = position.z - from.z;
        double projection = (progressX * segmentX + progressZ * segmentZ) / segmentLengthSqr;
        if (projection < PASSED_WAYPOINT_DOT_EPSILON) {
            return false;
        }
        double closestX = from.x + segmentX * projection;
        double closestZ = from.z + segmentZ * projection;
        double lateralX = position.x - closestX;
        double lateralZ = position.z - closestZ;
        double lateralSqr = lateralX * lateralX + lateralZ * lateralZ;
        if (lateralSqr <= PASSED_WAYPOINT_LATERAL_TOLERANCE * PASSED_WAYPOINT_LATERAL_TOLERANCE) {
            return true;
        }
        return false;
    }

    private boolean requiresWaypointCentering(int index, MovementMode mode) {
        return !isActionMode(mode) && isTurnWaypoint(index);
    }

    /**
     * Returns whether the path bends at this waypoint by more than the corner threshold.
     *
     * <p>The test uses the actual waypoint positions and the angle between the incoming and
     * outgoing segments, so it still detects same-quadrant bends that survive smoothing (e.g.
     * a {@code (+3,+1)} segment into a {@code (+1,+3)} segment), which the previous
     * sign-comparison missed. The first and last waypoints are never treated as turns: the last
     * one keeps the looser arrival tolerance so the citizen is not forced to centre exactly on
     * the goal cell.
     */
    private boolean isTurnWaypoint(int index) {
        return index > 0 && index < waypoints.size() - 1 && turnFlags[index];
    }

    private double arrivalDistance(int index, MovementMode mode) {
        if (requiresWaypointCentering(index, mode)) {
            return CORNER_ARRIVAL_DISTANCE;
        }
        return switch (mode) {
            case CLIMB, SWIM -> 1.15D;
            case JUMP, FALL -> 1.05D;
            default -> 0.72D;
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double speedFor(MovementIntent intent, MovementMode mode) {
        if (mode == MovementMode.CLIMB || mode == MovementMode.SWIM) {
            return 0.9D;
        }
        if (mode == MovementMode.RUN || intent == MovementIntent.RUN || intent == MovementIntent.RETURN_HOME) {
            return 1.2D;
        }
        if (intent == MovementIntent.WORK || intent == MovementIntent.SELF_FEEDING) {
            return 1.0D;
        }
        return 0.85D;
    }
}
