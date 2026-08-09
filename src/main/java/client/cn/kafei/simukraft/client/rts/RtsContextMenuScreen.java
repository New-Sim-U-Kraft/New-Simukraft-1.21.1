package client.cn.kafei.simukraft.client.rts;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import common.cn.kafei.simukraft.network.rts.RtsDemolishPacket;
import common.cn.kafei.simukraft.network.rts.RtsOpenTargetPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** RTS 右键下拉菜单：只负责客户端菜单呈现和发送已验证的动作请求。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class RtsContextMenuScreen extends Screen {
    private static final int MENU_WIDTH = 76;
    private static final int HEADER_HEIGHT = 20;
    private static final int ROW_HEIGHT = 15;
    private static final int DIVIDER_HEIGHT = 1;
    private static final int MENU_HEIGHT = HEADER_HEIGHT + ROW_HEIGHT * 3 + DIVIDER_HEIGHT * 3;
    private static final int COLOR_BORDER = 0xFF8A8F88;
    private static final int COLOR_BACKGROUND = 0xF022241F;
    private static final int COLOR_HOVER = 0xFF353831;
    private static final int COLOR_DIVIDER = 0xFF60655D;
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_ACTION = 0xFFFFFF00;
    private static final float TEXT_SCALE = 1.0F;
    private final BlockPos targetPos;
    private final Component targetName;
    private final int cursorX;
    private final int cursorY;
    private int menuX;
    private int menuY;

    private RtsContextMenuScreen(BlockPos targetPos, int cursorX, int cursorY) {
        super(Component.translatable("gui.simukraft.rts.menu.title"));
        this.targetPos = targetPos.immutable();
        this.targetName = resolveTargetName(this.targetPos);
        this.cursorX = cursorX;
        this.cursorY = cursorY;
    }

    /** open: 在当前系统光标附近打开菜单。 */
    public static void open(BlockPos targetPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (targetPos == null || minecraft.screen != null) {
            return;
        }
        int width = minecraft.getWindow().getScreenWidth();
        int height = minecraft.getWindow().getScreenHeight();
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        int cursorX = width <= 0 ? guiWidth / 2 : (int) (minecraft.mouseHandler.xpos() * guiWidth / width);
        int cursorY = height <= 0 ? guiHeight / 2 : (int) (minecraft.mouseHandler.ypos() * guiHeight / height);
        minecraft.setScreen(new RtsContextMenuScreen(targetPos, cursorX, cursorY));
    }

    @Override
    protected void init() {
        menuX = Math.max(4, Math.min(cursorX, width - MENU_WIDTH - 4));
        menuY = Math.max(4, Math.min(cursorY, height - MENU_HEIGHT - 4));
    }

    private void openDetails() {
        PacketDistributor.sendToServer(new RtsOpenTargetPacket(targetPos));
        closeMenu();
    }

    private void demolish() {
        PacketDistributor.sendToServer(new RtsDemolishPacket(targetPos));
        closeMenu();
    }

    /** beginMove: 关闭菜单并把目标交给光标落点选择状态。 */
    private void beginMove() {
        RtsSelectionManager.beginMove(targetPos);
        closeMenu();
    }

    private void closeMenu() {
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + 1, COLOR_BORDER);
        graphics.fill(menuX, menuY + 1, menuX + MENU_WIDTH, menuY + MENU_HEIGHT, COLOR_BACKGROUND);
        drawScaledText(graphics, Component.literal(fitTitle(targetName.getString())), menuX + 6, menuY + 6, COLOR_TITLE);

        int rowY = menuY + HEADER_HEIGHT;
        graphics.fill(menuX, rowY, menuX + MENU_WIDTH, rowY + DIVIDER_HEIGHT, COLOR_DIVIDER);
        rowY += DIVIDER_HEIGHT;
        renderActionRow(graphics, mouseX, mouseY, rowY, 0, "gui.simukraft.rts.menu.details");
        rowY += ROW_HEIGHT;
        graphics.fill(menuX, rowY, menuX + MENU_WIDTH, rowY + DIVIDER_HEIGHT, COLOR_DIVIDER);
        rowY += DIVIDER_HEIGHT;
        renderActionRow(graphics, mouseX, mouseY, rowY, 1, "gui.simukraft.rts.menu.move");
        rowY += ROW_HEIGHT;
        graphics.fill(menuX, rowY, menuX + MENU_WIDTH, rowY + DIVIDER_HEIGHT, COLOR_DIVIDER);
        rowY += DIVIDER_HEIGHT;
        renderActionRow(graphics, mouseX, mouseY, rowY, 2, "gui.simukraft.rts.menu.demolish");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int action = actionAt(mouseX, mouseY);
        if (button == 0 && action >= 0) {
            switch (action) {
                case 0 -> openDetails();
                case 1 -> beginMove();
                case 2 -> demolish();
                default -> {
                }
            }
            return true;
        }
        if (button == 0 || button == 1) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderActionRow(GuiGraphics graphics, int mouseX, int mouseY, int rowY, int action, String translationKey) {
        boolean hovered = actionAt(mouseX, mouseY) == action;
        if (hovered) {
            graphics.fill(menuX, rowY, menuX + MENU_WIDTH, rowY + ROW_HEIGHT, COLOR_HOVER);
        }
        drawScaledText(graphics, Component.translatable(translationKey), menuX + 6, rowY + 4,
                hovered ? COLOR_TITLE : COLOR_ACTION);
    }

    private void drawScaledText(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private int actionAt(double mouseX, double mouseY) {
        if (mouseX < menuX || mouseX >= menuX + MENU_WIDTH) {
            return -1;
        }
        int firstRowY = menuY + HEADER_HEIGHT + DIVIDER_HEIGHT;
        for (int action = 0; action < 3; action++) {
            int rowY = firstRowY + action * (ROW_HEIGHT + DIVIDER_HEIGHT);
            if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                return action;
            }
        }
        return -1;
    }

    /** resolveTargetName: 建筑优先显示登记名称，普通方块显示当前语言的本地化名称。 */
    private static Component resolveTargetName(BlockPos targetPos) {
        String buildingName = BuildingBoundsRenderer.knownRtsBuildingNameAt(targetPos);
        if (!buildingName.isBlank()) {
            return Component.literal(buildingName);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            BlockState state = minecraft.level.getBlockState(targetPos);
            if (!state.isAir()) {
                return state.getBlock().getName();
            }
        }
        return Component.empty();
    }

    /** fitTitle: 将标题限制在既有窄菜单宽度内，避免名称越界。 */
    private String fitTitle(String title) {
        int maxWidth = MENU_WIDTH - 12;
        if (font.width(title) <= maxWidth) {
            return title;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return font.plainSubstrByWidth(title, Math.max(1, maxWidth));
        }
        return font.plainSubstrByWidth(title, maxWidth - ellipsisWidth) + ellipsis;
    }
}
