package client.cn.kafei.simukraft.mixin.sodium;

import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Sodium RTS 兼容：正交视图中不按透视相机方向裁剪区段内方块面。 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public class MixinSodiumDefaultChunkRenderer {
    /** simukraft$disableFaceMaskCulling: RTS 时提交区段内全部可用朝向的面。 */
    @ModifyVariable(method = "fillCommandBuffer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static boolean simukraft$disableFaceMaskCulling(boolean useBlockFaceCulling) {
        return FreeCameraManager.isRtsActive() ? false : useBlockFaceCulling;
    }
}
