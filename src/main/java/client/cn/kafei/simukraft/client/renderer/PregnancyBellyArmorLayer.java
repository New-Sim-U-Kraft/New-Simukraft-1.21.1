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
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** PregnancyBellyArmorLayer：为已装备胸甲的孕期 NPC 绘制外扩腹部盔甲。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class PregnancyBellyArmorLayer extends RenderLayer<CitizenEntity, CitizenModel> {
    private static final int ARMOR_TEXTURE_U = 17;
    private static final int ARMOR_TEXTURE_V = 21;
    private static final float BELLY_TOP_Y = 5.5F;
    private static final float BELLY_HEIGHT = 5.0F;
    private static final float BELLY_DEPTH = 4.0F;
    private final ModelPart belly;
    private final TextureAtlas armorTrimAtlas;

    public PregnancyBellyArmorLayer(RenderLayerParent<CitizenEntity, CitizenModel> parent,
            ModelManager modelManager) {
        super(parent);
        this.belly = createArmorBellyModel();
        this.armorTrimAtlas = modelManager.getAtlas(Sheets.ARMOR_TRIMS_SHEET);
    }

    /** render：使用已装备胸甲的材质层、颜色和特效绘制腹部覆盖部分。 */
    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            CitizenEntity entity, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || entity.isChildNpc()) {
            return;
        }
        float scale = PregnancyBellyLayer.scaleForStage(entity.getPregnancyStage());
        if (scale <= 0.0F) {
            return;
        }

        ItemStack chestArmor = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chestArmor.getItem() instanceof ArmorItem armorItem)
                || armorItem.getEquipmentSlot() != EquipmentSlot.CHEST) {
            return;
        }

        PregnancyBellyLayer.configureBellyModel(belly, getParentModel().body, scale);
        IClientItemExtensions extensions = IClientItemExtensions.of(chestArmor);
        ArmorMaterial armorMaterial = armorItem.getMaterial().value();
        renderArmorLayers(poseStack, buffer, packedLight, entity, chestArmor, armorMaterial, extensions);
        renderTrim(poseStack, buffer, packedLight, chestArmor, armorItem);
        if (chestArmor.hasFoil()) {
            VertexConsumer glint = buffer.getBuffer(RenderType.armorEntityGlint());
            belly.render(poseStack, glint, packedLight, OverlayTexture.NO_OVERLAY);
        }
    }

    /** createArmorBellyModel：创建与原版胸甲躯干中下段连续衔接的腹部网格。 */
    private static ModelPart createArmorBellyModel() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("belly",
                CubeListBuilder.create()
                        .texOffs(ARMOR_TEXTURE_U, ARMOR_TEXTURE_V)
                        .addBox(-3.0F, BELLY_TOP_Y, -4.0F, 6.0F, BELLY_HEIGHT, BELLY_DEPTH,
                                new CubeDeformation(1.0F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("belly");
    }

    /** renderArmorLayers：按原版材质层顺序绘制胸甲基础贴图和染色层。 */
    private void renderArmorLayers(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            CitizenEntity entity, ItemStack chestArmor, ArmorMaterial armorMaterial,
            IClientItemExtensions extensions) {
        int fallbackColor = extensions.getDefaultDyeColor(chestArmor);
        for (int layerIndex = 0; layerIndex < armorMaterial.layers().size(); layerIndex++) {
            ArmorMaterial.Layer armorLayer = armorMaterial.layers().get(layerIndex);
            int tintColor = extensions.getArmorLayerTintColor(chestArmor, entity, armorLayer, layerIndex,
                    fallbackColor);
            if (tintColor != 0) {
                VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.armorCutoutNoCull(
                        ClientHooks.getArmorTexture(entity, chestArmor, armorLayer, false, EquipmentSlot.CHEST)));
                belly.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, tintColor);
            }
        }
    }

    /** renderTrim：为外扩腹部追加与胸甲一致的盔甲饰纹。 */
    private void renderTrim(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            ItemStack chestArmor, ArmorItem armorItem) {
        ArmorTrim trim = chestArmor.get(DataComponents.TRIM);
        if (trim == null) {
            return;
        }
        TextureAtlasSprite texture = armorTrimAtlas.getSprite(trim.outerTexture(armorItem.getMaterial()));
        VertexConsumer vertexConsumer = texture.wrap(
                buffer.getBuffer(Sheets.armorTrimsSheet(trim.pattern().value().decal())));
        belly.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
    }
}
