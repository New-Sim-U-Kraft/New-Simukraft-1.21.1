package common.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RTS 远程箱子兼容：仅维持当前已授权会话的有效性。 */
@Mixin(ChestMenu.class)
public abstract class MixinChestMenu {
    /** simukraft$keepRtsRemoteChestOpen: 为授权的远程箱子跳过本体距离校验。 */
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void simukraft$keepRtsRemoteChestOpen(Player player, CallbackInfoReturnable<Boolean> callback) {
        if (player instanceof ServerPlayer serverPlayer
                && RtsRemoteMenuAccess.keepsMenuOpen(serverPlayer, (ChestMenu) (Object) this)) {
            callback.setReturnValue(true);
        }
    }
}
