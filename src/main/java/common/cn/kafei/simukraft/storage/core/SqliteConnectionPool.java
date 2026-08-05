package common.cn.kafei.simukraft.storage.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import common.cn.kafei.simukraft.SimuKraft;

import java.io.Closeable;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 连接管理。
 *
 * <p>分成两条通道：
 * <ul>
 *   <li>{@link #writeConnection()}：常驻写连接，只允许写队列线程使用，用于把一批写入合并进一个事务；</li>
 *   <li>{@link #borrow()}：池化连接，供加载查询和少量"读-改-写"事务使用，调用方必须 close 归还。</li>
 * </ul>
 *
 * <p>旧实现每次读写都 {@code DriverManager.getConnection} 新建连接并重设 4 条 PRAGMA，
 * 一次增量写就要付一次建连开销。
 */
public final class SqliteConnectionPool implements Closeable {
    /** 池化连接的 busy_timeout：主线程宁可快速失败重试，也不能被写线程的事务卡住数秒。 */
    public static final int POOL_BUSY_TIMEOUT_MILLIS = 1_000;
    /** 写连接的 busy_timeout：写线程是后台线程，可以多等一会儿。 */
    public static final int WRITE_BUSY_TIMEOUT_MILLIS = 3_000;

    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    /*
     * PRAGMA 一律经 JDBC URL 参数设置，不走 "PRAGMA a; PRAGMA b" 这种多语句 SQL：
     * sqlite-jdbc 的 Statement.execute 只 prepare 第一条语句，分号之后的部分被静默丢弃且不报错
     * （实测：只有 journal_mode 生效，synchronous / busy_timeout / foreign_keys 全部保持默认值）。
     *
     * transaction_mode=IMMEDIATE（仅池化连接）：sqlite-jdbc 默认用 DEFERRED 事务，
     * "先 SELECT 后 UPDATE" 的事务在并发写时会被 SQLite 直接判为冲突而不是等待 busy_timeout。
     * 改成 IMMEDIATE 后开事务就取写锁，busy_timeout 才能真正生效。
     * 写连接刻意不用它，见 {@link #writeConnection()} 的注释。
     */
    private static final String POOL_URL_PARAMS =
            "?journal_mode=WAL&synchronous=NORMAL&busy_timeout=" + POOL_BUSY_TIMEOUT_MILLIS + "&foreign_keys=on&transaction_mode=IMMEDIATE";
    private static final String WRITE_URL_PARAMS =
            "?journal_mode=WAL&synchronous=NORMAL&busy_timeout=" + WRITE_BUSY_TIMEOUT_MILLIS + "&foreign_keys=on";
    private static final int POOL_SIZE = 8;
    private static final long CONNECTION_TIMEOUT_MILLIS = 10_000L;

    private final Path databasePath;
    private final String writeJdbcUrl;
    private final HikariDataSource dataSource;
    private Connection writeConnection;

    private SqliteConnectionPool(Path databasePath, String writeJdbcUrl, HikariDataSource dataSource) {
        this.databasePath = databasePath;
        this.writeJdbcUrl = writeJdbcUrl;
        this.dataSource = dataSource;
    }

    public static SqliteConnectionPool open(Path databasePath) {
        String baseUrl = JDBC_PREFIX + databasePath.toAbsolutePath().normalize();
        HikariConfig config = new HikariConfig();
        config.setPoolName("simukraft-sqlite");
        config.setJdbcUrl(baseUrl + POOL_URL_PARAMS);
        config.setMaximumPoolSize(POOL_SIZE);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
        // autoCommit=true 是归还连接时的复位值；需要事务的调用方自己设 false 并 commit/rollback。
        config.setAutoCommit(true);
        return new SqliteConnectionPool(databasePath, baseUrl + WRITE_URL_PARAMS, new HikariDataSource(config));
    }

    /** borrow: 借一条池化连接，调用方必须 close 归还（try-with-resources）。 */
    public Connection borrow() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * writeConnection: 返回常驻写连接，只允许写队列线程调用。
     * <p>连接以 {@code autoCommit=false} 打开，事务由 {@link TransactionRunner} 控制；
     * 调用方不得 close 它。
     * <p>刻意用 DEFERRED 事务模式（URL 里不带 transaction_mode）：IMMEDIATE 模式会在每次 commit 后
     * 立即 begin 下一个事务，批与批之间常驻持有写锁，池化连接上的写（schema 迁移、测试辅助）
     * 会被挡成 SQLITE_BUSY；DEFERRED 在批间只持有读快照，不挡其它写者。
     */
    public Connection writeConnection() throws SQLException {
        if (writeConnection == null || writeConnection.isClosed()) {
            // PRAGMA 已经在 URL 参数里，这里不再逐条 execute。
            writeConnection = java.sql.DriverManager.getConnection(writeJdbcUrl);
            writeConnection.setAutoCommit(false);
        }
        return writeConnection;
    }

    public Path databasePath() {
        return databasePath;
    }

    /** checkpoint: 把 WAL 合并回主库并截断，避免 -wal 无限增长以及备份只拿到半份数据。 */
    public void checkpoint() {
        try (Connection connection = borrow(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException exception) {
            SimuKraft.LOGGER.warn("Simukraft: failed to checkpoint SQLite WAL", exception);
        }
    }

    @Override
    public void close() {
        if (writeConnection != null) {
            try {
                if (!writeConnection.isClosed()) {
                    writeConnection.rollback();
                    writeConnection.close();
                }
            } catch (SQLException exception) {
                SimuKraft.LOGGER.warn("Simukraft: failed to close SQLite write connection", exception);
            }
            writeConnection = null;
        }
        checkpoint();
        dataSource.close();
    }
}
