package common.cn.kafei.simukraft.exchange;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.storage.ExchangeSqliteRepository;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** ExchangeMarketService: 开闭市、每日市况、小时报价、买卖。 */
public final class ExchangeMarketService {
    public static final int HISTORY_DAYS = 30;
    public static final int HISTORY_CANDLES = ExchangeMarketClock.HOURS_PER_SESSION * HISTORY_DAYS;
    private static final long MIN_PERIOD_INTERVAL_MS = 50L;
    private static final ConcurrentMap<String, MarketState> STATES = new ConcurrentHashMap<>();
    private static long lastPeriodRealtimeMs;

    public record TradeResult(boolean success, String messageKey) {
    }

    private ExchangeMarketService() {
    }

    /** tick: 主世界推进股市时钟。 */
    public static void tick(ServerLevel level) {
        if (level == null || level.dimension() != Level.OVERWORLD) {
            return;
        }
        MarketState state = state(level);
        long dayTime = level.getDayTime();
        pruneFutureCandles(level, state, dayTime);
        long targetDay = ExchangeMarketClock.dayIndex(dayTime);
        int targetHour = ExchangeMarketClock.hourIndex(dayTime);
        if (!behindClock(state, targetDay, targetHour)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPeriodRealtimeMs < MIN_PERIOD_INTERVAL_MS) {
            return;
        }
        if (advanceOnePeriod(level, state, targetDay, targetHour)) {
            lastPeriodRealtimeMs = now;
        }
    }

    /** saveToSqlite: 把内存行情整表写入，关服/存档时调用，避免只靠小时增量异步写丢失。 */
    public static void saveToSqlite(ServerLevel level) {
        if (level == null || level.dimension() != Level.OVERWORLD) {
            return;
        }
        MarketState state = STATES.get(SaveScopedCacheKey.levelKey(level));
        if (state == null) {
            return;
        }
        pruneFutureCandles(level, state, level.getDayTime());
        persistAll(level, state);
    }

    /** snapshot: 某城市看到的全部行情。 */
    public static List<ExchangeQuote> snapshot(ServerLevel level, UUID cityId) {
        MarketState state = state(level);
        Map<String, Holding> holdings = cityId != null ? holdingsOf(level, state, cityId) : Map.of();
        List<ExchangeQuote> quotes = new ArrayList<>();
        for (ExchangeCompany company : ExchangeCompanyLoader.INSTANCE.companies()) {
            Quote quote = state.quotes.computeIfAbsent(company.id(), ignored -> Quote.seed(company));
            Holding holding = holdings.getOrDefault(company.id(), Holding.EMPTY);
            quotes.add(new ExchangeQuote(company.id(), company.displayName(), quote.price, quote.previousClose,
                    quote.volume, holding.shares, holding.costBasis, quote.candles));
        }
        return quotes;
    }

    public static ExchangeMarketRegime regime(ServerLevel level) {
        return state(level).regime;
    }

    public static long marketDay(ServerLevel level) {
        return state(level).day;
    }

    /** buy: 用城市虚拟资金买入。 */
    public static TradeResult buy(ServerLevel level, ServerPlayer player, UUID cityId, String companyId, int shares) {
        if (!ExchangeMarketClock.isOpen(level.getDayTime())) {
            return new TradeResult(false, "message.simukraft.exchange.closed");
        }
        if (!CityService.canManageCity(level, cityId, player.getUUID())) {
            return new TradeResult(false, "message.simukraft.exchange.no_permission");
        }
        if (shares <= 0) {
            return new TradeResult(false, "message.simukraft.exchange.invalid_shares");
        }
        MarketState state = state(level);
        Quote quote = state.quotes.get(companyId);
        if (quote == null) {
            return new TradeResult(false, "message.simukraft.exchange.unknown_company");
        }
        double cost = EconomyService.normalizeAmount(quote.price * shares);
        if (!EconomyService.canAfford(level, cityId, cost)) {
            return new TradeResult(false, "message.simukraft.exchange.not_enough_funds");
        }
        if (!EconomyService.withdrawCityFunds(level, cityId, player, cost, "exchange_buy")) {
            return new TradeResult(false, "message.simukraft.exchange.buy_failed");
        }
        Map<String, Holding> holdings = holdingsOf(level, state, cityId);
        Holding current = holdings.getOrDefault(companyId, Holding.EMPTY);
        int total = current.shares + shares;
        double basis = current.costBasis + cost;
        holdings.put(companyId, new Holding(total, basis));
        SimuSqliteStorage.saveExchangeHolding(level, cityId, companyId, total, basis);
        return new TradeResult(true, "message.simukraft.exchange.buy_ok");
    }

