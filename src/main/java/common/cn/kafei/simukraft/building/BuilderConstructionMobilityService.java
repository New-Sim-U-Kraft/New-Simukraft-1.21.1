package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

@SuppressWarnings("null")
public final class BuilderConstructionMobilityService {
    private static final double READY_DISTANCE_SQR = 9.0D;

    private BuilderConstructionMobilityService() {
    }

    // prepareForConstruction: old builder start behavior; near starts directly, far teleports back.
    public static void prepareForConstruction(ServerLevel level, UUID citizenId, BlockPos buildBoxPos) {
        if (level == null || citizenId == null || buildBoxPos == null) {
            return;
        }
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizenId);
        if (entity == null) {
            return;
        }
        Vec3 target = Vec3.atBottomCenterOf(buildBoxPos).add(0.0D, 1.0D, 0.0D);
        CitizenNavigationService.stop(level, citizenId);
        entity.getNavigation().stop();
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity.position().distanceToSqr(target) <= READY_DISTANCE_SQR) {
            return;
        }
        CitizenTeleportService.teleportCitizen(level, citizenId, target);
    }
}
