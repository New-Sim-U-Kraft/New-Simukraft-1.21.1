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
        return List.of(
                new CityChunksDimensionPrimaryKey(),
                new CitizenReservedBabyBed(),
                new CommercialBoxesDimensionPrimaryKey(),
                new CitizenLastHospitalProgressDayTime());
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

    /**
     * v4：给商业箱 / 商业库存补上 {@code dimension_id} 并改成复合主键。
     *
     * <p>这两张表原先只有 {@code box_pos_long}。管理器按维度加载，tick 时若坐标处不是本维度的
     * 控制箱就会删库。跨维度同坐标或区块卸载时，会把别的维度商店一并抹掉。
     */
    private static final class CommercialBoxesDimensionPrimaryKey implements Migration {
        @Override
        public int version() {
            return 4;
        }

        @Override
        public String description() {
            return "rebuild commercial boxes and stock with dimension_id in the primary key";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE commercial_boxes_v4("
                        + "dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', "
                        + "box_pos_long INTEGER NOT NULL, "
                        + "building_id TEXT NOT NULL DEFAULT '', "
                        + "definition_id TEXT NOT NULL DEFAULT '', "
                        + "running INTEGER NOT NULL DEFAULT 1, "
                        + "status_key TEXT NOT NULL DEFAULT '', "
                        + "status_text TEXT NOT NULL DEFAULT '', "
                        + "updated_at INTEGER NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY(dimension_id, box_pos_long))");
                statement.executeUpdate("INSERT OR IGNORE INTO commercial_boxes_v4("
                        + "dimension_id, box_pos_long, building_id, definition_id, running, status_key, status_text, updated_at) "
                        + "SELECT 'minecraft:overworld', box_pos_long, building_id, definition_id, running, status_key, status_text, updated_at "
                        + "FROM commercial_boxes");
                statement.executeUpdate("DROP TABLE commercial_boxes");
                statement.executeUpdate("ALTER TABLE commercial_boxes_v4 RENAME TO commercial_boxes");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_commercial_boxes_running ON commercial_boxes(dimension_id, running)");

                statement.executeUpdate("CREATE TABLE commercial_stock_v4("
                        + "dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', "
                        + "box_pos_long INTEGER NOT NULL, "
                        + "item_id TEXT NOT NULL, "
                        + "current_stock INTEGER NOT NULL DEFAULT 0, "
                        + "max_stock INTEGER NOT NULL DEFAULT 0, "
                        + "last_restock_game_time INTEGER NOT NULL DEFAULT 0, "
                        + "updated_at INTEGER NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY(dimension_id, box_pos_long, item_id))");
                statement.executeUpdate("INSERT OR IGNORE INTO commercial_stock_v4("
                        + "dimension_id, box_pos_long, item_id, current_stock, max_stock, last_restock_game_time, updated_at) "
                        + "SELECT 'minecraft:overworld', box_pos_long, item_id, current_stock, max_stock, last_restock_game_time, updated_at "
                        + "FROM commercial_stock");
                statement.executeUpdate("DROP TABLE commercial_stock");
                statement.executeUpdate("ALTER TABLE commercial_stock_v4 RENAME TO commercial_stock");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_commercial_stock_box ON commercial_stock(dimension_id, box_pos_long)");
            }
        }
    }

    /** v5：记下住院治疗的世界时间锚点，睡觉跳过的区间才能在重启后继续结算。 */
    private static final class CitizenLastHospitalProgressDayTime implements Migration {
        @Override
        public int version() {
            return 5;
        }

        @Override
        public String description() {
            return "add last hospital progress day time to citizens";
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "ALTER TABLE citizens ADD COLUMN last_hospital_progress_day_time INTEGER NOT NULL DEFAULT 0");
            }
        }
    }
}
