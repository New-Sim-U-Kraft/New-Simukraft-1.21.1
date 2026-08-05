package client.cn.kafei.simukraft.client.ui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class SimuKraftFlexLayout {
    private static final int MIN_SCREEN_WIDTH = 320;
    private static final int MIN_SCREEN_HEIGHT = 240;
    private static final float TOP_ACTION_LEFT_RATIO = 0.0F;
    private static final float TOP_ACTION_TOP_RATIO = 0.02F;
    private static final float TOP_ACTION_WIDTH_RATIO = 0.12F;
    private static final float TOP_ACTION_HEIGHT_RATIO = 0.08F;
    private static final float WATERMARK_LEFT_RATIO = 0.635F;
    private static final float WATERMARK_TOP_RATIO = 0.026F;
    private static final float WATERMARK_WIDTH_RATIO = 0.36F;
    private static final float WATERMARK_HEIGHT_RATIO = 0.055F;

    private SimuKraftFlexLayout() {
    }

    public static ScreenSize screenSize() {
        var window = Minecraft.getInstance().getWindow();
        return new ScreenSize(
                Math.max(MIN_SCREEN_WIDTH, window.getGuiScaledWidth()),
                Math.max(MIN_SCREEN_HEIGHT, window.getGuiScaledHeight())
        );
    }

    public static UIElement root(ScreenSize screenSize) {
        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
        root.addChild(SimuKraftUiTheme.createShellPanel(screenSize.width(), screenSize.height()));
        return root;
    }

    public static UIElement centeredColumn(int width, int maxWidth, int height, int topOffset) {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.maxWidth(maxWidth);
            layout.height(height);
            layout.marginHorizontal(0);
            layout.marginTop(topOffset);
            layout.paddingAll(14);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.alignSelf(AlignItems.CENTER);
            layout.gapAll(0);
            layout.width(width);
        });
    }

    public static UIElement pageColumn() {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.paddingAll(10);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(6);
        });
    }

    public static UIElement row(int gap) {
        return new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.flexWrap(FlexWrap.WRAP);
            layout.gapAll(gap);
        });
    }

    public static UIElement spacer(float flex) {
        return new UIElement().layout(layout -> layout.flex(flex));
    }

    public static UIElement absoluteRegion(int left, int top, int width, int height) {
        return new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(left);
            layout.top(top);
            layout.width(Math.max(1, width));
            layout.height(Math.max(1, height));
        });
    }

    public static UIElement topActionRegion(ScreenSize screenSize) {
        UIElement region = relativeRegion(screenSize, TOP_ACTION_LEFT_RATIO, TOP_ACTION_TOP_RATIO, TOP_ACTION_WIDTH_RATIO, TOP_ACTION_HEIGHT_RATIO);
        region.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        return region;
    }

    public static UIElement watermarkRegion(ScreenSize screenSize) {
        UIElement region = relativeRegion(screenSize, WATERMARK_LEFT_RATIO, WATERMARK_TOP_RATIO, WATERMARK_WIDTH_RATIO, WATERMARK_HEIGHT_RATIO);
        region.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.FLEX_END);
        });
        return region;
    }

    /** 添加统一顶部操作区：左侧返回/完成按钮，右侧显示模组水印。 */
    public static void addTopChrome(UIElement root, ScreenSize screenSize, Component buttonText, Runnable action) {
        Button topButton = new Button();
        topButton.setText(buttonText);
        topButton.setOnClick(event -> action.run());
        topButton.layout(layout -> {
            layout.width(topActionButtonWidth(screenSize));
            layout.height(topActionButtonHeight(screenSize));
        });
        UIElement topActionRegion = topActionRegion(screenSize);
        topActionRegion.addChild(topButton);
        root.addChild(topActionRegion);

        UIElement copyrightLabel = text(Component.translatable("gui.copyright"), watermarkWidth(screenSize), SimuKraftUiTheme.TEXT_MUTED_COLOR, TextTexture.TextType.RIGHT, true);
        copyrightLabel.layout(layout -> {
            layout.widthPercent(100);
            layout.height(Math.max(12, Math.min(18, Math.round(screenSize.height() * WATERMARK_HEIGHT_RATIO))));
        });
        UIElement watermarkRegion = watermarkRegion(screenSize);
        watermarkRegion.addChild(copyrightLabel);
        root.addChild(watermarkRegion);
    }

    public static int watermarkWidth(ScreenSize screenSize) {
        return Math.max(1, Math.round(screenSize.width() * WATERMARK_WIDTH_RATIO));
    }

    public static int topActionButtonWidth(ScreenSize screenSize) {
        return Math.max(50, Math.round(screenSize.width() * TOP_ACTION_WIDTH_RATIO * 0.82F));
    }

    public static int topActionButtonHeight(ScreenSize screenSize) {
        return Math.max(20, Math.round(screenSize.height() * TOP_ACTION_HEIGHT_RATIO * 0.62F));
    }

    public static UIElement text(Component text, int width, int color, TextTexture.TextType type, boolean dropShadow) {
        UIElement element = new UIElement();
        element.style(style -> style.backgroundTexture(new TextTexture(text.getString())
                .setWidth(width)
                .setType(type)
                .setColor(color)
                .setDropShadow(dropShadow)));
        return element;
    }

    public static UIElement cornerBar() {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(28);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.FLEX_START);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
        });
    }

    public static UIElement absoluteLayer() {
        return new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.widthPercent(100);
            layout.heightPercent(100);
        });
    }

    private static UIElement relativeRegion(ScreenSize screenSize, float leftRatio, float topRatio, float widthRatio, float heightRatio) {
        int left = clamp(Math.round(screenSize.width() * leftRatio), 0, screenSize.width() - 1);
        int top = clamp(Math.round(screenSize.height() * topRatio), 0, screenSize.height() - 1);
        int width = clamp(Math.round(screenSize.width() * widthRatio), 1, screenSize.width() - left);
        int height = clamp(Math.round(screenSize.height() * heightRatio), 1, screenSize.height() - top);
        return absoluteRegion(left, top, width, height);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record ScreenSize(int width, int height) {
    }
}
