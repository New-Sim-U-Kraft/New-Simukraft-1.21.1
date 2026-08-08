package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.BuildingPoiDefinition;
import common.cn.kafei.simukraft.building.BuildingPoiInstance;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 已建成建筑的结构仓库。
 * <p>线程约定：写入（{@link #upsert} / {@link #delete}）经建筑库的写线程执行，可在任意非写线程调用；
 * 读取（{@link #loadByDimension}）借池化连接，调用方通常在服务器主线程。
 */
@SuppressWarnings("null")
public final class BuildingStructureRepository {
    private final BuildingStructureSqliteDatabase database;

    public BuildingStructureRepository(BuildingStructureSqliteDatabase database) {
        this.database = database;
    }

    /**
     * WriteOutcome: 一次写入的三种结局。
     * <p>{@link #FAILED} 与 {@link #STORAGE_UNAVAILABLE} 必须分开：前者是这一条写入出了问题，
     * 调用方不应更新内存缓存（避免内存与磁盘静默分叉）；后者是整库已降级或已关闭，是**整会话**状态，
     * 若也按失败作废，降级之后所有新建成的建筑都会彻底不生效（无 POI、无住房、无产线绑定）。
     */
    public enum WriteOutcome {
        PERSISTED,
        FAILED,
        STORAGE_UNAVAILABLE
    }

    /**
     * upsert: 异步保存建筑结构，写入提交到写队列后立即返回，不阻塞调用线程。
     * 写入失败由写线程记录日志；调用方可安全更新内存缓存而无需等待落库确认。
     *
     * @return 见 {@link WriteOutcome}
     */
    public WriteOutcome upsert(PlacedBuildingRecord record) {
        if (database.isWriteBlocked()) {
            return WriteOutcome.STORAGE_UNAVAILABLE;
        }
        database.submitAsync("placed_building:" + record.buildingId(),
                connection -> saveBuilding(connection, record));
        return WriteOutcome.PERSISTED;
    }

    /**
     * loadByDimension: 读取一个维度的全部已建成建筑。
     * <p>方块与 POI 都用一次带 JOIN 的查询批量取回后在内存分组。旧实现对每座建筑另发 3 条子查询
     * （其中 POI 定义和 POI 实例是完全相同的 SQL，被查了两遍），200 座建筑就是 601 次查询。
     *
     * @return 成功时返回建筑列表（可能为空）；加载失败返回 null 并把建筑库标记为降级，
     *         调用方不得缓存失败结果，留待下次访问重试
     */
    public List<PlacedBuildingRecord> loadByDimension(String dimensionId) {
        List<PlacedBuildingRecord> result = new ArrayList<>();
        try (Connection connection = database.borrowConnection()) {
            Map<UUID, List<BuildingBlockData>> blocksByBuilding = loadBlocksByDimension(connection, dimensionId);
            Map<UUID, List<BuildingPoiDefinition>> poiDefsByBuilding = new HashMap<>();
            Map<UUID, List<BuildingPoiInstance>> poiInstancesByBuilding = new HashMap<>();
            loadPoisByDimension(connection, dimensionId, poiDefsByBuilding, poiInstancesByBuilding);
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM placed_buildings WHERE dimension_id = ? ORDER BY completed_at")) {
                statement.setString(1, dimensionId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String buildingIdText = resultSet.getString("building_id");
                        try {
                            UUID buildingId = UUID.fromString(buildingIdText);
                            result.add(new PlacedBuildingRecord(
                                    buildingId,
                                    nullableUuid(resultSet.getString("city_id")),
                                    resultSet.getString("dimension_id"),
                                    resultSet.getString("category"),
                                    resultSet.getString("building_file_name"),
                                    resultSet.getString("display_name"),
                                    resolveAmount(resultSet.getString("amount"), resultSet.getString("category"), resultSet.getString("building_file_name")),
                                    resultSet.getString("structure_file_name"),
                                    resultSet.getString("facing"),
                                    new BlockPos(resultSet.getInt("origin_x"), resultSet.getInt("origin_y"), resultSet.getInt("origin_z")),
                                    new BlockPos(resultSet.getInt("anchor_x"), resultSet.getInt("anchor_y"), resultSet.getInt("anchor_z")),
                                    new BlockPos(resultSet.getInt("min_x"), resultSet.getInt("min_y"), resultSet.getInt("min_z")),
                                    new BlockPos(resultSet.getInt("max_x"), resultSet.getInt("max_y"), resultSet.getInt("max_z")),
                                    resultSet.getLong("completed_at"),
                                    List.copyOf(blocksByBuilding.getOrDefault(buildingId, List.of())),
                                    List.copyOf(poiDefsByBuilding.getOrDefault(buildingId, List.of())),
                                    List.copyOf(poiInstancesByBuilding.getOrDefault(buildingId, List.of())),
                                    List.of(),
                                    List.of()
                            ));
                        } catch (RuntimeException exception) {
                            // 单行脏数据不能拖垮整个维度的加载：跳过该行并留痕。
                            SimuKraft.LOGGER.warn("Skipping corrupted placed building row (building_id={})", buildingIdText, exception);
                        }
                    }
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {

            database.markDegraded("loadByDimension(placedBuildings)", exception);
            SimuKraft.LOGGER.error("Failed to load placed building structures", exception);
            return null;
        }
        return List.copyOf(result);
    }

    private Map<UUID, List<BuildingBlockData>> loadBlocksByDimension(Connection connection, String dimensionId) throws SQLException {
        Map<UUID, List<BuildingBlockData>> blocksByBuilding = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT b.building_id, b.relative_x, b.relative_y, b.relative_z, b.block_id, b.block_state_nbt, b.original_x, b.original_y, b.original_z "
                        + "FROM placed_building_blocks b JOIN placed_buildings p ON p.building_id = b.building_id "
                        + "WHERE p.dimension_id = ? ORDER BY b.building_id, b.relative_y, b.relative_x, b.relative_z")) {
            statement.setString(1, dimensionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String buildingIdText = resultSet.getString("building_id");
                    String blockId = resultSet.getString("block_id");
                    try {
                        BlockState state = decodeBlockState(resultSet.getString("block_state_nbt"), blockId);
                        if (state == null) {
                            SimuKraft.LOGGER.warn("Skipping undecodable placed building block row (building_id={}, block_id={})", buildingIdText, blockId);
                            continue;
                        }
                        blocksByBuilding.computeIfAbsent(UUID.fromString(buildingIdText), key -> new ArrayList<>())
                                .add(new BuildingBlockData(
                                        new BlockPos(resultSet.getInt("relative_x"), resultSet.getInt("relative_y"), resultSet.getInt("relative_z")),
                                        state,
                                        new BlockPos(resultSet.getInt("original_x"), resultSet.getInt("original_y"), resultSet.getInt("original_z"))
                                ));
                    } catch (RuntimeException exception) {
                        SimuKraft.LOGGER.warn("Skipping corrupted placed building block row (building_id={}, block_id={})", buildingIdText, blockId, exception);
                    }
                }
            }
        }
        return blocksByBuilding;
    }

    // loadPoisByDimension：POI 定义与 POI 实例来自同一批行，一次查询同时填两个映射。
    private void loadPoisByDimension(Connection connection, String dimensionId,
                                     Map<UUID, List<BuildingPoiDefinition>> definitions,
                                     Map<UUID, List<BuildingPoiInstance>> instances) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT o.building_id, o.poi_key, o.poi_type, o.capacity, o.world_x, o.world_y, o.world_z "
                        + "FROM placed_building_pois o JOIN placed_buildings p ON p.building_id = o.building_id "
                        + "WHERE p.dimension_id = ? ORDER BY o.building_id, o.poi_key")) {
            statement.setString(1, dimensionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String buildingIdText = resultSet.getString("building_id");
                    try {
                        UUID buildingId = UUID.fromString(buildingIdText);
                        String poiKey = resultSet.getString("poi_key");
                        CityPoiType poiType = CityPoiType.fromName(resultSet.getString("poi_type"));
                        int capacity = resultSet.getInt("capacity");
                        definitions.computeIfAbsent(buildingId, key -> new ArrayList<>())
                                .add(new BuildingPoiDefinition(poiKey, poiType, capacity));
                        instances.computeIfAbsent(buildingId, key -> new ArrayList<>())
                                .add(new BuildingPoiInstance(poiKey, poiType, capacity,
                                        new BlockPos(resultSet.getInt("world_x"), resultSet.getInt("world_y"), resultSet.getInt("world_z"))));
                    } catch (RuntimeException exception) {
                        SimuKraft.LOGGER.warn("Skipping corrupted placed building POI row (building_id={})", buildingIdText, exception);
                    }
                }
            }
        }
    }

    /** delete: 删除建筑结构及其方块与 POI；结局语义同 {@link #upsert}。 */
    public WriteOutcome delete(UUID buildingId) {
        if (buildingId == null) {
            return WriteOutcome.PERSISTED;
        }
        if (database.isWriteBlocked()) {
            return WriteOutcome.STORAGE_UNAVAILABLE;
        }
        Boolean deleted = database.callSync(connection -> {
            deleteBuilding(connection, buildingId);
            return true;
        });
        if (deleted != null && deleted) {
            return WriteOutcome.PERSISTED;
        }
        if (database.isWriteBlocked()) {
            return WriteOutcome.STORAGE_UNAVAILABLE;
        }
        SimuKraft.LOGGER.error("Failed to delete placed building structure {}", buildingId);
        return WriteOutcome.FAILED;
    }

    private void deleteBuilding(Connection connection, UUID buildingId) throws SQLException {
        try (PreparedStatement deleteBlocks = connection.prepareStatement("DELETE FROM placed_building_blocks WHERE building_id = ?");
             PreparedStatement deletePois = connection.prepareStatement("DELETE FROM placed_building_pois WHERE building_id = ?");
             PreparedStatement deleteBuilding = connection.prepareStatement("DELETE FROM placed_buildings WHERE building_id = ?")) {
            String id = buildingId.toString();
            deleteBlocks.setString(1, id);
            deleteBlocks.executeUpdate();
            deletePois.setString(1, id);
            deletePois.executeUpdate();
            deleteBuilding.setString(1, id);
            deleteBuilding.executeUpdate();
        }
    }

    private void saveBuilding(Connection connection, PlacedBuildingRecord record) throws SQLException {
        try (PreparedStatement buildingStatement = connection.prepareStatement("INSERT INTO placed_buildings(building_id, city_id, dimension_id, category, building_file_name, display_name, amount, structure_file_name, facing, origin_x, origin_y, origin_z, anchor_x, anchor_y, anchor_z, min_x, min_y, min_z, max_x, max_y, max_z, completed_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(building_id) DO UPDATE SET city_id = excluded.city_id, dimension_id = excluded.dimension_id, category = excluded.category, building_file_name = excluded.building_file_name, display_name = excluded.display_name, amount = excluded.amount, structure_file_name = excluded.structure_file_name, facing = excluded.facing, origin_x = excluded.origin_x, origin_y = excluded.origin_y, origin_z = excluded.origin_z, anchor_x = excluded.anchor_x, anchor_y = excluded.anchor_y, anchor_z = excluded.anchor_z, min_x = excluded.min_x, min_y = excluded.min_y, min_z = excluded.min_z, max_x = excluded.max_x, max_y = excluded.max_y, max_z = excluded.max_z, completed_at = excluded.completed_at");
             PreparedStatement deleteBlocks = connection.prepareStatement("DELETE FROM placed_building_blocks WHERE building_id = ?");
             PreparedStatement deletePois = connection.prepareStatement("DELETE FROM placed_building_pois WHERE building_id = ?");
             PreparedStatement blockStatement = connection.prepareStatement("INSERT INTO placed_building_blocks(building_id, relative_x, relative_y, relative_z, block_id, block_state_nbt, original_x, original_y, original_z) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)");
             PreparedStatement poiStatement = connection.prepareStatement("INSERT INTO placed_building_pois(building_id, poi_key, poi_type, capacity, world_x, world_y, world_z) VALUES(?, ?, ?, ?, ?, ?, ?)") ) {
            buildingStatement.setString(1, record.buildingId().toString());
            SqliteNbtHelper.setNullableString(buildingStatement, 2, record.cityId() != null ? record.cityId().toString() : null);
            buildingStatement.setString(3, record.dimensionId());
            buildingStatement.setString(4, record.category());
            buildingStatement.setString(5, record.buildingFileName());
            buildingStatement.setString(6, record.displayName());
            buildingStatement.setString(7, record.amount());
            buildingStatement.setString(8, record.structureFileName());
            buildingStatement.setString(9, record.facing());
            buildingStatement.setInt(10, record.worldOrigin().getX());
            buildingStatement.setInt(11, record.worldOrigin().getY());
            buildingStatement.setInt(12, record.worldOrigin().getZ());
            buildingStatement.setInt(13, record.structureAnchor().getX());
            buildingStatement.setInt(14, record.structureAnchor().getY());
            buildingStatement.setInt(15, record.structureAnchor().getZ());
            buildingStatement.setInt(16, record.minPos().getX());
            buildingStatement.setInt(17, record.minPos().getY());
            buildingStatement.setInt(18, record.minPos().getZ());
            buildingStatement.setInt(19, record.maxPos().getX());
            buildingStatement.setInt(20, record.maxPos().getY());
            buildingStatement.setInt(21, record.maxPos().getZ());
            buildingStatement.setLong(22, record.completedAt());
            buildingStatement.executeUpdate();

            deleteBlocks.setString(1, record.buildingId().toString());
            deleteBlocks.executeUpdate();
            deletePois.setString(1, record.buildingId().toString());
            deletePois.executeUpdate();

            for (BuildingBlockData block : record.blocks()) {
                Block blockType = block.state().getBlock();
                blockStatement.setString(1, record.buildingId().toString());
                blockStatement.setInt(2, block.relativePos().getX());
                blockStatement.setInt(3, block.relativePos().getY());
                blockStatement.setInt(4, block.relativePos().getZ());
                blockStatement.setString(5, BuiltInRegistries.BLOCK.getKey(blockType).toString());
                blockStatement.setString(6, encodeBlockState(block.state()));
                blockStatement.setInt(7, block.originalStructurePos().getX());
                blockStatement.setInt(8, block.originalStructurePos().getY());
                blockStatement.setInt(9, block.originalStructurePos().getZ());
                blockStatement.addBatch();
            }
            blockStatement.executeBatch();

            for (BuildingPoiInstance poi : record.poiInstances()) {
                poiStatement.setString(1, record.buildingId().toString());
                poiStatement.setString(2, poi.key());
                poiStatement.setString(3, poi.poiType().name());
                poiStatement.setInt(4, poi.capacity());
                poiStatement.setInt(5, poi.worldPos().getX());
                poiStatement.setInt(6, poi.worldPos().getY());
                poiStatement.setInt(7, poi.worldPos().getZ());
                poiStatement.addBatch();
            }
            poiStatement.executeBatch();
        }
    }

    private static String encodeBlockState(BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        CompoundTag properties = new CompoundTag();
        for (Property<?> property : state.getProperties()) {
            properties.putString(property.getName(), state.getValue(property).toString());
        }
        tag.put("Properties", properties);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(output)) {
            NbtIo.write(tag, data);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException ioException) {
            // 存入空串会在读取时回退成默认状态，方块属性静默丢失，必须留痕。
            SimuKraft.LOGGER.warn("Failed to encode block state for {}; its properties will be lost", BuiltInRegistries.BLOCK.getKey(state.getBlock()), ioException);
            return "";
        }
    }

    private static BlockState decodeBlockState(String encoded, String blockId) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            var tag = NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes)));
            if (tag == null) {
                return null;
            }
            String name = tag.getString("Name");
            Block block = BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.parse(name)).orElse(null);
            if (block == null) {
                return null;
            }
            BlockState state = block.defaultBlockState();
            if (tag.contains("Properties", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                CompoundTag properties = tag.getCompound("Properties");
                for (String key : properties.getAllKeys()) {
                    Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                    if (property != null) {
                        state = applyProperty(state, property, properties.getString(key));
                    }
                }
            }
            return state;
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Failed to decode stored block state for {}; falling back to its default state", blockId, exception);
            Block block = BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.parse(blockId)).orElse(null);
            return block != null ? block.defaultBlockState() : null;
        }
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private static UUID nullableUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static String resolveAmount(String storedAmount, String category, String buildingFileName) {
        if (storedAmount != null && !storedAmount.isBlank()) {
            return storedAmount;
        }
        return BuildingCatalog.findBuilding(category, buildingFileName)
                .map(BuildingCatalog.BuildingDefinition::amount)
                .orElse("");
    }
}
