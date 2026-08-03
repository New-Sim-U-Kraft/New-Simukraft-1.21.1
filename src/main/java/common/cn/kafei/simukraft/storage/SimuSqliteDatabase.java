package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Closeable;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("Null")
public final class SimuSqliteDatabase implements Closeable {
    private static final String STORAGE_DIR = SimuKraft.MOD_ID;
    private static final String DATABASE_FILE = SimuKraft.MOD_ID + ".sqlite";
    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final AtomicBoolean DRIVER_LOADED = new AtomicBoolean();

    private final Path databasePath;
    private final String jdbcUrl;
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "simukraft-db-write");
        t.setDaemon(true);
        return t;
    });

    private SimuSqliteDatabase(Path databasePath) {
        loadDriver();
        this.databasePath = databasePath;
        this.jdbcUrl = JDBC_PREFIX + databasePath.toAbsolutePath().normalize();
        SimuSqliteSchema.initialize(this);
    }

    public static SimuSqliteDatabase open(MinecraftServer server) {
        return new SimuSqliteDatabase(databasePath(server));
    }

    public static Path databasePath(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        return worldPath.resolve(STORAGE_DIR).resolve(DATABASE_FILE);
    }

    public Connection openConnection() throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = c.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA busy_timeout=5000");
            stmt.execute("PRAGMA foreign_keys=ON");
        } catch (SQLException e) {
            try { c.close(); } catch (SQLException ignored) {}
            throw e;
        }
        return c;
    }

    public void submitWrite(Runnable task) {
        writeExecutor.execute(task);
    }

    public Path databasePath() {
        return databasePath;
    }

    @Override
    public void close() {
        writeExecutor.shutdown();
    }

    private static void loadDriver() {
        if (DRIVER_LOADED.get()) {
            return;
        }
        synchronized (DRIVER_LOADED) {
            // JDBC 驱动只需加载一次，AtomicBoolean 防止多线程重复 Class.forName。
            if (DRIVER_LOADED.get()) {
                return;
            }
            try {
                Class.forName("org.sqlite.JDBC");
                DRIVER_LOADED.set(true);
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("SQLite JDBC driver is not available. Check sqlite-jdbc runtime dependency.", exception);
            }
        }
    }
}
