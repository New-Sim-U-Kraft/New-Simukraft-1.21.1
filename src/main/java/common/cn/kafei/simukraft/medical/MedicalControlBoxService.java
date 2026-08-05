package common.cn.kafei.simukraft.medical;

import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** 医疗控制箱的建筑绑定、医生岗位和只读视图服务。 */
@SuppressWarnings("null")
public final class MedicalControlBoxService {
    public static final String HIRE_SOURCE_TYPE = "medical_control_box";
    public static final String HIRE_ROLE = "doctor";

    private MedicalControlBoxService() {
    }

    /** buildView：构建医疗控制箱 LDLib 界面所需的状态快照。 */
    public static MedicalControlBoxView buildView(ServerLevel level, BlockPos boxPos) {
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        MedicalDefinitionLoader.LoadResult definitionResult = MedicalDefinitionLoader.loadForBuilding(building);
        MedicalDefinition definition = definitionResult.definition();
        CitizenData doctor = findAssignedDoctor(level, boxPos);
        MedicalService.BuildingSnapshot snapshot = MedicalService.snapshotForBuilding(level, building, boxPos);
        String statusKey = building == null
                ? "gui.simukraft.medical.status.no_building"
                : !definitionResult.valid() ? "gui.simukraft.medical.status.invalid_definition"
                : doctor == null ? "gui.simukraft.medical.status.no_doctor"
                : !isOperational(level, building, boxPos) ? "gui.simukraft.medical.status.doctor_unavailable"
                : "gui.simukraft.medical.status.open";
        int rings = definition != null ? definition.serviceRangeRings() : MedicalDefinition.DEFAULT_SERVICE_RANGE_RINGS;
        return new MedicalControlBoxView(
                boxPos,
                building != null,
                building != null ? building.displayName() : "",
                definitionResult.valid(),
                definition != null ? definition.name() : "",
                rings,
                MedicalService.coveredChunkCount(rings),
                statusKey,
                doctor != null,
                doctor != null ? doctor.uuid() : null,
                doctor != null ? doctor.name() : "",
                snapshot.bedCount(),
                snapshot.occupiedBedCount(),
                snapshot.patients());
    }

    /** resolveBuilding：仅解析包含控制箱的已完成医疗建筑。 */
    public static PlacedBuildingRecord resolveBuilding(ServerLevel level, BlockPos boxPos) {
        if (!isMedicalControlBox(level, boxPos)) {
            return null;
        }
        return PlacedBuildingService.findByContainedPos(level, boxPos);
    }

    /** findAssignedDoctor：查询当前控制箱绑定的医生。 */
    public static CitizenData findAssignedDoctor(ServerLevel level, BlockPos boxPos) {
        return CitizenEmploymentService.findAssigned(level, HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos).orElse(null);
    }

    /** isOperational：判断医院是否具备有效建筑、控制箱和可工作的医生。 */
    public static boolean isOperational(ServerLevel level, PlacedBuildingRecord building, BlockPos boxPos) {
        if (level == null || building == null || boxPos == null || !isMedicalControlBox(level, boxPos)) {
            return false;
        }
        CitizenData doctor = findAssignedDoctor(level, boxPos);
        long currentDay = level.getDayTime() / 24_000L;
        return doctor != null && !doctor.dead()
                && !MedicalService.isOnMedicalLeave(doctor, currentDay)
                && !MedicalService.needsCare(level, doctor, currentDay);
    }

    /** onRemoved：控制箱拆除时释放患者、解除医生并删除建筑登记。 */
    public static void onRemoved(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return;
        }
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        MedicalService.releasePatientsForControlBox(level, boxPos);
        CitizenEmploymentService.fireAssigned(level,
                CitizenEmploymentService.workplaceId(HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos),
                HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos, "medical_control_box_removed");
        if (building != null) {
            PlacedBuildingService.unregister(level, building.buildingId());
        }
    }

    /** isMedicalControlBox：判断指定坐标是否是已加载医疗控制箱。 */
    public static boolean isMedicalControlBox(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && level.isLoaded(pos)
                && level.getBlockState(pos).is(ModBlocks.MEDICAL_CONTROL_BOX.get());
    }

    /** resolveControlBoxPos：从医疗建筑记录中定位控制箱。 */
    public static BlockPos resolveControlBoxPos(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null) {
            return null;
        }
        for (BuildingBlockData block : building.blocks()) {
            if (!block.state().is(ModBlocks.MEDICAL_CONTROL_BOX.get())) {
                continue;
            }
            BlockPos direct = block.relativePos();
            if (isMedicalControlBox(level, direct)) {
                return direct.immutable();
            }
            BlockPos world = building.worldOrigin().offset(direct);
            if (isMedicalControlBox(level, world)) {
                return world.immutable();
            }
        }
        return null;
    }

    /** resolveDoctorBox：解析医生当前绑定的医疗控制箱。 */
    public static BlockPos resolveDoctorBox(ServerLevel level, CitizenData doctor) {
        if (level == null || doctor == null || doctor.workplacePos() == null || doctor.workplaceId() == null) {
            return null;
        }
        BlockPos pos = doctor.workplacePos();
        UUID expected = CitizenEmploymentService.workplaceId(HIRE_SOURCE_TYPE, HIRE_ROLE, pos);
        return expected.equals(doctor.workplaceId()) && isMedicalControlBox(level, pos) ? pos.immutable() : null;
    }
}
