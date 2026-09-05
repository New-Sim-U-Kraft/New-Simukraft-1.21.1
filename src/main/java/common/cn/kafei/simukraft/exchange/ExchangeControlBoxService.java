package common.cn.kafei.simukraft.exchange;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.building.PublicBuildingTypes;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** ExchangeControlBoxService: 交易所控制箱绑定与经纪人岗位。 */
public final class ExchangeControlBoxService {
    public static final String HIRE_SOURCE_TYPE = "exchange_control_box";
    public static final String HIRE_ROLE = "broker";

    private ExchangeControlBoxService() {
    }

    /** resolveBuilding: 解析包含该控制箱且 JSON type 为交易所的已完成公共建筑。 */
    public static PlacedBuildingRecord resolveBuilding(ServerLevel level, BlockPos boxPos) {
        if (!isExchangeControlBox(level, boxPos)) {
            return null;
        }
        PlacedBuildingRecord building = PlacedBuildingService.findByContainedPos(level, boxPos);
        return PublicBuildingTypes.isExchange(building) ? building : null;
    }

    /** findAssignedBroker: 查询经纪人。 */
    public static CitizenData findAssignedBroker(ServerLevel level, BlockPos boxPos) {
        return CitizenEmploymentService.findAssigned(level, HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos).orElse(null);
    }

    /** isOperational: 交易所可交易。 */
    public static boolean isOperational(ServerLevel level, BlockPos boxPos) {
        return resolveBuilding(level, boxPos) != null && findAssignedBroker(level, boxPos) != null;
    }

    /** onRemoved: 拆除时解雇经纪人并注销建筑。 */
    public static void onRemoved(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return;
        }
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        CitizenEmploymentService.fireAssigned(level,
                CitizenEmploymentService.workplaceId(HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos),
                HIRE_SOURCE_TYPE, HIRE_ROLE, boxPos, "exchange_control_box_removed");
        if (building != null) {
            PlacedBuildingService.unregister(level, building.buildingId());
        }
    }

    /** isExchangeControlBox: 坐标处是否为交易所控制箱。 */
    public static boolean isExchangeControlBox(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && level.isLoaded(pos)
                && level.getBlockState(pos).is(ModBlocks.EXCHANGE_CONTROL_BOX.get());
    }
}
