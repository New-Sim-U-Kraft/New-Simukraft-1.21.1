package client.cn.kafei.simukraft.client.exchange;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import common.cn.kafei.simukraft.exchange.ExchangeQuote;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/** ExchangeChartElement: Ore 面板内的 K 线或成交量画布，双击用 GraphView 全屏。 */
@OnlyIn(Dist.CLIENT)
public final class ExchangeChartElement extends UIElement {
    private static final int CHART_BG = 0xFF2D2D33;
    private static final int PAD = 4;
    static final String FULLSCREEN_ID = "simukraft-exchange-chart-fullscreen";
    static final String FULLSCREEN_CHART_ID = "simukraft-exchange-fullscreen-chart";
    private static final int TITLE_HEIGHT = 22;
    private static final int BUTTON_BAR_HEIGHT = 28;
    private static final int MAIN_DAYS = 1;
    private static final int FULLSCREEN_DEFAULT_DAYS = 3;
    private static final List<Integer> RANGE_DAYS = List.of(1, 3, 7, 14, 30);
    private static int fullscreenDays = FULLSCREEN_DEFAULT_DAYS;

    public enum Mode {
        CANDLE,
        VOLUME
    }

    private final Mode mode;
    private final boolean fullscreen;
    private final ScrollerView scroller;
    private final PlotCanvas canvas;
    private int visibleDays;
    private boolean pendingLatest = true;
    private boolean followLatest = true;
    private boolean snapping;
    private int lastCanvasWidth = -1;
    @Nullable
    private Label prevLegend;
    @Nullable
    private ExchangeQuote quote;

    public ExchangeChartElement(Mode mode, @Nullable ExchangeQuote quote) {
        this(mode, quote, false);
    }

