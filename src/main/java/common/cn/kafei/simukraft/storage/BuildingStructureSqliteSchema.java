package common.cn.kafei.simukraft.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class BuildingStructureSqliteSchema {
    private BuildingStructureSqliteSchema() {
    }

    public static void initialize(BuildingStructureSqliteDatabase database) {
        try (Connection connection = database.borrowConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS placed_buildings(building_id TEXT PRIMARY KEY, city_id TEXT, dimension_id TEXT NOT NULL, category TEXT NOT NULL, building_file_name TEXT NOT NULL, display_name TEXT NOT NULL, amount TEXT NOT NULL DEFAULT '', structure_file_name TEXT NOT NULL, facing TEXT NOT NULL, origin_x INTEGER NOT NULL, origin_y INTEGER NOT NULL, origin_z INTEGER NOT NULL, anchor_x INTEGER NOT NULL, anchor_y INTEGER NOT NULL, anchor_z INTEGER NOT NULL, min_x INTEGER NOT NULL, min_y INTEGER NOT NULL, min_z INTEGER NOT NULL, max_x INTEGER NOT NULL, max_y INTEGER NOT NULL, max_z INTEGER NOT NULL, completed_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS placed_building_blocks(building_id TEXT NOT NULL, relative_x INTEGER NOT NULL, relative_y INTEGER NOT NULL, relative_z INTEGER NOT NULL, block_id TEXT NOT NULL, block_state_nbt TEXT NOT NULL, original_x INTEGER NOT NULL, original_y INTEGER NOT NULL, original_z INTEGER NOT NULL, PRIMARY KEY(building_id, relative_x, relative_y, relative_z), FOREIGN KEY(building_id) REFERENCES placed_buildings(building_id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS placed_building_pois(building_id TEXT NOT NULL, poi_key TEXT NOT NULL, poi_type TEXT NOT NULL, capacity INTEGER NOT NULL, world_x INTEGER NOT NULL, world_y INTEGER NOT NULL, world_z INTEGER NOT NULL, PRIMARY KEY(building_id, poi_key), FOREIGN KEY(building_id) REFERENCES placed_buildings(building_id) ON DELETE CASCADE)");
            addColumnIfMissing(connection, "placed_buildings", "amount", "TEXT NOT NULL DEFAULT ''");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_placed_buildings_city ON placed_buildings(city_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_placed_buildings_dimension ON placed_buildings(dimension_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_placed_blocks_building ON placed_building_blocks(building_id)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize building structure SQLite database", exception);
        }
    }

    private static void addColumnIfMissing(Connection connection, String tableName, String columnName, String columnDefinition) throws SQLException {
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }
}
