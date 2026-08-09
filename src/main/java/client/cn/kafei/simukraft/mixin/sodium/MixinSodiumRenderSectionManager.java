package client.cn.kafei.simukraft.mixin.sodium;

import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium RTS 兼容：关闭依赖透视相机方向的区段遮挡剔除。 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public class MixinSodiumRenderSectionManager {
    /** simukraft$disableOcclusionCulling: 正交 RTS 下保留所有已加载的可达区段。 */
    @Inject(method = "shouldUseOcclusionCulling", at = @At("HEAD"), cancellable = true)
    private void simukraft$disableOcclusionCulling(
            Camera camera,
            boolean smartCull,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (FreeCameraManager.isRtsActive()) {
            callbackInfo.setReturnValue(false);
        }
    }
}
