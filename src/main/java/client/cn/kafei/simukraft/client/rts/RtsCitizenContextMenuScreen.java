package client.cn.kafei.simukraft.client.rts;

import common.cn.kafei.simukraft.network.rts.RtsCitizenActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/** RTS 市民右键菜单：提供信息、商店和移动三项操作。 */
@OnlyIn(Dist.CLIENT)
public final class RtsCitizenContextMenuScreen extends Screen {
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
    private final UUID citizenId;
    private final Component citizenName;
    private final int cursorX;
    private final int cursorY;
    private int menuX;
    private int menuY;

    private RtsCitizenContextMenuScreen(UUID citizenId, Component citizenName, int cursorX, int cursorY) {
        super(Component.translatable("gui.simukraft.rts.citizen_menu.title"));
        this.citizenId = citizenId;
        this.citizenName = citizenName == null ? Component.empty() : citizenName.copy();
        this.cursorX = cursorX;
        this.cursorY = cursorY;
    }

    /** open: 在系统光标旁打开指定市民的紧凑 RTS 操作菜单。 */
    public static void open(UUID citizenId, Component citizenName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (citizenId == null || minecraft.screen != null) {
            return;
        }
        int screenWidth = minecraft.getWindow().getScreenWidth();
        int screenHeight = minecraft.getWindow().getScreenHeight();
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        int cursorX = screenWidth <= 0 ? guiWidth / 2 : (int) (minecraft.mouseHandler.xpos() * guiWidth / screenWidth);
        int cursorY = screenHeight <= 0 ? guiHeight / 2 : (int) (minecraft.mouseHandler.ypos() * guiHeight / screenHeight);
        minecraft.setScreen(new RtsCitizenContextMenuScreen(citizenId, citizenName, cursorX, cursorY));
    }

    @Override
    protected void init() {
        menuX = Math.max(4, Math.min(cursorX, width - MENU_WIDTH - 4));
        menuY = Math.max(4, Math.min(cursorY, height - MENU_HEIGHT - 4));
    }

    /** openInfo: 请求服务端以 RTS 远程会话打开市民信息界面。 */
    private void openInfo() {
        sendAction(RtsCitizenActionPacket.Action.OPEN_INFO);
    }

    /** openShop: 请求服务端打开商店，非商业员工由服务端回退到信息界面。 */
    private void openShop() {
        sendAction(RtsCitizenActionPacket.Action.OPEN_SHOP);
    }

    /** beginMove: 将指定市民设为下一次地表点击的移动对象。 */
    private void beginMove() {
        RtsSelectionManager.beginCitizenMove(citizenId);
        onClose();
    }

    /** sendAction: 发送单一市民的界面打开操作并关闭当前下拉菜单。 */
    private void sendAction(RtsCitizenActionPacket.Action action) {
        PacketDistributor.sendToServer(new RtsCitizenActionPacket(action, List.of(citizenId), BlockPos.ZERO));
        onClose();
    }

    @Override
    public void render(@SuppressWarnings("null") GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + 1, COLOR_BORDER);
        graphics.fill(menuX, menuY + 1, menuX + MENU_WIDTH, menuY + MENU_HEIGHT, COLOR_BACKGROUND);
        graphics.drawString(font, fitTitle(citizenName.getString()), menuX + 6, menuY + 6, COLOR_TITLE, false);

        int rowY = menuY + HEADER_HEIGHT;
        graphics.fill(menuX, rowY, menuX + MENU_WIDTH, rowY + DIVIDER_HEIGHT, COLOR_DIVIDER);
        rowY += DIVIDER_HEIGHT;
        renderActionRow(graphics, mouseX, mouseY, rowY, 0, "gui.simukraft.rts.citizen_menu.info");
        rowY += ROW_HEIGHT;
        graphics.fill(menuX, rowY, menuX + MENU_WIDTH, rowY + DIVIDER_HEIGHT, COLOR_DIVIDER);
        rowY += DIVIDER_HEIGHT;
        renderActionRow(graphics, mouseX, mouseY, rowY, 1, "gui.simukraft.rts.citizen_menu.shop");
        rowY += ROW_HEIGHT;
        graphics.fill(menuX, rowY, menuX + MENU_WIDTH, rowY + DIVIDER_HEIGHT, COLOR_DIVIDER);
        rowY += DIVIDER_HEIGHT;
        renderActionRow(graphics, mouseX, mouseY, rowY, 2, "gui.simukraft.rts.citizen_menu.move");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int action = actionAt(mouseX, mouseY);
        if (button == 0 && action >= 0) {
            switch (action) {
                case 0 -> openInfo();
                case 1 -> openShop();
                case 2 -> beginMove();
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

    /** renderActionRow: 绘制一项带悬停色的紧凑下拉菜单操作。 */
    private void renderActionRow(GuiGraphics graphics, int mouseX, int mouseY, int rowY, int action, String key) {
        boolean hovered = actionAt(mouseX, mouseY) == action;
        if (hovered) {
            graphics.fill(menuX, rowY, menuX + MENU_WIDTH, rowY + ROW_HEIGHT, COLOR_HOVER);
        }
        graphics.drawString(font, fitTitle(Component.translatable(key).getString()), menuX + 6, rowY + 4,
                hovered ? COLOR_TITLE : COLOR_ACTION, false);
    }

    /** actionAt: 解析鼠标坐标对应的菜单操作索引。 */
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

    /** fitTitle: 将市民名称裁剪到现有菜单宽度，避免文本越界。 */
    private String fitTitle(String title) {
        int maxWidth = MENU_WIDTH - 12;
        if (font.width(title) <= maxWidth) {
            return title;
        }
        String ellipsis = "...";
        return font.plainSubstrByWidth(title, Math.max(1, maxWidth - font.width(ellipsis))) + ellipsis;
    }
}
