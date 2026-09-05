package client.cn.kafei.simukraft.client.bank;

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
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import common.cn.kafei.simukraft.bank.BankControlBoxService;
import common.cn.kafei.simukraft.bank.BankService;
import common.cn.kafei.simukraft.economy.CoinDenominations;
import common.cn.kafei.simukraft.network.bank.BankControlBoxActionPacket;
import common.cn.kafei.simukraft.network.bank.BankControlBoxDemolishPacket;
import common.cn.kafei.simukraft.network.bank.BankControlBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.bank.BankControlBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.npc.hire.NpcHireFirePacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** BankControlBoxScreenOpener: 银行存取转账界面，Ore 主题并按 GUI 缩放收缩。 */
@OnlyIn(Dist.CLIENT)
public final class BankControlBoxScreenOpener {
    private static final int MAX_PANEL_WIDTH = 400;
    private static final int MAX_PANEL_HEIGHT = 280;

    private BankControlBoxScreenOpener() {
    }

    /** request: 请求打开银行控制箱。 */
    public static void request(BlockPos pos) {
        PacketDistributor.sendToServer(new BankControlBoxOpenRequestPacket(pos));
    }

    /** open: 打开或刷新银行界面。 */
    public static void open(BankControlBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> minecraft.setScreen(new ModularUIScreen(createUi(packet), Component.empty())));
        }
    }

    private static ModularUI createUi(BankControlBoxOpenResponsePacket packet) {
        var window = Minecraft.getInstance().getWindow();
        int screenWidth = Math.max(1, window.getGuiScaledWidth());
        int screenHeight = Math.max(1, window.getGuiScaledHeight());
        LayoutMetrics metrics = layoutMetrics(screenWidth, screenHeight);

        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.paddingAll(metrics.rootPadding());
        });
        root.addChild(SimuKraftUiTheme.createShellPanel(screenWidth, screenHeight));

        UIElement panel = new UIElement().layout(layout -> {
            layout.width(metrics.panelWidth());
            layout.height(metrics.panelHeight());
            layout.paddingAll(metrics.panelPadding());
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(metrics.gap());
        }).addClass("simukraft_panel");

        TextField amountField = amountField();
        TextField targetField = targetField();
        boolean canOperate = packet.hasBuilding() && packet.hasTeller();

        panel.addChild(titleBar(packet, metrics));
        panel.addChild(infoLine("gui.simukraft.bank.building_line",
                packet.hasBuilding() ? packet.buildingName() : Component.translatable("gui.simukraft.bank.none").getString(),
                0xFFF5F5A0, metrics));
        panel.addChild(infoLineRaw(Component.translatable("gui.simukraft.bank.status_line",
                Component.translatable(packet.statusKey())), 0xFFF5F5A0, metrics));
        panel.addChild(infoLine("gui.simukraft.bank.teller_line",
                packet.hasTeller() ? packet.tellerName() : Component.translatable("gui.simukraft.bank.none").getString(),
                0xFFF5F5A0, metrics));
        panel.addChild(infoLineRaw(Component.translatable("gui.simukraft.bank.virtual_line",
                CoinDenominations.formatYuan(packet.cityFunds())), SimuKraftUiTheme.TEXT_SUCCESS_COLOR, metrics));
        panel.addChild(infoLineRaw(Component.translatable("gui.simukraft.bank.cash_line",
                CoinDenominations.formatYuan(packet.playerCash())), 0xFFFFD37C, metrics));
        panel.addChild(labeledField(Component.translatable("gui.simukraft.bank.amount"), amountField, metrics));
        panel.addChild(labeledField(Component.translatable("gui.simukraft.bank.transfer_target"), targetField, metrics));
        panel.addChild(buttonRow(metrics,
                actionButton(Component.translatable("gui.simukraft.bank.deposit"),
                        () -> sendAction(packet, BankService.Action.DEPOSIT, parseAmount(amountField), ""), canOperate, metrics),
                actionButton(Component.translatable("gui.simukraft.bank.withdraw"),
                        () -> sendAction(packet, BankService.Action.WITHDRAW, parseAmount(amountField), ""), canOperate, metrics),
                actionButton(Component.translatable("gui.simukraft.bank.transfer"),
                        () -> sendAction(packet, BankService.Action.TRANSFER, parseAmount(amountField), targetField.getValue()),
                        canOperate, metrics)));
        panel.addChild(buttonRow(metrics,
                actionButton(Component.translatable("gui.simukraft.bank.hire"),
                        () -> NpcHireScreen.request(packet.boxPos(), BankControlBoxService.HIRE_SOURCE_TYPE, BankControlBoxService.HIRE_ROLE),
                        packet.hasBuilding() && !packet.hasTeller(), metrics),
                actionButton(Component.translatable("gui.simukraft.bank.fire"),
                        () -> fire(packet), packet.hasTeller(), metrics)));
        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static UIElement titleBar(BankControlBoxOpenResponsePacket packet, LayoutMetrics metrics) {
        UIElement bar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.titleBarHeight());
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.gapAll(metrics.gap());
            layout.flexShrink(0);
        });
        bar.addChild(chromeButton(Component.translatable("gui.button.done"),
                () -> Minecraft.getInstance().setScreen(null), true, metrics));
        bar.addChild(label(Component.translatable("gui.simukraft.bank.title"), Horizontal.CENTER,
                0xFFFFFFFF, metrics.titleBarHeight()).layout(layout -> {
            layout.flex(1);
            layout.height(metrics.titleBarHeight());
        }));
        bar.addChild(chromeButton(Component.translatable("gui.button.demolish"), () -> {
            PacketDistributor.sendToServer(new BankControlBoxDemolishPacket(packet.boxPos()));
            Minecraft.getInstance().setScreen(null);
        }, packet.hasBuilding(), metrics));
        return bar;
    }

    private static UIElement labeledField(Component title, TextField field, LayoutMetrics metrics) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.fieldHeight());
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(metrics.gap());
            layout.flexShrink(0);
        });
        Label caption = label(title, Horizontal.LEFT, 0xFFFFFFFF, metrics.fieldHeight());
        caption.layout(layout -> {
            layout.width(metrics.labelWidth());
            layout.height(metrics.fieldHeight());
            layout.flexShrink(0);
        });
        row.addChild(caption);
        row.addChild(field);
        return row;
    }

    private static UIElement buttonRow(LayoutMetrics metrics, UIElement... buttons) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.flexWrap(FlexWrap.WRAP);
            layout.justifyContent(AlignContent.CENTER);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(metrics.gap());
            layout.flexShrink(0);
        });
        for (UIElement button : buttons) {
            row.addChild(button);
        }
        return row;
    }

    private static UIElement infoLine(String key, String value, int color, LayoutMetrics metrics) {
        return infoLineRaw(Component.translatable(key, value), color, metrics);
    }

    private static UIElement infoLineRaw(Component text, int color, LayoutMetrics metrics) {
        return label(text, Horizontal.LEFT, color, metrics.infoLineHeight());
    }

    private static TextField amountField() {
        TextField field = new TextField();
        field.setNumbersOnlyDouble(0.0D, 1_000_000_000.0D);
        field.setText("0", false);
        field.layout(layout -> {
            layout.flex(1);
            layout.heightPercent(100);
            layout.minWidth(60);
        });
        return field;
    }

    private static TextField targetField() {
        TextField field = new TextField();
        field.setAnyString();
        field.setText("", false);
        field.layout(layout -> {
            layout.flex(1);
            layout.heightPercent(100);
            layout.minWidth(60);
        });
        return field;
    }

    private static UIElement actionButton(Component text, Runnable action, boolean active, LayoutMetrics metrics) {
        return chromeButton(text, action, active, metrics.actionWidth(), metrics.actionHeight());
    }

    private static UIElement chromeButton(Component text, Runnable action, boolean active, LayoutMetrics metrics) {
        return chromeButton(text, action, active, metrics.chromeWidth(), metrics.chromeHeight());
    }

    private static UIElement chromeButton(Component text, Runnable action, boolean active, int width, int height) {
        Button button = new Button();
        button.setText(text);
        button.setActive(active);
        if (active) {
            button.setOnClick(event -> action.run());
        }
        button.layout(layout -> {
            layout.width(width);
            layout.height(height);
            layout.flexShrink(0);
        });
        return button;
    }

    private static Label label(Component text, Horizontal horizontal, int color, int height) {
        Label label = new Label();
        label.setText(text);
        label.setOverflowVisible(false);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
        label.textStyle(style -> style.textColor(color).textShadow(true).textWrap(TextWrap.HOVER_ROLL)
                .textAlignHorizontal(horizontal).textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static void sendAction(BankControlBoxOpenResponsePacket packet, BankService.Action action, double amount, String target) {
        PacketDistributor.sendToServer(new BankControlBoxActionPacket(packet.boxPos(), action, amount, target));
    }

    private static double parseAmount(TextField field) {
        try {
            return Double.parseDouble(field.getValue().trim().replace(',', '.'));
        } catch (Exception exception) {
            return 0.0D;
        }
    }

    private static void fire(BankControlBoxOpenResponsePacket packet) {
        if (packet.tellerId() != null) {
            PacketDistributor.sendToServer(new NpcHireFirePacket(packet.boxPos(), BankControlBoxService.HIRE_SOURCE_TYPE,
                    BankControlBoxService.HIRE_ROLE, packet.tellerId()));
        }
        Minecraft.getInstance().setScreen(null);
    }

    private static LayoutMetrics layoutMetrics(int screenWidth, int screenHeight) {
        int rootPadding = clamp(Math.round(Math.min(screenWidth, screenHeight) * 0.02F), 4, 8);
        int availableWidth = Math.max(1, screenWidth - rootPadding * 2);
        int availableHeight = Math.max(1, screenHeight - rootPadding * 2);
        int panelWidth = Math.min(MAX_PANEL_WIDTH, availableWidth);
        int panelHeight = Math.min(MAX_PANEL_HEIGHT, availableHeight);
        int panelPadding = clamp(Math.round(panelWidth * 0.024F), 6, 10);
        int gap = clamp(Math.round(panelHeight * 0.016F), 3, 5);
        int titleBarHeight = clamp(Math.round(panelHeight * 0.09F), 18, 24);
        int infoLineHeight = clamp(Math.round(panelHeight * 0.055F), 11, 14);
        int fieldHeight = clamp(Math.round(panelHeight * 0.085F), 18, 22);
        int actionHeight = clamp(Math.round(panelHeight * 0.085F), 18, 22);
        int chromeHeight = clamp(titleBarHeight - 2, 16, 22);
        int chromeWidth = clamp(Math.round(panelWidth * 0.18F), 44, 62);
        int labelWidth = clamp(Math.round(panelWidth * 0.24F), 56, 88);
        int innerWidth = Math.max(80, panelWidth - panelPadding * 2);
        int actionWidth = clamp((innerWidth - gap * 2) / 3, 70, 120);
        return new LayoutMetrics(rootPadding, panelWidth, panelHeight, panelPadding, gap, titleBarHeight,
                infoLineHeight, fieldHeight, actionHeight, actionWidth, chromeWidth, chromeHeight, labelWidth);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record LayoutMetrics(int rootPadding, int panelWidth, int panelHeight, int panelPadding, int gap,
                                 int titleBarHeight, int infoLineHeight, int fieldHeight, int actionHeight,
                                 int actionWidth, int chromeWidth, int chromeHeight, int labelWidth) {
    }
}
