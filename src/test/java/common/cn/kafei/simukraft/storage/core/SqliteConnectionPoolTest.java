package common.cn.kafei.simukraft.storage.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 回归：两条通道的 PRAGMA 必须真的生效。
 *
 * <p>旧实现把 4 条 PRAGMA 拼成 {@code "PRAGMA a; PRAGMA b; ..."} 交给 HikariCP 的
 * connectionInitSql。sqlite-jdbc 的 {@code Statement.execute} 只 prepare 第一条语句，
 * 分号之后的部分被静默丢弃且不抛异常：实测只有 journal_mode 生效，synchronous、
 * busy_timeout、foreign_keys 全部停留在驱动默认值（2 / 3000 / 0）。
 * 后果是池化连接上的外键级联根本不触发，而且没有任何报错。
 */
class SqliteConnectionPoolTest {
    @TempDir
    Path tempDir;

    @Test
    void pooledConnectionsGetEveryPragma() {
        try (SqliteConnectionPool pool = SqliteConnectionPool.open(tempDir.resolve("pooled.sqlite"))) {
            try (Connection connection = pool.borrow()) {
                assertPragmas(connection, SqliteConnectionPool.POOL_BUSY_TIMEOUT_MILLIS);
            } catch (SQLException exception) {
                throw new AssertionError(exception);
            }
        }
    }

    @Test
    void writeConnectionGetsEveryPragma() {
        try (SqliteConnectionPool pool = SqliteConnectionPool.open(tempDir.resolve("write.sqlite"))) {
            Connection connection = pool.writeConnection();
            assertPragmas(connection, SqliteConnectionPool.WRITE_BUSY_TIMEOUT_MILLIS);
            assertFalse(connection.getAutoCommit(), "写连接必须以 autoCommit=false 打开，事务边界由 TransactionRunner 控制");
        } catch (SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    /**
     * 外键级联是删除路径的正确性基础（删城市要连带清 city_members / finance_transactions）。
     * PRAGMA 静默失效时这条断言是唯一能抓住它的行为级证据。
     */
    @Test
    void foreignKeyCascadeActuallyFiresOnPooledConnections() throws Exception {
        try (SqliteConnectionPool pool = SqliteConnectionPool.open(tempDir.resolve("cascade.sqlite"));
             Connection connection = pool.borrow();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE parent(id TEXT PRIMARY KEY)");
            statement.executeUpdate("CREATE TABLE child(id TEXT PRIMARY KEY, parent_id TEXT NOT NULL, "
                    + "FOREIGN KEY(parent_id) REFERENCES parent(id) ON DELETE CASCADE)");
            statement.executeUpdate("INSERT INTO parent(id) VALUES('p')");
            statement.executeUpdate("INSERT INTO child(id, parent_id) VALUES('c', 'p')");

            statement.executeUpdate("DELETE FROM parent WHERE id = 'p'");

            try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM child")) {
                resultSet.next();
                assertEquals(0, resultSet.getInt(1), "foreign_keys=ON 未生效：子表行没有被级联删除");
            }
        }
    }

    private static void assertPragmas(Connection connection, int expectedBusyTimeoutMillis) throws SQLException {
        assertEquals("wal", queryString(connection, "PRAGMA journal_mode"));
        // synchronous: 0=OFF 1=NORMAL 2=FULL；驱动默认 FULL，NORMAL 说明设置真的到了连接上。
        assertEquals(1, queryInt(connection, "PRAGMA synchronous"));
        assertEquals(expectedBusyTimeoutMillis, queryInt(connection, "PRAGMA busy_timeout"));
        assertEquals(1, queryInt(connection, "PRAGMA foreign_keys"));
    }

    private static int queryInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
