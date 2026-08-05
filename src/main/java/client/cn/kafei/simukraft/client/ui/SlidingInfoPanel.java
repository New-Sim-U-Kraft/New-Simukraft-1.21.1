package client.cn.kafei.simukraft.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 通用右侧滑动信息面板，供各自由视角操作界面复用。
 * 使用方式：声明实例字段，render() 中调用 beginRender()，keyPressed() 中调用 toggle()。
 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class SlidingInfoPanel {

    private static final int PANEL_W = 132;

    private boolean visible = true;
    private float slideProgress = 1.0f;

    // beginRender() 后有效的布局值
    private Font font;
    private int panelX, panelY, panelW, panelH, innerX;

    /** 切换面板显隐。 */
    public void toggle() {
        visible = !visible;
    }

    /** 直接设定显隐状态（不经过动画）。 */
    public void setVisible(boolean v) {
        visible = v;
        slideProgress = v ? 1.0f : 0.0f;
    }

    public boolean isVisible() {
        return visible;
    }

    /**
     * 每帧调用：更新滑动动画并绘制面板框（拉片 + 背景）。
     * @param panelTop 面板顶部 Y（通常为标题栏底部，如 18）
     */
    public void beginRender(GuiGraphics g, Font font, int screenW, int screenH, int panelTop) {
        this.font  = font;
        this.panelW = PANEL_W;
        this.panelY = panelTop;
        this.panelH = screenH - panelTop;
        float slideTarget = visible ? 1.0f : 0.0f;
        float delta = slideTarget - slideProgress;
        slideProgress = Math.abs(delta) > 0.004f ? slideProgress + delta * 0.2f : slideTarget;
        panelX = screenW - (int)(slideProgress * (panelW + 6));
        innerX = panelX + 8;
        // 拉片
        int tabX = panelX - 12, tabY = panelY + panelH / 2 - 8;
        g.fill(tabX, tabY, tabX + 12, tabY + 16, 0xAA000000);
        g.fill(tabX, tabY, tabX + 12, tabY + 1,  0x44AAAACC);
        g.fill(tabX, tabY, tabX + 1,  tabY + 16, 0x44AAAACC);
        g.drawCenteredString(font, Component.literal(visible ? "▶" : "◀"), tabX + 6, tabY + 4, 0xFF8888AA);
        // 面板背景
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA000000);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1,           0x66AAAACC);
        g.fill(panelX, panelY, panelX + 1,      panelY + panelH,      0x66AAAACC);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0x33444466);
        g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0x33444466);
    }

    // ── 布局访问器 ─────────────────────────────────────────────

    public int getPanelX()    { return panelX; }
    public int getInnerX()    { return innerX; }
    public int getPanelW()    { return panelW; }
    public int getCenterX()   { return panelX + panelW / 2; }
    public int getInnerRight(){ return panelX + panelW - 8; }

    // ── 绘制辅助 ───────────────────────────────────────────────

    /** 绘制橙色区段标题。 */
    public void drawSectionTitle(GuiGraphics g, Component text, int y) {
        g.drawCenteredString(font, text, getCenterX(), y, 0xFFCC9944);
    }

    /** 绘制水平分隔线。 */
    public void drawSeparator(GuiGraphics g, int y) {
        g.fill(innerX, y, getInnerRight(), y + 1, 0x33FFFFFF);
    }

    /** 绘制以 (cx,cy) 为中心的键帽。 */
    public void drawKeyCap(GuiGraphics g, Component label, int cx, int cy, int w, int h) {
        drawKeyCapAt(g, label, cx - w / 2, cy - h / 2, w, h);
    }

    /** 绘制以 (x,y) 为左上角的键帽。 */
    public void drawKeyCapAt(GuiGraphics g, Component label, int x, int y, int w, int h) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xCC1E1E2E);
        g.fill(x, y, x + w, y + 1,         0xFF9999BB);
        g.fill(x, y, x + 1, y + h,         0xFF9999BB);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF333344);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF333344);
        g.drawCenteredString(font, label, x + w / 2, y + (h - font.lineHeight) / 2, 0xFFFFFFFF);
    }

    /**
     * 绘制一行"键帽 + 动作说明"：键帽在左，说明紧跟右侧。
     * @return 下一行的 Y 坐标（curY + kh + 3）
     */
    public int drawKeyAction(GuiGraphics g, Component key, int x, int y,
                             int kh, Component label, int labelColor) {
        int kw = Math.max(10, font.width(key) + 6);
        drawKeyCapAt(g, key, x, y, kw, kh);
        g.drawString(font, label, x + kw + 3, y + (kh - font.lineHeight) / 2, labelColor, false);
        return y + kh + 3;
    }

    // ── 鼠标图标 ───────────────────────────────────────────────

    /** 鼠标图标宽度（像素）。 */
    public static final int MOUSE_W = 13;
    /** 鼠标图标高度（像素）。 */
    public static final int MOUSE_H = 18;
    /** 高亮左键。 */
    public static final int MOUSE_LEFT   = 0;
    /** 高亮中键（滚轮）。 */
    public static final int MOUSE_MIDDLE = 1;
    /** 高亮右键。 */
    public static final int MOUSE_RIGHT  = 2;

    private static final int MOUSE_BTN_H = 7; // 按键区高度

    /**
     * 绘制鼠标形状图标（13×18，圆角，按键带高光），高亮指定按键区域。
     * 布局：左3 / 分隔1 / 中3 / 分隔1 / 右3 = 11px 内宽。
     * @param mouseButton MOUSE_LEFT / MOUSE_MIDDLE / MOUSE_RIGHT，其他值=无高亮
     */
    public void drawMouseIcon(GuiGraphics g, int x, int y, int mouseButton) {
        final int BODY   = 0xFF1E1E2E;
        final int BTN    = 0xFF383852;
        final int BTN_HL = 0xFF484864; // 按键顶部高光行
        final int BODY_HL= 0xFF272738; // 机身顶部微亮行
        final int HI     = 0xFFFFAA33;
        final int HI_HL  = 0xFFFFCC66; // 高亮按键顶部高光
        final int BORD   = 0xFF8888AA;
        final int DIV    = 0xFF555575;
        final int SCROLL = 0xFF555575;
        final int SCRL_HI= 0xFFFFEE99;

        // 背景整体填充
        g.fill(x, y, x + MOUSE_W, y + MOUSE_H, BODY);
        // 圆角边框（跳过四个角点）
        g.fill(x + 1, y,                x + MOUSE_W - 1, y + 1,              BORD);
        g.fill(x,     y + 1,            x + 1,           y + MOUSE_H - 1,    BORD);
        g.fill(x + MOUSE_W - 1, y + 1,  x + MOUSE_W,     y + MOUSE_H - 1,    BORD);
        g.fill(x + 1, y + MOUSE_H - 1,  x + MOUSE_W - 1, y + MOUSE_H,        BORD);

        // 三个按键区（内宽11：左3 / 分1 / 中3 / 分1 / 右3）
        int by1 = y + 1, by2 = y + MOUSE_BTN_H;
        boolean lHi = mouseButton == MOUSE_LEFT, mHi = mouseButton == MOUSE_MIDDLE, rHi = mouseButton == MOUSE_RIGHT;
        // 左键
        g.fill(x + 1, by1,     x + 4,  by2,     lHi ? HI    : BTN);
        g.fill(x + 1, by1,     x + 4,  by1 + 1, lHi ? HI_HL : BTN_HL);
        // 竖分隔
        g.fill(x + 4, by1, x + 5, by2, DIV);
        // 中键（滚轮区）
        g.fill(x + 5, by1,     x + 8,  by2,     mHi ? HI    : BTN);
        g.fill(x + 5, by1,     x + 8,  by1 + 1, mHi ? HI_HL : BTN_HL);
        // 竖分隔
        g.fill(x + 8, by1, x + 9, by2, DIV);
        // 右键
        g.fill(x + 9, by1,     x + 12, by2,     rHi ? HI    : BTN);
        g.fill(x + 9, by1,     x + 12, by1 + 1, rHi ? HI_HL : BTN_HL);

        // 水平分隔线（按键区与机身）
        g.fill(x + 1, y + MOUSE_BTN_H, x + MOUSE_W - 1, y + MOUSE_BTN_H + 1, DIV);
        // 机身顶部微亮行
        g.fill(x + 1, y + MOUSE_BTN_H + 1, x + MOUSE_W - 1, y + MOUSE_BTN_H + 2, BODY_HL);

        // 滚轮纹路（中键高亮时变亮）
        int wc = mHi ? SCRL_HI : SCROLL;
        g.fill(x + 5, by1 + 1, x + 8, by1 + 2, wc);
        g.fill(x + 5, by1 + 3, x + 8, by1 + 4, wc);
        g.fill(x + 5, by1 + 5, x + 8, by1 + 6, wc);
    }

    /**
     * 绘制一行"鼠标图标 + 动作说明"：图标在左，说明垂直居中于图标旁。
     * @param mouseButton 高亮按钮：MOUSE_LEFT / MOUSE_MIDDLE / MOUSE_RIGHT
     * @return 下一行 Y 坐标
     */
    public int drawMouseAction(GuiGraphics g, int mouseButton, int x, int y,
                               Component label, int labelColor) {
        drawMouseIcon(g, x, y, mouseButton);
        g.drawString(font, label, x + MOUSE_W + 4, y + (MOUSE_H - font.lineHeight) / 2, labelColor, false);
        return y + MOUSE_H + 3;
    }
}