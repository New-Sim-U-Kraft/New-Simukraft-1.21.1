package client.cn.kafei.simukraft.client.exchange;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import common.cn.kafei.simukraft.economy.CoinDenominations;
import common.cn.kafei.simukraft.exchange.ExchangeCandle;
import common.cn.kafei.simukraft.exchange.ExchangeMarketClock;
import common.cn.kafei.simukraft.exchange.ExchangePriceMath;
import common.cn.kafei.simukraft.exchange.ExchangeQuote;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** ExchangeChartPlot: 真实行情图的坐标、均线、十字光标和悬浮文案。 */
@OnlyIn(Dist.CLIENT)
final class ExchangeChartPlot {
    static final int UP = 0xFF55FF55;
    static final int DOWN = 0xFFFF5555;
    static final int MUTED = 0xFFAAAAAA;
    private static final int GRID = 0x33FFFFFF;
    static final int PREV = 0xFFFFC14A;
    static final int MA5 = 0xFFFFFF66;
    static final int MA10 = 0xFF55FFFF;
    private static final int CROSS = 0x88FFFFFF;
    private static final int HUD = 0xCC101018;
    static final int MIN_SLOT = 8;
    static final int AXIS_RIGHT = 38;
    private static final int AXIS_BOTTOM = 12;

    private ExchangeChartPlot() {
    }

    /** layout: 给绘图区和坐标轴留边，只铺最近若干交易日，均线仍用更早的历史。 */
    static Layout layout(@Nullable ExchangeQuote quote, int x, int y, int w, int h, ExchangeChartElement.Mode mode,
                         int days) {
        int plotX = x;
        int plotY = y;
        int plotW = Math.max(8, w - AXIS_RIGHT);
        int plotH = Math.max(8, h - AXIS_BOTTOM);
        List<ExchangeCandle> history = quote == null ? List.of() : quote.candles();
        List<ExchangeCandle> candles = quote == null ? List.of() : quote.candlesInLastDays(days);
        int historyStart = Math.max(0, history.size() - candles.size());
        double min = Double.MAX_VALUE;
        double max = 0.0D;
        if (mode == ExchangeChartElement.Mode.VOLUME) {
            min = 0.0D;
            max = 1.0D;
            for (ExchangeCandle candle : candles) {
                max = Math.max(max, candle.volume());
            }
        } else {
            for (ExchangeCandle candle : candles) {
                min = Math.min(min, candle.low());
                max = Math.max(max, candle.high());
            }
            if (quote != null && quote.previousClose() > 0.0D) {
                min = Math.min(min, quote.previousClose());
                max = Math.max(max, quote.previousClose());
            }
            if (min == Double.MAX_VALUE) {
                min = 0.0D;
                max = 1.0D;
            }
            double pad = Math.max(0.01D, (max - min) * 0.08D);
            min = Math.max(0.0D, min - pad);
            max += pad;
        }
        if (max - min < 0.01D) {
            max = min + 0.5D;
        }
        int slot = Math.max(MIN_SLOT, plotW / Math.max(1, candles.size()));
        return new Layout(x, y, w, h, plotX, plotY, plotW, plotH, slot, min, max, candles, history, historyStart, mode);
    }

    /** draw: 画网格、均线、K 线或成交量、坐标和悬浮十字。 */
    static void draw(GUIContext guiContext, Layout layout, @Nullable ExchangeQuote quote, int mouseX, int mouseY,
                     int clipLeft, int clipRight) {
        Font font = Minecraft.getInstance().font;
        if (layout.candles.isEmpty()) {
            guiContext.graphics.drawString(font, Component.translatable("gui.simukraft.exchange.no_candles"),
                    layout.plotX + 4, layout.plotY + Math.max(0, layout.plotH / 2 - 4), MUTED, false);
            return;
        }
        drawGrid(guiContext, layout);
        drawDaySeparators(guiContext, layout);
        if (layout.mode == ExchangeChartElement.Mode.CANDLE) {
            drawPreviousClose(guiContext, layout, quote);
            drawMovingAverage(guiContext, layout, 10, MA10);
            drawMovingAverage(guiContext, layout, 5, MA5);
            drawCandles(guiContext, layout, mouseX);
        } else {
            drawVolume(guiContext, layout, mouseX);
        }
        drawAxes(guiContext, font, layout, clipLeft, clipRight);
        if (layout.contains(mouseX, mouseY)) {
            int index = layout.indexAt(mouseX);
            drawCrosshair(guiContext, font, layout, index, mouseX, mouseY);
            if (layout.plotH >= 72) {
                drawHoverHud(guiContext, font, layout, quote, index, clipLeft, clipRight);
            }
        }
    }

