package common.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** RTS 远程容器兼容：只为当前 RTS 会话菜单绕过原版本体距离关闭。 */
@SuppressWarnings("null")
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {
    /** simukraft$keepRtsRemoteMenuOpen: 保持已授权远程 Menu 的服务端有效性。 */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean simukraft$keepRtsRemoteMenuOpen(AbstractContainerMenu menu, Player player) {
        return player instanceof ServerPlayer serverPlayer && RtsRemoteMenuAccess.keepsMenuOpen(serverPlayer, menu)
                || menu.stillValid(player);
    }
}
