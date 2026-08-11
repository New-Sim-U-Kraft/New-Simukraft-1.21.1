package client.cn.kafei.simukraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** PregnancyBellyLayer：按同步的孕期阶段绘制成年 NPC 的腹部几何体。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class PregnancyBellyLayer extends RenderLayer<CitizenEntity, CitizenModel> {
    private static final float EARLY_SCALE = 0.45F;
    private static final float MIDDLE_SCALE = 0.75F;
    private static final float LATE_SCALE = 1.0F;
    private static final float SKIN_DEFORMATION = 0.65F;
    private static final float BELLY_BOTTOM_Y = 10.5F;
    private static final float BELLY_HEIGHT = 5.0F;
    private final ModelPart belly;

    public PregnancyBellyLayer(RenderLayerParent<CitizenEntity, CitizenModel> parent) {
        super(parent);
        this.belly = createBellyModel(SKIN_DEFORMATION, 64);
    }

    /** render：使用 NPC 皮肤贴图绘制随孕期变化的腹部。 */
    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            CitizenEntity entity, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || entity.isChildNpc()) {
            return;
        }
        float scale = scaleForStage(entity.getPregnancyStage());
        if (scale <= 0.0F) {
            return;
        }

        configureBellyModel(belly, getParentModel().body, scale);
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        belly.render(poseStack, vertexConsumer, packedLight, LivingEntityRenderer.getOverlayCoords(entity, 0.0F));
    }

    /** createBellyModel：创建供皮肤和盔甲层复用的腹部网格。 */
    static ModelPart createBellyModel(float deformation, int textureHeight) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("belly",
                CubeListBuilder.create()
                        .texOffs(16, 16)
                        .addBox(-3.0F, BELLY_BOTTOM_Y - BELLY_HEIGHT, -4.0F, 6.0F, BELLY_HEIGHT, 4.5F,
                                new CubeDeformation(deformation)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, textureHeight).bakeRoot().getChild("belly");
    }

    /** configureBellyModel：对齐躯干并在缩放时固定腹部下缘。 */
    static void configureBellyModel(ModelPart belly, ModelPart body, float scale) {
        belly.copyFrom(body);
        belly.y += (1.0F - scale) * BELLY_BOTTOM_Y;
        belly.xScale = scale;
        belly.yScale = scale;
        belly.zScale = scale;
    }

    /** scaleForStage：将孕期阶段映射为早、中、晚期腹部尺寸。 */
    static float scaleForStage(String stage) {
        if (stage == null) {
            return 0.0F;
        }
        return switch (stage) {
            case "early" -> EARLY_SCALE;
            case "middle" -> MIDDLE_SCALE;
            case "late" -> LATE_SCALE;
            default -> 0.0F;
        };
    }
}
