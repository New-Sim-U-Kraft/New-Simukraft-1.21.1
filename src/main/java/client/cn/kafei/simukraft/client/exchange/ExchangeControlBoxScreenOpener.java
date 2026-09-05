package client.cn.kafei.simukraft.client.exchange;

import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import common.cn.kafei.simukraft.economy.CoinDenominations;
import common.cn.kafei.simukraft.exchange.ExchangeControlBoxService;
import common.cn.kafei.simukraft.exchange.ExchangeMarketClock;
import common.cn.kafei.simukraft.exchange.ExchangeQuote;
import common.cn.kafei.simukraft.network.exchange.ExchangeControlBoxActionPacket;
import common.cn.kafei.simukraft.network.exchange.ExchangeControlBoxDemolishPacket;
import common.cn.kafei.simukraft.network.exchange.ExchangeControlBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.exchange.ExchangeControlBoxOpenResponsePacket;
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

/** ExchangeControlBoxScreenOpener: Ore 主题股市界面，按 GUI 缩放收缩。 */
@OnlyIn(Dist.CLIENT)
public final class ExchangeControlBoxScreenOpener {
    private static final int UP = 0xFF55FF55;
    private static final int DOWN = 0xFFFF5555;
    private static final String ROOT_ID = "simukraft-exchange-root";
    private static final String CANDLE_ID = "simukraft-exchange-candle";
    private static final String VOLUME_ID = "simukraft-exchange-volume";
    private static final String PRICE_LINE_ID = "simukraft-exchange-price-line";
    private static final String SHARES_ID = "simukraft-exchange-shares";
    private static final String HOLDING_VALUE_ID = "simukraft-exchange-holding-value";
    private static final String TODAY_CHANGE_ID = "simukraft-exchange-today-change";
    private static final String DAY_LINE_ID = "simukraft-exchange-day-line";
    private static final String FUNDS_LINE_ID = "simukraft-exchange-funds-line";
    private static final String STATUS_LINE_ID = "simukraft-exchange-status-line";
    private static final String COMPANY_ROW_PREFIX = "simukraft-exchange-company-";
    private static ExchangeControlBoxOpenResponsePacket currentPacket;
    private static String selectedId = "";
    private static long polledDay = Long.MIN_VALUE;
    private static int polledHour = Integer.MIN_VALUE;
    private static int refreshTicks;

    private ExchangeControlBoxScreenOpener() {
    }

    /** request: 向服务端请求股市快照。 */
    public static void request(BlockPos pos) {
        PacketDistributor.sendToServer(new ExchangeControlBoxOpenRequestPacket(pos, selectedId == null ? "" : selectedId));
    }

