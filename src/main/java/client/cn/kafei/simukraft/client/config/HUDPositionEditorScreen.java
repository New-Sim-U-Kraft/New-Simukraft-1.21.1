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

/**
 * HUDPositionEditorScreen: 通过拖拽预览文本设置 HUD 锚点和相对偏移。
 * 位置保存为锚点加偏移，分辨率或 GUI 缩放改变后仍能保持原有相对位置。
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public final class HUDPositionEditorScreen extends Screen {
    private static final int PADDING = 6;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private String previewText;
    private int previewTextWidth;
    private int hudAbsoluteX;
    private int hudAbsoluteY;
    private boolean dragging;
    private int dragStartMouseX;
    private int dragStartMouseY;
    private int dragStartHudX;
    private int dragStartHudY;
    private ClientHUDConfig.Anchor currentAnchor;
    private int regionX1;
    private int regionX2;
    private int regionY2;

    /** HUDPositionEditorScreen: 创建 HUD 拖拽编辑器并保留原配置页返回入口。 */
    public HUDPositionEditorScreen(Screen parent) {
        super(Component.translatable("gui.hud_editor.title"));
        this.parent = parent;
        this.previewText = previewText();
    }

    /** init: 初始化拖拽预览、区域划分和底部操作按钮。 */
    @Override
    protected void init() {
        super.init();
        previewText = previewText();
        previewTextWidth = font.width(previewText);
        regionX1 = width / 3;
        regionX2 = width * 2 / 3;
        regionY2 = height / 2;
        calculateAbsolutePosition();

        int buttonY = height - 36;
        int centerX = width / 2;
        int totalWidth = BUTTON_WIDTH * 3 + 12;
        addRenderableWidget(Button.builder(Component.translatable("gui.hud_editor.save"), button -> saveAndClose())
                .bounds(centerX - totalWidth / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.hud_editor.reset"), button -> resetPosition())
                .bounds(centerX - BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.hud_editor.cancel"), button -> onClose())
                .bounds(centerX + totalWidth / 2 - BUTTON_WIDTH, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /** previewText: 获取与实际 HUD 相同的当前状态文本。 */
    private String previewText() {
        String text = ClientHUDOverlay.getCurrentDisplayText();
        return text.isBlank() ? Component.translatable("gui.hud_editor.preview_fallback").getString() : text;
    }

    /** calculateAbsolutePosition: 从锚点和偏移恢复屏幕绝对坐标。 */
    private void calculateAbsolutePosition() {
        currentAnchor = ClientHUDConfig.getAnchor();
        int[] position = ClientHUDConfig.calculatePosition(width, height, previewTextWidth);
        hudAbsoluteX = clamp(position[0], 0, Math.max(0, width - previewTextWidth));
        hudAbsoluteY = clamp(position[1], 0, Math.max(0, height - font.lineHeight));
    }

    /** detectAnchor: 根据 HUD 中心所在六区确定新的锚点。 */
    private ClientHUDConfig.Anchor detectAnchor(int centerX, int centerY) {
        boolean left = centerX < regionX1;
        boolean right = centerX >= regionX2;
        boolean top = centerY < regionY2;
        if (top && left) return ClientHUDConfig.Anchor.TOP_LEFT;
        if (top && right) return ClientHUDConfig.Anchor.TOP_RIGHT;
        if (!top && left) return ClientHUDConfig.Anchor.BOTTOM_LEFT;
        if (!top && right) return ClientHUDConfig.Anchor.BOTTOM_RIGHT;
        return top ? ClientHUDConfig.Anchor.TOP_CENTER : ClientHUDConfig.Anchor.BOTTOM_CENTER;
    }

    /** saveAbsolutePosition: 将屏幕绝对坐标转换为选中锚点的相对偏移并持久化。 */
    private void saveAbsolutePosition() {
        int offsetX;
        int offsetY;
        switch (currentAnchor) {
            case TOP_LEFT -> { offsetX = hudAbsoluteX; offsetY = hudAbsoluteY; }
            case TOP_RIGHT -> { offsetX = hudAbsoluteX - (width - previewTextWidth); offsetY = hudAbsoluteY; }
            case BOTTOM_LEFT -> { offsetX = hudAbsoluteX; offsetY = hudAbsoluteY - (height - 10); }
            case BOTTOM_RIGHT -> { offsetX = hudAbsoluteX - (width - previewTextWidth); offsetY = hudAbsoluteY - (height - 10); }
            case TOP_CENTER -> { offsetX = hudAbsoluteX - (width - previewTextWidth) / 2; offsetY = hudAbsoluteY; }
            case BOTTOM_CENTER -> { offsetX = hudAbsoluteX - (width - previewTextWidth) / 2; offsetY = hudAbsoluteY - (height - 10); }
            default -> { offsetX = hudAbsoluteX; offsetY = hudAbsoluteY; }
        }
        ClientConfig.HUD_ANCHOR.set(currentAnchor.name());
        ClientConfig.HUD_POS_X.set(clamp(offsetX, -4096, 4096));
        ClientConfig.HUD_POS_Y.set(clamp(offsetY, -4096, 4096));
        ClientConfig.SPEC.save();
        ClientHUDOverlay.resetCache();
    }

    /** saveAndClose: 保存 HUD 锚点和偏移后返回客户端配置页。 */
    private void saveAndClose() {
        saveAbsolutePosition();
        Minecraft.getInstance().setScreen(parent);
    }

    /** resetPosition: 将预览恢复到默认右上角位置并写入默认配置。 */
    private void resetPosition() {
        ClientHUDConfig.reset();
        calculateAbsolutePosition();
    }

    /** clamp: 将拖拽坐标限制在屏幕和配置允许范围内。 */
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** isMouseOverHud: 判断鼠标是否位于可拖拽的 HUD 预览范围。 */
    private boolean isMouseOverHud(double mouseX, double mouseY) {
        return mouseX >= hudAbsoluteX - PADDING && mouseX <= hudAbsoluteX + previewTextWidth + PADDING
                && mouseY >= hudAbsoluteY - PADDING && mouseY <= hudAbsoluteY + font.lineHeight + PADDING;
    }

    /** renderBackground: 禁用原版菜单背景模糊，由编辑器自行绘制暗色遮罩。 */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    /** render: 绘制遮罩、六区参考线、HUD 预览和操作按钮。 */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, 0xCC000000);
        renderRegions(guiGraphics);
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        guiGraphics.drawCenteredString(font, Component.translatable("gui.hud_editor.instruction"), width / 2, 28, 0xAAAAAA);
        Component status = Component.translatable("gui.hud_editor.status",
                Component.translatable("gui.hud_editor.anchor." + currentAnchor.name().toLowerCase(java.util.Locale.ROOT)),
                hudAbsoluteX, hudAbsoluteY);
        guiGraphics.drawCenteredString(font, status, width / 2, 46, 0xFFFFAA);
        guiGraphics.renderOutline(hudAbsoluteX - PADDING, hudAbsoluteY - PADDING, previewTextWidth + PADDING * 2,
                font.lineHeight + PADDING * 2, dragging ? 0xFF00FF00 : 0xFF4A90A4);
        guiGraphics.fill(hudAbsoluteX - PADDING, hudAbsoluteY - PADDING, hudAbsoluteX + previewTextWidth + PADDING,
                hudAbsoluteY + font.lineHeight + PADDING, 0x66000000);
        guiGraphics.drawString(font, previewText, hudAbsoluteX, hudAbsoluteY, 0xFFFFFF, true);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /** renderRegions: 绘制六区参考线并高亮当前锚点区域。 */
    private void renderRegions(GuiGraphics guiGraphics) {
        int highlightX;
        int highlightY;
        int highlightWidth;
        int highlightHeight;
        switch (currentAnchor) {
            case TOP_LEFT -> {
                highlightX = 0; highlightY = 0; highlightWidth = regionX1; highlightHeight = regionY2;
            }
            case TOP_RIGHT -> {
                highlightX = regionX2; highlightY = 0; highlightWidth = width - regionX2; highlightHeight = regionY2;
            }
            case BOTTOM_LEFT -> {
                highlightX = 0; highlightY = regionY2; highlightWidth = regionX1; highlightHeight = height - regionY2;
            }
            case BOTTOM_RIGHT -> {
                highlightX = regionX2; highlightY = regionY2; highlightWidth = width - regionX2; highlightHeight = height - regionY2;
            }
            case TOP_CENTER -> {
                highlightX = regionX1; highlightY = 0; highlightWidth = regionX2 - regionX1; highlightHeight = regionY2;
            }
            case BOTTOM_CENTER -> {
                highlightX = regionX1; highlightY = regionY2; highlightWidth = regionX2 - regionX1; highlightHeight = height - regionY2;
            }
            default -> {
                highlightX = 0; highlightY = 0; highlightWidth = 0; highlightHeight = 0;
            }
        }
        guiGraphics.fill(highlightX, highlightY, highlightX + highlightWidth, highlightY + highlightHeight, 0x44FFAA00);
        guiGraphics.fill(regionX1, 0, regionX1 + 1, height, 0x44FFFFFF);
        guiGraphics.fill(regionX2, 0, regionX2 + 1, height, 0x44FFFFFF);
        guiGraphics.fill(0, regionY2, width, regionY2 + 1, 0x44FFFFFF);
    }

    /** mouseClicked: 开始拖拽 HUD 预览文本。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && isMouseOverHud(mouseX, mouseY)) {
            dragging = true;
            dragStartMouseX = (int) mouseX;
            dragStartMouseY = (int) mouseY;
            dragStartHudX = hudAbsoluteX;
            dragStartHudY = hudAbsoluteY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** mouseReleased: 结束 HUD 拖拽状态。 */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** mouseDragged: 更新 HUD 预览坐标并实时切换锚点。 */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == 0) {
            hudAbsoluteX = clamp(dragStartHudX + (int) (mouseX - dragStartMouseX), 0, Math.max(0, width - previewTextWidth));
            hudAbsoluteY = clamp(dragStartHudY + (int) (mouseY - dragStartMouseY), 0, Math.max(0, height - font.lineHeight));
            currentAnchor = detectAnchor(hudAbsoluteX + previewTextWidth / 2, hudAbsoluteY + font.lineHeight / 2);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    /** onClose: 放弃未保存的拖拽并返回上一级配置页。 */
    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /** isPauseScreen: 编辑 HUD 时暂停单人游戏逻辑。 */
    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
