package common.cn.kafei.simukraft.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExchangeMarketClockTest {
    @Test
    void isOpen_betweenEightAndTwenty() {
        assertFalse(ExchangeMarketClock.isOpen(1999L));
        assertTrue(ExchangeMarketClock.isOpen(2000L));
        assertTrue(ExchangeMarketClock.isOpen(6000L));
        assertFalse(ExchangeMarketClock.isOpen(14000L));
        assertFalse(ExchangeMarketClock.isOpen(18000L));
    }

    @Test
    void hourIndex_countsFromOpen() {
        assertEquals(-1, ExchangeMarketClock.hourIndex(1000L));
        assertEquals(0, ExchangeMarketClock.hourIndex(2000L));
        assertEquals(4, ExchangeMarketClock.hourIndex(6000L));
        assertEquals(11, ExchangeMarketClock.hourIndex(13000L));
    }

    @Test
    void dayIndex_usesMinecraftDayLength() {
        assertEquals(0L, ExchangeMarketClock.dayIndex(23999L));
        assertEquals(1L, ExchangeMarketClock.dayIndex(24000L));
    }

    @Test
    void clockLabel_mapsOpenHourIndex() {
        assertEquals("08:00", ExchangeMarketClock.clockLabel(0));
        assertEquals("12:00", ExchangeMarketClock.clockLabel(4));
        assertEquals("19:00", ExchangeMarketClock.clockLabel(11));
    }

    @Test
    void hoursPerSession_coversOpenToClose() {
        assertEquals(12, ExchangeMarketClock.HOURS_PER_SESSION);
    }

    @Test
    void maxValidHour_keepsOnlyReachedHours() {
        assertEquals(-1, ExchangeMarketClock.maxValidHour(1999L));
        assertEquals(0, ExchangeMarketClock.maxValidHour(2000L));
        assertEquals(2, ExchangeMarketClock.maxValidHour(4698L));
        assertEquals(11, ExchangeMarketClock.maxValidHour(13000L));
        assertEquals(11, ExchangeMarketClock.maxValidHour(14000L));
        assertEquals(11, ExchangeMarketClock.maxValidHour(18000L));
        assertEquals(2, ExchangeMarketClock.maxValidHour(24000L + 4698L));
    }
}
