package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Closeable;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("Null")
public final class BuildingStructureSqliteDatabase implements Closeable {
    private static final String STORAGE_DIR = SimuKraft.MOD_ID;
    private static final String DATABASE_FILE = SimuKraft.MOD_ID + "_buildings.sqlite";
    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final AtomicBoolean DRIVER_LOADED = new AtomicBoolean();

    private final Path databasePath;
    private final String jdbcUrl;
    private Connection cachedConnection;

    private BuildingStructureSqliteDatabase(Path databasePath) {
        loadDriver();
        this.databasePath = databasePath;
        this.jdbcUrl = JDBC_PREFIX + databasePath.toAbsolutePath().normalize();
        BuildingStructureSqliteSchema.initialize(this);
    }

    public static BuildingStructureSqliteDatabase open(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        return new BuildingStructureSqliteDatabase(worldPath.resolve(STORAGE_DIR).resolve(DATABASE_FILE));
    }

    public Connection openConnection() throws SQLException {
        if (cachedConnection == null || cachedConnection.isClosed()) {
            cachedConnection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = cachedConnection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA foreign_keys=ON");
            }
        }
        Connection c = cachedConnection;
        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class[]{Connection.class},
            (proxy, method, args) -> "close".equals(method.getName()) ? null : method.invoke(c, args));
    }

    public Path databasePath() {
        return databasePath;
    }

    @Override
    public void close() {
        if (cachedConnection != null) {
            try { cachedConnection.close(); } catch (java.sql.SQLException ignored) {}
            cachedConnection = null;
        }
    }

    private static void loadDriver() {
        if (DRIVER_LOADED.get()) {
            return;
        }
        synchronized (DRIVER_LOADED) {
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
