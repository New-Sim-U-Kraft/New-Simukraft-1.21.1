package common.cn.kafei.simukraft.exchange;

/** ExchangeCandle: 一根小时 K 线，带交易日便于坐标轴和悬浮框。 */
public record ExchangeCandle(long marketDay, int hourIndex, double open, double high, double low, double close, int volume) {
    public ExchangeCandle {
        high = Math.max(high, Math.max(open, close));
        low = Math.min(low, Math.min(open, close));
        volume = Math.max(0, volume);
    }
}