    public ExchangeChartElement(Mode mode, @Nullable ExchangeQuote quote, boolean fullscreen) {
        this.mode = mode;
        this.quote = quote;
        this.fullscreen = fullscreen;
        this.visibleDays = fullscreen ? clampRange(fullscreenDays) : MAIN_DAYS;
        layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
        if (!fullscreen) {
            addClass("simukraft_grid_panel");
        }
        scroller = new ScrollerView();
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.HORIZONTAL)
                .horizontalScrollDisplay(ScrollDisplay.AUTO)
                .verticalScrollDisplay(ScrollDisplay.NEVER));
        scroller.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
            layout.minHeight(28);
        });
        scroller.viewContainer(container -> container.layout(layout -> layout.heightPercent(100)));
        canvas = new PlotCanvas();
        scroller.addScrollViewChild(canvas);
        if (mode == Mode.CANDLE) {
            addChild(legendBar());
        }
        addChild(scroller);
        scroller.viewPort.addEventListener(UIEvents.LAYOUT_CHANGED, event -> refreshCanvasWidth());
        canvas.addEventListener(UIEvents.LAYOUT_CHANGED, event -> jumpToLatest());
        scroller.viewContainer.addEventListener(UIEvents.LAYOUT_CHANGED, event -> jumpToLatest());
        scroller.horizontalScroller.setOnValueChanged(value -> {
            if (snapping) {
                return;
            }
            pendingLatest = false;
            followLatest = scroller.horizontalScroller.getNormalizedValue() >= 0.99F;
        });
        refreshCanvasWidth();
    }

    /** setQuote: 刷新行情；换股才跳到最新，轮询刷新不得抢走滚动条。 */
    public void setQuote(@Nullable ExchangeQuote quote) {
        String previousId = this.quote == null ? null : this.quote.id();
        this.quote = quote;
        boolean companyChanged = quote != null && !quote.id().equals(previousId);
        if (companyChanged || previousId == null) {
            pendingLatest = true;
            followLatest = true;
        }
        refreshLegend();
        refreshCanvasWidth();
    }

    /** setVisibleDays: 全屏下拉切换显示天数。 */
    private void setVisibleDays(int days) {
        int clamped = clampRange(days);
        if (fullscreen) {
            fullscreenDays = clamped;
        }
        if (visibleDays == clamped) {
            return;
        }
        visibleDays = clamped;
        pendingLatest = true;
        followLatest = true;
        lastCanvasWidth = -1;
        refreshCanvasWidth();
    }

    @Override
    public void screenTick() {
        super.screenTick();
        jumpToLatest();
    }

    /** refreshCanvasWidth: 柱宽保底 8px，超出视口的部分交给横向滚动。 */
    private void refreshCanvasWidth() {
        int count = quote == null ? 0 : quote.candlesInLastDays(visibleDays).size();
        int minWidth = PAD * 2 + ExchangeChartPlot.AXIS_RIGHT + Math.max(1, count) * ExchangeChartPlot.MIN_SLOT;
        int portWidth = Math.round(scroller.viewPort.getContentWidth());
        int width = Math.max(minWidth, Math.max(1, portWidth));
        if (width == lastCanvasWidth) {
            return;
        }
        boolean grew = lastCanvasWidth > 0 && width > lastCanvasWidth;
        lastCanvasWidth = width;
        canvas.layout(layout -> {
            layout.heightPercent(100);
            layout.flexShrink(0);
            layout.width(width);
        });
        if (grew && followLatest) {
            pendingLatest = true;
        }
    }

    /** jumpToLatest: 仅在首次打开、换股、改天数，或用户本来就停在最右侧时滚到最新。 */
    private void jumpToLatest() {
        if (!pendingLatest) {
            return;
        }
        refreshCanvasWidth();
        float port = scroller.viewPort.getContentWidth();
        if (port <= 1.0F) {
            return;
        }
        float content = Math.max(scroller.getContainerWidth(), canvas.getSizeWidth());
        if (content <= port + 1.0F) {
            pendingLatest = false;
            return;
        }
        snapping = true;
        scroller.horizontalScroller.setNormalizedValue(1.0F);
        snapping = false;
        followLatest = true;
        if (scroller.horizontalScroller.getNormalizedValue() >= 0.99F) {
            pendingLatest = false;
        }
    }

    /** legendBar: MA / 昨收放在画布外，不随横向滚动跑掉。 */
    private UIElement legendBar() {
        UIElement bar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(12);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.paddingLeft(6);
            layout.gapAll(10);
            layout.flexShrink(0);
        });
        bar.addChild(legendLabel("gui.simukraft.exchange.legend.ma5", ExchangeChartPlot.MA5));
        bar.addChild(legendLabel("gui.simukraft.exchange.legend.ma10", ExchangeChartPlot.MA10));
        prevLegend = legendLabel("gui.simukraft.exchange.legend.prev", ExchangeChartPlot.PREV);
        bar.addChild(prevLegend);
        refreshLegend();
        return bar;
    }

    private static Label legendLabel(String key, int color) {
        Label label = new Label();
        label.setText(Component.translatable(key));
        label.layout(layout -> {
            layout.height(12);
            layout.flexShrink(0);
        });
        label.textStyle(style -> style.textColor(color).textShadow(false).textWrap(TextWrap.HIDE)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER));
        return label;
    }

    private void refreshLegend() {
        if (prevLegend != null) {
            prevLegend.setDisplay(quote != null && quote.previousClose() > 0.0D);
        }
    }

    /** openFullscreen: 按当前 GUI 缩放铺满整个屏幕，尺寸与截图一致。 */
    private void openFullscreen() {
        ModularUI modularUI = getModularUI();
        if (modularUI == null || modularUI.getElementById(FULLSCREEN_ID) != null) {
            return;
        }
        var window = Minecraft.getInstance().getWindow();
        int screenWidth = Math.max(1, window.getGuiScaledWidth());
        int screenHeight = Math.max(1, window.getGuiScaledHeight());

        UIElement overlay = new UIElement().setId(FULLSCREEN_ID);
        overlay.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(screenWidth);
            layout.height(screenHeight);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
        overlay.style(style -> style.zIndex(200)
                .backgroundTexture(new ColorRectTexture(CHART_BG)));
        overlay.addEventListener(UIEvents.KEY_DOWN, event -> {
            if (event.keyCode == GLFW.GLFW_KEY_ESCAPE) {
                removeOverlay(overlay);
                event.stopPropagation();
            }
        });

        GraphView graphView = new GraphView();
        graphView.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
            layout.minHeight(80);
        });
        graphView.style(style -> style.backgroundTexture(new ColorRectTexture(CHART_BG)));
        graphView.graphViewStyle(style -> style.allowPan(false).allowZoom(false)
                .gridTexture(IGuiTexture.EMPTY));
        ExchangeChartElement fullChart = new ExchangeChartElement(mode, quote, true);
        fullChart.setId(FULLSCREEN_CHART_ID);

        UIElement titleBar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(TITLE_HEIGHT);
            layout.paddingLeft(6);
            layout.paddingRight(6);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.flexShrink(0);
        });
        titleBar.style(style -> style.zIndex(250));
        titleBar.addClass("__dialog_title__");
        Label title = new Label();
        title.setText(Component.translatable(titleKey()));
        title.layout(layout -> {
            layout.flex(1);
            layout.height(TITLE_HEIGHT);
            layout.minWidth(40);
        });
        title.textStyle(style -> style.textColor(0xFF222222).textShadow(false).textWrap(TextWrap.HIDE)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER));
        titleBar.addChild(title);
        titleBar.addChild(rangeSelector(fullChart));
        fullChart.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.widthPercent(100);
            layout.heightPercent(100);
        });
        graphView.addChild(fullChart);
        graphView.addEventListener(UIEvents.DOUBLE_CLICK, event -> {
            removeOverlay(overlay);
            event.stopPropagation();
        });

        UIElement buttonBar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(BUTTON_BAR_HEIGHT);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.flexShrink(0);
        });
        buttonBar.addClass("__dialog_button-container__");
        Button close = new Button();
        close.setText(Component.translatable("gui.button.done"));
        close.setOnClick(event -> removeOverlay(overlay));
        close.layout(layout -> {
            layout.width(50);
            layout.height(20);
        });
        buttonBar.addChild(close);

        overlay.addChild(titleBar);
        overlay.addChild(graphView);
        overlay.addChild(buttonBar);
        modularUI.ui.getRootElement().addChild(overlay);
        modularUI.requestFocus(overlay);
    }

    /** closeFullscreen: 关掉铺满屏幕的 K 线层。 */
    private void closeFullscreen() {
        UIElement node = this;
        while (node != null) {
            if (FULLSCREEN_ID.equals(node.getId())) {
                removeOverlay(node);
                return;
            }
            node = node.getParent();
        }
    }

    private static void removeOverlay(UIElement overlay) {
        UIElement parent = overlay.getParent();
        if (parent != null) {
            parent.removeChild(overlay);
        }
    }

    private String titleKey() {
        return mode == Mode.CANDLE ? "gui.simukraft.exchange.kline" : "gui.simukraft.exchange.volume";
    }

    /** rangeSelector: 全屏标题栏右侧的天数下拉。 */
    private static Selector<Integer> rangeSelector(ExchangeChartElement chart) {
        Selector<Integer> selector = new Selector<>();
        selector.setCandidates(RANGE_DAYS);
        selector.setCandidateUIProvider(ExchangeChartElement::rangeOptionLabel);
        selector.setSelected(clampRange(fullscreenDays), false);
        selector.setOnValueChanged(days -> {
            if (days != null) {
                chart.setVisibleDays(days);
            }
        });
        selector.selectorStyle(style -> style.maxItemCount(RANGE_DAYS.size())
                .scrollerViewHeight(RANGE_DAYS.size() * 16 + 8)
                .closeAfterSelect(true));
        selector.layout(layout -> {
            layout.width(80);
            layout.height(18);
            layout.flexShrink(0);
        });
        selector.style(style -> style.zIndex(260));
        // 下拉挂在 UI 根上，默认 zIndex=1，会被全屏层（200）挡住，只剩残缺的一层。
        selector.dialog.style(style -> style.zIndex(400));
        return selector;
    }

    /** rangeOptionLabel: 下拉每一行固定高度，避免 Ore 主题里行高塌成一条。 */
    private static UIElement rangeOptionLabel(Integer days) {
        Label label = new Label();
        label.setText(days == null ? Component.empty() : Component.translatable("gui.simukraft.exchange.range_days", days));
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(16);
        });
        label.textStyle(style -> style.textShadow(false).textWrap(TextWrap.HIDE)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static int clampRange(int days) {
        return RANGE_DAYS.contains(days) ? days : FULLSCREEN_DEFAULT_DAYS;
    }

    /** PlotCanvas: 实际绘制 K 线/成交量，宽度按 8px 柱距撑开。 */
    private final class PlotCanvas extends UIElement {
        private PlotCanvas() {
            addEventListener(UIEvents.DOUBLE_CLICK, event -> {
                if (fullscreen) {
                    closeFullscreen();
                } else {
                    openFullscreen();
                }
                event.stopPropagation();
            });
            addEventListener(UIEvents.HOVER_TOOLTIPS, this::onHoverTooltips);
        }

        @Override
        public void drawBackgroundAdditional(@Nonnull GUIContext guiContext) {
            int x = Math.round(getPositionX());
            int y = Math.round(getPositionY());
            int width = Math.round(getSizeWidth());
            int height = Math.round(getSizeHeight());
            if (width <= PAD * 2 || height <= PAD * 2) {
                return;
            }
            int innerX = x + PAD;
            int innerY = y + PAD;
            int innerW = width - PAD * 2;
            int innerH = height - PAD * 2;
            guiContext.graphics.fill(innerX, innerY, innerX + innerW, innerY + innerH, CHART_BG);
            int clipLeft = Math.round(scroller.viewPort.getPositionX());
            int clipRight = clipLeft + Math.max(1, Math.round(scroller.viewPort.getContentWidth()));
            ExchangeChartPlot.Layout plot = ExchangeChartPlot.layout(quote, innerX, innerY, innerW, innerH, mode, visibleDays);
            ExchangeChartPlot.draw(guiContext, plot, quote, guiContext.mouseX, guiContext.mouseY, clipLeft, clipRight);
        }

        /** onHoverTooltips: 鼠标所在 K 线给出开高低收、涨跌、振幅、均线。 */
        private void onHoverTooltips(UIEvent event) {
            int innerX = Math.round(getPositionX()) + PAD;
            int innerY = Math.round(getPositionY()) + PAD;
            int innerW = Math.round(getSizeWidth()) - PAD * 2;
            int innerH = Math.round(getSizeHeight()) - PAD * 2;
            ExchangeChartPlot.Layout layout = ExchangeChartPlot.layout(quote, innerX, innerY, innerW, innerH, mode, visibleDays);
            if (!layout.contains(event.x, event.y)) {
                return;
            }
            List<Component> lines = ExchangeChartPlot.tooltip(quote, layout, layout.indexAt(event.x));
            if (lines.isEmpty()) {
                return;
            }
            HoverTooltips tips = event.hoverTooltips == null ? HoverTooltips.empty() : event.hoverTooltips;
            event.hoverTooltips = tips.append(lines.toArray(Component[]::new));
        }
    }
}
