package client.cn.kafei.simukraft.client.geology;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

/** GeologicalSurveyHintOverlay: 在准星右下方绘制地质锤短提示。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class GeologicalSurveyHintOverlay {
    private static final long DISPLAY_MILLIS = 2_000L;
    private static final int MAX_TEXT_WIDTH = 180;
    private static final int OFFSET_X = 12;
    private static final int OFFSET_Y = 10;
    private static final int PADDING = 5;
    private static final int BACKGROUND_COLOR = 0xA6000000;
    private static final int TEXT_COLOR = 0xFFF2F2F2;

    private static Component message = Component.empty();
    private static long expiresAtMillis;

    private GeologicalSurveyHintOverlay() {
    }

    /** show: 替换当前提示并重新开始两秒计时。 */
    public static void show(Component newMessage) {
        message = newMessage != null ? newMessage : Component.empty();
        expiresAtMillis = Util.getMillis() + DISPLAY_MILLIS;
    }

    /** render: 绘制仍在有效期内的勘探提示。 */
    public static void render(RenderGuiEvent.Post event) {
        if (message == null || message.getString().isBlank()) {
            return;
        }
        if (Util.getMillis() >= expiresAtMillis) {
            clear();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.gui.getDebugOverlay().showDebugScreen()) {
            return;
        }

        Font font = minecraft.font;
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(message, MAX_TEXT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int maxLineWidth = lines.stream().mapToInt(font::width).max().orElse(0);
        int boxWidth = maxLineWidth + PADDING * 2;
        int boxHeight = lines.size() * font.lineHeight + PADDING * 2;
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        int x = Math.min(centerX + OFFSET_X, graphics.guiWidth() - boxWidth - 4);
        int y = Math.min(centerY + OFFSET_Y, graphics.guiHeight() - boxHeight - 4);
        x = Math.max(4, x);
        y = Math.max(4, y);

        graphics.fill(x, y, x + boxWidth, y + boxHeight, BACKGROUND_COLOR);
        for (int line = 0; line < lines.size(); line++) {
            graphics.drawString(font, lines.get(line), x + PADDING, y + PADDING + line * font.lineHeight, TEXT_COLOR, false);
        }
    }

    /** clear: 清理退出服务器后的临时提示状态。 */
    public static void clear() {
        message = Component.empty();
        expiresAtMillis = 0L;
    }
}
