package client.cn.kafei.simukraft.client.renderer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class CitizenAnimationActions {
    private CitizenAnimationActions() {
    }

    public static boolean canUseWorkSwing(CitizenEntity entity) {
        return entity != null && entity.hasActiveVisualTask();
    }

    // applyBuilderWorkSwing：照抄原版攻击动画算法，但把旧版施工曲线固定应用到右手。
    public static void applyBuilderWorkSwing(PlayerModel<CitizenEntity> model, float ageInTicks) {
        if (model == null) {
            return;
        }
        float attackTime = builderWorkSwingCurve(ageInTicks);
        ModelPart arm = model.rightArm;
        model.body.yRot = Mth.sin(Mth.sqrt(attackTime) * (float) (Math.PI * 2.0D)) * 0.2F;
        model.rightArm.z = Mth.sin(model.body.yRot) * 5.0F;
        model.rightArm.x = -Mth.cos(model.body.yRot) * 5.0F;
        model.leftArm.z = -Mth.sin(model.body.yRot) * 5.0F;
        model.leftArm.x = Mth.cos(model.body.yRot) * 5.0F;
        model.rightArm.yRot += model.body.yRot;
        model.leftArm.yRot += model.body.yRot;
        model.leftArm.xRot += model.body.yRot;
        float easedAttackTime = 1.0F - attackTime;
        easedAttackTime *= easedAttackTime;
        easedAttackTime *= easedAttackTime;
        easedAttackTime = 1.0F - easedAttackTime;
        float swing = Mth.sin(easedAttackTime * (float) Math.PI);
        float headOffset = Mth.sin(attackTime * (float) Math.PI) * -(model.head.xRot - 0.7F) * 0.75F;
        arm.xRot -= swing * 1.2F + headOffset;
        arm.yRot += model.body.yRot * 2.0F;
        arm.zRot += Mth.sin(attackTime * (float) Math.PI) * -0.4F;
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    // applyBuilderWorkSwing：按旧版 tickCount + partialTick 入口调用施工动作。
    public static void applyBuilderWorkSwing(PlayerModel<CitizenEntity> model, CitizenEntity entity, float partialTick) {
        if (entity == null) {
            return;
        }
        applyBuilderWorkSwing(model, entity.tickCount + partialTick);
    }

    // builderWorkSwingCurve：旧版 getAttackAnim 使用的施工动作曲线。
    public static float builderWorkSwingCurve(float ageInTicks) {
        return Mth.sin(ageInTicks / 2.0F) / 20.0F + 0.05F;
    }
}
