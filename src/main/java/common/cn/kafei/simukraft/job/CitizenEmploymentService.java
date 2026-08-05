package common.cn.kafei.simukraft.job;

import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenHomeRestService;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.commercial.CommercialConstants;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxService;
import common.cn.kafei.simukraft.commercial.CommercialWorkService;
import common.cn.kafei.simukraft.farmland.FarmlandBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialConstants;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialDefinitionLoader;
import common.cn.kafei.simukraft.industrial.IndustrialWorkService;
import common.cn.kafei.simukraft.logistics.LogisticsConstants;
import common.cn.kafei.simukraft.medical.MedicalControlBoxService;
import common.cn.kafei.simukraft.medical.MedicalService;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingConstants;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingControlBoxService;
import common.cn.kafei.simukraft.planner.PlannerWorkService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public final class CitizenEmploymentService {
    private CitizenEmploymentService() {
    }

    // workplaceId：所有非 POI 岗位统一按 sourceType/role/坐标生成稳定 UUID。
    public static UUID workplaceId(String sourceType, String role, BlockPos pos) {
        String safeSource = normalizeKey(sourceType);
        String safeRole = normalizeKey(role);
        return UUID.nameUUIDFromBytes((safeSource + ":" + safeRole + "@" + pos.toShortString()).getBytes(StandardCharsets.UTF_8));
    }

    // hireForSource：玩家从方块界面雇佣 NPC 时的统一入口。
    public static void hireForSource(ServerLevel level, UUID citizenId, String sourceType, String role, BlockPos sourcePos, String statusLabel) {
        CityJobType jobType = CityJobMobilityService.resolveHireRole(role);
        hire(level, citizenId, jobType, workplaceId(sourceType, role, sourcePos), sourcePos, CitizenWorkStatus.WORKING, statusLabel);
    }

    // assignForSource：按方块来源恢复岗位绑定，不移动实体；用于任务重载/旧数据修复。
    public static void assignForSource(ServerLevel level, UUID citizenId, String sourceType, String role, BlockPos sourcePos, CitizenWorkStatus workStatus, String statusLabel) {
        if (sourcePos == null) {
            return;
        }
        CityJobType jobType = CityJobMobilityService.resolveHireRole(role);
        assign(level, citizenId, jobType, workplaceId(sourceType, role, sourcePos), sourcePos, workStatus, statusLabel);
    }

    // hire：任务服务直接指定职业和岗位时的统一入口。
    public static void hire(ServerLevel level, UUID citizenId, CityJobType jobType, UUID workplaceId, BlockPos workplacePos, CitizenWorkStatus workStatus, String statusLabel) {
        if (level == null || citizenId == null || workplaceId == null) {
            return;
        }
        CityJobType safeJob = jobType != null ? jobType : CityJobType.OTHER;
        CitizenWorkStatus safeStatus = workStatus != null ? workStatus : CitizenWorkStatus.WORKING;
        boolean restTime = CitizenHomeRestService.isRestTime(level);
        CitizenData preHire = restTime ? CitizenService.findCitizen(level, citizenId).orElse(null) : null;
        boolean wasResting = preHire != null && preHire.workStatusType() == CitizenWorkStatus.RESTING;
        String savedLabel = wasResting ? preHire.statusLabel() : null;
        String savedDetail = wasResting ? preHire.workNeedDetail() : null;
        assign(level, citizenId, safeJob, workplaceId, workplacePos, safeStatus, statusLabel);
        if (!restTime) {
            CityJobMobilityService.teleportCitizenToWorkplace(level, citizenId, workplacePos, safeJob, safeStatus, statusLabel != null ? statusLabel : "");
        } else if (wasResting) {
            preHire.setWorkStatus(CitizenWorkStatus.RESTING);
            preHire.setStatusLabel(savedLabel != null ? savedLabel : "");
            preHire.setWorkNeedDetail(savedDetail != null ? savedDetail : "");
            CitizenService.save(level, citizenId);
        }
    }

    // assign：只写职业和岗位，不主动移动实体，适合 POI 自动分配。
    public static void assign(ServerLevel level, UUID citizenId, CityJobType jobType, UUID workplaceId, BlockPos workplacePos, CitizenWorkStatus workStatus, String statusLabel) {
        if (level == null || citizenId == null || workplaceId == null) {
            return;
        }
        CityJobType safeJob = jobType != null ? jobType : CityJobType.OTHER;
        CitizenWorkStatus safeStatus = workStatus != null ? workStatus : CitizenWorkStatus.WORKING;
        CitizenService.applyEmployment(level, citizenId, safeJob, workplaceId, workplacePos, statusLabel != null ? statusLabel : "");
        CitizenService.findCitizen(level, citizenId).ifPresent(citizen -> {
            citizen.setWorkStatus(safeStatus);
            CitizenService.save(level, citizenId);
        });
    }

    // fire：解雇时先清职业运行时，再清岗位数据，最后同步实体显示。
    public static Optional<CitizenData> fire(ServerLevel level, UUID citizenId, @Nullable String sourceType, @Nullable String role, @Nullable BlockPos sourcePos, String reason) {
        if (level == null || citizenId == null) {
            return Optional.empty();
        }
        Optional<CitizenData> citizenOptional = CitizenService.findCitizen(level, citizenId);
        if (citizenOptional.isEmpty() || citizenOptional.get().dead()) {
            return Optional.empty();
        }
        CitizenData citizen = citizenOptional.get();
        cleanupRuntime(level, citizen, sourceType, role, sourcePos, reason);
        CitizenService.clearEmployment(level, citizen.uuid());
        CityJobMobilityService.resetCitizenAfterFire(level, citizen.uuid());
        return Optional.of(citizen);
    }

    public static Optional<CitizenData> fireAssigned(ServerLevel level, UUID workplaceId, @Nullable String sourceType, @Nullable String role, @Nullable BlockPos sourcePos, String reason) {
        UUID citizenId = CitizenService.findAssignedCitizen(level, workplaceId);
        return citizenId != null ? fire(level, citizenId, sourceType, role, sourcePos, reason) : Optional.empty();
    }

    // findAssigned：按统一 workplaceId 查找岗位绑定 NPC，用于兼容旧数据里职业类型写错但岗位仍正确的情况。
    public static Optional<CitizenData> findAssigned(ServerLevel level, String sourceType, String role, BlockPos sourcePos) {
        if (level == null || sourcePos == null) {
            return Optional.empty();
        }
        UUID citizenId = CitizenService.findAssignedCitizen(level, workplaceId(sourceType, role, sourcePos));
        return citizenId != null
                ? CitizenService.findCitizen(level, citizenId).filter(citizen -> !citizen.dead())
                : Optional.empty();
    }

    // clearAfterJobFinished：任务已经正常收尾时只清职业数据，不再反向中断任务。
    public static Optional<CitizenData> clearAfterJobFinished(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return Optional.empty();
        }
        Optional<CitizenData> citizenOptional = CitizenService.findCitizen(level, citizenId);
        if (citizenOptional.isEmpty() || citizenOptional.get().dead()) {
            return Optional.empty();
        }
        CitizenData citizen = citizenOptional.get();
        CitizenService.clearEmployment(level, citizen.uuid());
        CityJobMobilityService.resetCitizenAfterFire(level, citizen.uuid());
        return Optional.of(citizen);
    }

    // repairLoadedEmployment：加载旧存档时按稳定 workplaceId 修正错误职业，避免农民/建筑师被旧数据写成规划师。
    public static boolean repairLoadedEmployment(CitizenData citizen) {
        if (citizen == null || citizen.dead()) {
            return false;
        }
        if (citizen.workplaceId() == null) {
            if (citizen.jobType() != CityJobType.UNEMPLOYED && citizen.jobType() != CityJobType.RESIDENT) {
                citizen.setJobType(CityJobType.UNEMPLOYED);
                citizen.setWorkStatus(CitizenWorkStatus.IDLE);
                citizen.setWorkplacePos(null);
                citizen.setStatusLabel("");
                citizen.setWorkNeedDetail("");
                return true;
            }
            return false;
        }
        if (citizen.workplacePos() == null) {
            return false;
        }
        CityJobType expectedJob = expectedStableWorkplaceJob(citizen.workplaceId(), citizen.workplacePos());
        if (expectedJob == null || citizen.jobType() == expectedJob) {
            return false;
        }
        citizen.setJobType(expectedJob);
        if (citizen.workStatusType() == CitizenWorkStatus.IDLE) {
            citizen.setWorkStatus(CitizenWorkStatus.WORKING);
        }
        return true;
    }

    public static boolean repairLoadedEmployment(ServerLevel level, CitizenData citizen) {
        boolean changed = repairLoadedEmployment(citizen);
        return repairIndustrialJobKey(level, citizen) || changed;
    }

    // expectedStableWorkplaceJob：识别建筑盒/农田盒这类非 POI 岗位的稳定 UUID。
    public static CityJobType expectedStableWorkplaceJob(UUID workplaceId, BlockPos workplacePos) {
        if (workplaceId == null || workplacePos == null) {
            return null;
        }
        if (workplaceId("build_box", "builder", workplacePos).equals(workplaceId)) {
            return CityJobType.BUILDER;
        }
        if (workplaceId("build_box", "planner", workplacePos).equals(workplaceId)) {
            return CityJobType.PLANNER;
        }
        if (workplaceId(FarmlandBoxService.HIRE_SOURCE_TYPE, FarmlandBoxService.HIRE_ROLE, workplacePos).equals(workplaceId)) {
            return CityJobType.FARMER;
        }
        if (workplaceId(IndustrialConstants.HIRE_SOURCE_TYPE, IndustrialConstants.HIRE_ROLE, workplacePos).equals(workplaceId)) {
            return CityJobType.INDUSTRIAL_WORKER;
        }
        if (workplaceId(MineralDrillingConstants.HIRE_SOURCE_TYPE, MineralDrillingConstants.HIRE_ROLE, workplacePos).equals(workplaceId)) {
            return CityJobType.INDUSTRIAL_WORKER;
        }
        if (workplaceId(CommercialConstants.HIRE_SOURCE_TYPE, CommercialConstants.HIRE_ROLE, workplacePos).equals(workplaceId)) {
            return CityJobType.COMMERCIAL_WORKER;
        }
        if (workplaceId(LogisticsConstants.SERVER_SOURCE_TYPE, LogisticsConstants.STORAGE_ROLE, workplacePos).equals(workplaceId)) {
            return CityJobType.STORAGE_WORKER;
        }
        if (workplaceId(MedicalControlBoxService.HIRE_SOURCE_TYPE, MedicalControlBoxService.HIRE_ROLE, workplacePos).equals(workplaceId)) {
            return CityJobType.DOCTOR;
        }
        return null;
    }

    private static void cleanupRuntime(ServerLevel level, CitizenData citizen, @Nullable String sourceType, @Nullable String role, @Nullable BlockPos sourcePos, String reason) {
        String safeReason = reason != null && !reason.isBlank() ? reason : "employment_cleared";
        String normalizedSource = normalizeKey(sourceType);
        String normalizedRole = normalizeKey(role);
        CityJobType jobType = citizen.jobType();
        boolean mineralDrillingEmployment = isMineralDrillingEmployment(citizen, normalizedSource, normalizedRole, sourcePos);
        if (jobType == CityJobType.BUILDER || "builder".equals(normalizedRole)) {
            BuilderConstructionService.interruptTask(level, citizen.uuid(), safeReason);
        }
        if (jobType == CityJobType.PLANNER || "planner".equals(normalizedRole)) {
            PlannerWorkService.interruptTask(level, citizen.uuid(), safeReason);
        }
        if (jobType == CityJobType.FARMER || "farmer".equals(normalizedRole) || FarmlandBoxService.HIRE_SOURCE_TYPE.equals(normalizedSource)) {
            BlockPos boxPos = sourcePos != null ? sourcePos : citizen.workplacePos();
            if (boxPos != null) {
                FarmlandBoxService.toggleRunningOff(level, boxPos);
            }
        }
        if (!mineralDrillingEmployment
                && (jobType == CityJobType.INDUSTRIAL_WORKER
                || "industrial".equals(normalizedRole)
                || IndustrialConstants.HIRE_SOURCE_TYPE.equals(normalizedSource))) {
            IndustrialWorkService.clearServerCaches(level.getServer());
            common.cn.kafei.simukraft.industrial.IndustrialControlBoxService.interrupt(level, citizen.uuid(), safeReason);
        }
        if (mineralDrillingEmployment) {
            MineralDrillingControlBoxService.interrupt(level, citizen.uuid(), safeReason);
        }
        if (jobType == CityJobType.COMMERCIAL_WORKER || "commercial".equals(normalizedRole) || CommercialConstants.HIRE_SOURCE_TYPE.equals(normalizedSource)) {
            CommercialWorkService.clearServerCaches(level.getServer());
            CommercialControlBoxService.interrupt(level, citizen.uuid(), safeReason);
        }
        if (jobType == CityJobType.STORAGE_WORKER || LogisticsConstants.SERVER_SOURCE_TYPE.equals(normalizedSource)) {
            common.cn.kafei.simukraft.citizen.CitizenJobVisualService.clearMainHandOverride(citizen.uuid());
        }
        if (jobType == CityJobType.DOCTOR || MedicalControlBoxService.HIRE_SOURCE_TYPE.equals(normalizedSource)) {
            BlockPos boxPos = sourcePos != null ? sourcePos : citizen.workplacePos();
            if (boxPos != null) {
                MedicalService.releasePatientsForControlBox(level, boxPos);
            }
        }
    }

    /** isMineralDrillingEmployment: 优先识别复用工业职业枚举的钻井岗位，避免清理工业控制箱任务。 */
    private static boolean isMineralDrillingEmployment(CitizenData citizen, String normalizedSource, String normalizedRole,
                                                        @Nullable BlockPos sourcePos) {
        if (MineralDrillingConstants.HIRE_SOURCE_TYPE.equals(normalizedSource)
                && MineralDrillingConstants.HIRE_ROLE.equals(normalizedRole)) {
            return true;
        }
        BlockPos workplacePos = sourcePos != null ? sourcePos : citizen.workplacePos();
        return workplacePos != null
                && workplaceId(MineralDrillingConstants.HIRE_SOURCE_TYPE, MineralDrillingConstants.HIRE_ROLE, workplacePos)
                .equals(citizen.workplaceId());
    }

    private static boolean repairIndustrialJobKey(ServerLevel level, CitizenData citizen) {
        if (level == null || citizen == null || citizen.dead() || citizen.jobType() != CityJobType.INDUSTRIAL_WORKER || citizen.workplacePos() == null) {
            return false;
        }
        if (!workplaceId(IndustrialConstants.HIRE_SOURCE_TYPE, IndustrialConstants.HIRE_ROLE, citizen.workplacePos()).equals(citizen.workplaceId())) {
            return false;
        }
        IndustrialDefinition definition = IndustrialDefinitionLoader.loadForBuilding(IndustrialControlBoxService.resolveBuilding(level, citizen.workplacePos())).definition();
        if (definition == null || definition.jobType() == null || definition.jobType().isBlank() || definition.jobType().equals(citizen.jobId())) {
            return false;
        }
        citizen.setJobIdRaw(definition.jobType().trim());
        return true;
    }

    private static String normalizeKey(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
