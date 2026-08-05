package client.cn.kafei.simukraft.client.logistics;

import client.cn.kafei.simukraft.client.selection.TwoPointSelectionScreen;
import common.cn.kafei.simukraft.network.logistics.LogisticsBoxActionPacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsClientBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsClientBoxOpenResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class LogisticsClientBoxScreenOpener {
    private LogisticsClientBoxScreenOpener() {
    }

    /** request: 请求打开旧版物流客户端盒主界面。 */
    public static void request(BlockPos pos) {
        PacketDistributor.sendToServer(new LogisticsClientBoxOpenRequestPacket(pos));
    }

    /** open: 接收服务端快照并打开旧版四入口主界面。 */
    public static void open(LogisticsClientBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> minecraft.setScreen(new LogisticsClientBoxScreen(packet)));
        }
    }

    private static final class LogisticsClientBoxScreen extends Screen {
        private static final int BUTTON_WIDTH = 160;
        private static final int BUTTON_HEIGHT = 20;
        private static final int BUTTON_GAP = 24;

        private final LogisticsClientBoxOpenResponsePacket packet;

        private LogisticsClientBoxScreen(LogisticsClientBoxOpenResponsePacket packet) {
            super(Component.translatable("gui.simukraft.logistics.client.title"));
            this.packet = packet;
        }

        /** init: 创建旧版两个居中入口按钮。 */
        @Override
        protected void init() {
            int centerX = this.width / 2;
            int startY = this.height / 2 - 12;
            Button bind = addRenderableWidget(LogisticsNativeStyle.button(packet.ports().isEmpty()
                            ? Component.translatable("gui.simukraft.logistics.bind_port")
                            : Component.translatable("gui.simukraft.logistics.bind_port.count", packet.ports().size()),
                    centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    () -> TwoPointSelectionScreen.openLogistics(packet.boxPos(), LogisticsBoxActionPacket.Action.BIND_CLIENT_AREA)));
            bind.active = packet.hasCity();
            Button manage = addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.manage_ports"),
                    centerX - BUTTON_WIDTH / 2, startY + BUTTON_GAP, BUTTON_WIDTH, BUTTON_HEIGHT,
                    () -> openScreen(new LogisticsClientPortManageScreen(packet))));
            manage.active = hasPorts();
        }

        /** renderBackground: 绘制旧版半透明深色背景。 */
        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            LogisticsNativeStyle.drawBackdrop(graphics, this.width, this.height);
        }

        /** render: 绘制旧版居中标题。 */
        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 44, LogisticsNativeStyle.TEXT);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        private boolean hasPorts() {
            return !packet.ports().isEmpty();
        }

        /** openScreen: 安全切换到旧版客户端子页面。 */
        private void openScreen(Screen screen) {
            Minecraft minecraft = this.minecraft;
            if (minecraft != null) {
                minecraft.setScreen(screen);
            }
        }
    }
}
