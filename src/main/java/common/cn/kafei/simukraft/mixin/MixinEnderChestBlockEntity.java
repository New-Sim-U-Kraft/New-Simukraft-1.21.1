package common.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RTS 远程末影箱兼容：保持已授权会话的原版开盖计数。 */
@Mixin(EnderChestBlockEntity.class)
public abstract class MixinEnderChestBlockEntity {
    /** simukraft$keepRtsEnderChestOpen: 远程菜单有效时跳过原版近距离开箱者复检。 */
    @Inject(method = "recheckOpen", at = @At("HEAD"), cancellable = true)
    private void simukraft$keepRtsEnderChestOpen(CallbackInfo callback) {
        EnderChestBlockEntity chest = (EnderChestBlockEntity) (Object) this;
        if (chest.getLevel() instanceof ServerLevel level && RtsRemoteMenuAccess.keepsEnderChestOpen(level, chest)) {
            // 已确认菜单仍绑定此末影箱，原版的近距离复检会错误关闭远程会话。
            level.scheduleTick(chest.getBlockPos(), chest.getBlockState().getBlock(), 5);
            callback.cancel();
        }
    }
}
