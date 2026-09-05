package common.cn.kafei.simukraft.exchange;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExchangeQuoteTest {
    @Test
    void candlesInLastDays_keepsSuffixOfMarketDays() {
        List<ExchangeCandle> candles = List.of(
                candle(1, 0), candle(1, 11),
                candle(2, 0), candle(2, 11),
                candle(3, 0));
        ExchangeQuote quote = new ExchangeQuote("id", "name", 1.0D, 1.0D, 0, 0, 0.0D, candles);
        assertEquals(1, quote.candlesInLastDays(1).size());
        assertEquals(3, quote.candlesInLastDays(1).getFirst().marketDay());
        assertEquals(5, quote.candlesInLastDays(3).size());
        assertEquals(1L, quote.candlesInLastDays(3).getFirst().marketDay());
        assertEquals(5, quote.candlesInLastDays(30).size());
    }

    private static ExchangeCandle candle(long day, int hour) {
        return new ExchangeCandle(day, hour, 1.0D, 1.0D, 1.0D, 1.0D, 1);
    }
}