    /** tooltip: 组装一根 K 线的完整悬浮信息。 */
    static List<Component> tooltip(@Nullable ExchangeQuote quote, Layout layout, int index) {
        if (quote == null || index < 0 || index >= layout.candles.size()) {
            return List.of();
        }
        ExchangeCandle candle = layout.candles.get(index);
        double prev = previousClose(layout, quote, index);
        double change = candle.close() - prev;
        double changePct = prev <= 0.0D ? 0.0D : change / prev;
        double amplitude = candle.high() - candle.low();
        double amplitudePct = prev <= 0.0D ? 0.0D : amplitude / prev;
        int color = change >= 0 ? UP : DOWN;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(quote.displayName()).withStyle(style -> style.withColor(0xFFFFFF)));
        lines.add(Component.translatable("gui.simukraft.exchange.candle.time", candle.marketDay() + 1,
                ExchangeMarketClock.clockLabel(candle.hourIndex())));
        lines.add(Component.translatable("gui.simukraft.exchange.candle.open", CoinDenominations.formatYuan(candle.open())));
        lines.add(Component.translatable("gui.simukraft.exchange.candle.high", CoinDenominations.formatYuan(candle.high())));
        lines.add(Component.translatable("gui.simukraft.exchange.candle.low", CoinDenominations.formatYuan(candle.low())));
        lines.add(Component.translatable("gui.simukraft.exchange.candle.close", CoinDenominations.formatYuan(candle.close()))
                .withStyle(style -> style.withColor(color)));
        lines.add(Component.translatable("gui.simukraft.exchange.candle.change",
                signedYuan(change), percent(changePct)).withStyle(style -> style.withColor(color)));
        lines.add(Component.translatable("gui.simukraft.exchange.candle.amplitude",
                CoinDenominations.formatYuan(amplitude), percent(amplitudePct)));
        lines.add(Component.translatable("gui.simukraft.exchange.candle.volume", candle.volume()));
        lines.add(Component.translatable("gui.simukraft.exchange.candle.prev_close", CoinDenominations.formatYuan(prev)));
        appendAverage(lines, layout, index, 5, "gui.simukraft.exchange.candle.ma5");
        appendAverage(lines, layout, index, 10, "gui.simukraft.exchange.candle.ma10");
        return lines;
    }

    private static double movingAverage(Layout layout, int visibleIndex, int period) {
        return ExchangePriceMath.simpleMovingAverage(layout.history, layout.historyStart + visibleIndex, period);
    }

    private static void drawGrid(GUIContext guiContext, Layout layout) {
        for (int i = 0; i <= 4; i++) {
            int y = layout.plotY + Math.round(layout.plotH * i / 4.0F);
            guiContext.graphics.fill(layout.plotX, y, layout.plotX + layout.plotW, y + 1, GRID);
        }
    }

    /** drawDaySeparators: 换日处画一条竖线，区分前几日的 K 线。 */
    private static void drawDaySeparators(GUIContext guiContext, Layout layout) {
        long lastDay = Long.MIN_VALUE;
        for (int i = 0; i < layout.candles.size(); i++) {
            long day = layout.candles.get(i).marketDay();
            if (i > 0 && day != lastDay) {
                int x = layout.plotX + i * layout.slot;
                guiContext.graphics.fill(x, layout.plotY, x + 1, layout.plotY + layout.plotH, 0x55FFFFFF);
            }
            lastDay = day;
        }
    }

    private static void drawPreviousClose(GUIContext guiContext, Layout layout, @Nullable ExchangeQuote quote) {
        if (quote == null || quote.previousClose() <= 0.0D) {
            return;
        }
        int y = layout.yOf(quote.previousClose());
        if (y < layout.plotY || y > layout.plotY + layout.plotH) {
            return;
        }
        dashH(guiContext, layout.plotX, y, layout.plotW, PREV);
    }

    private static void drawMovingAverage(GUIContext guiContext, Layout layout, int period, int color) {
        int lastX = 0;
        int lastY = 0;
        boolean started = false;
        for (int i = 0; i < layout.candles.size(); i++) {
            double value = movingAverage(layout, i, period);
            if (Double.isNaN(value)) {
                continue;
            }
            int x = layout.centerX(i);
            int y = layout.yOf(value);
            if (started) {
                line(guiContext, lastX, lastY, x, y, color);
            }
            lastX = x;
            lastY = y;
            started = true;
        }
    }

    private static void drawCandles(GUIContext guiContext, Layout layout, int mouseX) {
        int hover = mouseX >= layout.plotX && mouseX < layout.plotX + layout.plotW ? layout.indexAt(mouseX) : -1;
        for (int i = 0; i < layout.candles.size(); i++) {
            ExchangeCandle candle = layout.candles.get(i);
            int cx = layout.centerX(i);
            int highY = layout.yOf(candle.high());
            int lowY = layout.yOf(candle.low());
            int openY = layout.yOf(candle.open());
            int closeY = layout.yOf(candle.close());
            int color = candle.close() >= candle.open() ? UP : DOWN;
            if (i == hover) {
                int left = layout.plotX + i * layout.slot;
                guiContext.graphics.fill(left, layout.plotY, left + layout.slot, layout.plotY + layout.plotH, 0x22FFFFFF);
            }
            guiContext.graphics.fill(cx, highY, cx + 1, Math.max(highY + 1, lowY), color);
            int bodyTop = Math.min(openY, closeY);
            int bodyBottom = Math.max(openY, closeY);
            int half = Math.max(1, Math.min(3, layout.slot / 3));
            guiContext.graphics.fill(cx - half, bodyTop, cx + half + 1, Math.max(bodyTop + 1, bodyBottom), color);
        }
    }

    private static void drawVolume(GUIContext guiContext, Layout layout, int mouseX) {
        int hover = mouseX >= layout.plotX && mouseX < layout.plotX + layout.plotW ? layout.indexAt(mouseX) : -1;
        for (int i = 0; i < layout.candles.size(); i++) {
            ExchangeCandle candle = layout.candles.get(i);
            int barH = Math.max(1, (int) Math.round(candle.volume() / layout.max * layout.plotH));
            int left = layout.plotX + i * layout.slot + 1;
            int right = Math.max(left + 1, layout.plotX + (i + 1) * layout.slot - 1);
            int color = candle.close() >= candle.open() ? UP : DOWN;
            if (i == hover) {
                color = 0xFFFFFFFF;
            }
            guiContext.graphics.fill(left, layout.plotY + layout.plotH - barH, right, layout.plotY + layout.plotH, color);
        }
    }

    private static void drawAxes(GUIContext guiContext, Font font, Layout layout, int clipLeft, int clipRight) {
        for (int i = 0; i <= 4; i++) {
            double value = layout.max - (layout.max - layout.min) * i / 4.0D;
            int y = layout.plotY + Math.round(layout.plotH * i / 4.0F);
            String label = layout.mode == ExchangeChartElement.Mode.VOLUME
                    ? Integer.toString((int) Math.round(value))
                    : CoinDenominations.formatYuan(value);
            guiContext.graphics.drawString(font, label, layout.plotX + layout.plotW + 2, Math.max(layout.plotY, y - 4), MUTED, false);
        }
        if (layout.candles.isEmpty()) {
            return;
        }
        int first = layout.indexAt(clipLeft);
        int last = layout.indexAt(clipRight - 1);
        if (first < 0) {
            first = 0;
        }
        if (last < 0) {
            last = layout.candles.size() - 1;
        }
        if (first > last) {
            int swap = first;
            first = last;
            last = swap;
        }
        boolean withDay = layout.candles.get(first).marketDay() != layout.candles.get(last).marketDay();
        drawTimeLabel(guiContext, font, layout, first, withDay);
        if (last - first > 1) {
            drawTimeLabel(guiContext, font, layout, first + (last - first) / 2, withDay);
        }
        drawTimeLabel(guiContext, font, layout, last, withDay);
    }

    private static void drawTimeLabel(GUIContext guiContext, Font font, Layout layout, int index, boolean withDay) {
        ExchangeCandle candle = layout.candles.get(index);
        String text = axisTime(candle, withDay);
        int width = font.width(text);
        int x = Math.min(layout.plotX + layout.plotW - width, Math.max(layout.plotX, layout.centerX(index) - width / 2));
        guiContext.graphics.drawString(font, text, x, layout.plotY + layout.plotH + 2, MUTED, false);
    }

    private static String axisTime(ExchangeCandle candle, boolean withDay) {
        String clock = ExchangeMarketClock.clockLabel(candle.hourIndex());
        return withDay ? (candle.marketDay() + 1) + "日" + clock : clock;
    }

    private static void drawCrosshair(GUIContext guiContext, Font font, Layout layout,
                                      int index, int mouseX, int mouseY) {
        guiContext.graphics.fill(mouseX, layout.plotY, mouseX + 1, layout.plotY + layout.plotH, CROSS);
        guiContext.graphics.fill(layout.plotX, mouseY, layout.plotX + layout.plotW, mouseY + 1, CROSS);
        if (index >= 0 && index < layout.candles.size()) {
            String time = ExchangeMarketClock.clockLabel(layout.candles.get(index).hourIndex());
            guiContext.graphics.fill(layout.centerX(index) - 14, layout.plotY + layout.plotH + 1,
                    layout.centerX(index) + 14, layout.plotY + layout.plotH + AXIS_BOTTOM, 0xAA000000);
            guiContext.graphics.drawString(font, time, layout.centerX(index) - 12, layout.plotY + layout.plotH + 2, 0xFFFFFFFF, false);
        }
        if (layout.mode == ExchangeChartElement.Mode.CANDLE) {
            double price = layout.priceOf(mouseY);
            String text = CoinDenominations.formatYuan(price);
            guiContext.graphics.fill(layout.plotX + layout.plotW, mouseY - 5,
                    layout.x + layout.w, mouseY + 6, 0xAA000000);
            guiContext.graphics.drawString(font, text, layout.plotX + layout.plotW + 2, mouseY - 4, 0xFFFFFFFF, false);
        }
    }

    private static void drawHoverHud(GUIContext guiContext, Font font, Layout layout, @Nullable ExchangeQuote quote,
                                     int index, int clipLeft, int clipRight) {
        if (quote == null || index < 0 || index >= layout.candles.size()) {
            return;
        }
        ExchangeCandle candle = layout.candles.get(index);
        double prev = previousClose(layout, quote, index);
        double change = candle.close() - prev;
        int color = change >= 0 ? UP : DOWN;
        List<Component> lines = List.of(
                Component.translatable("gui.simukraft.exchange.candle.time", candle.marketDay() + 1,
                        ExchangeMarketClock.clockLabel(candle.hourIndex())),
                Component.translatable("gui.simukraft.exchange.candle.close", CoinDenominations.formatYuan(candle.close()))
                        .withStyle(style -> style.withColor(color)),
                Component.translatable("gui.simukraft.exchange.candle.change",
                        signedYuan(change), percent(prev <= 0.0D ? 0.0D : change / prev))
                        .withStyle(style -> style.withColor(color)),
                Component.translatable("gui.simukraft.exchange.candle.volume", candle.volume())
        );
        int boxW = 112;
        int lineH = 9;
        int boxH = 4 + lines.size() * lineH;
        int boxX = Math.max(layout.plotX, clipLeft) + 4;
        if (boxX + boxW > clipRight - 2) {
            boxX = Math.max(clipLeft + 2, clipRight - boxW - 2);
        }
        int boxY = layout.plotY + 4;
        guiContext.graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, HUD);
        guiContext.graphics.renderOutline(boxX, boxY, boxW, boxH, 0x66FFFFFF);
        int textY = boxY + 2;
        for (Component line : lines) {
            guiContext.graphics.drawString(font, line, boxX + 3, textY, 0xFFE8E8E8, false);
            textY += lineH;
        }
    }

    private static void appendAverage(List<Component> lines, Layout layout, int index, int period, String key) {
        double value = movingAverage(layout, index, period);
        if (!Double.isNaN(value)) {
            lines.add(Component.translatable(key, CoinDenominations.formatYuan(value)));
        }
    }

    private static double previousClose(Layout layout, ExchangeQuote quote, int visibleIndex) {
        int historyIndex = layout.historyStart + visibleIndex;
        if (historyIndex > 0 && historyIndex <= layout.history.size()) {
            return layout.history.get(historyIndex - 1).close();
        }
        if (quote.previousClose() > 0.0D) {
            return quote.previousClose();
        }
        return layout.candles.get(visibleIndex).open();
    }

    private static void dashH(GUIContext guiContext, int x, int y, int width, int color) {
        for (int dx = 0; dx < width; dx += 4) {
            guiContext.graphics.fill(x + dx, y, x + Math.min(width, dx + 2), y + 1, color);
        }
    }

    private static void line(GUIContext guiContext, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps);
            int y = y0 + (y1 - y0) * i / Math.max(1, steps);
            guiContext.graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static String signedYuan(double value) {
        return (value >= 0 ? "+" : "") + CoinDenominations.formatYuan(value);
    }

    private static String percent(double ratio) {
        return String.format(Locale.ROOT, "%+.2f%%", ratio * 100.0D);
    }

    record Layout(int x, int y, int w, int h, int plotX, int plotY, int plotW, int plotH, int slot,
                  double min, double max, List<ExchangeCandle> candles, List<ExchangeCandle> history, int historyStart,
                  ExchangeChartElement.Mode mode) {
        /** indexAt: 鼠标 X 落到哪一根 K 线。 */
        int indexAt(float mouseX) {
            if (candles.isEmpty() || slot <= 0) {
                return -1;
            }
            if (mouseX < plotX || mouseX >= plotX + plotW) {
                return -1;
            }
            return Mth.clamp((int) ((mouseX - plotX) / slot), 0, candles.size() - 1);
        }

        boolean contains(float mouseX, float mouseY) {
            return mouseX >= plotX && mouseX < plotX + plotW && mouseY >= plotY && mouseY < plotY + plotH;
        }

        int centerX(int index) {
            return plotX + index * slot + slot / 2;
        }

        int yOf(double value) {
            return plotY + (int) ((max - value) / (max - min) * plotH);
        }

        double priceOf(int mouseY) {
            return max - (mouseY - plotY) * (max - min) / Math.max(1, plotH);
        }
    }
}
