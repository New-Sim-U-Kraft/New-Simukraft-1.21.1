package client.cn.kafei.simukraft.mixin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
@OnlyIn(Dist.CLIENT)
public abstract class MixinCamera {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot, float roll);

    /** simukraft$setup: 完成原版相机初始化后覆盖为独立 RTS 相机姿态。 */
    @Inject(method = "setup", at = @At("TAIL"))
    private void simukraft$setup(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo callbackInfo) {
        if (FreeCameraManager.isActive() && entity instanceof LocalPlayer) {
            setPosition(FreeCameraManager.getPosition());
            setRotation(FreeCameraManager.getYaw(), FreeCameraManager.getPitch(), 0.0F);
        }
    }
}
