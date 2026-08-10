package client.cn.kafei.simukraft.client.config;

import client.cn.kafei.simukraft.client.ClientHUDConfig;
import client.cn.kafei.simukraft.client.ClientHUDOverlay;
import common.cn.kafei.simukraft.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * HUDPositionEditorScreen:
 *   - 拖拽左/右边框竖条 → 调整行宽（左边框向右拖=缩窄，右边框向右拖=扩宽）
 *   - 拖拽内部/上下边框 → 移动位置
 * 锚点决定文本对齐方向，实际 HUD 渲染同步生效。
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public final class HUDPositionEditorScreen extends Screen {
    private static final int PADDING = 6;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;
    // 左右边框感应宽度（像素）
    private static final int EDGE_HIT = 8;
    private static final int WIDTH_MIN = 20;

    private final Screen parent;
    private List<String> previewLines;
    private int previewBoxWidth;
    private int previewBoxHeight;
    private int previewMaxWidth;   // 0 = 不限制

    private enum DragMode { NONE, MOVE, RESIZE_LEFT, RESIZE_RIGHT }
    private DragMode dragMode = DragMode.NONE;
    private int dragStartMouseX, dragStartMouseY;
    private int dragStartHudX, dragStartHudY;
    private int dragStartWidth;   // resize 时的起始宽度

    private int hudAbsoluteX;
    private int hudAbsoluteY;
    private ClientHUDConfig.Anchor currentAnchor;
    private int regionX1, regionX2, regionY2;

    public HUDPositionEditorScreen(Screen parent) {
        super(Component.translatable("gui.hud_editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        previewMaxWidth = ClientConfig.hudMaxWidth();
        regionX1 = width / 3;
        regionX2 = width * 2 / 3;
        regionY2 = height / 2;
        rebuildPreview();
        calculateAbsolutePosition();

        int buttonY = height - 36;
        int cx = width / 2;
        int totalW = BUTTON_WIDTH * 3 + 12;
        addRenderableWidget(Button.builder(Component.translatable("gui.hud_editor.save"), b -> saveAndClose())
                .bounds(cx - totalW / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.hud_editor.reset"), b -> resetPosition())
                .bounds(cx - BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.hud_editor.cancel"), b -> onClose())
                .bounds(cx + totalW / 2 - BUTTON_WIDTH, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void rebuildPreview() {
        List<String> fields = getPreviewFields();
        previewLines = ClientHUDOverlay.wrapFieldsToLines(font, fields, previewMaxWidth);
        previewBoxWidth = ClientHUDOverlay.widestLineWidth(font, previewLines);
        if (previewBoxWidth <= 0) {
            previewBoxWidth = font.width(Component.translatable("gui.hud_editor.preview_fallback").getString());
        }
        previewBoxHeight = previewLines.size() * (font.lineHeight + 2);
    }

    private List<String> getPreviewFields() {
        String text = ClientHUDOverlay.getCurrentDisplayText();
        if (text.isBlank()) return List.of(Component.translatable("gui.hud_editor.preview_fallback").getString());
        return List.of(text.split(" \\| "));
    }

    private void calculateAbsolutePosition() {
        currentAnchor = ClientHUDConfig.getAnchor();
        int[] pos = ClientHUDConfig.calculatePosition(width, height, previewBoxWidth);
        hudAbsoluteX = clamp(pos[0], 0, Math.max(0, width - previewBoxWidth));
        hudAbsoluteY = clamp(pos[1], 0, Math.max(0, height - previewBoxHeight));
    }

    private ClientHUDConfig.Anchor detectAnchor(int cx, int cy) {
        boolean left = cx < regionX1, right = cx >= regionX2, top = cy < regionY2;
        if (top && left)  return ClientHUDConfig.Anchor.TOP_LEFT;
        if (top && right) return ClientHUDConfig.Anchor.TOP_RIGHT;
        if (!top && left) return ClientHUDConfig.Anchor.BOTTOM_LEFT;
        if (!top && right)return ClientHUDConfig.Anchor.BOTTOM_RIGHT;
        return top ? ClientHUDConfig.Anchor.TOP_CENTER : ClientHUDConfig.Anchor.BOTTOM_CENTER;
    }

    private void saveAbsolutePosition() {
        int ox, oy;
        switch (currentAnchor) {
            case TOP_LEFT    -> { ox = hudAbsoluteX; oy = hudAbsoluteY; }
            case TOP_RIGHT   -> { ox = hudAbsoluteX - (width - previewBoxWidth); oy = hudAbsoluteY; }
            case BOTTOM_LEFT -> { ox = hudAbsoluteX; oy = hudAbsoluteY - (height - 10); }
            case BOTTOM_RIGHT-> { ox = hudAbsoluteX - (width - previewBoxWidth); oy = hudAbsoluteY - (height - 10); }
            case TOP_CENTER  -> { ox = hudAbsoluteX - (width - previewBoxWidth) / 2; oy = hudAbsoluteY; }
            case BOTTOM_CENTER->{ ox = hudAbsoluteX - (width - previewBoxWidth) / 2; oy = hudAbsoluteY - (height - 10); }
            default          -> { ox = hudAbsoluteX; oy = hudAbsoluteY; }
        }
        ClientConfig.HUD_ANCHOR.set(currentAnchor.name());
        ClientConfig.HUD_POS_X.set(clamp(ox, -4096, 4096));
        ClientConfig.HUD_POS_Y.set(clamp(oy, -4096, 4096));
        ClientConfig.HUD_MAX_WIDTH.set(previewMaxWidth);
        ClientConfig.SPEC.save();
        ClientHUDOverlay.resetCache();
    }

    private void saveAndClose() { saveAbsolutePosition(); Minecraft.getInstance().setScreen(parent); }
    private void resetPosition() {
        ClientHUDConfig.reset();
        previewMaxWidth = ClientConfig.DEFAULT_HUD_MAX_WIDTH;
        rebuildPreview();
        calculateAbsolutePosition();
    }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    // ── 命中检测 ──────────────────────────────────────────────

    /** 左边框竖条感应区（含 padding 外侧到内侧 EDGE_HIT 范围） */
    private boolean isOnLeftEdge(double mx, double my) {
        int lx = hudAbsoluteX - PADDING;
        return mx >= lx - EDGE_HIT && mx <= lx + EDGE_HIT
                && my >= hudAbsoluteY - PADDING && my <= hudAbsoluteY + previewBoxHeight + PADDING;
    }

    /** 右边框竖条感应区 */
    private boolean isOnRightEdge(double mx, double my) {
        int rx = hudAbsoluteX + previewBoxWidth + PADDING;
        return mx >= rx - EDGE_HIT && mx <= rx + EDGE_HIT
                && my >= hudAbsoluteY - PADDING && my <= hudAbsoluteY + previewBoxHeight + PADDING;
    }

    /** 预览框整体（内部+上下边框），用于移动 */
    private boolean isMouseOverHud(double mx, double my) {
        return mx >= hudAbsoluteX - PADDING && mx <= hudAbsoluteX + previewBoxWidth + PADDING
                && my >= hudAbsoluteY - PADDING && my <= hudAbsoluteY + previewBoxHeight + PADDING;
    }

    // ── 渲染 ──────────────────────────────────────────────────

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xCC000000);
        renderRegions(g);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.hud_editor.instruction"), width / 2, 28, 0xAAAAAA);

        String widthLabel = previewMaxWidth <= 0
                ? Component.translatable("gui.hud_editor.width_unlimited").getString()
                : previewMaxWidth + "px";
        g.drawCenteredString(font,
                Component.translatable("gui.hud_editor.status_with_width",
                        Component.translatable("gui.hud_editor.anchor." + currentAnchor.name().toLowerCase(java.util.Locale.ROOT)),
                        hudAbsoluteX, hudAbsoluteY, widthLabel),
                width / 2, 46, 0xFFFFAA);

        boolean onLeft  = isOnLeftEdge(mouseX, mouseY);
        boolean onRight = isOnRightEdge(mouseX, mouseY);
        boolean resizing = dragMode == DragMode.RESIZE_LEFT || dragMode == DragMode.RESIZE_RIGHT;

        // 框体颜色
        int boxColor = resizing ? 0xFFFFAA00
                : (onLeft || onRight) ? 0xFFFFDD55
                : (dragMode == DragMode.MOVE) ? 0xFF00FF00
                : 0xFF4A90A4;
        g.renderOutline(hudAbsoluteX - PADDING, hudAbsoluteY - PADDING,
                previewBoxWidth + PADDING * 2, previewBoxHeight + PADDING * 2, boxColor);
        g.fill(hudAbsoluteX - PADDING, hudAbsoluteY - PADDING,
                hudAbsoluteX + previewBoxWidth + PADDING, hudAbsoluteY + previewBoxHeight + PADDING, 0x66000000);

        // 左/右边框高亮条
        boolean showLeft  = onLeft  || dragMode == DragMode.RESIZE_LEFT;
        boolean showRight = onRight || dragMode == DragMode.RESIZE_RIGHT;
        if (showLeft) {
            int lx = hudAbsoluteX - PADDING;
            g.fill(lx - 1, hudAbsoluteY - PADDING, lx + 2, hudAbsoluteY + previewBoxHeight + PADDING, 0xCCFFAA00);
        }
        if (showRight) {
            int rx = hudAbsoluteX + previewBoxWidth + PADDING - 1;
            g.fill(rx, hudAbsoluteY - PADDING, rx + 2, hudAbsoluteY + previewBoxHeight + PADDING, 0xCCFFAA00);
        }

        // 多行文本，按锚点对齐
        int lineStep = font.lineHeight + 2;
        for (int i = 0; i < previewLines.size(); i++) {
            String line = previewLines.get(i);
            int lw = font.width(line);
            int tx = switch (currentAnchor) {
                case TOP_RIGHT, BOTTOM_RIGHT -> hudAbsoluteX + previewBoxWidth - lw;
                case TOP_CENTER, BOTTOM_CENTER -> hudAbsoluteX + (previewBoxWidth - lw) / 2;
                default -> hudAbsoluteX;
            };
            g.drawString(font, line, tx, hudAbsoluteY + i * lineStep, 0xFFFFFF, true);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderRegions(GuiGraphics g) {
        int hx, hy, hw, hh;
        switch (currentAnchor) {
            case TOP_LEFT    -> { hx = 0;        hy = 0;        hw = regionX1;            hh = regionY2; }
            case TOP_RIGHT   -> { hx = regionX2; hy = 0;        hw = width - regionX2;    hh = regionY2; }
            case BOTTOM_LEFT -> { hx = 0;        hy = regionY2; hw = regionX1;            hh = height - regionY2; }
            case BOTTOM_RIGHT-> { hx = regionX2; hy = regionY2; hw = width - regionX2;    hh = height - regionY2; }
            case TOP_CENTER  -> { hx = regionX1; hy = 0;        hw = regionX2 - regionX1; hh = regionY2; }
            case BOTTOM_CENTER->{ hx = regionX1; hy = regionY2; hw = regionX2 - regionX1; hh = height - regionY2; }
            default          -> { hx = 0; hy = 0; hw = 0; hh = 0; }
        }
        g.fill(hx, hy, hx + hw, hy + hh, 0x44FFAA00);
        g.fill(regionX1, 0, regionX1 + 1, height, 0x44FFFFFF);
        g.fill(regionX2, 0, regionX2 + 1, height, 0x44FFFFFF);
        g.fill(0, regionY2, width, regionY2 + 1, 0x44FFFFFF);
    }

    // ── 鼠标事件 ──────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            // 左/右边框优先于内部区域
            if (isOnLeftEdge(mouseX, mouseY)) {
                dragMode = DragMode.RESIZE_LEFT;
                dragStartMouseX = (int) mouseX;
                dragStartWidth = previewMaxWidth > 0 ? previewMaxWidth : previewBoxWidth;
                dragStartHudX = hudAbsoluteX;
                return true;
            }
            if (isOnRightEdge(mouseX, mouseY)) {
                dragMode = DragMode.RESIZE_RIGHT;
                dragStartMouseX = (int) mouseX;
                dragStartWidth = previewMaxWidth > 0 ? previewMaxWidth : previewBoxWidth;
                return true;
            }
            if (isMouseOverHud(mouseX, mouseY)) {
                dragMode = DragMode.MOVE;
                dragStartMouseX = (int) mouseX;
                dragStartMouseY = (int) mouseY;
                dragStartHudX = hudAbsoluteX;
                dragStartHudY = hudAbsoluteY;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragMode = DragMode.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button != 0) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        int deltaX = (int) (mouseX - dragStartMouseX);

        if (dragMode == DragMode.RESIZE_RIGHT) {
            // 向右拖扩宽，向左拖缩窄
            int next = dragStartWidth + deltaX;
            previewMaxWidth = next < WIDTH_MIN ? 0 : next;
            rebuildPreview();
            hudAbsoluteX = clamp(hudAbsoluteX, 0, Math.max(0, width - previewBoxWidth));
            hudAbsoluteY = clamp(hudAbsoluteY, 0, Math.max(0, height - previewBoxHeight));
            return true;
        }

        if (dragMode == DragMode.RESIZE_LEFT) {
            // 左边框向右拖=缩窄，向左拖=扩宽；同时 HUD 框左边界跟随移动
            int next = dragStartWidth - deltaX;
            previewMaxWidth = next < WIDTH_MIN ? 0 : next;
            rebuildPreview();
            // 左边界跟随：框右侧固定，左侧被推动
            int fixedRight = dragStartHudX + (dragStartWidth > 0 ? dragStartWidth : previewBoxWidth);
            hudAbsoluteX = clamp(fixedRight - previewBoxWidth, 0, Math.max(0, width - previewBoxWidth));
            hudAbsoluteY = clamp(hudAbsoluteY, 0, Math.max(0, height - previewBoxHeight));
            return true;
        }

        if (dragMode == DragMode.MOVE) {
            hudAbsoluteX = clamp(dragStartHudX + deltaX, 0, Math.max(0, width - previewBoxWidth));
            hudAbsoluteY = clamp(dragStartHudY + (int) (mouseY - dragStartMouseY),
                    0, Math.max(0, height - previewBoxHeight));
            currentAnchor = detectAnchor(hudAbsoluteX + previewBoxWidth / 2, hudAbsoluteY + previewBoxHeight / 2);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }
}
