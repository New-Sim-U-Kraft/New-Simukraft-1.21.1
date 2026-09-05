package common.cn.kafei.simukraft.exchange;

import common.cn.kafei.simukraft.economy.EconomyService;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** ExchangePriceMath: 按当日市况生成下一小时价格。 */
public final class ExchangePriceMath {
    private ExchangePriceMath() {
    }

    /** nextPrice: 在市况漂移上叠加公司波动。 */
    public static double nextPrice(double current, ExchangeCompany company, ExchangeMarketRegime regime) {
        double base = current > 0.0D ? current : company.basePrice();
        double roll = ThreadLocalRandom.current().nextDouble() * 2.0D - 1.0D;
        double change = regime.drift() + roll * (regime.noise() + company.volatility());
        if (regime == ExchangeMarketRegime.BULL && change < -0.008D) {
            change = -0.008D;
        }
        if (regime == ExchangeMarketRegime.BEAR && change > 0.008D) {
            change = 0.008D;
        }
        return EconomyService.normalizeAmount(Math.max(0.01D, base * (1.0D + change)));
    }

    /** volume: 按涨跌幅度估成交量。 */
    public static int volume(double previous, double next) {
        double move = previous <= 0.0D ? 0.0D : Math.abs(next - previous) / previous;
        int base = 8 + ThreadLocalRandom.current().nextInt(24);
        return Math.max(1, (int) Math.round(base + move * 180.0D));
    }

    /** simpleMovingAverage: 收盘价简单均线，样本不足返回 NaN。 */
    public static double simpleMovingAverage(List<ExchangeCandle> candles, int index, int period) {
        if (candles == null || period <= 0 || index < period - 1 || index >= candles.size()) {
            return Double.NaN;
        }
        double sum = 0.0D;
        for (int i = index - period + 1; i <= index; i++) {
            sum += candles.get(i).close();
        }
        return sum / period;
    }
}
