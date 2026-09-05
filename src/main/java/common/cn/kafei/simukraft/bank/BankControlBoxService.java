package common.cn.kafei.simukraft.bank;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.building.PublicBuildingTypes;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** BankControlBoxService: 银行控制箱绑定、柜员岗位和视图。 */
public final class BankControlBoxService {
    public static final String HIRE_SOURCE_TYPE = "bank_control_box";
    public static final String HIRE_ROLE = "teller";

    private BankControlBoxService() {
    }

    /** buildView: 构建银行控制箱只读快照。 */
    public static BankControlBoxView buildView(ServerLevel level, BlockPos boxPos, double cityFunds, double playerCash) {
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        CitizenData teller = findAssignedTeller(level, boxPos);
        String statusKey = building == null
                ? "gui.simukraft.bank.status.no_building"
                : teller == null ? "gui.simukraft.bank.status.no_teller"
                : "gui.simukraft.bank.status.open";
        return new BankControlBoxView(
                boxPos,
                building != null,
                building != null ? building.displayName() : "",
                building != null ? building.cityId() : null,
                statusKey,
                teller != null,
                teller != null ? teller.uuid() : null,
                teller != null ? teller.name() : "",
                cityFunds,
                playerCash);
    }

    /** resolveBuilding: 解析包含该控制箱且 JSON type 为银行的已完成公共建筑。 */
    public static PlacedBuildingRecord resolveBuilding(ServerLevel level, BlockPos boxPos) {
        if (!isBankControlBox(level, boxPos)) {
            return null;
        }
        PlacedBuildingRecord building = PlacedBuildingService.findByContainedPos(level, boxPos);
        return PublicBuildingTypes.isBank(building) ? building : null;
    }

    /** findAssignedTeller: 查询绑定柜员。 */
    public static CitizenData findAssignedTeller(ServerLevel level, BlockPos boxPos) {
        return CitizenEmploymentService.findAssigned(level, HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos).orElse(null);
    }

    /** isOperational: 银行可办理业务。 */
    public static boolean isOperational(ServerLevel level, BlockPos boxPos) {
        return resolveBuilding(level, boxPos) != null && findAssignedTeller(level, boxPos) != null;
    }

    /** onRemoved: 拆除时解雇柜员并注销建筑。 */
    public static void onRemoved(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return;
        }
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        CitizenEmploymentService.fireAssigned(level,
                CitizenEmploymentService.workplaceId(HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos),
                HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos, "bank_control_box_removed");
        if (building != null) {
            PlacedBuildingService.unregister(level, building.buildingId());
        }
    }

    /** isBankControlBox: 坐标处是否为银行控制箱。 */
    public static boolean isBankControlBox(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && level.isLoaded(pos)
                && level.getBlockState(pos).is(ModBlocks.BANK_CONTROL_BOX.get());
    }
}
