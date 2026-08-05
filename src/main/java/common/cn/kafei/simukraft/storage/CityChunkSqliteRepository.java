package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@SuppressWarnings("null")
public final class CityChunkSqliteRepository {
    private final SimuSqliteDatabase database;

    public CityChunkSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /**
     * saveAll: 把内存中的领地区块全部 upsert 进库。
     * <p>不再 "DELETE WHERE dimension_id" 再重写：取消领地只走 {@link #deleteChunk} / {@link #deleteCity}，
     * 否则加载失败时会把整个维度的领地清空。用 INSERT OR IGNORE 以兼容主键尚未包含 dimension_id 的旧库。
     */
    public void saveAll(Connection connection, CompoundTag tag, String dimensionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO city_chunks(city_id, chunk_long, dimension_id) VALUES(?, ?, ?)")) {
            ListTag cityTags = tag.getList("CityChunks", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < cityTags.size(); i++) {
                CompoundTag cityTag = cityTags.getCompound(i);
                String cityId = cityTag.getUUID("CityId").toString();
                ListTag chunks = cityTag.getList("Chunks", LongTag.TAG_LONG);
                for (int j = 0; j < chunks.size(); j++) {
                    statement.setString(1, cityId);
                    statement.setLong(2, ((LongTag) chunks.get(j)).getAsLong());
                    statement.setString(3, dimensionId);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    public void upsert(Connection connection, UUID cityId, long chunkLong, String dimensionId) throws SQLException {
        if (cityId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO city_chunks(city_id, chunk_long, dimension_id) VALUES(?, ?, ?)")) {
            statement.setString(1, cityId.toString());
            statement.setLong(2, chunkLong);
            statement.setString(3, dimensionId);
            statement.executeUpdate();
        }
    }

    public void deleteChunk(Connection connection, UUID cityId, long chunkLong, String dimensionId) throws SQLException {
        if (cityId == null) return;
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM city_chunks WHERE city_id = ? AND chunk_long = ? AND dimension_id = ?")) {
            statement.setString(1, cityId.toString());
            statement.setLong(2, chunkLong);
            statement.setString(3, dimensionId);
            statement.executeUpdate();
        }
    }

    public void deleteCity(Connection connection, UUID cityId, String dimensionId) throws SQLException {
        if (cityId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM city_chunks WHERE city_id = ? AND dimension_id = ?")) {
            statement.setString(1, cityId.toString());
            statement.setString(2, dimensionId);
            statement.executeUpdate();
        }
    }

    // loadAll：单次查询后在内存里按城市分组，避免"每个城市一次子查询"的 N+1。
    public synchronized CompoundTag loadAll(String dimensionId) {
        CompoundTag tag = new CompoundTag();
        ListTag cityTags = new ListTag();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT city_id, chunk_long FROM city_chunks WHERE dimension_id = ? ORDER BY city_id, chunk_long")) {
            statement.setString(1, dimensionId);
            java.util.Map<String, ListTag> chunksByCity = new java.util.LinkedHashMap<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    chunksByCity.computeIfAbsent(resultSet.getString("city_id"), key -> new ListTag())
                            .add(LongTag.valueOf(resultSet.getLong("chunk_long")));
                }
            }
            chunksByCity.forEach((cityId, chunks) -> {
                CompoundTag cityTag = new CompoundTag();
                cityTag.putUUID("CityId", UUID.fromString(cityId));
                cityTag.put("Chunks", chunks);
                cityTags.add(cityTag);
            });
            tag.put("CityChunks", cityTags);
            return cityTags.isEmpty() ? null : tag;
        } catch (SQLException | IllegalArgumentException exception) {
            database.markDegraded("loadAll(cityChunks)", exception);
            SimuKraft.LOGGER.error("Failed to load city chunks from SQLite", exception);
            return null;
        }
    }
}
