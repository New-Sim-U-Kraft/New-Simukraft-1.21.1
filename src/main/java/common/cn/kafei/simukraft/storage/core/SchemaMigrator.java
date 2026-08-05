package common.cn.kafei.simukraft.storage.core;

import common.cn.kafei.simukraft.SimuKraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 基于 {@code PRAGMA user_version} 的版本化迁移。
 *
 * <p>取代原来散落的 {@code CREATE TABLE IF NOT EXISTS} + {@code addColumnIfMissing}：
 * 那种写法只能加列，改不了主键，也无从判断"这个存档已经升到哪一版"。
 *
 * <p>约定：
 * <ul>
 *   <li>version 0 表示未记录过版本（全新库或所有历史存档）；先跑基线 DDL 把它带到 version 1；</li>
 *   <li>每个 {@link Migration} 在独立事务里执行，成功后立刻写入 user_version，中途失败不会留下半套结构；</li>
 *   <li>基线之上还有待执行迁移时，先 checkpoint 并把库文件复制到 backups/ 再动手。</li>
 * </ul>
 */
@SuppressWarnings("null")
public final class SchemaMigrator {
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int BASELINE_VERSION = 1;

    private final List<Migration> migrations;
    private final BaselineSchema baseline;

    public SchemaMigrator(BaselineSchema baseline, List<Migration> migrations) {
        this.baseline = baseline;
        this.migrations = migrations.stream().sorted(java.util.Comparator.comparingInt(Migration::version)).toList();
    }

    /** 基线 DDL。必须幂等：全新库和历史存档都会执行它。 */
    @FunctionalInterface
    public interface BaselineSchema {
        void create(Connection connection) throws SQLException;
    }

    /**
     * migrate: 把库升到最新版本。
     *
     * @throws SQLException 迁移失败。调用方应据此进入降级（只读）而不是继续写入。
     */
    public void migrate(SqliteConnectionPool connections) throws SQLException {
        try (Connection connection = connections.borrow()) {
            int current = readUserVersion(connection);
            if (current == 0) {
                baseline.create(connection);
                writeUserVersion(connection, BASELINE_VERSION);
                current = BASELINE_VERSION;
                SimuKraft.LOGGER.info("Simukraft: SQLite schema baseline applied (user_version={}).", BASELINE_VERSION);
            }

            // current 可能被基线重写，lambda 需要事实 final 的副本。
            int currentVersion = current;
            List<Migration> pending = migrations.stream().filter(migration -> migration.version() > currentVersion).toList();
            if (pending.isEmpty()) {
                return;
            }

            connections.checkpoint();
            backup(connections.databasePath(), current);

            for (Migration migration : pending) {
                SimuKraft.LOGGER.info("Simukraft: applying SQLite migration v{} ({}).", migration.version(), migration.description());
                applyInTransaction(connection, migration);
            }
            SimuKraft.LOGGER.info("Simukraft: SQLite schema is now at user_version={}.", pending.get(pending.size() - 1).version());
        }
    }

    private static void applyInTransaction(Connection connection, Migration migration) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        /*
         * 表重建型迁移要先关外键：重建过程中旧表被 DROP，
         * 指向它的外键会在事务中途就报约束失败。
         */
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=OFF");
        }
        connection.setAutoCommit(false);
        try {
            migration.apply(connection);
            writeUserVersion(connection, migration.version());
            connection.commit();
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
            }
        }
    }

    // backup：迁移不可逆，出问题时玩家至少还有一份升级前的库。
    private static void backup(Path databasePath, int fromVersion) {
        try {
            Path backupDirectory = databasePath.getParent().resolve("backups");
            Files.createDirectories(backupDirectory);
            String fileName = databasePath.getFileName().toString();
            String stamp = LocalDateTime.now().format(BACKUP_STAMP);
            Path target = backupDirectory.resolve(fileName + ".v" + fromVersion + "-" + stamp + ".bak");
            Files.copy(databasePath, target, StandardCopyOption.REPLACE_EXISTING);
            SimuKraft.LOGGER.info("Simukraft: pre-migration backup written to {}", target);
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("Simukraft: failed to write pre-migration SQLite backup; continuing without it.", exception);
        }
    }

    private static int readUserVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private static void writeUserVersion(Connection connection, int version) throws SQLException {
        // user_version 不能用占位符，只能拼进 SQL；version 来自代码常量，没有注入面。
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = " + version);
        }
    }
}