    /** open: 打开或原地刷新股市界面，不拆掉全屏 K 线。 */
    public static void open(ExchangeControlBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> {
            currentPacket = packet;
            selectedId = resolveSelected(packet, selectedId);
            markPolled(minecraft);
            ModularUI ui = currentUi();
            if (ui != null && ui.getElementById(ROOT_ID) != null) {
                applySnapshot(ui, packet);
                return;
            }
            minecraft.setScreen(new ModularUIScreen(createUi(packet, selectedQuote(packet, selectedId)), Component.empty()));
        });
    }

    /** onClientTick: 开着界面时定时拉行情，才能一根一根看到新时段。 */
    public static void onClientTick() {
        if (currentPacket == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ModularUI ui = currentUi();
        if (ui == null || ui.getElementById(ROOT_ID) == null) {
            return;
        }
        refreshTicks++;
        long day = ExchangeMarketClock.dayIndex(minecraft.level.getDayTime());
        int hour = ExchangeMarketClock.hourIndex(minecraft.level.getDayTime());
        if (refreshTicks < 5 && day == polledDay && hour == polledHour) {
            return;
        }
        refreshTicks = 0;
        polledDay = day;
        polledHour = hour;
        request(currentPacket.boxPos());
    }

    private static ModularUI createUi(ExchangeControlBoxOpenResponsePacket packet, ExchangeQuote selected) {
        var window = Minecraft.getInstance().getWindow();
        int screenWidth = Math.max(1, window.getGuiScaledWidth());
        int screenHeight = Math.max(1, window.getGuiScaledHeight());
        LayoutMetrics metrics = layoutMetrics(screenWidth, screenHeight);

        UIElement root = new UIElement().setId(ROOT_ID).layout(layout -> {
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

        panel.addChild(titleBar(packet, metrics));
        panel.addChild(header(packet, selected, metrics));
        panel.addChild(body(packet, selected, metrics));
        panel.addChild(holding(selected, metrics));
        panel.addChild(actions(packet, selected, metrics));
        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static UIElement titleBar(ExchangeControlBoxOpenResponsePacket packet, LayoutMetrics metrics) {
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
                () -> Minecraft.getInstance().setScreen(null), true, metrics.chromeWidth(), metrics.chromeHeight()));
        bar.addChild(label(Component.translatable("gui.simukraft.exchange.title"), Horizontal.CENTER,
                0xFFFFFFFF, metrics.titleBarHeight()).layout(layout -> {
            layout.flex(1);
            layout.height(metrics.titleBarHeight());
        }));
        bar.addChild(chromeButton(Component.translatable("gui.button.demolish"), () -> {
            PacketDistributor.sendToServer(new ExchangeControlBoxDemolishPacket(packet.boxPos()));
            Minecraft.getInstance().setScreen(null);
        }, packet.hasBuilding(), metrics.chromeWidth(), metrics.chromeHeight()));
        return bar;
    }

    private static UIElement header(ExchangeControlBoxOpenResponsePacket packet, ExchangeQuote selected, LayoutMetrics metrics) {
        UIElement header = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(1);
            layout.flexShrink(0);
        });
        Label dayLine = label(dayLineText(packet), Horizontal.LEFT, 0xFFF5F5A0, metrics.infoLineHeight());
        dayLine.setId(DAY_LINE_ID);
        header.addChild(dayLine);
        Label fundsLine = label(fundsLineText(packet), Horizontal.LEFT, UP, metrics.infoLineHeight());
        fundsLine.setId(FUNDS_LINE_ID);
        header.addChild(fundsLine);
        Label priceLine = label(priceLineText(selected), Horizontal.LEFT, changeColor(selected), metrics.infoLineHeight());
        priceLine.setId(PRICE_LINE_ID);
        header.addChild(priceLine);
        Label statusLine = label(Component.translatable(packet.statusKey()), Horizontal.LEFT,
                SimuKraftUiTheme.TEXT_MUTED_COLOR, metrics.infoLineHeight());
        statusLine.setId(STATUS_LINE_ID);
        header.addChild(statusLine);
        return header;
    }

    private static UIElement body(ExchangeControlBoxOpenResponsePacket packet, ExchangeQuote selected, LayoutMetrics metrics) {
        UIElement body = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(metrics.gap());
            layout.minHeight(64);
        });
        UIElement charts = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(metrics.gap());
            layout.minWidth(80);
        });
        ExchangeChartElement candle = new ExchangeChartElement(ExchangeChartElement.Mode.CANDLE, selected);
        candle.setId(CANDLE_ID);
        ExchangeChartElement volume = new ExchangeChartElement(ExchangeChartElement.Mode.VOLUME, selected);
        volume.setId(VOLUME_ID);
        charts.addChild(chartBlock("gui.simukraft.exchange.kline", candle, 1.0F, 0));
        charts.addChild(chartBlock("gui.simukraft.exchange.volume", volume, 0.0F, metrics.volumeHeight()));
        body.addChild(charts);
        body.addChild(companyList(packet, selected, metrics));
        return body;
    }

    private static UIElement chartBlock(String titleKey, ExchangeChartElement chart, float flex, int height) {
        UIElement block = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            if (flex > 0.0F) {
                layout.flex(flex);
            } else {
                layout.height(Math.max(32, height));
                layout.flexShrink(0);
            }
        });
        block.addChild(label(Component.translatable(titleKey), Horizontal.LEFT,
                SimuKraftUiTheme.TEXT_MUTED_COLOR, 12).layout(layout -> {
            layout.widthPercent(100);
            layout.height(12);
            layout.flexShrink(0);
        }));
        chart.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
            layout.minHeight(28);
        });
        block.addChild(chart);
        return block;
    }

    private static UIElement companyList(ExchangeControlBoxOpenResponsePacket packet, ExchangeQuote selected, LayoutMetrics metrics) {
        UIElement column = new UIElement().layout(layout -> {
            layout.width(metrics.listWidth());
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.flexShrink(0);
        }).addClass("simukraft_grid_panel");
        column.addChild(label(Component.translatable("gui.simukraft.exchange.list"), Horizontal.LEFT,
                SimuKraftUiTheme.TEXT_MUTED_COLOR, 12).layout(layout -> {
            layout.widthPercent(100);
            layout.height(14);
            layout.flexShrink(0);
        }));
        UIElement content = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(2);
        });
        String selectedKey = selected != null ? selected.id() : "";
        for (ExchangeQuote quote : packet.quotes()) {
            content.addChild(companyRow(quote, quote.id().equals(selectedKey), metrics));
        }
        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.NEVER));
        scroller.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        });
        scroller.addScrollViewChild(content);
        column.addChild(scroller);
        return column;
    }

    private static UIElement companyRow(ExchangeQuote quote, boolean selected, LayoutMetrics metrics) {
        Button button = new Button().noText();
        button.setId(companyRowId(quote.id()));
        if (selected) {
            button.addClass("simukraft_large_button");
        }
        button.setOnClick(event -> selectCompany(quote.id()));
        button.layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.rowHeight());
            layout.paddingLeft(4);
            layout.paddingRight(4);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.justifyContent(AlignContent.CENTER);
            layout.flexShrink(0);
        });
        button.addChild(label(Component.literal(quote.displayName()), Horizontal.LEFT, 0xFFFFFFFF, 10));
        Label price = label(companyPriceText(quote), Horizontal.LEFT, quote.change() >= 0 ? UP : DOWN, 10);
        price.setId(companyPriceId(quote.id()));
        button.addChild(price);
        return button;
    }

    private static UIElement holding(ExchangeQuote selected, LayoutMetrics metrics) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.infoLineHeight());
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.flexShrink(0);
        });
        Label shares = label(sharesText(selected), Horizontal.LEFT, 0xFFFFC14A, metrics.infoLineHeight());
        shares.setId(SHARES_ID);
        shares.layout(layout -> {
            layout.flex(1);
            layout.height(metrics.infoLineHeight());
        });
        Label holdingValue = label(holdingValueText(selected), Horizontal.CENTER, 0xFFFFC14A, metrics.infoLineHeight());
        holdingValue.setId(HOLDING_VALUE_ID);
        holdingValue.layout(layout -> {
            layout.flex(1);
            layout.height(metrics.infoLineHeight());
        });
        Label todayChange = label(todayChangeText(selected), Horizontal.RIGHT, changeColor(selected), metrics.infoLineHeight());
        todayChange.setId(TODAY_CHANGE_ID);
        todayChange.layout(layout -> {
            layout.flex(1);
            layout.height(metrics.infoLineHeight());
        });
        row.addChild(shares);
        row.addChild(holdingValue);
        row.addChild(todayChange);
        return row;
    }

    private static UIElement actions(ExchangeControlBoxOpenResponsePacket packet, ExchangeQuote selected, LayoutMetrics metrics) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.flexWrap(FlexWrap.WRAP);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(metrics.gap());
            layout.flexShrink(0);
        });
        boolean canTrade = packet.hasBuilding() && packet.hasBroker() && packet.marketOpen() && selected != null;
        row.addChild(chromeButton(Component.translatable("gui.simukraft.exchange.buy"),
                () -> trade(true), canTrade, metrics.actionWidth(), metrics.actionHeight()));
        row.addChild(chromeButton(Component.translatable("gui.simukraft.exchange.sell"),
                () -> trade(false), canTrade, metrics.actionWidth(), metrics.actionHeight()));
        row.addChild(chromeButton(Component.translatable("gui.simukraft.exchange.hire"),
                () -> NpcHireScreen.request(packet.boxPos(), ExchangeControlBoxService.HIRE_SOURCE_TYPE,
                        ExchangeControlBoxService.HIRE_ROLE),
                packet.hasBuilding() && !packet.hasBroker(), metrics.actionWidth(), metrics.actionHeight()));
        row.addChild(chromeButton(Component.translatable("gui.simukraft.exchange.fire"),
                () -> fire(packet), packet.hasBroker(), metrics.actionWidth(), metrics.actionHeight()));
        return row;
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

    /** selectCompany: 只切换当前股票，不拆掉整棵界面。 */
    private static void selectCompany(String id) {
        if (id == null || id.equals(selectedId) || currentPacket == null || !contains(currentPacket, id)) {
            return;
        }
        selectedId = id;
        ModularUI ui = currentUi();
        if (ui == null || ui.getElementById(ROOT_ID) == null) {
            return;
        }
        applySnapshot(ui, currentPacket);
    }

    /** applySnapshot: 把最新快照写到现有节点，全屏 K 线一起刷新。 */
    private static void applySnapshot(ModularUI ui, ExchangeControlBoxOpenResponsePacket packet) {
        ExchangeQuote selected = selectedQuote(packet, selectedId);
        applySelection(ui, selected);
        writeLabel(ui, DAY_LINE_ID, dayLineText(packet), 0xFFF5F5A0);
        writeLabel(ui, FUNDS_LINE_ID, fundsLineText(packet), UP);
        writeLabel(ui, STATUS_LINE_ID, Component.translatable(packet.statusKey()), SimuKraftUiTheme.TEXT_MUTED_COLOR);
        for (ExchangeQuote quote : packet.quotes()) {
            writeLabel(ui, companyPriceId(quote.id()), companyPriceText(quote), quote.change() >= 0 ? UP : DOWN);
        }
        if (ui.getElementById(ExchangeChartElement.FULLSCREEN_CHART_ID) instanceof ExchangeChartElement fullChart) {
            fullChart.setQuote(selected);
        }
    }

    /** applySelection: 把行情图、持仓文案和列表高亮写到现有节点。 */
    private static void applySelection(ModularUI ui, ExchangeQuote selected) {
        if (ui.getElementById(CANDLE_ID) instanceof ExchangeChartElement candle) {
            candle.setQuote(selected);
        }
        if (ui.getElementById(VOLUME_ID) instanceof ExchangeChartElement volume) {
            volume.setQuote(selected);
        }
        writeLabel(ui, PRICE_LINE_ID, priceLineText(selected), changeColor(selected));
        writeLabel(ui, SHARES_ID, sharesText(selected), 0xFFFFC14A);
        writeLabel(ui, HOLDING_VALUE_ID, holdingValueText(selected), 0xFFFFC14A);
        writeLabel(ui, TODAY_CHANGE_ID, todayChangeText(selected), changeColor(selected));
        for (ExchangeQuote quote : currentPacket.quotes()) {
            UIElement row = ui.getElementById(companyRowId(quote.id()));
            if (row == null) {
                continue;
            }
            if (quote.id().equals(selectedId)) {
                row.addClass("simukraft_large_button");
            } else {
                row.removeClass("simukraft_large_button");
            }
        }
    }

    private static void trade(boolean buy) {
        if (currentPacket == null) {
            return;
        }
        ExchangeQuote selected = selectedQuote(currentPacket, selectedId);
        if (selected == null) {
            return;
        }
        PacketDistributor.sendToServer(new ExchangeControlBoxActionPacket(currentPacket.boxPos(), buy, selected.id(), 1));
    }

    private static void fire(ExchangeControlBoxOpenResponsePacket packet) {
        if (packet.brokerId() != null) {
            PacketDistributor.sendToServer(new NpcHireFirePacket(packet.boxPos(), ExchangeControlBoxService.HIRE_SOURCE_TYPE,
                    ExchangeControlBoxService.HIRE_ROLE, packet.brokerId()));
        }
        Minecraft.getInstance().setScreen(null);
    }

    private static String resolveSelected(ExchangeControlBoxOpenResponsePacket packet, String previous) {
        if (packet.selectedCompanyId() != null && !packet.selectedCompanyId().isBlank()
                && contains(packet, packet.selectedCompanyId())) {
            return packet.selectedCompanyId();
        }
        if (previous != null && contains(packet, previous)) {
            return previous;
        }
        return packet.quotes().isEmpty() ? "" : packet.quotes().getFirst().id();
    }

    private static boolean contains(ExchangeControlBoxOpenResponsePacket packet, String id) {
        for (ExchangeQuote quote : packet.quotes()) {
            if (quote.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static ExchangeQuote selectedQuote(ExchangeControlBoxOpenResponsePacket packet, String id) {
        for (ExchangeQuote quote : packet.quotes()) {
            if (quote.id().equals(id)) {
                return quote;
            }
        }
        return packet.quotes().isEmpty() ? null : packet.quotes().getFirst();
    }

    private static void markPolled(Minecraft minecraft) {
        if (minecraft.level == null) {
            return;
        }
        polledDay = ExchangeMarketClock.dayIndex(minecraft.level.getDayTime());
        polledHour = ExchangeMarketClock.hourIndex(minecraft.level.getDayTime());
    }

    private static ModularUI currentUi() {
        if (Minecraft.getInstance().screen instanceof ModularUIScreen screen) {
            return screen.getModularUI();
        }
        return null;
    }

    private static void writeLabel(ModularUI ui, String id, Component text, int color) {
        if (ui.getElementById(id) instanceof Label label) {
            label.setText(text);
            label.textStyle(style -> style.textColor(color));
        }
    }

    private static Component dayLineText(ExchangeControlBoxOpenResponsePacket packet) {
        return Component.translatable("gui.simukraft.exchange.day", packet.marketDay() + 1)
                .append(Component.literal("  "))
                .append(Component.translatable("gui.simukraft.exchange.regime_line",
                        Component.translatable(packet.regime().translationKey())));
    }

    private static Component fundsLineText(ExchangeControlBoxOpenResponsePacket packet) {
        return Component.translatable("gui.simukraft.exchange.virtual",
                CoinDenominations.formatYuan(packet.cityFunds()));
    }

    private static Component companyPriceText(ExchangeQuote quote) {
        return Component.literal(CoinDenominations.formatYuan(quote.price()) + "  " + quote.sharesHeld());
    }

    private static Component priceLineText(ExchangeQuote selected) {
        if (selected == null) {
            return Component.empty();
        }
        return Component.translatable("gui.simukraft.exchange.price_line", CoinDenominations.formatYuan(selected.price()))
                .append(Component.literal("  "))
                .append(Component.translatable("gui.simukraft.exchange.viewing", selected.displayName()));
    }

    private static Component sharesText(ExchangeQuote selected) {
        return Component.translatable("gui.simukraft.exchange.shares", selected == null ? 0 : selected.sharesHeld());
    }

    private static Component holdingValueText(ExchangeQuote selected) {
        return Component.translatable("gui.simukraft.exchange.holding_value",
                CoinDenominations.formatYuan(selected == null ? 0.0D : selected.holdingValue()));
    }

    private static Component todayChangeText(ExchangeQuote selected) {
        return Component.translatable("gui.simukraft.exchange.today_change",
                formatSigned(selected == null ? 0.0D : selected.change()));
    }

    private static int changeColor(ExchangeQuote selected) {
        return selected != null && selected.change() >= 0 ? UP : DOWN;
    }

    private static String companyRowId(String companyId) {
        return COMPANY_ROW_PREFIX + companyId;
    }

    private static String companyPriceId(String companyId) {
        return COMPANY_ROW_PREFIX + companyId + "-price";
    }

    private static String formatSigned(double value) {
        return (value >= 0 ? "+" : "") + CoinDenominations.formatYuan(value);
    }

    private static LayoutMetrics layoutMetrics(int screenWidth, int screenHeight) {
        int rootPadding = clamp(Math.round(Math.min(screenWidth, screenHeight) * 0.015F), 3, 8);
        int panelWidth = Math.max(1, screenWidth - rootPadding * 2);
        int panelHeight = Math.max(1, screenHeight - rootPadding * 2);
        int panelPadding = clamp(Math.round(Math.min(panelWidth, panelHeight) * 0.018F), 4, 8);
        int gap = clamp(Math.round(panelHeight * 0.012F), 2, 5);
        int titleBarHeight = clamp(Math.round(panelHeight * 0.08F), 18, 24);
        int infoLineHeight = clamp(Math.round(panelHeight * 0.048F), 10, 13);
        int chromeHeight = clamp(titleBarHeight - 2, 16, 22);
        int chromeWidth = clamp(Math.round(panelWidth * 0.12F), 44, 62);
        int actionHeight = clamp(Math.round(panelHeight * 0.075F), 18, 22);
        int actionWidth = clamp((panelWidth - panelPadding * 2 - gap * 3) / 4, 56, 90);
        int listWidth = clamp(Math.round(panelWidth * 0.28F), 90, 150);
        int volumeHeight = clamp(Math.round(panelHeight * 0.18F), 36, 70);
        int rowHeight = clamp(Math.round(panelHeight * 0.09F), 22, 30);
        return new LayoutMetrics(rootPadding, panelWidth, panelHeight, panelPadding, gap, titleBarHeight,
                infoLineHeight, chromeWidth, chromeHeight, actionWidth, actionHeight, listWidth, volumeHeight, rowHeight);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record LayoutMetrics(int rootPadding, int panelWidth, int panelHeight, int panelPadding, int gap,
                                 int titleBarHeight, int infoLineHeight, int chromeWidth, int chromeHeight,
                                 int actionWidth, int actionHeight, int listWidth, int volumeHeight, int rowHeight) {
    }
}
