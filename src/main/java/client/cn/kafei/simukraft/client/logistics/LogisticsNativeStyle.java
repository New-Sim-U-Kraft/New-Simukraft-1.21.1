package client.cn.kafei.simukraft.client.logistics;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

@SuppressWarnings("null")
final class LogisticsNativeStyle {
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT_DIM = 0xFFAAAAAA;
    static final int TEXT_MUTED = 0xFF888888;
    static final int TEXT_GOOD = 0xFF55FF55;
    static final int TEXT_WARN = 0xFFFFDD66;
    static final int TEXT_BAD = 0xFFFF6666;
    static final int PANEL = 0xE0111122;
    static final int PANEL_DARK = 0xCC0D0D1A;
    static final int PANEL_LINE = 0xFF333355;
    static final int WAREHOUSE = 0xFF4488FF;
    static final int CLIENT = 0xFFFF8844;
    static final int CHANNEL = 0xCC55FF55;
    static final int CHANNEL_DISABLED = 0x66888888;
    static final int STATUS_ON = 0xCC1F8F3A;
    static final int STATUS_OFF = 0xCC9B2C2C;
    static final int STATUS_ON_LINE = 0xFF55FF77;
    static final int STATUS_OFF_LINE = 0xFFFF7777;

    private LogisticsNativeStyle() {
    }

    /** drawBackdrop: 绘制旧版物流界面的半透明深色背景。 */
    static void drawBackdrop(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0x660D0D1A);
    }

    /** drawPanel: 绘制旧版直角信息面板。 */
    static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 1, PANEL_LINE);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_LINE);
        graphics.fill(x, y, x + 1, y + height, PANEL_LINE);
        graphics.fill(x + width - 1, y, x + width, y + height, PANEL_LINE);
    }

    /** button: 创建原生按钮，外观跟随 Minecraft 默认样式。 */
    static Button button(Component text, int x, int y, int width, int height, Runnable action) {
        return Button.builder(text, ignored -> action.run()).bounds(x, y, width, height).build();
    }

    /** drawFitString: 在给定宽度内绘制单行文本，过长时截断。 */
    static void drawFitString(GuiGraphics graphics, Font font, String text, int x, int y, int maxWidth, int color) {
        graphics.drawString(font, fit(font, text, maxWidth), x, y, color, false);
    }

    /** drawFitString: 在给定宽度内绘制组件文本，过长时截断。 */
    static void drawFitString(GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth, int color) {
        drawFitString(graphics, font, text.getString(), x, y, maxWidth, color);
    }

    /** drawStatusBadge: 绘制路线启停状态的红绿文字色块。 */
    static void drawStatusBadge(GuiGraphics graphics, Font font, boolean enabled, int x, int y) {
        String text = enabled ? "ON" : "OFF";
        int width = 25;
        int height = 11;
        int fill = enabled ? STATUS_ON : STATUS_OFF;
        int line = enabled ? STATUS_ON_LINE : STATUS_OFF_LINE;
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, line);
        graphics.fill(x, y + height - 1, x + width, y + height, line);
        graphics.fill(x, y, x + 1, y + height, line);
        graphics.fill(x + width - 1, y, x + width, y + height, line);
        graphics.drawCenteredString(font, text, x + width / 2, y + 1, TEXT);
    }

    /** fit: 将长文本压缩成带省略号的单行内容。 */
    static String fit(Font font, String text, int maxWidth) {
        if (text == null || maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return font.plainSubstrByWidth(text, Math.max(1, maxWidth));
        }
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth - ellipsisWidth)) + ellipsis;
    }

    /** posText: 格式化方块坐标，供列表和提示使用。 */
    static String posText(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /** clamp: 将数值限制到指定范围。 */
    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
