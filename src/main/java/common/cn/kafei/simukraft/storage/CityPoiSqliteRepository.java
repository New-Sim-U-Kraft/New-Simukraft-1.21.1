package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@SuppressWarnings("null")
public final class CityPoiSqliteRepository {
    private final SimuSqliteDatabase database;

    public CityPoiSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /**
     * saveAll: 把内存中的 POI 全部 upsert 进库。
     * <p>不再 "DELETE WHERE dimension_id" 再重写。旧实现在 POI 尚未加载完成（异步加载）时执行整表覆盖，
     * 会把整个维度的 POI 清空。POI 的删除只走 {@link #delete} / {@link #deleteCity}。
     */
    public void saveAll(Connection connection, CompoundTag tag, String dimensionId) throws SQLException {
        ListTag pois = tag.getList("Pois", CompoundTag.TAG_COMPOUND);
        if (!pois.isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO city_pois(poi_id, dimension_id, city_id, pos_long, type, capacity, active, unit_id) VALUES(?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(poi_id) DO UPDATE SET dimension_id = excluded.dimension_id, city_id = excluded.city_id, pos_long = excluded.pos_long, type = excluded.type, capacity = excluded.capacity, active = excluded.active, unit_id = excluded.unit_id")) {
                for (int i = 0; i < pois.size(); i++) {
                    CompoundTag poi = pois.getCompound(i);
                    statement.setString(1, poi.getUUID("PoiId").toString());
                    statement.setString(2, normalizeDimensionId(dimensionId));
                    statement.setString(3, poi.getUUID("CityId").toString());
                    statement.setLong(4, poi.getLong("Pos"));
                    statement.setString(5, poi.getString("Type"));
                    statement.setInt(6, poi.getInt("Capacity"));
                    statement.setInt(7, poi.getBoolean("Active") ? 1 : 0);
                    SqliteNbtHelper.setNullableString(statement, 8, poi.hasUUID("UnitId") ? poi.getUUID("UnitId").toString() : null);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }

    public void upsert(Connection connection, CompoundTag poiTag, String dimensionId) throws SQLException {
        savePoi(connection, poiTag, normalizeDimensionId(dimensionId));
    }

    /**
     * delete: 删除单个 POI。
     * <p>{@link #saveAll} 不再按维度清表重写，所以内存里丢弃一个 poiId 时必须显式删库：
     * 残留行会在下次进档被 {@link #loadAll} 读回，和替换它的新 POI 一起进城市索引，让容量统计双计。
     */
    public void delete(Connection connection, java.util.UUID poiId) throws SQLException {
        if (poiId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM city_pois WHERE poi_id = ?")) {
            statement.setString(1, poiId.toString());
            statement.executeUpdate();
        }
    }

    public void deleteCity(Connection connection, java.util.UUID cityId) throws SQLException {
        if (cityId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM city_pois WHERE city_id = ?")) {
            statement.setString(1, cityId.toString());
            statement.executeUpdate();
        }
    }

    public synchronized CompoundTag loadAll(String dimensionId) {
        CompoundTag tag = new CompoundTag();
        ListTag pois = new ListTag();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM city_pois WHERE dimension_id = ? ORDER BY poi_id")) {
            statement.setString(1, normalizeDimensionId(dimensionId));
            try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                CompoundTag poi = new CompoundTag();
                poi.putUUID("PoiId", java.util.UUID.fromString(resultSet.getString("poi_id")));
                poi.putUUID("CityId", java.util.UUID.fromString(resultSet.getString("city_id")));
                poi.putLong("Pos", resultSet.getLong("pos_long"));
                poi.putString("Type", resultSet.getString("type"));
                poi.putInt("Capacity", resultSet.getInt("capacity"));
                poi.putBoolean("Active", resultSet.getInt("active") != 0);
                SqliteNbtHelper.putNullableUuid(poi, "UnitId", resultSet.getString("unit_id"));
                pois.add(poi);
            }
            }
            tag.put("Pois", pois);
            return pois.isEmpty() ? null : tag;
        } catch (SQLException | IllegalArgumentException exception) {
            database.markDegraded("loadAll(cityPois)", exception);
            SimuKraft.LOGGER.error("Failed to load city POIs from SQLite", exception);
            return null;
        }
    }

    private void savePoi(Connection connection, CompoundTag poi, String dimensionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO city_pois(poi_id, dimension_id, city_id, pos_long, type, capacity, active, unit_id) VALUES(?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(poi_id) DO UPDATE SET dimension_id = excluded.dimension_id, city_id = excluded.city_id, pos_long = excluded.pos_long, type = excluded.type, capacity = excluded.capacity, active = excluded.active, unit_id = excluded.unit_id")) {
            statement.setString(1, poi.getUUID("PoiId").toString());
            statement.setString(2, dimensionId);
            statement.setString(3, poi.getUUID("CityId").toString());
            statement.setLong(4, poi.getLong("Pos"));
            statement.setString(5, poi.getString("Type"));
            statement.setInt(6, poi.getInt("Capacity"));
            statement.setInt(7, poi.getBoolean("Active") ? 1 : 0);
            SqliteNbtHelper.setNullableString(statement, 8, poi.hasUUID("UnitId") ? poi.getUUID("UnitId").toString() : null);
            statement.executeUpdate();
        }
    }

    private static String normalizeDimensionId(String dimensionId) {
        return dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
    }
}
