package common.cn.kafei.simukraft.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PathResultRebindingTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void cachedResultIsBoundToTheConsumingRequest() {
        UUID originalCitizen = UUID.randomUUID();
        UUID consumingCitizen = UUID.randomUUID();
        PathRequest original = new PathRequest(originalCitizen, DIMENSION,
                new BlockPos(0, 64, 0), new Vec3(8.25D, 64.0D, 8.75D), MovementIntent.WORK, 1L);
        PathRequest consuming = new PathRequest(consumingCitizen, DIMENSION,
                new BlockPos(0, 64, 0), new Vec3(8.75D, 64.0D, 8.25D), MovementIntent.WORK, 2L);
        List<PathWaypoint> waypoints = List.of(
                new PathWaypoint(new BlockPos(0, 64, 0), new Vec3(0.5D, 64.0D, 0.5D), MovementMode.WALK));

        PathResult cached = PathResult.success(original, waypoints);
        PathResult rebound = cached.forRequest(consuming);

        assertEquals(consumingCitizen, rebound.citizenId());
        assertEquals(consuming.target(), rebound.target());
        assertEquals(consuming.intent(), rebound.intent());
        assertSame(cached.waypoints(), rebound.waypoints());
    }
}
