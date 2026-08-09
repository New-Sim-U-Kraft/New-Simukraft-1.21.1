package client.cn.kafei.simukraft.mixin;

import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RTS 正交投影：仅在 RTS 相机启用时替换世界渲染投影矩阵。 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererProjection {
    /** simukraft$applyRtsProjection: 使用 RTS 缩放范围生成正交投影。 */
    @Inject(method = "getProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void simukraft$applyRtsProjection(double fov, CallbackInfoReturnable<Matrix4f> callbackInfo) {
        if (FreeCameraManager.isRtsActive()) {
            callbackInfo.setReturnValue(FreeCameraManager.rtsProjectionMatrix());
        }
    }
}