    /** sell: 卖出持仓回到城市资金。 */
    public static TradeResult sell(ServerLevel level, ServerPlayer player, UUID cityId, String companyId, int shares) {
        if (!ExchangeMarketClock.isOpen(level.getDayTime())) {
            return new TradeResult(false, "message.simukraft.exchange.closed");
        }
        if (!CityService.canManageCity(level, cityId, player.getUUID())) {
            return new TradeResult(false, "message.simukraft.exchange.no_permission");
        }
        if (shares <= 0) {
            return new TradeResult(false, "message.simukraft.exchange.invalid_shares");
        }
        MarketState state = state(level);
        Quote quote = state.quotes.get(companyId);
        Map<String, Holding> holdings = holdingsOf(level, state, cityId);
        Holding current = holdings.getOrDefault(companyId, Holding.EMPTY);
        if (quote == null || current.shares < shares) {
            return new TradeResult(false, "message.simukraft.exchange.not_enough_shares");
        }
        double proceeds = EconomyService.normalizeAmount(quote.price * shares);
        double avg = current.shares > 0 ? current.costBasis / current.shares : 0.0D;
        int remaining = current.shares - shares;
        double basis = remaining <= 0 ? 0.0D : EconomyService.normalizeAmount(avg * remaining);
        if (remaining <= 0) {
            holdings.remove(companyId);
        } else {
            holdings.put(companyId, new Holding(remaining, basis));
        }
        SimuSqliteStorage.saveExchangeHolding(level, cityId, companyId, remaining, basis);
        EconomyService.depositCityFunds(level, cityId, player, proceeds, "exchange_sell");
        return new TradeResult(true, "message.simukraft.exchange.sell_ok");
    }

    /** clearServerCaches: 关服清缓存。 */
    public static void clearServerCaches(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        STATES.clear();
        lastPeriodRealtimeMs = 0L;
    }

    private static Map<String, Holding> holdingsOf(ServerLevel level, MarketState state, UUID cityId) {
        return state.holdings.computeIfAbsent(cityId, id -> {
            ConcurrentMap<String, Holding> loaded = new ConcurrentHashMap<>();
            for (ExchangeSqliteRepository.HoldingRow row : SimuSqliteStorage.loadExchangeHoldings(level, id)) {
                loaded.put(row.companyId(), new Holding(row.shares(), row.costBasis()));
            }
            return loaded;
        });
    }

    private static void rollNewDay(ServerLevel level, MarketState state, long day) {
        for (Quote quote : state.quotes.values()) {
            quote.previousClose = quote.price;
            quote.volume = 0;
        }
        state.day = day;
        state.regime = ExchangeMarketRegime.roll();
        state.lastHour = -1;
        SimuSqliteStorage.saveExchangeMarket(level, day, state.regime.name(), state.lastHour);
        persistQuotes(level, state);
    }

    private static boolean behindClock(MarketState state, long targetDay, int targetHour) {
        if (state.day < targetDay) {
            return true;
        }
        return state.day == targetDay && targetHour >= 0 && state.lastHour < targetHour;
    }

    /** advanceOnePeriod: 每次只推进一个交易小时，避免一次性吐出全天 K 线。 */
    private static boolean advanceOnePeriod(ServerLevel level, MarketState state, long targetDay, int targetHour) {
        if (state.day < targetDay) {
            int next = state.lastHour < 0 ? 0 : state.lastHour + 1;
            if (next < ExchangeMarketClock.HOURS_PER_SESSION) {
                emitHour(level, state, next);
                return true;
            }
            rollNewDay(level, state, state.day + 1);
            return true;
        }
        if (targetHour < 0 || state.lastHour >= targetHour) {
            return false;
        }
        int next = state.lastHour < 0 ? 0 : state.lastHour + 1;
        if (next > targetHour) {
            return false;
        }
        emitHour(level, state, next);
        return true;
    }

