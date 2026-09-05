package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.exchange.ExchangeCandle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** ExchangeSqliteRepository: 股市行情、K 线与持仓。 */
public final class ExchangeSqliteRepository {
    private final SimuSqliteDatabase database;

    public ExchangeSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /** loadMarket: 读取当日市况。 */
    public MarketRow loadMarket(String dimensionId) {
        try (Connection connection = database.borrowConnection()) {
            return loadMarket(connection, dimensionId);
        } catch (SQLException exception) {
            SimuKraft.LOGGER.warn("Failed to load exchange market", exception);
            return null;
        }
    }

    /** loadQuotes: 读取全部行情。 */
    public List<QuoteRow> loadQuotes(String dimensionId) {
        try (Connection connection = database.borrowConnection()) {
            return loadQuotes(connection, dimensionId);
        } catch (SQLException exception) {
            SimuKraft.LOGGER.warn("Failed to load exchange quotes", exception);
            return List.of();
        }
    }

    /** loadCandles: 读取一只股票的小时线。 */
    public List<ExchangeCandle> loadCandles(String dimensionId, String companyId) {
        try (Connection connection = database.borrowConnection()) {
            return loadCandles(connection, dimensionId, companyId);
        } catch (SQLException exception) {
            SimuKraft.LOGGER.warn("Failed to load exchange candles for {}", companyId, exception);
            return List.of();
        }
    }

    /** loadHoldings: 读取一座城市的持仓。 */
    public List<HoldingRow> loadHoldings(UUID cityId) {
        try (Connection connection = database.borrowConnection()) {
            return loadHoldings(connection, cityId);
        } catch (SQLException exception) {
            SimuKraft.LOGGER.warn("Failed to load exchange holdings", exception);
            return List.of();
        }
    }

    public record QuoteRow(String companyId, double price, double previousClose, int volume) {
    }

    public record HoldingRow(UUID cityId, String companyId, int shares, double costBasis) {
    }

    public record MarketRow(long day, String regime, int lastHour) {
    }

    /** loadMarket: 读取当日市况。 */
    public MarketRow loadMarket(Connection connection, String dimensionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT market_day, regime, last_hour FROM exchange_market WHERE dimension_id=?")) {
            statement.setString(1, dimensionId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new MarketRow(result.getLong(1), result.getString(2), result.getInt(3));
                }
            }
        }
        return null;
    }

    /** saveMarket: 写入当日市况。 */
    public void saveMarket(Connection connection, String dimensionId, long day, String regime, int lastHour) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO exchange_market(dimension_id, market_day, regime, last_hour) VALUES(?,?,?,?) "
                        + "ON CONFLICT(dimension_id) DO UPDATE SET market_day=excluded.market_day, regime=excluded.regime, last_hour=excluded.last_hour")) {
            statement.setString(1, dimensionId);
            statement.setLong(2, day);
            statement.setString(3, regime);
            statement.setInt(4, lastHour);
            statement.executeUpdate();
        }
    }

    /** loadQuotes: 读取全部行情。 */
    public List<QuoteRow> loadQuotes(Connection connection, String dimensionId) throws SQLException {
        List<QuoteRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT company_id, price, previous_close, volume FROM exchange_quotes WHERE dimension_id=?")) {
            statement.setString(1, dimensionId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new QuoteRow(result.getString(1), result.getDouble(2), result.getDouble(3), result.getInt(4)));
                }
            }
        }
        return rows;
    }

    /** saveQuote: 写入单只行情。 */
    public void saveQuote(Connection connection, String dimensionId, String companyId, double price, double previousClose, int volume)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO exchange_quotes(dimension_id, company_id, price, previous_close, volume) VALUES(?,?,?,?,?) "
                        + "ON CONFLICT(dimension_id, company_id) DO UPDATE SET price=excluded.price, previous_close=excluded.previous_close, volume=excluded.volume")) {
            statement.setString(1, dimensionId);
            statement.setString(2, companyId);
            statement.setDouble(3, price);
            statement.setDouble(4, previousClose);
            statement.setInt(5, volume);
            statement.executeUpdate();
        }
    }

    /** loadCandles: 读取一只股票的小时线。 */
    public List<ExchangeCandle> loadCandles(Connection connection, String dimensionId, String companyId) throws SQLException {
        List<ExchangeCandle> candles = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT market_day, hour_index, open_price, high_price, low_price, close_price, volume FROM exchange_candles "
                        + "WHERE dimension_id=? AND company_id=? ORDER BY market_day, hour_index")) {
            statement.setString(1, dimensionId);
            statement.setString(2, companyId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    candles.add(new ExchangeCandle(result.getLong(1), result.getInt(2), result.getDouble(3),
                            result.getDouble(4), result.getDouble(5), result.getDouble(6), result.getInt(7)));
                }
            }
        }
        return candles;
    }

    /** deleteCandlesAfter: 删除指定交易日之后（含当日超过 maxHour）的小时柱。 */
    public void deleteCandlesAfter(Connection connection, String dimensionId, long day, int maxHour) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM exchange_candles WHERE dimension_id=? AND (market_day>? OR (market_day=? AND hour_index>?))")) {
            statement.setString(1, dimensionId);
            statement.setLong(2, day);
            statement.setLong(3, day);
            statement.setInt(4, maxHour);
            statement.executeUpdate();
        }
    }

    /** saveCandle: 写入一根 K 线。 */
    public void saveCandle(Connection connection, String dimensionId, String companyId, long day, ExchangeCandle candle)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO exchange_candles(dimension_id, company_id, market_day, hour_index, open_price, high_price, low_price, close_price, volume) "
                        + "VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(dimension_id, company_id, market_day, hour_index) DO UPDATE SET "
                        + "open_price=excluded.open_price, high_price=excluded.high_price, low_price=excluded.low_price, "
                        + "close_price=excluded.close_price, volume=excluded.volume")) {
            statement.setString(1, dimensionId);
            statement.setString(2, companyId);
            statement.setLong(3, candle.marketDay());
            statement.setInt(4, candle.hourIndex());
            statement.setDouble(5, candle.open());
            statement.setDouble(6, candle.high());
            statement.setDouble(7, candle.low());
            statement.setDouble(8, candle.close());
            statement.setInt(9, candle.volume());
            statement.executeUpdate();
        }
    }

    /** loadHoldings: 读取一座城市的持仓。 */
    public List<HoldingRow> loadHoldings(Connection connection, UUID cityId) throws SQLException {
        List<HoldingRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT city_id, company_id, shares, cost_basis FROM exchange_holdings WHERE city_id=?")) {
            statement.setString(1, cityId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new HoldingRow(UUID.fromString(result.getString(1)), result.getString(2),
                            result.getInt(3), result.getDouble(4)));
                }
            }
        }
        return rows;
    }

    /** saveHolding: 写入或删除持仓。 */
    public void saveHolding(Connection connection, UUID cityId, String companyId, int shares, double costBasis) throws SQLException {
        if (shares <= 0) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM exchange_holdings WHERE city_id=? AND company_id=?")) {
                statement.setString(1, cityId.toString());
                statement.setString(2, companyId);
                statement.executeUpdate();
            }
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO exchange_holdings(city_id, company_id, shares, cost_basis) VALUES(?,?,?,?) "
                        + "ON CONFLICT(city_id, company_id) DO UPDATE SET shares=excluded.shares, cost_basis=excluded.cost_basis")) {
            statement.setString(1, cityId.toString());
            statement.setString(2, companyId);
            statement.setInt(3, shares);
            statement.setDouble(4, costBasis);
            statement.executeUpdate();
        }
    }
}
