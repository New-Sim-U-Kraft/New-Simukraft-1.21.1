package common.cn.kafei.simukraft.exchange;

/** ExchangeMarketClock: 股市开闭市。开市 2000 tick（早 8 点），闭市 14000 tick（晚 8 点）。 */
public final class ExchangeMarketClock {
    public static final int OPEN_TICK = 2000;
    public static final int CLOSE_TICK = 14000;
    public static final int TICKS_PER_DAY = 24_000;
    public static final int TICKS_PER_HOUR = 1000;
    public static final int HOURS_PER_SESSION = (CLOSE_TICK - OPEN_TICK) / TICKS_PER_HOUR;

    private ExchangeMarketClock() {
    }

    /** timeOfDay: 当天 0~23999。 */
    public static int timeOfDay(long dayTime) {
        long wrapped = Math.floorMod(dayTime, TICKS_PER_DAY);
        return (int) wrapped;
    }

    /** dayIndex: 游戏日序号。 */
    public static long dayIndex(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY);
    }

    /** isOpen: 是否处于开市时段。 */
    public static boolean isOpen(long dayTime) {
        int time = timeOfDay(dayTime);
        return time >= OPEN_TICK && time < CLOSE_TICK;
    }

    /** hourIndex: 开市后的第几个游戏小时，闭市返回 -1。 */
    public static int hourIndex(long dayTime) {
        if (!isOpen(dayTime)) {
            return -1;
        }
        return (timeOfDay(dayTime) - OPEN_TICK) / TICKS_PER_HOUR;
    }

    /** maxValidHour: 当前时钟允许保留的最后一根小时柱；开市前为 -1（当天还没有柱）。 */
    public static int maxValidHour(long dayTime) {
        int time = timeOfDay(dayTime);
        if (time < OPEN_TICK) {
            return -1;
        }
        if (time >= CLOSE_TICK) {
            return HOURS_PER_SESSION - 1;
        }
        return (time - OPEN_TICK) / TICKS_PER_HOUR;
    }

    /** clockHour: 开市第 N 小时对应的钟点，hourIndex 0 为 8 点。 */
    public static int clockHour(int hourIndex) {
        return Math.floorMod(8 + Math.max(0, hourIndex), 24);
    }

    /** clockLabel: 行情时间轴标签，如 08:00。 */
    public static String clockLabel(int hourIndex) {
        return String.format(java.util.Locale.ROOT, "%02d:00", clockHour(hourIndex));
    }
}
