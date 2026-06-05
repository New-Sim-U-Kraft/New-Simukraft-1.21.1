package common.cn.kafei.simukraft.commercial;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

@SuppressWarnings("null")
public final class CommercialTradeAccessValidator {
    private CommercialTradeAccessValidator() {
    }

    /** canUseTradeMenu: 校验玩家是否仍可使用指定商业交易容器。 */
    public static boolean canUseTradeMenu(ServerLevel level, ServerPlayer player, BlockPos boxPos, UUID workerId) {
        return isValidWorker(level, boxPos, workerId)
                && isTradeReachable(level, player, boxPos, workerId)
                && level.getBlockState(boxPos).is(ModBlocks.COMMERCIAL_CONTROL_BOX.get());
    }

    /** isValidWorker: 校验交易 NPC 是否仍绑定到该商业控制箱。 */
    public static boolean isValidWorker(ServerLevel level, BlockPos boxPos, UUID workerId) {
        CitizenData worker = CommercialControlBoxService.findAssignedWorker(level, boxPos);
        return worker != null && workerId != null && workerId.equals(worker.uuid());
    }

    /** isTradeReachable: 校验玩家距离 NPC 或控制箱是否允许交易。 */
    public static boolean isTradeReachable(ServerLevel level, ServerPlayer player, BlockPos boxPos, UUID workerId) {
        var workerEntity = CitizenTeleportService.findCitizenEntity(level, workerId);
        if (workerEntity != null && player.distanceToSqr(workerEntity) <= 64.0D) {
            return true;
        }
        return player.blockPosition().closerThan(boxPos, 32.0D);
    }
}
