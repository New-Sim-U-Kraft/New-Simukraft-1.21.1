package client.cn.kafei.simukraft.client.renderer;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CityJobType;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public class CitizenModel extends PlayerModel<CitizenEntity> {
    private static final float BUILDER_ARM_BASE_X_ROT = -0.85F;
    private static final float BUILDER_ARM_Z_ROT = 0.08F;
    private static final float BUILDER_ARM_SWING_SCALE = 1.6F;

    public CitizenModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    // setupAnim：在原版玩家模型动作之后叠加建筑师专属施工右臂动作。
    @Override
    public void setupAnim(CitizenEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (shouldUseBuilderWorkSwing(entity)) {
            applyBuilderWorkSwing(ageInTicks);
        }
    }

    // shouldUseBuilderWorkSwing：只让建筑师使用独立施工动作，避免影响农民和工业工人。
    private static boolean shouldUseBuilderWorkSwing(CitizenEntity entity) {
        return entity != null
                && entity.hasActiveVisualTask()
                && CityJobType.BUILDER.name().equalsIgnoreCase(entity.getJob());
    }

    // applyBuilderWorkSwing：参考旧版的轻量周期姿势值，让右手持续做施工摆动。
    private void applyBuilderWorkSwing(float ageInTicks) {
        float oldVersionPose = Mth.sin(ageInTicks / 2.0F) / 20.0F + 0.05F;
        this.rightArm.xRot = BUILDER_ARM_BASE_X_ROT + oldVersionPose * BUILDER_ARM_SWING_SCALE;
        this.rightArm.yRot = 0.0F;
        this.rightArm.zRot = BUILDER_ARM_Z_ROT;
        this.rightSleeve.copyFrom(this.rightArm);
    }
}