    private static void emitHour(ServerLevel level, MarketState state, int hour) {
        if (hasCandle(state, state.day, hour)) {
            state.lastHour = hour;
            return;
        }
        advanceHour(level, state, hour);
    }

    private static void advanceHour(ServerLevel level, MarketState state, int hour) {
        state.lastHour = hour;
        for (ExchangeCompany company : ExchangeCompanyLoader.INSTANCE.companies()) {
            Quote quote = state.quotes.computeIfAbsent(company.id(), ignored -> Quote.seed(company));
            double previous = quote.price;
            double next = ExchangePriceMath.nextPrice(previous, company, state.regime);
            int volume = ExchangePriceMath.volume(previous, next);
            quote.price = next;
            quote.volume += volume;
            ExchangeCandle candle = new ExchangeCandle(state.day, hour, previous, Math.max(previous, next),
                    Math.min(previous, next), next, volume);
            quote.push(candle);
            SimuSqliteStorage.saveExchangeQuote(level, company.id(), quote.price, quote.previousClose, quote.volume);
            SimuSqliteStorage.saveExchangeCandle(level, company.id(), state.day, candle);
        }
        SimuSqliteStorage.saveExchangeMarket(level, state.day, state.regime.name(), hour);
    }

    private static void persistQuotes(ServerLevel level, MarketState state) {
        for (Map.Entry<String, Quote> entry : state.quotes.entrySet()) {
            Quote quote = entry.getValue();
            SimuSqliteStorage.saveExchangeQuote(level, entry.getKey(), quote.price, quote.previousClose, quote.volume);
        }
    }

    private static MarketState state(ServerLevel level) {
        return STATES.computeIfAbsent(SaveScopedCacheKey.levelKey(level), ignored -> load(level));
    }

    private static MarketState load(ServerLevel level) {
        MarketState state = new MarketState();
        ExchangeSqliteRepository.MarketRow market = SimuSqliteStorage.loadExchangeMarket(level);
        long today = ExchangeMarketClock.dayIndex(level.getDayTime());
        if (market != null) {
            state.day = market.day();
            state.regime = ExchangeMarketRegime.fromName(market.regime());
            state.lastHour = market.lastHour();
        } else {
            state.day = today;
            state.regime = ExchangeMarketRegime.roll();
            state.lastHour = -1;
        }
        for (ExchangeCompany company : ExchangeCompanyLoader.INSTANCE.companies()) {
            Quote quote = Quote.seed(company);
            state.quotes.put(company.id(), quote);
        }
        for (ExchangeSqliteRepository.QuoteRow row : SimuSqliteStorage.loadExchangeQuotes(level)) {
            Quote quote = state.quotes.computeIfAbsent(row.companyId(), ignored -> new Quote());
            quote.price = row.price();
            quote.previousClose = row.previousClose();
            quote.volume = row.volume();
        }
        for (String companyId : state.quotes.keySet()) {
            Quote quote = state.quotes.get(companyId);
            quote.candles.addAll(SimuSqliteStorage.loadExchangeCandles(level, companyId));
            trimHistory(quote);
        }
        pruneFutureCandles(level, state, level.getDayTime());
        int savedHour = latestHour(state, today);
        if (savedHour >= 0) {
            state.lastHour = savedHour;
        }
        if (state.day != today) {
            rollNewDay(level, state, today);
        } else if (market == null) {
            SimuSqliteStorage.saveExchangeMarket(level, state.day, state.regime.name(), state.lastHour);
        }
        return state;
    }

    private static void persistAll(ServerLevel level, MarketState state) {
        SimuSqliteStorage.saveExchangeMarket(level, state.day, state.regime.name(), state.lastHour);
        persistQuotes(level, state);
        for (Map.Entry<String, Quote> entry : state.quotes.entrySet()) {
            for (ExchangeCandle candle : List.copyOf(entry.getValue().candles)) {
                SimuSqliteStorage.saveExchangeCandle(level, entry.getKey(), candle.marketDay(), candle);
            }
        }
        for (Map.Entry<UUID, ConcurrentMap<String, Holding>> cityHoldings : state.holdings.entrySet()) {
            for (Map.Entry<String, Holding> holding : cityHoldings.getValue().entrySet()) {
                SimuSqliteStorage.saveExchangeHolding(level, cityHoldings.getKey(), holding.getKey(),
                        holding.getValue().shares(), holding.getValue().costBasis());
            }
        }
    }

