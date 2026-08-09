package client.cn.kafei.simukraft.mixin;

import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RTS 区段可见性兼容：相机独立于玩家时每帧重新应用正交视锥。 */
@Mixin(SectionOcclusionGraph.class)
public class MixinSectionOcclusionGraph {
    /** simukraft$forceFrustumUpdate: 让独立 RTS 相机的可见区段跟随相机位置刷新。 */
    @Inject(method = "consumeFrustumUpdate", at = @At("HEAD"), cancellable = true)
    private void simukraft$forceFrustumUpdate(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (FreeCameraManager.isRtsActive()) {
            callbackInfo.setReturnValue(true);
        }
    }
}
