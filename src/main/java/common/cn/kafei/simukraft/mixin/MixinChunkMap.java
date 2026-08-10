package common.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.network.rts.RtsChunkViewService;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RTS 区块追踪兼容：仅将客户端订阅中心替换为摄像机焦点。 */
@Mixin(ChunkMap.class)
public abstract class MixinChunkMap {
    /** simukraft$enterRtsTracking: 为本次原版追踪刷新记录玩家上下文。 */
    @Inject(method = "updateChunkTracking", at = @At("HEAD"))
    private void simukraft$enterRtsTracking(ServerPlayer player, CallbackInfo callback) {
        RtsChunkViewService.enterTrackingUpdate(player);
    }

    /** simukraft$leaveRtsTracking: 原版追踪刷新完成后清除玩家上下文。 */
    @Inject(method = "updateChunkTracking", at = @At("RETURN"))
    private void simukraft$leaveRtsTracking(ServerPlayer player, CallbackInfo callback) {
        RtsChunkViewService.leaveTrackingUpdate();
    }

    /** simukraft$useRtsTrackingCenter: RTS 激活期间使用焦点生成原版区块和实体追踪视窗。 */
    @Redirect(method = "updateChunkTracking", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkTrackingView;of(Lnet/minecraft/world/level/ChunkPos;I)Lnet/minecraft/server/level/ChunkTrackingView;"))
    private ChunkTrackingView simukraft$useRtsTrackingCenter(ChunkPos center, int viewDistance) {
        return RtsChunkViewService.trackingView(center, viewDistance);
    }

}
