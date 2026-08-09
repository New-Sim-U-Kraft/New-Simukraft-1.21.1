package common.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import common.cn.kafei.simukraft.network.rts.RtsRemoteCitizenAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RTS 远程容器兼容：只为当前 RTS 会话菜单绕过原版本体距离关闭。 */
@SuppressWarnings("null")
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {
    /** simukraft$keepRtsRemoteMenuOpen: 保持已授权远程 Menu 的服务端有效性。 */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean simukraft$keepRtsRemoteMenuOpen(AbstractContainerMenu menu, Player player) {
        return player instanceof ServerPlayer serverPlayer && (RtsRemoteMenuAccess.keepsMenuOpen(serverPlayer, menu)
                || RtsRemoteCitizenAccess.keepsMenuOpen(serverPlayer, menu))
                || menu.stillValid(player);
    }

    /** simukraft$finishRtsRemoteMenu: 关闭原版容器前结束 RTS 会话，恢复原版距离和开盖状态。 */
    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void simukraft$finishRtsRemoteMenu(CallbackInfo callback) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        RtsRemoteMenuAccess.finishMenu(player, player.containerMenu);
        RtsRemoteCitizenAccess.finishMenu(player, player.containerMenu);
    }
}
