package common.cn.kafei.simukraft.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExchangePriceMathTest {
    @Test
    void simpleMovingAverage_needsFullWindow() {
        List<ExchangeCandle> candles = List.of(
                candle(1.0D), candle(2.0D), candle(3.0D), candle(4.0D), candle(5.0D));
        assertTrue(Double.isNaN(ExchangePriceMath.simpleMovingAverage(candles, 3, 5)));
        assertEquals(3.0D, ExchangePriceMath.simpleMovingAverage(candles, 4, 5), 0.0001D);
        assertEquals(4.0D, ExchangePriceMath.simpleMovingAverage(candles, 4, 3), 0.0001D);
    }

    private static ExchangeCandle candle(double close) {
        return new ExchangeCandle(0L, 0, close, close, close, close, 1);
    }
}