    private static boolean hasCandle(MarketState state, long day, int hour) {
        for (Quote quote : state.quotes.values()) {
            for (ExchangeCandle candle : quote.candles) {
                if (candle.marketDay() == day && candle.hourIndex() == hour) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int latestHour(MarketState state, long day) {
        int latest = -1;
        for (Quote quote : state.quotes.values()) {
            for (ExchangeCandle candle : quote.candles) {
                if (candle.marketDay() == day) {
                    latest = Math.max(latest, candle.hourIndex());
                }
            }
        }
        return latest;
    }

    /** pruneFutureCandles: 丢掉尚未走到的小时柱，并让 lastHour / 现价回到当前时钟。 */
    private static void pruneFutureCandles(ServerLevel level, MarketState state, long dayTime) {
        long day = ExchangeMarketClock.dayIndex(dayTime);
        int maxHour = ExchangeMarketClock.maxValidHour(dayTime);
        boolean removed = false;
        for (Quote quote : state.quotes.values()) {
            int before = quote.candles.size();
            quote.candles.removeIf(candle -> candle.marketDay() > day
                    || (candle.marketDay() == day && candle.hourIndex() > maxHour));
            if (quote.candles.size() != before) {
                removed = true;
            }
        }
        if (state.day == day && state.lastHour > maxHour) {
            state.lastHour = latestHour(state, day);
            removed = true;
        }
        if (!removed) {
            return;
        }
        for (Quote quote : state.quotes.values()) {
            restoreQuoteFromCandles(quote, day);
        }
        SimuSqliteStorage.deleteExchangeCandlesAfter(level, day, maxHour);
        persistQuotes(level, state);
        SimuSqliteStorage.saveExchangeMarket(level, state.day, state.regime.name(), state.lastHour);
        SimuKraft.LOGGER.info("Pruned future exchange candles after day {} hour {}", day, maxHour);
    }

    /** restoreQuoteFromCandles: 现价取最后一根保留柱的收盘，成交量只计当天剩下的柱。 */
    private static void restoreQuoteFromCandles(Quote quote, long day) {
        ExchangeCandle last = null;
        int volume = 0;
        for (ExchangeCandle candle : quote.candles) {
            if (last == null
                    || candle.marketDay() > last.marketDay()
                    || (candle.marketDay() == last.marketDay() && candle.hourIndex() > last.hourIndex())) {
                last = candle;
            }
            if (candle.marketDay() == day) {
                volume += candle.volume();
            }
        }
        quote.price = last != null ? last.close() : quote.previousClose;
        quote.volume = volume;
    }

    private static final class MarketState {
        private long day;
        private ExchangeMarketRegime regime = ExchangeMarketRegime.MIXED;
        private int lastHour = -1;
        private final ConcurrentMap<String, Quote> quotes = new ConcurrentHashMap<>();
        private final ConcurrentMap<UUID, ConcurrentMap<String, Holding>> holdings = new ConcurrentHashMap<>();
    }

    private static final class Quote {
        private double price = 1.0D;
        private double previousClose = 1.0D;
        private int volume;
        private final List<ExchangeCandle> candles = new CopyOnWriteArrayList<>();

        private static Quote seed(ExchangeCompany company) {
            Quote quote = new Quote();
            quote.price = company.basePrice();
            quote.previousClose = company.basePrice();
            return quote;
        }

        private void push(ExchangeCandle candle) {
            candles.add(candle);
            trimHistory(this);
        }
    }

    /** trimHistory: 内存只留最近若干交易日的小时线，库里更早的记录仍在。 */
    private static void trimHistory(Quote quote) {
        while (quote.candles.size() > HISTORY_CANDLES) {
            quote.candles.remove(0);
        }
    }

    private record Holding(int shares, double costBasis) {
        private static final Holding EMPTY = new Holding(0, 0.0D);
    }
}
