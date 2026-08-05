package client.cn.kafei.simukraft.client.logistics;

import common.cn.kafei.simukraft.logistics.LogisticsDirection;
import common.cn.kafei.simukraft.logistics.LogisticsPortData;
import common.cn.kafei.simukraft.network.logistics.LogisticsBoxActionPacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsClientBoxOpenResponsePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
final class LogisticsClientPortManageScreen extends Screen {
    private final LogisticsClientBoxOpenResponsePacket packet;
    private EditBox nameField;

    LogisticsClientPortManageScreen(LogisticsClientBoxOpenResponsePacket packet) {
        super(Component.translatable("gui.simukraft.logistics.manage_ports"));
        this.packet = packet;
    }

    /** init: 创建返回按钮和可见端口删除按钮。 */
    @Override
    protected void init() {
        int bottomY = this.height - 28;
        int controlsX = this.width / 2 - 150;
        nameField = new EditBox(this.font, controlsX, bottomY, 126, 20, Component.translatable("gui.simukraft.logistics.client_name"));
        nameField.setMaxLength(64);
        nameField.setValue(packet.name());
        addRenderableWidget(nameField);
        addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.rename_endpoint"),
                controlsX + 132, bottomY, 92, 20, this::renameEndpoint));
        addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.button.back"),
                controlsX + 230, bottomY, 70, 20, () -> LogisticsClientBoxScreenOpener.open(packet)));
        int startX = this.width / 2 - 150;
        int rowY = 38;
        for (LogisticsPortData port : packet.ports()) {
            addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.delete"),
                    startX + 250, rowY - 2, 50, 16,
                    () -> send(LogisticsBoxActionPacket.Action.REMOVE_CLIENT_PORT, null, port.id())));
            rowY += 20;
            if (rowY > this.height - 44) {
                break;
            }
        }
    }

    /** renderBackground: 绘制旧版深色背景。 */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LogisticsNativeStyle.drawBackdrop(graphics, this.width, this.height);
    }

    /** render: 绘制端口列表。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, LogisticsNativeStyle.TEXT);
        int startX = this.width / 2 - 150;
        int rowY = 38;
        if (packet.ports().isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.simukraft.logistics.empty"), this.width / 2, this.height / 2, LogisticsNativeStyle.TEXT_MUTED);
        } else {
            for (LogisticsPortData port : packet.ports()) {
                LogisticsNativeStyle.drawFitString(graphics, this.font,
                        port.name() + " [" + port.kind() + "] " + LogisticsNativeStyle.posText(port.pos()),
                        startX, rowY, 240, LogisticsNativeStyle.TEXT_DIM);
                rowY += 20;
                if (rowY > this.height - 44) {
                    break;
                }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** send: 向服务端发送客户端端口管理操作。 */
    private void send(LogisticsBoxActionPacket.Action action, UUID channelId, String value) {
        PacketDistributor.sendToServer(new LogisticsBoxActionPacket(packet.boxPos(), action, null, channelId, BlockPos.ZERO, value, LogisticsDirection.WAREHOUSE_TO_CLIENT));
    }

    /** renameEndpoint: 把输入框中的名称提交为当前客户端端点名。 */
    private void renameEndpoint() {
        String name = nameField != null ? nameField.getValue() : packet.name();
        send(LogisticsBoxActionPacket.Action.RENAME_CLIENT, null, name);
    }
}
