package common.cn.kafei.simukraft.exchange;

import java.util.List;

/** ExchangeQuote: 一只股票的行情与持仓。 */
public record ExchangeQuote(String id,
                            String displayName,
                            double price,
                            double previousClose,
                            int volume,
                            int sharesHeld,
                            double costBasis,
                            List<ExchangeCandle> candles) {
    public ExchangeQuote {
        candles = candles != null ? List.copyOf(candles) : List.of();
    }

    public double change() {
        return previousClose <= 0.0D ? 0.0D : price - previousClose;
    }

    public double changePercent() {
        return previousClose <= 0.0D ? 0.0D : change() / previousClose;
    }

    public double holdingValue() {
        return price * sharesHeld;
    }

    /** candlesInLastDays: 从最近一根起向前取若干个交易日的小时柱。 */
    public List<ExchangeCandle> candlesInLastDays(int days) {
        if (candles.isEmpty() || days <= 0) {
            return List.of();
        }
        long minDay = candles.get(candles.size() - 1).marketDay() - (long) days + 1L;
        int start = 0;
        for (int i = 0; i < candles.size(); i++) {
            if (candles.get(i).marketDay() >= minDay) {
                start = i;
                break;
            }
        }
        return candles.subList(start, candles.size());
    }
}
