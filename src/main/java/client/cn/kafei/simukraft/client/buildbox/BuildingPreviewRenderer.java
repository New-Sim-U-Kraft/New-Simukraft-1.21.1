package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.OnlyIn;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@SuppressWarnings("null")
@EventBusSubscriber(modid = SimuKraft.MOD_ID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public final class BuildingPreviewRenderer {
    private static boolean loggedOnce;

    private BuildingPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!isPreviewRenderStage(event.getStage()) && event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !BuildingPreviewManager.isPreviewActive()) {
            return;
        }

        PreviewMesh mesh = BuildingPreviewManager.getCachedMesh();
        if (mesh == null || mesh.isEmpty()) {
            if (!loggedOnce) {
                SimuKraft.LOGGER.warn("SimuKraft: Preview mesh is empty during render – blocks likely failed to tesselate");
                loggedOnce = true;
            }
            return;
        }
        loggedOnce = false;

        renderPreviewMesh(mesh, event);
    }

    /** renderPreviewMesh: 按世界渲染阶段绘制一个已构建的预览网格。 */
    public static void renderPreviewMesh(PreviewMesh mesh, RenderLevelStageEvent event) {
        if (mesh == null || mesh.isEmpty()
                || (!isPreviewRenderStage(event.getStage()) && event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            PreviewSpecialBlockRenderer.render(mesh.entityBlocks(), event.getPoseStack(), minecraft.renderBuffers().bufferSource(), event.getCamera().getPosition());
            minecraft.renderBuffers().bufferSource().endBatch();
            return;
        }

        Matrix4f projection = event.getProjectionMatrix();
        Matrix4f modelView = event.getModelViewMatrix();
        Vec3 cameraPos = event.getCamera().getPosition();

        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            drawVbo(mesh.solidBuffer(), RenderType.solid(), modelView, projection, mesh.origin(), cameraPos, GameRenderer.getRendertypeSolidShader());
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS) {
            drawVbo(mesh.cutoutMippedBuffer(), RenderType.cutoutMipped(), modelView, projection, mesh.origin(), cameraPos, GameRenderer.getRendertypeCutoutMippedShader());
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            drawVbo(mesh.cutoutBuffer(), RenderType.cutout(), modelView, projection, mesh.origin(), cameraPos, GameRenderer.getRendertypeCutoutShader());
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            RenderSystem.depthMask(false);
            drawVbo(mesh.translucentBuffer(), RenderType.translucent(), modelView, projection, mesh.origin(), cameraPos, GameRenderer.getRendertypeTranslucentShader());
            RenderSystem.depthMask(true);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            drawVbo(mesh.tripwireBuffer(), RenderType.tripwire(), modelView, projection, mesh.origin(), cameraPos, GameRenderer.getRendertypeTripwireShader());
        }

        RenderSystem.enableCull();
    }

    private static boolean isPreviewRenderStage(RenderLevelStageEvent.Stage stage) {
        return stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS;
    }

    private static void drawVbo(VertexBuffer vertexBuffer, RenderType renderType, Matrix4f modelViewMatrix, Matrix4f projection, BlockPos origin, Vec3 cameraPos, ShaderInstance shader) {
        if (vertexBuffer == null) {
            return;
        }
        renderType.setupRenderState();

        RenderSystem.setShader(() -> shader);
        initShader(shader, renderType, modelViewMatrix, projection);

        if (shader.CHUNK_OFFSET != null) {
            shader.CHUNK_OFFSET.set((float) (origin.getX() - cameraPos.x), (float) (origin.getY() - cameraPos.y), (float) (origin.getZ() - cameraPos.z));
            shader.CHUNK_OFFSET.upload();
        }

        vertexBuffer.bind();
        vertexBuffer.draw();
        VertexBuffer.unbind();

        if (shader.CHUNK_OFFSET != null) {
            shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
            shader.CHUNK_OFFSET.upload();
        }

        shader.clear();
        renderType.clearRenderState();
    }

    private static void initShader(ShaderInstance shader, RenderType renderType, Matrix4f modelViewMatrix, Matrix4f projection) {
        shader.setDefaultUniforms(renderType.mode(), modelViewMatrix, projection, Minecraft.getInstance().getWindow());
        shader.apply();
    }
}
