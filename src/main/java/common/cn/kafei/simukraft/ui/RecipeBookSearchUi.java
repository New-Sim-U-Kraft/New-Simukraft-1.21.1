package common.cn.kafei.simukraft.ui;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class RecipeBookSearchUi {
    public static final int FRAME_WIDTH = 95;
    public static final int FRAME_TEXTURE_WIDTH = 89;
    public static final int FRAME_HEIGHT = 17;
    public static final int TEXT_OFFSET_X = 20;
    public static final int TEXT_OFFSET_Y = 2;
    public static final int TEXT_WIDTH = 72;
    public static final int TEXT_HEIGHT = 13;
    private static final ResourceLocation RECIPE_BOOK_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/recipe_book.png");

    private RecipeBookSearchUi() {
    }

    /** createField: 创建原版配方书搜索框文本输入层。 */
    public static TextField createField(int left, int top, String value, Consumer<String> responder) {
        return createField(left, top, TEXT_WIDTH, TEXT_HEIGHT, value, responder);
    }

    /** createField: 创建可自定义尺寸的配方书搜索框文本输入层。 */
    public static TextField createField(int left, int top, int width, int height, String value, Consumer<String> responder) {
        TextField field = new TextField();
        field.setAnyString();
        field.setText(value == null ? "" : value, false);
        field.setTextResponder(responder);
        field.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(top)
                .width(width)
                .height(height)
                .paddingAll(1));
        field.style(style -> style.backgroundTexture(IGuiTexture.EMPTY).zIndex(30));
        field.textFieldStyle(style -> style.textColor(0xFFFFFFFF)
                .cursorColor(0xFFFFFFFF)
                .textShadow(false)
                .fontSize(9.0F)
                .placeholder(Component.translatable("gui.recipebook.search_hint"))
                .focusOverlay(IGuiTexture.EMPTY));
        return field;
    }

    /** frameElement: 创建只负责绘制原版配方书搜索框外框的视觉层。 */
    public static UIElement frameElement(int left, int top) {
        return frameElement(left, top, FRAME_WIDTH);
    }

    /** frameElement: 创建可变宽搜索框外观，保留原版放大镜图标。 */
    public static UIElement frameElement(int left, int top, int width) {
        return frameElement(left, top, width, FRAME_HEIGHT);
    }

    /** frameElement: 创建可变宽高搜索框外观，用于和同排按钮保持高度一致。 */
    public static UIElement frameElement(int left, int top, int width, int height) {
        int frameWidth = Math.max(FRAME_WIDTH, width);
        int frameHeight = Math.max(FRAME_HEIGHT, height);
        int textOffsetY = Math.max(1, (frameHeight - TEXT_HEIGHT) / 2);
        int textWidth = Math.max(TEXT_WIDTH, frameWidth - TEXT_OFFSET_X - 4);
        return new SearchFrameElement(frameWidth, Math.min(FRAME_TEXTURE_WIDTH, frameWidth), frameHeight, TEXT_OFFSET_X, textOffsetY, textWidth, TEXT_HEIGHT)
                .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                        .left(left)
                        .top(top)
                        .width(frameWidth)
                        .height(frameHeight));
    }

    /** renderFrame: 绘制原版配方书搜索框贴图和文本底色。 */
    public static void renderFrame(GUIContext guiContext, int left, int top, int frameWidth, int frameTextureWidth,
                                   int frameHeight, int textOffsetX, int textOffsetY, int textWidth, int textHeight) {
        int textureTop = top + Math.max(0, (frameHeight - FRAME_HEIGHT) / 2);
        int textureHeight = Math.min(frameHeight, FRAME_HEIGHT);
        guiContext.graphics.blit(RECIPE_BOOK_LOCATION, left, textureTop, 0, 9.0F, 12.0F, frameTextureWidth, textureHeight, 256, 256);
        int textBorderRight = Math.min(left + frameWidth - 1, left + textOffsetX + textWidth + 2);
        guiContext.graphics.fill(left + textOffsetX - 1, top + textOffsetY - 1, textBorderRight,
                top + textOffsetY + textHeight + 1, 0xFF101010);
        guiContext.graphics.fill(left + textOffsetX, top + textOffsetY, textBorderRight - 1,
                top + textOffsetY + textHeight, 0xFF000000);
    }

    private static final class SearchFrameElement extends UIElement {
        private final int frameWidth;
        private final int frameTextureWidth;
        private final int frameHeight;
        private final int textOffsetX;
        private final int textOffsetY;
        private final int textWidth;
        private final int textHeight;

        private SearchFrameElement(int frameWidth, int frameTextureWidth, int frameHeight, int textOffsetX, int textOffsetY, int textWidth, int textHeight) {
            this.frameWidth = frameWidth;
            this.frameTextureWidth = frameTextureWidth;
            this.frameHeight = frameHeight;
            this.textOffsetX = textOffsetX;
            this.textOffsetY = textOffsetY;
            this.textWidth = textWidth;
            this.textHeight = textHeight;
            setAllowHitTest(false);
        }

        /** drawBackgroundAdditional: 绘制搜索框外框，不参与交互。 */
        @Override
        public void drawBackgroundAdditional(GUIContext guiContext) {
            renderFrame(guiContext, (int) getPositionX(), (int) getPositionY(), frameWidth, frameTextureWidth,
                    frameHeight, textOffsetX, textOffsetY, textWidth, textHeight);
        }
    }
}
