package common.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RTS 远程潜影盒兼容：仅维持当前已授权会话的菜单有效性。 */
@Mixin(ShulkerBoxMenu.class)
public abstract class MixinShulkerBoxMenu {
    /** simukraft$keepRtsRemoteShulkerBoxOpen: 已绑定 RTS 会话时跳过原版距离校验。 */
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void simukraft$keepRtsRemoteShulkerBoxOpen(
            Player player, CallbackInfoReturnable<Boolean> callback) {
        if (player instanceof ServerPlayer serverPlayer
                && RtsRemoteMenuAccess.keepsMenuOpen(serverPlayer, (ShulkerBoxMenu) (Object) this)) {
            callback.setReturnValue(true);
        }
    }
}
