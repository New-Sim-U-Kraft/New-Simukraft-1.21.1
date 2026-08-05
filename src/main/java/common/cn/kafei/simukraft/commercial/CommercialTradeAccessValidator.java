package common.cn.kafei.simukraft.commercial;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

@SuppressWarnings("null")
public final class CommercialTradeAccessValidator {
    private static final double TRADE_RANGE_SQR = 4.5D * 4.5D;

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

    /** isTradeReachable：校验玩家和商业职员是否在近距离且没有隔墙。 */
    public static boolean isTradeReachable(ServerLevel level, ServerPlayer player, BlockPos boxPos, UUID workerId) {
        var workerEntity = CitizenTeleportService.findCitizenEntity(level, workerId);
        return workerEntity != null
                && player.distanceToSqr(workerEntity) <= TRADE_RANGE_SQR
                && hasLineOfSight(level, player, workerEntity);
    }

    /** hasLineOfSight：用碰撞射线阻止玩家隔墙使用商业职员。 */
    private static boolean hasLineOfSight(ServerLevel level, ServerPlayer player, Entity workerEntity) {
        Vec3 from = player.getEyePosition();
        Vec3 to = workerEntity.getEyePosition();
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }
}
