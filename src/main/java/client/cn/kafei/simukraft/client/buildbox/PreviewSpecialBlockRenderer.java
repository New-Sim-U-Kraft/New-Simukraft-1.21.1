package client.cn.kafei.simukraft.client.buildbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Calendar;
import java.util.List;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class PreviewSpecialBlockRenderer {
    private static final boolean XMAS_CHEST_TEXTURES = isXmasChestTexturePeriod();

    private static ModelPart bedHeadRoot;
    private static ModelPart bedFootRoot;
    private static ModelPart chestLid;
    private static ModelPart chestBottom;
    private static ModelPart chestLock;
    private static ModelPart doubleLeftChestLid;
    private static ModelPart doubleLeftChestBottom;
    private static ModelPart doubleLeftChestLock;
    private static ModelPart doubleRightChestLid;
    private static ModelPart doubleRightChestBottom;
    private static ModelPart doubleRightChestLock;

    private PreviewSpecialBlockRenderer() {
    }

    /** render: 渲染没有普通方块模型的预览方块。 */
    public static void render(List<PreviewBlockData> blocks, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 cameraPos) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        for (PreviewBlockData block : blocks) {
            BlockState state = block.state();
            if (state.getBlock() instanceof BedBlock bedBlock) {
                renderBed(block, bedBlock, poseStack, bufferSource, cameraPos);
            } else if (state.getBlock() instanceof AbstractChestBlock<?>) {
                renderChest(block, poseStack, bufferSource, cameraPos);
            } else {
                renderBlockEntity(block, poseStack, bufferSource, cameraPos);
            }
        }

        bufferSource.endBatch(Sheets.bedSheet());
        bufferSource.endBatch(Sheets.chestSheet());
        bufferSource.endBatch(Sheets.bannerSheet());
    }

    private static void renderBlockEntity(PreviewBlockData block, PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        CompoundTag data = block.copyBlockEntityData();
        if (data == null || !(block.state().getBlock() instanceof EntityBlock entityBlock)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BlockEntity blockEntity = entityBlock.newBlockEntity(block.pos(), block.state());
        if (blockEntity == null) {
            return;
        }
        try {
            blockEntity.setLevel(minecraft.level);
            blockEntity.loadWithComponents(data, minecraft.level.registryAccess());
            BlockEntityRenderer<BlockEntity> renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
            if (renderer == null) {
                return;
            }
            poseStack.pushPose();
            try {
                poseStack.translate(block.pos().getX() - cameraPos.x, block.pos().getY() - cameraPos.y, block.pos().getZ() - cameraPos.z);
                renderer.render(blockEntity, 0.0F, poseStack, bufferSource, block.packedLight(), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            } finally {
                poseStack.popPose();
            }
        } catch (RuntimeException exception) {
            common.cn.kafei.simukraft.SimuKraft.LOGGER.warn("Simukraft: Failed to render preview block entity at {}", block.pos(), exception);
        }
    }

    /** renderChest: 使用原版箱子模型渲染普通箱、陷阱箱和末影箱。 */
    private static void renderChest(PreviewBlockData block, PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        ensureChestModels();
        BlockState state = block.state();
        ChestType chestType = state.hasProperty(ChestBlock.TYPE) ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
        Direction facing = state.hasProperty(ChestBlock.FACING) ? state.getValue(ChestBlock.FACING) : Direction.SOUTH;
        VertexConsumer vertexConsumer = chestMaterial(state, chestType).buffer(bufferSource, RenderType::entityCutout);

        poseStack.pushPose();
        poseStack.translate(block.pos().getX() - cameraPos.x, block.pos().getY() - cameraPos.y, block.pos().getZ() - cameraPos.z);
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        if (chestType == ChestType.LEFT) {
            renderChestParts(poseStack, vertexConsumer, doubleLeftChestLid, doubleLeftChestLock, doubleLeftChestBottom, block.packedLight());
        } else if (chestType == ChestType.RIGHT) {
            renderChestParts(poseStack, vertexConsumer, doubleRightChestLid, doubleRightChestLock, doubleRightChestBottom, block.packedLight());
        } else {
            renderChestParts(poseStack, vertexConsumer, chestLid, chestLock, chestBottom, block.packedLight());
        }

        poseStack.popPose();
    }

    /** chestMaterial: 选择与原版一致的箱子贴图。 */
    private static Material chestMaterial(BlockState state, ChestType chestType) {
        if (state.getBlock() instanceof EnderChestBlock) {
            return Sheets.ENDER_CHEST_LOCATION;
        }
        if (XMAS_CHEST_TEXTURES) {
            return switch (chestType) {
                case LEFT -> Sheets.CHEST_XMAS_LOCATION_LEFT;
                case RIGHT -> Sheets.CHEST_XMAS_LOCATION_RIGHT;
                case SINGLE -> Sheets.CHEST_XMAS_LOCATION;
            };
        }
        if (state.is(Blocks.TRAPPED_CHEST)) {
            return switch (chestType) {
                case LEFT -> Sheets.CHEST_TRAP_LOCATION_LEFT;
                case RIGHT -> Sheets.CHEST_TRAP_LOCATION_RIGHT;
                case SINGLE -> Sheets.CHEST_TRAP_LOCATION;
            };
        }
        return switch (chestType) {
            case LEFT -> Sheets.CHEST_LOCATION_LEFT;
            case RIGHT -> Sheets.CHEST_LOCATION_RIGHT;
            case SINGLE -> Sheets.CHEST_LOCATION;
        };
    }

    /** ensureChestModels: 首次预览箱子时懒加载原版模型层。 */
    private static void ensureChestModels() {
        if (chestLid != null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ModelPart singleChest = minecraft.getEntityModels().bakeLayer(ModelLayers.CHEST);
        chestBottom = singleChest.getChild("bottom");
        chestLid = singleChest.getChild("lid");
        chestLock = singleChest.getChild("lock");

        ModelPart doubleLeftChest = minecraft.getEntityModels().bakeLayer(ModelLayers.DOUBLE_CHEST_LEFT);
        doubleLeftChestBottom = doubleLeftChest.getChild("bottom");
        doubleLeftChestLid = doubleLeftChest.getChild("lid");
        doubleLeftChestLock = doubleLeftChest.getChild("lock");

        ModelPart doubleRightChest = minecraft.getEntityModels().bakeLayer(ModelLayers.DOUBLE_CHEST_RIGHT);
        doubleRightChestBottom = doubleRightChest.getChild("bottom");
        doubleRightChestLid = doubleRightChest.getChild("lid");
        doubleRightChestLock = doubleRightChest.getChild("lock");
    }

    /** renderChestParts: 以关闭状态绘制箱盖、锁扣和箱体。 */
    private static void renderChestParts(PoseStack poseStack, VertexConsumer vertexConsumer, ModelPart lid, ModelPart lock, ModelPart bottom, int packedLight) {
        lid.xRot = 0.0F;
        lock.xRot = 0.0F;
        lid.render(poseStack, vertexConsumer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        lock.render(poseStack, vertexConsumer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        bottom.render(poseStack, vertexConsumer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
    }

    /** renderBed: 使用原版床模型渲染床头和床尾。 */
    private static void renderBed(PreviewBlockData block, BedBlock bedBlock, PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        ensureBedModels();
        BlockState state = block.state();
        ModelPart bedPartRoot = state.getValue(BedBlock.PART) == BedPart.HEAD ? bedHeadRoot : bedFootRoot;
        Material material = Sheets.BED_TEXTURES[bedBlock.getColor().getId()];

        poseStack.pushPose();
        poseStack.translate(block.pos().getX() - cameraPos.x, block.pos().getY() - cameraPos.y, block.pos().getZ() - cameraPos.z);
        renderBedPiece(poseStack, bufferSource, bedPartRoot, state.getValue(BedBlock.FACING), material, block.packedLight());
        poseStack.popPose();
    }

    /** ensureBedModels: 首次预览床时懒加载原版模型层。 */
    private static void ensureBedModels() {
        if (bedHeadRoot != null && bedFootRoot != null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        bedHeadRoot = minecraft.getEntityModels().bakeLayer(ModelLayers.BED_HEAD);
        bedFootRoot = minecraft.getEntityModels().bakeLayer(ModelLayers.BED_FOOT);
    }

    /** renderBedPiece: 按床朝向渲染单个床部件。 */
    private static void renderBedPiece(PoseStack poseStack, MultiBufferSource bufferSource, ModelPart modelPart, Direction facing, Material material, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.5625F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F + facing.toYRot()));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        VertexConsumer vertexConsumer = material.buffer(bufferSource, RenderType::entitySolid);
        modelPart.render(poseStack, vertexConsumer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /** isXmasChestTexturePeriod: 保留原版圣诞箱子贴图规则。 */
    private static boolean isXmasChestTexturePeriod() {
        Calendar calendar = Calendar.getInstance();
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        return month == 12 && day >= 24 && day <= 26;
    }
}
