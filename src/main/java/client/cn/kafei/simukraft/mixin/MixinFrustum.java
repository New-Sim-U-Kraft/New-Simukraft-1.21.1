package client.cn.kafei.simukraft.mixin;

import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RTS 视锥兼容：跳过正交投影不需要的相机立方体偏移。 */
@Mixin(Frustum.class)
public class MixinFrustum {
    /** simukraft$skipCameraCubeOffset: 避免正交视锥被原版相机立方体重复偏移。 */
    @Inject(method = "offsetToFullyIncludeCameraCube", at = @At("HEAD"), cancellable = true)
    private void simukraft$skipCameraCubeOffset(int cubeSize, CallbackInfoReturnable<Frustum> callbackInfo) {
        if (FreeCameraManager.isRtsActive()) {
            callbackInfo.setReturnValue((Frustum) (Object) this);
        }
    }
}
