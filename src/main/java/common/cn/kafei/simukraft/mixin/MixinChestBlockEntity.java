package common.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RTS 远程箱子兼容：保持原版箱子开盖计数。 */
@Mixin(ChestBlockEntity.class)
public abstract class MixinChestBlockEntity {
    /** simukraft$keepRtsChestOpen: 远程菜单有效时跳过原版的近距离开箱者重检。 */
    @Inject(method = "recheckOpen", at = @At("HEAD"), cancellable = true)
    private void simukraft$keepRtsChestOpen(CallbackInfo callback) {
        ChestBlockEntity chest = (ChestBlockEntity) (Object) this;
        if (chest.getLevel() instanceof ServerLevel level && RtsRemoteMenuAccess.keepsChestOpen(level, chest)) {
            level.scheduleTick(chest.getBlockPos(), chest.getBlockState().getBlock(), 5);
            callback.cancel();
        }
    }
}
