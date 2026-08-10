package client.cn.kafei.simukraft.client.toast;

import common.cn.kafei.simukraft.SimuKraft;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/** ClientToastLayout: 计算并渲染单个通知的缩放布局。 */
final class ClientToastLayout {
    private static final ResourceLocation LOGO_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SimuKraft.MOD_ID, "textures/gui/logo.png");
    private static final int DEFAULT_WIDTH = 184;
    private static final int DEFAULT_HEIGHT = 48;
    private static final int BASE_TEXT_X = 36;
    private static final int BASE_ICON_X = 9;
    private static final int BASE_ICON_Y = 15;
    private static final int BASE_ICON_SIZE = 18;
    private static final int BASE_ICON_BACKING_PADDING = 2;
    private static final int BASE_TITLE_Y = 8;
    private static final int BASE_MESSAGE_Y = 22;
    private static final int BASE_LINE_HEIGHT = 10;
    private static final int BASE_BOTTOM_PADDING = 6;
    private static final int BASE_ITEM_GAP = 3;
    private static final int BASE_INLINE_ITEM_SIZE = 16;
    private static final int LOGO_TEXTURE_SIZE = 128;
    private static final float RENDER_DEPTH = 500.0F;

    private final Component title;
    private final ItemStack iconStack;
    private final Component itemPrefix;
    private final int itemPrefixWidth;
    private final List<FormattedCharSequence> messageLines;
    private final List<FormattedCharSequence> itemNameLines;
    private final int logicalWidth;
    private final int logicalHeight;
    private final int textX;
    private final int iconX;
    private final int iconSize;
    private final int iconY;
    private final int titleY;
    private final int messageY;
    private final int itemGap;
    private final int inlineItemSize;
    private final int sidePadding;
    private final float scale;

    private ClientToastLayout(Component title, ItemStack iconStack, LayoutMetrics metrics, int logicalWidth,
            int logicalHeight, int verticalOffset, float scale) {
        this.title = title;
        this.iconStack = iconStack;
        this.itemPrefix = metrics.itemPrefix();
        this.itemPrefixWidth = metrics.itemPrefixWidth();
        this.messageLines = metrics.messageLines();
        this.itemNameLines = metrics.itemNameLines();
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.textX = metrics.textX();
        this.iconX = metrics.iconX();
        this.iconSize = metrics.iconSize();
        this.iconY = metrics.iconY();
        this.titleY = verticalOffset + BASE_TITLE_Y;
        this.messageY = verticalOffset + BASE_MESSAGE_Y;
        this.itemGap = Math.max(1, Math.round(BASE_ITEM_GAP * metrics.horizontalScale()));
        this.inlineItemSize = Math.max(12, Math.round(BASE_INLINE_ITEM_SIZE * metrics.horizontalScale()));
        this.sidePadding = metrics.sidePadding();
        this.scale = scale;
    }

    /** create: 根据目标尺寸迭代计算字体缩放和自动换行。 */
    static ClientToastLayout create(Font font, Component title, Component message, ItemStack iconStack,
            int targetWidth, int targetHeight) {
        int safeWidth = Math.max(1, targetWidth);
        int safeHeight = Math.max(1, targetHeight);
        float maximumScale = safeHeight / (float) DEFAULT_HEIGHT;
        float minimumScale = 0.05F;
        float scale = findMaximumFittingScale(
                font, message, iconStack, safeWidth, safeHeight, minimumScale, maximumScale);
        int logicalWidth = Math.max(1, Math.round(safeWidth / scale));
        LayoutMetrics metrics = LayoutMetrics.create(font, message, iconStack, logicalWidth);
        int logicalHeight = Math.max(metrics.requiredHeight(), Math.round(safeHeight / scale));
        int verticalOffset = Math.max(0, (logicalHeight - metrics.requiredHeight()) / 2);
        Component safeTitle = title != null ? title : Component.translatable("toast.simukraft.title");
        return new ClientToastLayout(
                safeTitle,
                iconStack != null ? iconStack : ItemStack.EMPTY,
                metrics,
                logicalWidth,
                logicalHeight,
                verticalOffset,
                scale);
    }

    /** findMaximumFittingScale: 用二分搜索确定不溢出目标高度的最大字体比例。 */
    private static float findMaximumFittingScale(Font font, Component message, ItemStack iconStack,
            int targetWidth, int targetHeight, float minimumScale, float maximumScale) {
        float lowerBound = minimumScale;
        float upperBound = Math.max(minimumScale, maximumScale);
        for (int iteration = 0; iteration < 8; iteration++) {
            float candidate = (lowerBound + upperBound) / 2.0F;
            int logicalWidth = Math.max(1, Math.round(targetWidth / candidate));
            LayoutMetrics metrics = LayoutMetrics.create(font, message, iconStack, logicalWidth);
            if (candidate * metrics.requiredHeight() <= targetHeight) {
                lowerBound = candidate;
            } else {
                upperBound = candidate;
            }
        }
        return lowerBound;
    }

    /** render: 将布局缩放至配置的目标矩形。 */
    void render(GuiGraphics graphics, Font font, int x, int y, int count, String style) {
        graphics.pose().pushPose();
        // 通知必须位于其他 HUD 与屏幕元素之上，避免文本被后续图层覆盖。
        graphics.pose().translate(x, y, RENDER_DEPTH);
        graphics.pose().scale(scale, scale, 1.0F);
        int accentColor = accentColor(style);
        graphics.fill(0, 0, logicalWidth, logicalHeight, 0xE6101010);
        int accentWidth = Math.max(2, Math.round(4 * logicalWidth / (float) DEFAULT_WIDTH));
        graphics.fill(0, 0, accentWidth, logicalHeight, accentColor);
        graphics.fill(accentWidth, 0, logicalWidth, 1, 0x66FFFFFF);
        graphics.fill(accentWidth, logicalHeight - 1, logicalWidth, logicalHeight, 0x66000000);
        int centeredIconY = Math.max(iconY, (logicalHeight - iconSize) / 2);
        int backingPadding = Math.max(1, Math.round(BASE_ICON_BACKING_PADDING
                * logicalWidth / (float) DEFAULT_WIDTH));
        graphics.fill(
                iconX - backingPadding,
                centeredIconY - backingPadding,
                iconX + iconSize + backingPadding,
                centeredIconY + iconSize + backingPadding,
                accentColor);
        graphics.blit(
                LOGO_TEXTURE,
                iconX,
                centeredIconY,
                iconSize,
                iconSize,
                0.0F,
                0.0F,
                LOGO_TEXTURE_SIZE,
                LOGO_TEXTURE_SIZE,
                LOGO_TEXTURE_SIZE,
                LOGO_TEXTURE_SIZE);
        graphics.drawString(font, title, textX, titleY, 0xFFFFFFFF, false);
        if (count > 1) {
            String countText = "x" + count;
            graphics.drawString(
                    font,
                    countText,
                    logicalWidth - sidePadding - font.width(countText),
                    titleY,
                    accentColor,
                    false);
        }

        renderMessage(graphics, font);
        graphics.pose().popPose();
    }

    /** renderMessage: 绘制文本和可选物品信息。 */
    private void renderMessage(GuiGraphics graphics, Font font) {
        for (int index = 0; index < messageLines.size(); index++) {
            graphics.drawString(
                    font,
                    messageLines.get(index),
                    textX,
                    messageY + index * BASE_LINE_HEIGHT,
                    0xFFE6E6E6,
                    false);
        }
        if (iconStack.isEmpty()) {
            return;
        }

        int itemY = messageY + Math.max(1, messageLines.size()) * BASE_LINE_HEIGHT + itemGap;
        graphics.drawString(font, itemPrefix, textX, itemY + 4, 0xFFFFFFFF, false);
        int itemX = textX + itemPrefixWidth + 4;
        graphics.renderItem(iconStack, itemX, itemY);
        int itemTextX = itemX + inlineItemSize + 4;
        for (int index = 0; index < itemNameLines.size(); index++) {
            graphics.drawString(
                    font,
                    itemNameLines.get(index),
                    itemTextX,
                    itemY + 4 + index * BASE_LINE_HEIGHT,
                    0xFFFFFFFF,
                    false);
        }
    }

    /** accentColor: 返回通知样式对应的强调色。 */
    private static int accentColor(String style) {
        return switch ((style != null ? style : "info").toLowerCase(java.util.Locale.ROOT)) {
            case "success" -> 0xFF42D17A;
            case "warning" -> 0xFFFFC857;
            case "error" -> 0xFFFF5C5C;
            case "money" -> 0xFFFFD166;
            default -> 0xFF58A6FF;
        };
    }

    /** LayoutMetrics: 保存宽度变化后的换行和控件定位结果。 */
    private record LayoutMetrics(
            List<FormattedCharSequence> messageLines,
            List<FormattedCharSequence> itemNameLines,
            Component itemPrefix,
            int itemPrefixWidth,
            int requiredHeight,
            int textX,
            int iconX,
            int iconY,
            int iconSize,
            int sidePadding,
            float horizontalScale) {

        /** create: 根据逻辑宽度分割消息与物品名称。 */
        private static LayoutMetrics create(Font font, Component message, ItemStack iconStack,
                int logicalWidth) {
            float horizontalScale = logicalWidth / (float) DEFAULT_WIDTH;
            int textX = Math.max(24, Math.round(BASE_TEXT_X * horizontalScale));
            int sidePadding = Math.max(6, Math.round(8 * horizontalScale));
            int iconX = Math.max(6, Math.round(BASE_ICON_X * horizontalScale));
            int iconY = Math.max(8, Math.round(BASE_ICON_Y * horizontalScale));
            int iconSize = Math.max(12, Math.round(BASE_ICON_SIZE * horizontalScale));
            int textWidth = Math.max(1, logicalWidth - textX - sidePadding);
            Component safeMessage = message != null ? message : Component.empty();
            List<FormattedCharSequence> messageLines = List.copyOf(font.split(safeMessage, textWidth));
            ItemStack safeIconStack = iconStack != null ? iconStack : ItemStack.EMPTY;
            if (safeIconStack.isEmpty()) {
                int requiredHeight = Math.max(
                        DEFAULT_HEIGHT,
                        BASE_MESSAGE_Y + Math.max(1, messageLines.size()) * BASE_LINE_HEIGHT
                                + BASE_BOTTOM_PADDING);
                return new LayoutMetrics(
                        messageLines,
                        List.of(),
                        Component.empty(),
                        0,
                        requiredHeight,
                        textX,
                        iconX,
                        iconY,
                        iconSize,
                        sidePadding,
                        horizontalScale);
            }

            Component itemPrefix = Component.translatable("message.simukraft.material.required_prefix");
            int itemPrefixWidth = font.width(itemPrefix);
            int inlineItemSize = Math.max(12, Math.round(BASE_INLINE_ITEM_SIZE * horizontalScale));
            int itemTextWidth = Math.max(1, textWidth - itemPrefixWidth - inlineItemSize - 8);
            List<FormattedCharSequence> itemNameLines = List.copyOf(
                    font.split(safeIconStack.getHoverName(), itemTextWidth));
            int itemContentHeight = Math.max(
                    inlineItemSize,
                    Math.max(1, itemNameLines.size()) * BASE_LINE_HEIGHT);
            int requiredHeight = Math.max(
                    DEFAULT_HEIGHT,
                    BASE_MESSAGE_Y
                            + Math.max(1, messageLines.size()) * BASE_LINE_HEIGHT
                            + Math.max(1, Math.round(BASE_ITEM_GAP * horizontalScale))
                            + itemContentHeight
                            + BASE_BOTTOM_PADDING);
            return new LayoutMetrics(
                    messageLines,
                    itemNameLines,
                    itemPrefix,
                    itemPrefixWidth,
                    requiredHeight,
                    textX,
                    iconX,
                    iconY,
                    iconSize,
                    sidePadding,
                    horizontalScale);
        }
    }
}
