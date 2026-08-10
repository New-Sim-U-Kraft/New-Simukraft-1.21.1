package client.cn.kafei.simukraft.client.config;

import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import client.cn.kafei.simukraft.client.toast.ClientToastConfig;
import common.cn.kafei.simukraft.config.ClientConfig;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** ToastPositionEditorScreen: 预览并调整独立通知的尺寸与六锚点位置。 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public final class ToastPositionEditorScreen extends Screen {
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;
    private static final int EDGE_HIT = 8;
    private static final int MIN_WIDTH = 120;
    private static final int MAX_WIDTH = 512;
    private static final int MIN_HEIGHT = 36;
    private static final int MAX_HEIGHT = 160;

    private final Screen parent;
    private DragMode dragMode = DragMode.NONE;
    private ClientToastConfig.Anchor currentAnchor;
    private int toastAbsoluteX;
    private int toastAbsoluteY;
    private int toastWidth;
    private int toastHeight;
    private int dragStartMouseX;
    private int dragStartMouseY;
    private int dragStartX;
    private int dragStartY;
    private int dragStartWidth;
    private int dragStartHeight;
    private int regionX1;
    private int regionX2;
    private int regionY2;

    private enum DragMode {
        NONE,
        MOVE,
        RESIZE_LEFT,
        RESIZE_RIGHT,
        RESIZE_TOP,
        RESIZE_BOTTOM
    }

    public ToastPositionEditorScreen(Screen parent) {
        super(Component.translatable("gui.toast_editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        regionX1 = width / 3;
        regionX2 = width * 2 / 3;
        regionY2 = height / 2;
        loadConfig();

        int buttonY = height - 36;
        int centerX = width / 2;
        int totalWidth = BUTTON_WIDTH * 3 + 12;
        addRenderableWidget(Button.builder(Component.translatable("gui.toast_editor.save"), button -> saveAndClose())
                .bounds(centerX - totalWidth / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.toast_editor.reset"), button -> resetLayout())
                .bounds(centerX - BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.toast_editor.cancel"), button -> onClose())
                .bounds(centerX + totalWidth / 2 - BUTTON_WIDTH, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    /** loadConfig: 读取配置并计算通知的当前绝对位置。 */
    private void loadConfig() {
        currentAnchor = ClientToastConfig.getAnchor();
        toastWidth = ClientToastConfig.width();
        toastHeight = ClientToastConfig.height();
        int[] position = ClientToastConfig.calculatePosition(width, height, toastWidth, toastHeight);
        toastAbsoluteX = clamp(position[0], 0, Math.max(0, width - toastWidth));
        toastAbsoluteY = clamp(position[1], 0, Math.max(0, height - toastHeight));
    }

    /** detectAnchor: 根据通知中心点判定所在六锚点区域。 */
    private ClientToastConfig.Anchor detectAnchor(int centerX, int centerY) {
        boolean isLeft = centerX < regionX1;
        boolean isRight = centerX >= regionX2;
        boolean isTop = centerY < regionY2;
        if (isTop && isLeft) {
            return ClientToastConfig.Anchor.TOP_LEFT;
        }
        if (isTop && isRight) {
            return ClientToastConfig.Anchor.TOP_RIGHT;
        }
        if (!isTop && isLeft) {
            return ClientToastConfig.Anchor.BOTTOM_LEFT;
        }
        if (!isTop && isRight) {
            return ClientToastConfig.Anchor.BOTTOM_RIGHT;
        }
        return isTop ? ClientToastConfig.Anchor.TOP_CENTER : ClientToastConfig.Anchor.BOTTOM_CENTER;
    }

    /** saveAndClose: 将编辑器的绝对位置换算成锚点偏移后保存。 */
    private void saveAndClose() {
        int offsetX;
        int offsetY;
        switch (currentAnchor) {
            case TOP_LEFT -> {
                offsetX = toastAbsoluteX;
                offsetY = toastAbsoluteY;
            }
            case TOP_RIGHT -> {
                offsetX = toastAbsoluteX - (width - toastWidth);
                offsetY = toastAbsoluteY;
            }
            case BOTTOM_LEFT -> {
                offsetX = toastAbsoluteX;
                offsetY = toastAbsoluteY - (height - toastHeight);
            }
            case BOTTOM_RIGHT -> {
                offsetX = toastAbsoluteX - (width - toastWidth);
                offsetY = toastAbsoluteY - (height - toastHeight);
            }
            case TOP_CENTER -> {
                offsetX = toastAbsoluteX - (width - toastWidth) / 2;
                offsetY = toastAbsoluteY;
            }
            case BOTTOM_CENTER -> {
                offsetX = toastAbsoluteX - (width - toastWidth) / 2;
                offsetY = toastAbsoluteY - (height - toastHeight);
            }
            default -> throw new IllegalStateException("Unhandled toast anchor: " + currentAnchor);
        }

        ClientConfig.TOAST_ANCHOR.set(currentAnchor.name());
        ClientConfig.TOAST_POS_X.set(clamp(offsetX, -4096, 4096));
        ClientConfig.TOAST_POS_Y.set(clamp(offsetY, -4096, 4096));
        ClientConfig.TOAST_WIDTH.set(toastWidth);
        ClientConfig.TOAST_HEIGHT.set(toastHeight);
        ClientConfig.SPEC.save();
        Minecraft.getInstance().setScreen(parent);
    }

    /** resetLayout: 将编辑中的预览恢复为默认通知布局。 */
    private void resetLayout() {
        currentAnchor = ClientToastConfig.Anchor.TOP_RIGHT;
        toastWidth = ClientConfig.DEFAULT_TOAST_WIDTH;
        toastHeight = ClientConfig.DEFAULT_TOAST_HEIGHT;
        int[] position = ClientToastConfig.calculatePosition(
                currentAnchor,
                ClientConfig.DEFAULT_TOAST_POS_X,
                ClientConfig.DEFAULT_TOAST_POS_Y,
                width,
                height,
                toastWidth,
                toastHeight);
        toastAbsoluteX = clamp(position[0], 0, Math.max(0, width - toastWidth));
        toastAbsoluteY = clamp(position[1], 0, Math.max(0, height - toastHeight));
    }

    /** isOnLeftEdge: 判断鼠标是否位于左侧调宽边缘。 */
    private boolean isOnLeftEdge(double mouseX, double mouseY) {
        return isWithinEdge(mouseX, mouseY, toastAbsoluteX, toastAbsoluteY, 1, toastHeight);
    }

    /** isOnRightEdge: 判断鼠标是否位于右侧调宽边缘。 */
    private boolean isOnRightEdge(double mouseX, double mouseY) {
        return isWithinEdge(mouseX, mouseY, toastAbsoluteX + toastWidth, toastAbsoluteY, 1, toastHeight);
    }

    /** isOnTopEdge: 判断鼠标是否位于顶部调高边缘。 */
    private boolean isOnTopEdge(double mouseX, double mouseY) {
        return isWithinEdge(mouseX, mouseY, toastAbsoluteX, toastAbsoluteY, toastWidth, 1);
    }

    /** isOnBottomEdge: 判断鼠标是否位于底部调高边缘。 */
    private boolean isOnBottomEdge(double mouseX, double mouseY) {
        return isWithinEdge(mouseX, mouseY, toastAbsoluteX, toastAbsoluteY + toastHeight, toastWidth, 1);
    }

    /** isWithinEdge: 判断鼠标是否命中指定边缘的感应区域。 */
    private boolean isWithinEdge(double mouseX, double mouseY, int edgeX, int edgeY, int edgeWidth, int edgeHeight) {
        return mouseX >= edgeX - EDGE_HIT
                && mouseX <= edgeX + edgeWidth + EDGE_HIT
                && mouseY >= edgeY - EDGE_HIT
                && mouseY <= edgeY + edgeHeight + EDGE_HIT;
    }

    /** isOverToast: 判断鼠标是否位于通知预览内部。 */
    private boolean isOverToast(double mouseX, double mouseY) {
        return mouseX >= toastAbsoluteX
                && mouseX <= toastAbsoluteX + toastWidth
                && mouseY >= toastAbsoluteY
                && mouseY <= toastAbsoluteY + toastHeight;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC000000);
        renderRegions(graphics);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable("gui.toast_editor.instruction"),
                width / 2,
                28,
                0xAAAAAA);
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "gui.toast_editor.status",
                        Component.translatable(
                                "gui.toast_editor.anchor." + currentAnchor.name().toLowerCase(Locale.ROOT)),
                        toastAbsoluteX,
                        toastAbsoluteY,
                        toastWidth,
                        toastHeight),
                width / 2,
                46,
                0xFFFFAA);

        int outlineColor = outlineColor(mouseX, mouseY);
        graphics.fill(
                toastAbsoluteX,
                toastAbsoluteY,
                toastAbsoluteX + toastWidth,
                toastAbsoluteY + toastHeight,
                0x22000000);
        graphics.renderOutline(toastAbsoluteX, toastAbsoluteY, toastWidth, toastHeight, outlineColor);
        renderResizeIndicators(graphics, mouseX, mouseY);
        ClientInfoToast.renderPreview(
                graphics,
                font,
                toastAbsoluteX,
                toastAbsoluteY,
                toastWidth,
                toastHeight);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** outlineColor: 返回当前拖拽或悬停状态的预览边框颜色。 */
    private int outlineColor(int mouseX, int mouseY) {
        if (dragMode != DragMode.NONE) {
            return dragMode == DragMode.MOVE ? 0xFF42D17A : 0xFFFFAA00;
        }
        if (isOnLeftEdge(mouseX, mouseY)
                || isOnRightEdge(mouseX, mouseY)
                || isOnTopEdge(mouseX, mouseY)
                || isOnBottomEdge(mouseX, mouseY)) {
            return 0xFFFFDD55;
        }
        return 0xFF58A6FF;
    }

    /** renderResizeIndicators: 高亮当前可拖动的宽高边缘。 */
    private void renderResizeIndicators(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isOnLeftEdge(mouseX, mouseY) || dragMode == DragMode.RESIZE_LEFT) {
            graphics.fill(toastAbsoluteX - 1, toastAbsoluteY, toastAbsoluteX + 2, toastAbsoluteY + toastHeight, 0xCCFFAA00);
        }
        if (isOnRightEdge(mouseX, mouseY) || dragMode == DragMode.RESIZE_RIGHT) {
            graphics.fill(toastAbsoluteX + toastWidth - 1, toastAbsoluteY, toastAbsoluteX + toastWidth + 2, toastAbsoluteY + toastHeight, 0xCCFFAA00);
        }
        if (isOnTopEdge(mouseX, mouseY) || dragMode == DragMode.RESIZE_TOP) {
            graphics.fill(toastAbsoluteX, toastAbsoluteY - 1, toastAbsoluteX + toastWidth, toastAbsoluteY + 2, 0xCCFFAA00);
        }
        if (isOnBottomEdge(mouseX, mouseY) || dragMode == DragMode.RESIZE_BOTTOM) {
            graphics.fill(toastAbsoluteX, toastAbsoluteY + toastHeight - 1, toastAbsoluteX + toastWidth, toastAbsoluteY + toastHeight + 2, 0xCCFFAA00);
        }
    }

    /** renderRegions: 绘制当前六锚点区域与选中区域提示。 */
    private void renderRegions(GuiGraphics graphics) {
        int highlightX;
        int highlightY;
        int highlightWidth;
        int highlightHeight;
        switch (currentAnchor) {
            case TOP_LEFT -> {
                highlightX = 0;
                highlightY = 0;
                highlightWidth = regionX1;
                highlightHeight = regionY2;
            }
            case TOP_RIGHT -> {
                highlightX = regionX2;
                highlightY = 0;
                highlightWidth = width - regionX2;
                highlightHeight = regionY2;
            }
            case BOTTOM_LEFT -> {
                highlightX = 0;
                highlightY = regionY2;
                highlightWidth = regionX1;
                highlightHeight = height - regionY2;
            }
            case BOTTOM_RIGHT -> {
                highlightX = regionX2;
                highlightY = regionY2;
                highlightWidth = width - regionX2;
                highlightHeight = height - regionY2;
            }
            case TOP_CENTER -> {
                highlightX = regionX1;
                highlightY = 0;
                highlightWidth = regionX2 - regionX1;
                highlightHeight = regionY2;
            }
            case BOTTOM_CENTER -> {
                highlightX = regionX1;
                highlightY = regionY2;
                highlightWidth = regionX2 - regionX1;
                highlightHeight = height - regionY2;
            }
            default -> throw new IllegalStateException("Unhandled toast anchor: " + currentAnchor);
        }
        graphics.fill(
                highlightX,
                highlightY,
                highlightX + highlightWidth,
                highlightY + highlightHeight,
                0x44FFAA00);
        graphics.fill(regionX1, 0, regionX1 + 1, height, 0x44FFFFFF);
        graphics.fill(regionX2, 0, regionX2 + 1, height, 0x44FFFFFF);
        graphics.fill(0, regionY2, width, regionY2 + 1, 0x44FFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (isOnLeftEdge(mouseX, mouseY)) {
            beginDrag(DragMode.RESIZE_LEFT, mouseX, mouseY);
            return true;
        }
        if (isOnRightEdge(mouseX, mouseY)) {
            beginDrag(DragMode.RESIZE_RIGHT, mouseX, mouseY);
            return true;
        }
        if (isOnTopEdge(mouseX, mouseY)) {
            beginDrag(DragMode.RESIZE_TOP, mouseX, mouseY);
            return true;
        }
        if (isOnBottomEdge(mouseX, mouseY)) {
            beginDrag(DragMode.RESIZE_BOTTOM, mouseX, mouseY);
            return true;
        }
        if (isOverToast(mouseX, mouseY)) {
            beginDrag(DragMode.MOVE, mouseX, mouseY);
            return true;
        }
        return false;
    }

    /** beginDrag: 记录一次拖拽所需的初始几何数据。 */
    private void beginDrag(DragMode nextDragMode, double mouseX, double mouseY) {
        dragMode = nextDragMode;
        dragStartMouseX = (int) mouseX;
        dragStartMouseY = (int) mouseY;
        dragStartX = toastAbsoluteX;
        dragStartY = toastAbsoluteY;
        dragStartWidth = toastWidth;
        dragStartHeight = toastHeight;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0 || dragMode == DragMode.NONE) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        int deltaX = (int) mouseX - dragStartMouseX;
        int deltaY = (int) mouseY - dragStartMouseY;
        switch (dragMode) {
            case MOVE -> movePreview(deltaX, deltaY);
            case RESIZE_LEFT -> resizeLeft(deltaX);
            case RESIZE_RIGHT -> resizeRight(deltaX);
            case RESIZE_TOP -> resizeTop(deltaY);
            case RESIZE_BOTTOM -> resizeBottom(deltaY);
            case NONE -> {
            }
        }
        return true;
    }

    /** movePreview: 移动预览并在跨区域时更新锚点。 */
    private void movePreview(int deltaX, int deltaY) {
        toastAbsoluteX = clamp(dragStartX + deltaX, 0, Math.max(0, width - toastWidth));
        toastAbsoluteY = clamp(dragStartY + deltaY, 0, Math.max(0, height - toastHeight));
        currentAnchor = detectAnchor(toastAbsoluteX + toastWidth / 2, toastAbsoluteY + toastHeight / 2);
    }

    /** resizeLeft: 固定右边界并调整宽度。 */
    private void resizeLeft(int deltaX) {
        int nextWidth = clamp(dragStartWidth - deltaX, MIN_WIDTH, MAX_WIDTH);
        nextWidth = Math.min(nextWidth, dragStartX + dragStartWidth);
        toastWidth = nextWidth;
        toastAbsoluteX = dragStartX + dragStartWidth - nextWidth;
    }

    /** resizeRight: 固定左边界并调整宽度。 */
    private void resizeRight(int deltaX) {
        int availableWidth = Math.max(MIN_WIDTH, width - dragStartX);
        toastWidth = clamp(dragStartWidth + deltaX, MIN_WIDTH, Math.min(MAX_WIDTH, availableWidth));
    }

    /** resizeTop: 固定底边界并调整高度。 */
    private void resizeTop(int deltaY) {
        int nextHeight = clamp(dragStartHeight - deltaY, MIN_HEIGHT, MAX_HEIGHT);
        nextHeight = Math.min(nextHeight, dragStartY + dragStartHeight);
        toastHeight = nextHeight;
        toastAbsoluteY = dragStartY + dragStartHeight - nextHeight;
    }

    /** resizeBottom: 固定顶边界并调整高度。 */
    private void resizeBottom(int deltaY) {
        int availableHeight = Math.max(MIN_HEIGHT, height - dragStartY);
        toastHeight = clamp(dragStartHeight + deltaY, MIN_HEIGHT, Math.min(MAX_HEIGHT, availableHeight));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragMode = DragMode.NONE;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    /** clamp: 将整数限制在指定闭区间内。 */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
