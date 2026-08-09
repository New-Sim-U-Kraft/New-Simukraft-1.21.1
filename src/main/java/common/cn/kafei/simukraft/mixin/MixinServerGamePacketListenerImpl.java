package common.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import common.cn.kafei.simukraft.network.rts.RtsRemoteCitizenAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** RTS 远程容器兼容：允许已授权容器处理物品栏操作。 */
@SuppressWarnings("null")
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {
    /** simukraft$allowRtsRemoteMenuInteraction: 仅绕过当前 RTS 容器会话的距离校验。 */
    @Redirect(method = {"handleContainerClick", "handleContainerButtonClick", "handlePlaceRecipe"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean simukraft$allowRtsRemoteMenuInteraction(AbstractContainerMenu menu, Player player) {
        return player instanceof ServerPlayer serverPlayer && (RtsRemoteMenuAccess.keepsMenuOpen(serverPlayer, menu)
                || RtsRemoteCitizenAccess.keepsMenuOpen(serverPlayer, menu))
                || menu.stillValid(player);
    }
}
