package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.storage.core.SchemaMigrator;
import common.cn.kafei.simukraft.storage.core.SimuMigrations;
import common.cn.kafei.simukraft.storage.core.SqlFunction;
import common.cn.kafei.simukraft.storage.core.SqlWrite;
import common.cn.kafei.simukraft.storage.core.SqliteConnectionPool;
import common.cn.kafei.simukraft.storage.core.StorageMetrics;
import common.cn.kafei.simukraft.storage.core.TransactionRunner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 主库句柄。
 *
 * <p>三条通道：
 * <ul>
 *   <li>写入 → {@link #submitWrite}：进写队列，由写线程按批合并进事务，主线程零 JDBC 调用；</li>
 *   <li>读取 → {@link #borrowConnection()}：池化连接，调用方 try-with-resources 归还；</li>
 *   <li>需要立刻返回结果的"读-改-写" → {@link #callSync}：提交到写线程执行并阻塞等待，
 *       与队列中其他写入保持全序，调用线程不再直接执行 SQL。
 * </ul>
 */
@SuppressWarnings("null")
public final class SimuSqliteDatabase implements Closeable {
    private static final String STORAGE_DIR = SimuKraft.MOD_ID;
    private static final String DATABASE_FILE = SimuKraft.MOD_ID + ".sqlite";
    private static final long SYNC_WRITE_TIMEOUT_MILLIS = 30_000L;

    private final Path databasePath;
    private final SqliteConnectionPool connections;
    private final TransactionRunner transactions;
    private final StorageMetrics metrics = new StorageMetrics();
    private final StorageWriteQueue writeQueue;
    // degraded：加载或写入出现真实故障后置位。降级后禁止任何写入，避免用不完整的内存状态覆盖磁盘。
    private volatile boolean degraded;
    private volatile boolean closed;

    private SimuSqliteDatabase(Path databasePath) {
        this.databasePath = databasePath;
        createStorageDirectory(databasePath);
        this.connections = SqliteConnectionPool.open(databasePath);
        this.transactions = new TransactionRunner(connections, this::markDegraded, metrics);
        try {
            new SchemaMigrator(SimuSqliteSchema::createBaseline, SimuMigrations.all()).migrate(connections);
        } catch (SQLException exception) {
            connections.close();
            throw new IllegalStateException("Failed to initialize Sim-U-Kraft SQLite schema", exception);
        }
        this.writeQueue = new StorageWriteQueue("simukraft-db-write", transactions, metrics);
    }

    public static SimuSqliteDatabase open(MinecraftServer server) {
        return new SimuSqliteDatabase(databasePath(server));
    }

    public static Path databasePath(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        return worldPath.resolve(STORAGE_DIR).resolve(DATABASE_FILE);
    }

    /** borrowConnection: 借一条池化连接用于查询，调用方必须 close（try-with-resources）归还。 */
    public Connection borrowConnection() throws SQLException {
        return connections.borrow();
    }

    /** submitWrite: 提交一次带合并键的写入，同一 key 的后续提交覆盖尚未执行的旧提交。 */
    public void submitWrite(Object key, SqlWrite write) {
        if (degraded || closed) {
            return;
        }
        writeQueue.submit(key, write);
    }

    /** submitWrite: 提交一次不参与合并的写入，严格按提交顺序执行。 */
    public void submitWrite(SqlWrite write) {
        if (degraded || closed) {
            return;
        }
        writeQueue.submitOnce(write);
    }

    /** callSync: 把必须同步得到结果的"读-改-写"提交到写线程执行并阻塞等待结果；失败返回 null。 */
    public <T> T callSync(SqlFunction<T> function) {
        if (degraded || closed) {
            return null;
        }
        return writeQueue.submitAndWait(SYNC_WRITE_TIMEOUT_MILLIS, function);
    }

    /** markDegraded: 记录一次真实的存储故障，之后所有写入被拒绝直到重开存档。 */
    public void markDegraded(String context, Throwable cause) {
        if (!degraded) {
            degraded = true;
            SimuKraft.LOGGER.error("Simukraft: SQLite storage entered DEGRADED mode ({}). Writes are disabled to protect existing data; the world will run on in-memory state only.", context, cause);
        }
    }

    public boolean isDegraded() {
        return degraded;
    }

    public boolean isClosed() {
        return closed;
    }

    /** drainWrites: 等待队列中的写入全部落库，返回是否在超时前完成。 */
    public boolean drainWrites() {
        return writeQueue.drainAndReport();
    }

    /** pendingWrites: 当前仍在队列中等待落库的写入条数（指标用）。 */
    public int pendingWrites() {
        return writeQueue.pendingCount();
    }

    public StorageMetrics metrics() {
        return metrics;
    }

    public Path databasePath() {
        return databasePath;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        writeQueue.close(5_000L);
        connections.close();
    }

    private static void createStorageDirectory(Path databasePath) {
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create Sim-U-Kraft SQLite directory", exception);
        }
    }
}
