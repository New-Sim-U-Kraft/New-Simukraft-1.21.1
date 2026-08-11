package common.cn.kafei.simukraft.storage.core;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** 主库的迁移清单。新增迁移只需往 {@link #all()} 里追加，版本号必须连续递增。 */
public final class SimuMigrations {
    private SimuMigrations() {
    }

    public static List<Migration> all() {
        return List.of(new CityChunksDimensionPrimaryKey(), new CitizenReservedBabyBed());
    }

    /**
     * v2：把 {@code city_chunks} 的主键从 {@code (city_id, chunk_long)} 改成
     * {@code (dimension_id, city_id, chunk_long)}。
     *
     * <p>{@code dimension_id} 是后期用 ALTER TABLE 补上的列，主键没跟着改。结果是同一个城市
     * 在两个维度占用同坐标区块时会撞主键，整批保存事务回滚、保存静默失败。
     * SQLite 不支持改主键，只能重建表。
     */
    private static final class CityChunksDimensionPrimaryKey implements Migration {
        @Override
        public int version() {
            return 2;
        }

        @Override
        public String description() {
            return "rebuild city_chunks with dimension_id in the primary key";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE city_chunks_v2("
                        + "city_id TEXT NOT NULL, chunk_long INTEGER NOT NULL, "
                        + "dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', "
                        + "PRIMARY KEY(dimension_id, city_id, chunk_long))");
                // 空维度归入主世界，和仓库层 normalizeDimensionId 的口径一致。
                statement.executeUpdate("INSERT OR IGNORE INTO city_chunks_v2(city_id, chunk_long, dimension_id) "
                        + "SELECT city_id, chunk_long, "
                        + "COALESCE(NULLIF(TRIM(dimension_id), ''), 'minecraft:overworld') FROM city_chunks");
                statement.executeUpdate("DROP TABLE city_chunks");
                statement.executeUpdate("ALTER TABLE city_chunks_v2 RENAME TO city_chunks");
                // DROP TABLE 会连带删掉旧索引，重建。
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_chunks_city ON city_chunks(city_id)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_chunks_dimension ON city_chunks(dimension_id)");
            }
        }
    }

    /** v3：持久化孕妇已预约的婴儿床位，避免重启后丢失分娩前置条件。 */
    private static final class CitizenReservedBabyBed implements Migration {
        @Override
        public int version() {
            return 3;
        }

        @Override
        public String description() {
            return "add reserved baby bed to citizens";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE citizens ADD COLUMN reserved_baby_bed_poi_id TEXT");
            }
        }
    }
}
