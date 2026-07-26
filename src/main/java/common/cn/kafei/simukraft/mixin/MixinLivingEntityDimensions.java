package common.cn.kafei.simukraft.mixin;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让幼儿 NPC 的物理碰撞箱随年龄缩放，与渲染层保持一致。
 * getDimensions 在 LivingEntity 中是 final，只能通过 Mixin 覆盖。
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDimensions {
    // 与 CitizenRenderer 保持完全一致的缩放参数
    private static final float ADULT_SCALE = 0.9375F;
    private static final float CHILD_MIN_SCALE = 0.45F;
    private static final float HITBOX_WIDTH = 0.6F;
    private static final float HITBOX_HEIGHT = 1.8F;

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void simukraft$childNpcDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((Object) this instanceof CitizenEntity citizen && citizen.isChildNpc()) {
            int age = Math.max(1, citizen.getAge());
            // 1岁→17岁线性从 CHILD_MIN_SCALE 渐变到 ADULT_SCALE，与渲染缩放完全同步
            float t = Mth.clamp((age - 1) / 16.0f, 0.0f, 1.0f);
            float scale = CHILD_MIN_SCALE + t * (ADULT_SCALE - CHILD_MIN_SCALE);
            float ratio = scale / ADULT_SCALE;
            cir.setReturnValue(EntityDimensions.scalable(HITBOX_WIDTH * ratio, HITBOX_HEIGHT * ratio));
        }
    }
}
