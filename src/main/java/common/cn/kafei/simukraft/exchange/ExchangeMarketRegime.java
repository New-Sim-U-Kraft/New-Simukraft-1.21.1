package common.cn.kafei.simukraft.exchange;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** ExchangeMarketRegime: 当日市况牌。 */
public enum ExchangeMarketRegime {
    MIXED(0.0D, 0.035D),
    BULL(0.018D, 0.022D),
    BEAR(-0.018D, 0.022D);

    private final double drift;
    private final double noise;

    ExchangeMarketRegime(double drift, double noise) {
        this.drift = drift;
        this.noise = noise;
    }

    public double drift() {
        return drift;
    }

    public double noise() {
        return noise;
    }

    public String translationKey() {
        return "gui.simukraft.exchange.regime." + name().toLowerCase(Locale.ROOT);
    }

    /** roll: 涨跌 50%、涨涨 25%、跌跌 25%。 */
    public static ExchangeMarketRegime roll() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 50) {
            return MIXED;
        }
        return roll < 75 ? BULL : BEAR;
    }

    public static ExchangeMarketRegime fromName(String name) {
        for (ExchangeMarketRegime regime : values()) {
            if (regime.name().equalsIgnoreCase(name)) {
                return regime;
            }
        }
        return MIXED;
    }
}
