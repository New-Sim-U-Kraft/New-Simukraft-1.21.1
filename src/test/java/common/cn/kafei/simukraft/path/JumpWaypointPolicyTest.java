package common.cn.kafei.simukraft.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class JumpWaypointPolicyTest {
    @Test
    void consecutiveStepsDoNotLaunchFromPreviousLandingCenter() {
        PathWaypoint firstLanding = waypoint(1, 65, 0, MovementMode.JUMP);
        PathWaypoint secondLanding = waypoint(2, 66, 0, MovementMode.JUMP);

        assertFalse(JumpWaypointPolicy.isAtLaunchPoint(firstLanding.position(), firstLanding, secondLanding, 0.6D),
                "连续台阶的第二跳不能在上一格中心立即触发");

        Vec3 launchTarget = JumpWaypointPolicy.launchTarget(firstLanding, secondLanding, 0.6D);
        assertEquals(1.65D, launchTarget.x, 1.0E-6D);
        assertEquals(65.0D, launchTarget.y, 1.0E-6D);
        assertEquals(0.5D, launchTarget.z, 1.0E-6D);
        assertTrue(JumpWaypointPolicy.isAtLaunchPoint(launchTarget, firstLanding, secondLanding, 0.6D),
                "到达台阶边缘前的预备点后才允许起跳");
    }

    @Test
    void launchPointFollowsReverseDirection() {
        PathWaypoint start = waypoint(2, 65, 0, MovementMode.WALK);
        PathWaypoint landing = waypoint(1, 66, 0, MovementMode.JUMP);

        Vec3 launchTarget = JumpWaypointPolicy.launchTarget(start, landing, 0.6D);

        assertEquals(2.35D, launchTarget.x, 1.0E-6D);
        assertTrue(JumpWaypointPolicy.isAtLaunchPoint(launchTarget, start, landing, 0.6D));
    }

    @Test
    void lateralDriftMustBeCorrectedBeforeLaunch() {
        PathWaypoint start = waypoint(0, 65, 0, MovementMode.WALK);
        PathWaypoint landing = waypoint(1, 66, 0, MovementMode.JUMP);

        assertFalse(JumpWaypointPolicy.isAtLaunchPoint(new Vec3(0.65D, 65.0D, 0.75D), start, landing, 0.6D),
                "偏离台阶中心线时不能直接起跳");
    }

    @Test
    void fluidLaunchRetainsLateralDriftTolerance() {
        PathWaypoint start = waypoint(0, 65, 0, MovementMode.SWIM);
        PathWaypoint landing = waypoint(1, 66, 0, MovementMode.JUMP);

        assertTrue(JumpWaypointPolicy.isAtFluidLaunchPoint(new Vec3(0.65D, 65.0D, 0.85D), start, landing, 0.6D),
                "水流带来的横向偏移不能阻止 NPC 从水面跳上岸");
    }

    private static PathWaypoint waypoint(int x, int y, int z, MovementMode mode) {
        return new PathWaypoint(new BlockPos(x, y, z), new Vec3(x + 0.5D, y, z + 0.5D), mode);
    }
}
