package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** 处理信息界面的跟随玩家与原地停留开关。 */
@SuppressWarnings("null")
public final class CitizenManualControlService {
    private static final double FOLLOW_START_DISTANCE_SQR = 16.0D;
    private static final double FOLLOW_STOP_DISTANCE_SQR = 6.25D;
    private static final int FOLLOW_REPATH_INTERVAL_TICKS = 10;

    private CitizenManualControlService() {
    }

    /** tick：原地停留优先级最高；否则按低频路径请求跟随指定玩家。 */
    public static void tick(ServerLevel level, CitizenEntity citizen) {
        if (level == null || citizen == null || citizen.isRemoved()) {
            return;
        }
        if (citizen.isStayInPlace()) {
            if (CitizenNavigationService.isNavigating(level, citizen.getUUID())) {
                CitizenNavigationService.stop(level, citizen.getUUID());
            }
            Vec3 motion = citizen.getDeltaMovement();
            citizen.setDeltaMovement(0.0D, motion.y, 0.0D);
            return;
        }
        if (citizen.getFollowPlayerId() == null) {
            return;
        }
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(citizen.getFollowPlayerId());
        if (target == null || target.serverLevel() != level) {
            return;
        }
        double distanceSqr = citizen.distanceToSqr(target);
        if (distanceSqr <= FOLLOW_STOP_DISTANCE_SQR) {
            if (CitizenNavigationService.isNavigating(level, citizen.getUUID())) {
                CitizenNavigationService.stop(level, citizen.getUUID());
            }
            citizen.getLookControl().setLookAt(target);
            return;
        }
        if (distanceSqr >= FOLLOW_START_DISTANCE_SQR
                && Math.floorMod(citizen.tickCount, FOLLOW_REPATH_INTERVAL_TICKS) == 0) {
            CitizenNavigationService.requestMove(level, citizen.getUUID(), target.position(), MovementIntent.FOLLOW);
        }
    }
}
