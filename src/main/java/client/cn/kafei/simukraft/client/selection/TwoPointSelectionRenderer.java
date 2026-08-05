package client.cn.kafei.simukraft.client.selection;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class TwoPointSelectionRenderer {
    private static final int COLOR_POINT_1 = 0xD8FF3333;
    private static final int COLOR_POINT_2 = 0xD8FFE066;
    private static final int COLOR_SELECTION = 0xC866CCFF;
    private static final double POINT_INFLATE = 0.04D;

    private TwoPointSelectionRenderer() {
    }

    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !TwoPointSelectionManager.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        TwoPointSelectionManager.SelectionState state = TwoPointSelectionManager.state();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        renderPoint(poseStack, cameraPos, state.point1(), COLOR_POINT_1);
        renderPoint(poseStack, cameraPos, state.point2(), COLOR_POINT_2);
        AABB selection = TwoPointSelectionManager.selectedAabb();
        if (selection != null) {
            renderWireBox(poseStack, cameraPos, selection.inflate(POINT_INFLATE), COLOR_SELECTION);
        }
    }

    private static void renderPoint(PoseStack poseStack, Vec3 cameraPos, BlockPos pos, int color) {
        if (pos == null) {
            return;
        }
        renderWireBox(poseStack, cameraPos, new AABB(pos).inflate(POINT_INFLATE), color);
    }

    private static void renderWireBox(PoseStack poseStack, Vec3 cameraPos, AABB bounds, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        double minX = bounds.minX - cameraPos.x;
        double minY = bounds.minY - cameraPos.y;
        double minZ = bounds.minZ - cameraPos.z;
        double maxX = bounds.maxX - cameraPos.x;
        double maxY = bounds.maxY - cameraPos.y;
        double maxZ = bounds.maxZ - cameraPos.z;
        line(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        line(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        line(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        line(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);
        line(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        line(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);
        line(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        line(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float red, float green, float blue, float alpha) {
        buffer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(red, green, blue, alpha);
    }
}
