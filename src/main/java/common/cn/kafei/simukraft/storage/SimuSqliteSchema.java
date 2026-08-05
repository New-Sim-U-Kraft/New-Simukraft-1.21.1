package common.cn.kafei.simukraft.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 基线 schema（{@code user_version = 1}）。
 *
 * <p>必须保持幂等：全新库和所有历史存档都会执行它一次。基线之上的结构变更（改主键、补维度列等）
 * 走 {@link common.cn.kafei.simukraft.storage.core.SchemaMigrator} 的版本化迁移，不要再往这里加
 * {@code addColumnIfMissing}。
 */
public final class SimuSqliteSchema {
    private static final String VIRTUAL_VEIN_FIELD_TABLE = "virtual_vein_fields";
    private static final String LEGACY_VIRTUAL_VEIN_FIELD_TABLE = "virtual_vein_fields_legacy_v3";

    private SimuSqliteSchema() {
    }

    public static void createBaseline(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS cities(city_id TEXT PRIMARY KEY, city_name TEXT NOT NULL, dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', core_x INTEGER NOT NULL, core_y INTEGER NOT NULL, core_z INTEGER NOT NULL, funds REAL NOT NULL, city_level INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS city_members(city_id TEXT NOT NULL, player_id TEXT NOT NULL, player_name TEXT NOT NULL, permission_level TEXT NOT NULL, PRIMARY KEY(city_id, player_id), FOREIGN KEY(city_id) REFERENCES cities(city_id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS finance_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, city_id TEXT NOT NULL, sort_index INTEGER NOT NULL, time INTEGER NOT NULL, actor_id TEXT, actor_name TEXT NOT NULL, amount REAL NOT NULL, balance_after REAL NOT NULL, type TEXT NOT NULL, reason TEXT NOT NULL, FOREIGN KEY(city_id) REFERENCES cities(city_id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS city_chunks(city_id TEXT NOT NULL, chunk_long INTEGER NOT NULL, PRIMARY KEY(city_id, chunk_long))");
            ensureVirtualVeinFieldTable(connection);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS city_pois(poi_id TEXT PRIMARY KEY, dimension_id TEXT NOT NULL DEFAULT 'minecraft:overworld', city_id TEXT NOT NULL, pos_long INTEGER NOT NULL, type TEXT NOT NULL, capacity INTEGER NOT NULL, active INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS citizens(uuid TEXT PRIMARY KEY, name TEXT NOT NULL, gender TEXT NOT NULL, age INTEGER NOT NULL, lifespan INTEGER NOT NULL, job_type TEXT NOT NULL, job_id TEXT NOT NULL, status TEXT NOT NULL, work_status TEXT NOT NULL, work_need_detail TEXT NOT NULL, status_label TEXT NOT NULL, is_working INTEGER NOT NULL, npc_id INTEGER NOT NULL, skin_path TEXT NOT NULL, city_id TEXT, home_id TEXT, workplace_id TEXT, workplace_pos_long INTEGER, health REAL NOT NULL, happiness REAL NOT NULL, sick INTEGER NOT NULL, child INTEGER NOT NULL, child_growth_due_day INTEGER NOT NULL, born_day INTEGER NOT NULL, last_age_growth_day INTEGER NOT NULL DEFAULT -1, disease_id TEXT NOT NULL DEFAULT 'NONE', disease_since_day INTEGER NOT NULL DEFAULT 0, disease_treatment_ticks INTEGER NOT NULL DEFAULT 0, medical_bed_poi_id TEXT, postpartum_until_day INTEGER NOT NULL DEFAULT 0, last_hospital_meal_day INTEGER NOT NULL DEFAULT -1)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS citizen_skills(citizen_id TEXT NOT NULL, skill_key TEXT NOT NULL, skill_value INTEGER NOT NULL, PRIMARY KEY(citizen_id, skill_key), FOREIGN KEY(citizen_id) REFERENCES citizens(uuid) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS building_tasks(task_id TEXT PRIMARY KEY, citizen_id TEXT NOT NULL UNIQUE, city_id TEXT, dimension_id TEXT NOT NULL, build_box_x INTEGER NOT NULL, build_box_y INTEGER NOT NULL, build_box_z INTEGER NOT NULL, category TEXT NOT NULL, building_file_name TEXT NOT NULL, display_name TEXT NOT NULL, amount TEXT NOT NULL DEFAULT '', structure_file_name TEXT NOT NULL, origin_x INTEGER NOT NULL, origin_y INTEGER NOT NULL, origin_z INTEGER NOT NULL, rotation_degrees INTEGER NOT NULL, current_block_index INTEGER NOT NULL, total_blocks INTEGER NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS building_task_pois(task_id TEXT NOT NULL, poi_key TEXT NOT NULL, poi_type TEXT NOT NULL, capacity INTEGER NOT NULL, PRIMARY KEY(task_id, poi_key), FOREIGN KEY(task_id) REFERENCES building_tasks(task_id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS farmland_boxes(box_pos_long INTEGER PRIMARY KEY, crop TEXT, plot_min_long INTEGER, plot_max_long INTEGER, chest_pos_long INTEGER, running INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS planning_tasks(task_id TEXT PRIMARY KEY, citizen_id TEXT NOT NULL UNIQUE, city_id TEXT, dimension_id TEXT NOT NULL, box_long INTEGER NOT NULL, min_long INTEGER NOT NULL, max_long INTEGER NOT NULL, operation TEXT NOT NULL, fill_block TEXT, source_block TEXT, material_chest_long INTEGER, replacement_map TEXT NOT NULL DEFAULT '', current_index INTEGER NOT NULL, total_blocks INTEGER NOT NULL, completed_blocks INTEGER NOT NULL DEFAULT 0, target_blocks INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS industrial_boxes(box_pos_long INTEGER PRIMARY KEY, building_id TEXT NOT NULL DEFAULT '', definition_id TEXT NOT NULL DEFAULT '', selected_recipe_id TEXT NOT NULL DEFAULT '', running INTEGER NOT NULL DEFAULT 0, spawn_entity_done INTEGER NOT NULL DEFAULT 0, current_step INTEGER NOT NULL DEFAULT 0, status_key TEXT NOT NULL DEFAULT '', status_text TEXT NOT NULL DEFAULT '', machine_state TEXT NOT NULL DEFAULT '', work_state TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS mineral_drilling_boxes(dimension_id TEXT NOT NULL, box_pos_long INTEGER NOT NULL, drill_depth INTEGER NOT NULL DEFAULT 0, lowest_reached_depth INTEGER NOT NULL DEFAULT 2147483647, running INTEGER NOT NULL DEFAULT 0, status_key TEXT NOT NULL DEFAULT '', status_text TEXT NOT NULL DEFAULT '', selected_vein_id TEXT NOT NULL DEFAULT '', inventory_nbt TEXT NOT NULL DEFAULT '{}', updated_at INTEGER NOT NULL DEFAULT 0, revision INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(dimension_id, box_pos_long))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS commercial_boxes(box_pos_long INTEGER PRIMARY KEY, building_id TEXT NOT NULL DEFAULT '', definition_id TEXT NOT NULL DEFAULT '', running INTEGER NOT NULL DEFAULT 1, status_key TEXT NOT NULL DEFAULT '', status_text TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS commercial_stock(box_pos_long INTEGER NOT NULL, item_id TEXT NOT NULL, current_stock INTEGER NOT NULL DEFAULT 0, max_stock INTEGER NOT NULL DEFAULT 0, last_restock_game_time INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(box_pos_long, item_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS commercial_daily_income(city_id TEXT NOT NULL, income_day INTEGER NOT NULL, income REAL NOT NULL DEFAULT 0, tax_collected INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(city_id, income_day), FOREIGN KEY(city_id) REFERENCES cities(city_id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS logistics_warehouses(warehouse_id TEXT PRIMARY KEY, box_pos_long INTEGER NOT NULL, city_id TEXT, dimension_id TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL DEFAULT 0, UNIQUE(dimension_id, box_pos_long))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS logistics_clients(client_id TEXT PRIMARY KEY, box_pos_long INTEGER NOT NULL, city_id TEXT, dimension_id TEXT NOT NULL DEFAULT '', name TEXT NOT NULL DEFAULT '', automatic INTEGER NOT NULL DEFAULT 0, source_type TEXT NOT NULL DEFAULT '', source_id TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS logistics_ports(owner_id TEXT NOT NULL, owner_type TEXT NOT NULL, port_id TEXT NOT NULL, name TEXT NOT NULL DEFAULT '', kind TEXT NOT NULL DEFAULT '', pos_long INTEGER NOT NULL, PRIMARY KEY(owner_id, owner_type, port_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS logistics_channels(channel_id TEXT PRIMARY KEY, warehouse_id TEXT NOT NULL, client_id TEXT NOT NULL, direction TEXT NOT NULL, name TEXT NOT NULL DEFAULT '', enabled INTEGER NOT NULL DEFAULT 1, filters TEXT NOT NULL DEFAULT '[]', updated_at INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS families(family_id TEXT PRIMARY KEY, city_id TEXT NOT NULL, husband_id TEXT, wife_id TEXT, paternal_family_id TEXT, maternal_family_id TEXT, generation INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'FORMING')");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS family_members(family_id TEXT NOT NULL, citizen_id TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY(family_id, citizen_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS building_abandonment(building_id TEXT PRIMARY KEY, city_id TEXT NOT NULL, abandonment_index INTEGER NOT NULL DEFAULT 0, last_tick_day INTEGER NOT NULL DEFAULT 0)");
            dropColumnIfPresent(connection, "citizens", "hunger");
            addColumnIfMissing(connection, "cities", "city_level", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "cities", "dimension_id", "TEXT NOT NULL DEFAULT 'minecraft:overworld'");
            addColumnIfMissing(connection, "city_pois", "dimension_id", "TEXT NOT NULL DEFAULT 'minecraft:overworld'");
            addColumnIfMissing(connection, "citizens", "workplace_pos_long", "INTEGER");
            addColumnIfMissing(connection, "building_tasks", "amount", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "building_tasks", "replace_with_air", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "planning_tasks", "material_chest_long", "INTEGER");
            addColumnIfMissing(connection, "planning_tasks", "replacement_map", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "planning_tasks", "completed_blocks", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "planning_tasks", "target_blocks", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "industrial_boxes", "spawn_entity_done", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "industrial_boxes", "machine_state", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "industrial_boxes", "work_state", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "mineral_drilling_boxes", "lowest_reached_depth", "INTEGER NOT NULL DEFAULT 2147483647");
            // 物流表是后续版本新增的，旧存档已有表时需要补齐列，否则端口保存会失败并导致重进丢绑定。
            addColumnIfMissing(connection, "logistics_warehouses", "city_id", "TEXT");
            addColumnIfMissing(connection, "logistics_warehouses", "dimension_id", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "logistics_warehouses", "updated_at", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "logistics_clients", "city_id", "TEXT");
            addColumnIfMissing(connection, "logistics_clients", "dimension_id", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "logistics_clients", "name", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "logistics_clients", "automatic", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "logistics_clients", "source_type", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "logistics_clients", "source_id", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "logistics_clients", "updated_at", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "logistics_ports", "name", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "logistics_ports", "kind", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "logistics_channels", "name", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "logistics_channels", "enabled", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(connection, "logistics_channels", "filters", "TEXT NOT NULL DEFAULT '[]'");
            addColumnIfMissing(connection, "logistics_channels", "updated_at", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "logistics_channels", "keep_quantity", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "logistics_channels", "keep_source", "INTEGER NOT NULL DEFAULT 0");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_members_city ON city_members(city_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_cities_dimension ON cities(dimension_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_finance_city ON finance_transactions(city_id, sort_index)");
            addColumnIfMissing(connection, "citizens", "dimension_id", "TEXT NOT NULL DEFAULT 'minecraft:overworld'");
            addColumnIfMissing(connection, "citizens", "family_id",        "TEXT");
            addColumnIfMissing(connection, "citizens", "origin_family_id", "TEXT");
            addColumnIfMissing(connection, "citizens", "pregnant",         "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "citizens", "pregnant_since",   "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "citizens", "last_age_growth_day", "INTEGER NOT NULL DEFAULT -1");
            addColumnIfMissing(connection, "citizens", "disease_id", "TEXT NOT NULL DEFAULT 'NONE'");
            addColumnIfMissing(connection, "citizens", "disease_since_day", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "citizens", "disease_treatment_ticks", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "citizens", "medical_bed_poi_id", "TEXT");
            addColumnIfMissing(connection, "citizens", "postpartum_until_day", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "citizens", "last_hospital_meal_day", "INTEGER NOT NULL DEFAULT -1");
            addColumnIfMissing(connection, "city_pois", "unit_id", "TEXT");
            addColumnIfMissing(connection, "city_chunks", "dimension_id", "TEXT NOT NULL DEFAULT 'minecraft:overworld'");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_chunks_city ON city_chunks(city_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_chunks_dimension ON city_chunks(dimension_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_pois_city ON city_pois(city_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_pois_dimension ON city_pois(dimension_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_citizens_city ON citizens(city_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_building_tasks_city ON building_tasks(city_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_building_tasks_dimension ON building_tasks(dimension_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_planning_tasks_dimension ON planning_tasks(dimension_id)");
            addColumnIfMissing(connection, "industrial_boxes", "step_elapsed_ticks", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "industrial_boxes", "worker_work_pos_long", "INTEGER NOT NULL DEFAULT " + Long.MIN_VALUE);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_industrial_boxes_running ON industrial_boxes(running)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mineral_drilling_boxes_running ON mineral_drilling_boxes(dimension_id, running)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_commercial_boxes_running ON commercial_boxes(running)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_commercial_stock_box ON commercial_stock(box_pos_long)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_commercial_daily_income_due ON commercial_daily_income(tax_collected, income_day)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logistics_warehouses_city ON logistics_warehouses(city_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logistics_clients_city ON logistics_clients(city_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logistics_ports_owner ON logistics_ports(owner_id, owner_type)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logistics_channels_warehouse ON logistics_channels(warehouse_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logistics_channels_client ON logistics_channels(client_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_families_city ON families(city_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_family_members_citizen ON family_members(citizen_id)");
        }
    }

    /** ensureVirtualVeinFieldTable: 迁移 256 格群系边界矿区所需的复合主键。 */
    private static void ensureVirtualVeinFieldTable(Connection connection) throws SQLException {
        if (!tableExists(connection, VIRTUAL_VEIN_FIELD_TABLE)) {
            try (Statement statement = connection.createStatement()) {
                createVirtualVeinFieldTable(statement, VIRTUAL_VEIN_FIELD_TABLE);
            }
            return;
        }
        if (!hasColumn(connection, VIRTUAL_VEIN_FIELD_TABLE, "field_biome_id")) {
            migrateVirtualVeinFieldTable(connection);
            return;
        }
        try (Statement statement = connection.createStatement()) {
            createVirtualVeinFieldTable(statement, VIRTUAL_VEIN_FIELD_TABLE);
        }
    }

    /** migrateVirtualVeinFieldTable: 保留旧矿区档案并补齐群系复合主键。 */
    private static void migrateVirtualVeinFieldTable(Connection connection) throws SQLException {
        if (tableExists(connection, LEGACY_VIRTUAL_VEIN_FIELD_TABLE)) {
            throw new SQLException("Incomplete virtual vein field migration backup exists");
        }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + VIRTUAL_VEIN_FIELD_TABLE + " RENAME TO " + LEGACY_VIRTUAL_VEIN_FIELD_TABLE);
            createVirtualVeinFieldTable(statement, VIRTUAL_VEIN_FIELD_TABLE);
            statement.executeUpdate("INSERT INTO " + VIRTUAL_VEIN_FIELD_TABLE + "("
                    + "dimension_id, field_cell_x, field_cell_z, field_biome_id, center_x, center_z, center_biome_id, created_game_time, vein_count, generation_version, "
                    + "slot0_vein_id, slot0_display_name, slot0_product_id, slot0_min_y, slot0_max_y, slot0_amount, slot0_period_ticks, slot0_initial_reserve, slot0_remaining_reserve, slot0_state, "
                    + "slot1_vein_id, slot1_display_name, slot1_product_id, slot1_min_y, slot1_max_y, slot1_amount, slot1_period_ticks, slot1_initial_reserve, slot1_remaining_reserve, slot1_state) "
                    + "SELECT dimension_id, field_cell_x, field_cell_z, center_biome_id, center_x, center_z, center_biome_id, created_game_time, vein_count, generation_version, "
                    + "slot0_vein_id, slot0_display_name, slot0_product_id, slot0_min_y, slot0_max_y, slot0_amount, slot0_period_ticks, slot0_initial_reserve, slot0_remaining_reserve, slot0_state, "
                    + "slot1_vein_id, slot1_display_name, slot1_product_id, slot1_min_y, slot1_max_y, slot1_amount, slot1_period_ticks, slot1_initial_reserve, slot1_remaining_reserve, slot1_state "
                    + "FROM " + LEGACY_VIRTUAL_VEIN_FIELD_TABLE);
            statement.executeUpdate("DROP TABLE " + LEGACY_VIRTUAL_VEIN_FIELD_TABLE);
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /** createVirtualVeinFieldTable: 创建当前版本的虚拟矿区档案表。 */
    private static void createVirtualVeinFieldTable(Statement statement, String tableName) throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tableName + "("
                + "dimension_id TEXT NOT NULL, field_cell_x INTEGER NOT NULL, field_cell_z INTEGER NOT NULL, field_biome_id TEXT NOT NULL, center_x INTEGER NOT NULL, center_z INTEGER NOT NULL, center_biome_id TEXT NOT NULL, created_game_time INTEGER NOT NULL, vein_count INTEGER NOT NULL, generation_version INTEGER NOT NULL DEFAULT 3, "
                + "slot0_vein_id TEXT NOT NULL DEFAULT '', slot0_display_name TEXT NOT NULL DEFAULT '', slot0_product_id TEXT NOT NULL DEFAULT '', slot0_min_y INTEGER NOT NULL DEFAULT 0, slot0_max_y INTEGER NOT NULL DEFAULT 0, slot0_amount INTEGER NOT NULL DEFAULT 0, slot0_period_ticks INTEGER NOT NULL DEFAULT 0, slot0_initial_reserve INTEGER NOT NULL DEFAULT 0, slot0_remaining_reserve INTEGER NOT NULL DEFAULT 0, slot0_state TEXT NOT NULL DEFAULT 'EMPTY', "
                + "slot1_vein_id TEXT NOT NULL DEFAULT '', slot1_display_name TEXT NOT NULL DEFAULT '', slot1_product_id TEXT NOT NULL DEFAULT '', slot1_min_y INTEGER NOT NULL DEFAULT 0, slot1_max_y INTEGER NOT NULL DEFAULT 0, slot1_amount INTEGER NOT NULL DEFAULT 0, slot1_period_ticks INTEGER NOT NULL DEFAULT 0, slot1_initial_reserve INTEGER NOT NULL DEFAULT 0, slot1_remaining_reserve INTEGER NOT NULL DEFAULT 0, slot1_state TEXT NOT NULL DEFAULT 'EMPTY', "
                + "PRIMARY KEY(dimension_id, field_cell_x, field_cell_z, field_biome_id))");
    }

    /** tableExists: 判断 SQLite 业务表是否已经存在。 */
    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '" + tableName + "'")) {
            return resultSet.next();
        }
    }

    private static boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
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

    // dropColumnIfPresent：迁移旧表结构，删除已经迁出到实体 NBT 的字段。
    private static void dropColumnIfPresent(Connection connection, String tableName, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    try (Statement dropStatement = connection.createStatement()) {
                        dropStatement.executeUpdate("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
                    }
                    return;
                }
            }
        }
    }
}
