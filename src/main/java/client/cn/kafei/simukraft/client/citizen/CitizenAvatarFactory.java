package client.cn.kafei.simukraft.client.citizen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.lowdragmc.lowdraglib2.client.shader.LDLibRenderTypes;
import common.cn.kafei.simukraft.SimuKraft;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;


@OnlyIn(Dist.CLIENT)
public final class CitizenAvatarFactory {
    private static final int FRAME_BACKGROUND = 0xFF7A8085;
    private static final int FRAME_INNER = 0xFF646A6F;
    private static final String MOD_ID = "simukraft";

    private CitizenAvatarFactory() {
    }

    public static UIElement createHead(String skinPath, int borderColor) {
        UIElement head = new UIElement();
        try {
            head.style(style -> style.backgroundTexture(new GuiTextureGroup(
                    new ColorRectTexture(FRAME_BACKGROUND),
                    new ColorRectTexture(FRAME_INNER).scale(0.92f),
                    createAvatarTexture(skinPath),
                    new ColorBorderTexture(1, borderColor)
            )));
        } catch (Exception exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to create citizen avatar skinPath={} borderColor={}", skinPath, Integer.toHexString(borderColor), exception);
            head.style(style -> style.backgroundTexture(new GuiTextureGroup(
                    new ColorRectTexture(FRAME_BACKGROUND),
                    new ColorBorderTexture(1, borderColor)
            )));
        }
        return head;
    }

    public static boolean isValidSkinPath(String skinPath) {
        return skinPath != null && !skinPath.isBlank() && !skinPath.contains("..") && !skinPath.startsWith("/");
    }

    private static IGuiTexture createAvatarTexture(String skinPath) {
        if (!isValidSkinPath(skinPath)) {
            return new ColorRectTexture(0xFF8A9298).scale(0.78f);
        }
        try {
            ResourceLocation textureLocation = resolveSkinTexture(skinPath);
            return (graphics, mouseX, mouseY, x, y, width, height, partialTicks) -> drawAvatar(graphics, textureLocation, x, y, width, height);
        } catch (Exception exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to create custom-draw avatar texture for skinPath={}", skinPath, exception);
            return new ColorRectTexture(0xFF8A9298).scale(0.78f);
        }
    }

    private static ResourceLocation resolveSkinTexture(String skinPath) {
        String normalized = skinPath.replace('\\', '/').trim();
        if (normalized.startsWith(MOD_ID + ":")) {
            normalized = normalized.substring((MOD_ID + ":").length());
        }
        if (normalized.startsWith("assets/" + MOD_ID + "/")) {
            normalized = normalized.substring(("assets/" + MOD_ID + "/").length());
        }
        if (normalized.endsWith(".png")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (!normalized.startsWith("textures/")) {
            normalized = normalized.startsWith("entity/") ? "textures/" + normalized : "textures/entity/" + normalized;
        }
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, normalized + ".png");
    }

    /** blitHead：在画布上绘制头像，已故市民使用灰阶着色。 */
    public static void blitHead(GuiGraphics graphics, String skinPath, float x, float y, float size, boolean grayscale) {
        int frame = grayscale ? 0xFF2A2A2A : FRAME_BACKGROUND;
        int inner = grayscale ? 0xFF3F3F3F : FRAME_INNER;
        graphics.fill((int) x, (int) y, (int) (x + size), (int) (y + size), frame);
        graphics.fill((int) (x + 1), (int) (y + 1), (int) (x + size - 1), (int) (y + size - 1), inner);
        if (!isValidSkinPath(skinPath)) {
            graphics.fill((int) (x + size * 0.08f), (int) (y + size * 0.08f),
                    (int) (x + size * 0.92f), (int) (y + size * 0.92f), grayscale ? 0xFF6A6A6A : 0xFF8A9298);
            return;
        }
        try {
            drawAvatar(graphics, resolveSkinTexture(skinPath), x, y, size, size, grayscale);
        } catch (Exception exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to blit citizen avatar skinPath={}", skinPath, exception);
        }
    }

    private static void drawAvatar(GuiGraphics graphics, ResourceLocation textureLocation, float x, float y, float width, float height) {
        drawAvatar(graphics, textureLocation, x, y, width, height, false);
    }

    private static void drawAvatar(GuiGraphics graphics, ResourceLocation textureLocation, float x, float y,
                                   float width, float height, boolean grayscale) {
        try {
            float insetX = width * 0.04f;
            float insetY = height * 0.04f;
            float drawWidth = width * 0.92f;
            float drawHeight = height * 0.92f;
            int color = grayscale ? 0xFF9A9A9A : 0xFFFFFFFF;
            drawFaceLayer(graphics, textureLocation, x + insetX, y + insetY, drawWidth, drawHeight, 8, 8, 8, 8, color);
            drawFaceLayer(graphics, textureLocation, x + insetX, y + insetY, drawWidth, drawHeight, 40, 8, 8, 8, color);
            if (grayscale) {
                graphics.fill((int) (x + insetX), (int) (y + insetY),
                        (int) (x + insetX + drawWidth), (int) (y + insetY + drawHeight), 0x66202020);
            }
        } catch (Exception exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to draw avatar texture {}", textureLocation, exception);
        }
    }

    private static void drawFaceLayer(GuiGraphics graphics, ResourceLocation textureLocation, float x, float y, float width, float height,
                                      int u, int v, int regionWidth, int regionHeight, int color) {
        var matrix = graphics.pose().last().pose();
        var buffer = graphics.bufferSource().getBuffer(LDLibRenderTypes.guiTexture(textureLocation));
        float texSize = 64.0f;
        float u0 = u / texSize;
        float v0 = v / texSize;
        float u1 = (u + regionWidth) / texSize;
        float v1 = (v + regionHeight) / texSize;
        buffer.addVertex(matrix, x, y + height, 0).setUv(u0, v1).setColor(color);
        buffer.addVertex(matrix, x + width, y + height, 0).setUv(u1, v1).setColor(color);
        buffer.addVertex(matrix, x + width, y, 0).setUv(u1, v0).setColor(color);
        buffer.addVertex(matrix, x, y, 0).setUv(u0, v0).setColor(color);
    }
}
