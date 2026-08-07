package common.cn.kafei.simukraft.job;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.citizen.CitizenWorkplaceMoveService;
import common.cn.kafei.simukraft.city.CityRuntimeService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.medical.MedicalService;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.UUID;

@SuppressWarnings("null")
public final class CityJobMobilityService {
    private CityJobMobilityService() {
    }

    /** resolveHireRole: 将雇佣界面岗位标识映射为统一职业枚举。 */
    public static CityJobType resolveHireRole(String role) {
        if (role == null || role.isBlank()) {
            return CityJobType.OTHER;
        }
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "builder" -> CityJobType.BUILDER;
            case "planner" -> CityJobType.PLANNER;
            case "farmer" -> CityJobType.FARMER;
            case "guard" -> CityJobType.GUARD;
            case "gatherer" -> CityJobType.GATHERER;
            case "commercial_worker", "commercial" -> CityJobType.COMMERCIAL_WORKER;
            case "industrial_worker", "industrial", MineralDrillingConstants.HIRE_ROLE -> CityJobType.INDUSTRIAL_WORKER;
            case "logistics_worker", "logistics" -> CityJobType.LOGISTICS_WORKER;
            case "storage_worker", "storage" -> CityJobType.STORAGE_WORKER;
            case "doctor" -> CityJobType.DOCTOR;
            default -> CityJobType.OTHER;
        };
    }

    /** sendToWorkplace：雇佣入职时移动 NPC 到岗位，根据距离选择传送或寻路。 */
    public static void sendToWorkplace(ServerLevel level, UUID citizenId, BlockPos workplacePos, CityJobType jobType, CitizenWorkStatus workStatus, String statusLabel) {
        if (level == null || citizenId == null || workplacePos == null) {
            return;
        }
        CitizenData citizenData = CitizenService.findCitizen(level, citizenId).orElse(null);
        if (MedicalService.isOnMedicalLeave(citizenData, level.getDayTime() / 24_000L)) {
            return;
        }
        CitizenEntity citizenEntity = findCitizenEntity(level, citizenId);
        if (citizenEntity == null) {
            CitizenService.findCitizen(level, citizenId)
                    .ifPresent(citizen -> CityRuntimeService.requestCitizenRecovery(level, citizen));
            SimuKraft.LOGGER.debug("Simukraft: sendToWorkplace - queued recovery for citizen {} at workplace {}", citizenId, workplacePos);
            return;
        }
        Vec3 target = (jobType == CityJobType.COMMERCIAL_WORKER
                ? CitizenWorkplaceMoveService.targetAdjacentToWorkplace(level, workplacePos)
                : CitizenWorkplaceMoveService.targetNearWorkplace(level, workplacePos))
                .orElse(Vec3.atBottomCenterOf(workplacePos.above()));
        boolean selfFeeding = CitizenSelfFeedingService.isSelfFeeding(level, citizenId);
        if (!selfFeeding) {
            CitizenNavigationService.stop(level, citizenId);
            double threshold = ServerConfig.pathFarMovementTeleportDistance();
            if (citizenEntity.position().distanceToSqr(target) >= threshold * threshold) {
                teleportToTarget(level, citizenId, target);
            } else {
                pathfindToTarget(level, citizenId, target);
            }
        }
        String effectiveStatusLabel = selfFeeding
                ? CitizenSelfFeedingService.effectiveStatusLabel(level, citizenId, statusLabel)
                : statusLabel;
        syncCitizenEntityState(citizenEntity, jobType, workStatus, effectiveStatusLabel);
    }

    /** teleportToTarget：直接传送 NPC 到目标位置。 */
    private static void teleportToTarget(ServerLevel level, UUID citizenId, Vec3 target) {
        CitizenTeleportService.teleportCitizen(level, citizenId, target);
    }

    /** pathfindToTarget：寻路到目标位置，寻路失败时传送兜底。 */
    private static void pathfindToTarget(ServerLevel level, UUID citizenId, Vec3 target) {
        if (!CitizenNavigationService.requestMove(level, citizenId, target, MovementIntent.WORK)) {
            CitizenTeleportService.teleportCitizen(level, citizenId, target);
        }
    }

    /** teleportCitizenToWorkplace：保留旧方法名供现有调用点兼容，委托给 sendToWorkplace。 */
    public static void teleportCitizenToWorkplace(ServerLevel level, UUID citizenId, BlockPos workplacePos, CityJobType jobType, CitizenWorkStatus workStatus, String statusLabel) {
        sendToWorkplace(level, citizenId, workplacePos, jobType, workStatus, statusLabel);
    }

    public static void resetCitizenAfterFire(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return;
        }
        CitizenEntity citizenEntity = CitizenTeleportService.findCitizenEntity(level, citizenId);
        if (citizenEntity == null) {
            return;
        }
        CitizenNavigationService.stop(level, citizenId);
        citizenEntity.getNavigation().stop();
        citizenEntity.setDeltaMovement(Vec3.ZERO);
        syncCitizenEntityState(citizenEntity, CityJobType.UNEMPLOYED, CitizenWorkStatus.IDLE, "");
    }

    public static void syncCitizenEntityState(CitizenEntity citizenEntity, CityJobType jobType, CitizenWorkStatus workStatus, String statusLabel) {
        if (citizenEntity == null) {
            return;
        }
        citizenEntity.setJob((jobType != null ? jobType : CityJobType.OTHER).name().toLowerCase(Locale.ROOT));
        citizenEntity.setWorkStatus((workStatus != null ? workStatus : CitizenWorkStatus.WORKING).translationKey());
        citizenEntity.setStatusLabel(statusLabel != null ? statusLabel : "");
    }

    private static CitizenEntity findCitizenEntity(ServerLevel level, UUID citizenId) {
        return CitizenTeleportService.findCitizenEntity(level, citizenId);
    }
}
