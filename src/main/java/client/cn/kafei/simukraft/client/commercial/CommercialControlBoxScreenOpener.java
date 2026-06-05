package client.cn.kafei.simukraft.client.commercial;

import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import common.cn.kafei.simukraft.commercial.CommercialConstants;
import common.cn.kafei.simukraft.network.commercial.CommercialControlBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.commercial.CommercialControlBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.npc.hire.NpcHireFirePacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class CommercialControlBoxScreenOpener {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 210;
    private static final int TEXT_LINE_HEIGHT = 18;
    private static final float TEXT_ROLL_SPEED = 0.25F;
    private CommercialControlBoxScreenOpener() {
    }

    /** request: 请求服务端打开商业控制箱管理界面。 */
    public static void request(BlockPos pos) {
        PacketDistributor.sendToServer(new CommercialControlBoxOpenRequestPacket(pos));
    }

    /** open: 打开或刷新商业控制箱管理界面。 */
    public static void open(CommercialControlBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        packet.boxPos().immutable();
        minecraft.execute(() -> minecraft.setScreen(new CommercialControlBoxScreen(createUi(packet), Component.empty())));
    }

    private static ModularUI createUi(CommercialControlBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = Math.max(320, minecraft.getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(240, minecraft.getWindow().getGuiScaledHeight());
        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.paddingAll(8);
        });
        root.addChild(SimuKraftUiTheme.createShellPanel(screenWidth, screenHeight));
        UIElement panel = new UIElement().layout(layout -> {
            layout.width(Math.min(PANEL_WIDTH, screenWidth - 16));
            layout.height(Math.min(PANEL_HEIGHT, screenHeight - 16));
            layout.paddingAll(12);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(8);
        }).addClass("simukraft_panel");
        panel.addChild(titleBar());
        panel.addChild(statusBody(packet));
        panel.addChild(actionRow(packet));
        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement titleBar() {
        UIElement bar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(24);
        });
        bar.addChild(label(Component.translatable("gui.simukraft.commercial.title"), Horizontal.CENTER, 0xFFFFFFFF, 24, TextWrap.HIDE));
        bar.addChild(panelTopButton("gui.button.done", 58, 22, CommercialControlBoxScreenOpener::close));
        return bar;
    }

    private static UIElement statusBody(CommercialControlBoxOpenResponsePacket packet) {
        UIElement body = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(5);
        });
        body.addChild(label(buildingLine(packet), Horizontal.LEFT, 0xFFF5F5A0, TEXT_LINE_HEIGHT, TextWrap.HOVER_ROLL));
        body.addChild(label(definitionLine(packet), Horizontal.LEFT, packet.definitionValid() ? 0xFFF5F5A0 : 0xFFFF7070, TEXT_LINE_HEIGHT, TextWrap.HOVER_ROLL));
        body.addChild(label(workerLine(packet), Horizontal.LEFT, 0xFFF5F5A0, TEXT_LINE_HEIGHT, TextWrap.HOVER_ROLL));
        body.addChild(label(statusLine(packet), Horizontal.LEFT, packet.running() ? 0xFF7CE07C : 0xFFFFB060, TEXT_LINE_HEIGHT, TextWrap.HOVER_ROLL));
        body.addChild(label(Component.translatable("gui.simukraft.commercial.balance", money(packet.cityBalance())), Horizontal.LEFT, 0xFFE0E0FF, TEXT_LINE_HEIGHT, TextWrap.HOVER_ROLL));
        return body;
    }

    private static UIElement actionRow(CommercialControlBoxOpenResponsePacket packet) {
        boolean canManage = packet.hasBuilding() && packet.definitionValid();
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(26);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(8);
        });
        row.addChild(flatButton(Component.translatable("gui.simukraft.commercial.hire"), () -> hire(packet), canManage && !packet.hasWorker(), 86, 24));
        row.addChild(flatButton(Component.translatable("gui.simukraft.commercial.fire"), () -> fire(packet), packet.hasWorker(), 86, 24));
        return row;
    }

    private static Button panelTopButton(String key, int width, int height, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(width);
            layout.height(height);
        });
        return button;
    }

    private static Button flatButton(Component text, Runnable action, boolean active, int width, int height) {
        Button button = new Button();
        button.setText(text);
        button.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED));
        if (active) {
            button.setOnClick(event -> action.run());
        }
        button.setActive(active);
        button.layout(layout -> {
            layout.width(width);
            layout.height(height);
            layout.flexShrink(0);
        });
        return button;
    }

    private static Label label(Component text, Horizontal horizontal, int color, int height, TextWrap wrap) {
        Label label = new Label();
        label.setText(text);
        label.setOverflowVisible(false);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
        label.textStyle(style -> style
                .textColor(color)
                .textShadow(true)
                .textWrap(wrap)
                .rollSpeed(TEXT_ROLL_SPEED)
                .textAlignHorizontal(horizontal)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static Component buildingLine(CommercialControlBoxOpenResponsePacket packet) {
        Component value = packet.hasBuilding() ? Component.literal(packet.buildingName()) : Component.translatable("gui.simukraft.commercial.none");
        return Component.translatable("gui.simukraft.commercial.building_line", value);
    }

    private static Component definitionLine(CommercialControlBoxOpenResponsePacket packet) {
        Component value = packet.definitionValid() ? Component.literal(packet.definitionName()) : Component.translatable("gui.simukraft.commercial.definition_missing");
        return Component.translatable("gui.simukraft.commercial.definition_line", value);
    }

    private static Component workerLine(CommercialControlBoxOpenResponsePacket packet) {
        Component value = packet.hasWorker() ? Component.literal(packet.workerName()) : Component.translatable("gui.simukraft.commercial.none");
        return Component.translatable("gui.simukraft.commercial.worker_line", value);
    }

    private static Component statusLine(CommercialControlBoxOpenResponsePacket packet) {
        Component status = Component.translatable(packet.statusKey());
        if (!packet.statusText().isBlank()) {
            status = status.copy().append(Component.literal(" " + packet.statusText()));
        }
        return Component.translatable("gui.simukraft.commercial.status_line", status);
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void hire(CommercialControlBoxOpenResponsePacket packet) {
        NpcHireScreen.request(packet.boxPos(), CommercialConstants.HIRE_SOURCE_TYPE, CommercialConstants.HIRE_ROLE);
    }

    private static void fire(CommercialControlBoxOpenResponsePacket packet) {
        if (packet.hasWorker() && packet.workerId() != null) {
            PacketDistributor.sendToServer(new NpcHireFirePacket(packet.boxPos(), CommercialConstants.HIRE_SOURCE_TYPE, CommercialConstants.HIRE_ROLE, packet.workerId()));
        }
    }

    private static void close() {
        Minecraft.getInstance().setScreen(null);
    }

    private static final class CommercialControlBoxScreen extends ModularUIScreen {
        private CommercialControlBoxScreen(ModularUI modularUI, Component title) {
            super(modularUI, title);
        }

        @Override
        public void removed() {
            super.removed();
            Minecraft minecraft = Minecraft.getInstance();
            if (!(minecraft.screen instanceof CommercialControlBoxScreen)) {
            }
        }
    }
}
