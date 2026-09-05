package common.cn.kafei.simukraft.economy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CoinDenominationsTest {
    @Test
    void cashValue_usesConfiguredDenominations() {
        assertEquals(1.00D, CoinDenominations.cashValue(1, 0, 0), 0.0001D);
        assertEquals(0.10D, CoinDenominations.cashValue(0, 1, 0), 0.0001D);
        assertEquals(0.01D, CoinDenominations.cashValue(0, 0, 1), 0.0001D);
        assertEquals(1.11D, CoinDenominations.cashValue(1, 1, 1), 0.0001D);
    }

    @Test
    void breakdown_isGreedyGoldThenSilverThenCopper() {
        assertArrayEquals(new int[] {12, 3, 4}, CoinDenominations.breakdown(12.34D));
        assertArrayEquals(new int[] {0, 0, 0}, CoinDenominations.breakdown(0.0D));
    }

    @Test
    void formatCount_usesKForThousands() {
        assertEquals("10K", CoinDenominations.formatCount(10000));
        assertEquals("1K", CoinDenominations.formatCount(1000));
        assertEquals("99", CoinDenominations.formatCount(99));
    }

    @Test
    void formatYuan_usesKForThousands() {
        assertEquals("1.25K", CoinDenominations.formatYuan(1250.0D));
        assertEquals("12.40", CoinDenominations.formatYuan(12.4D));
    }
}
