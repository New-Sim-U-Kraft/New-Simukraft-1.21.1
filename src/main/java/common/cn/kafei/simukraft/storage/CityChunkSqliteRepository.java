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

@SuppressWarnings("Null")
public final class CityChunkSqliteRepository {
    private final SimuSqliteDatabase database;

    public CityChunkSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    public synchronized void saveAll(CompoundTag tag, String dimensionId) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement del = connection.prepareStatement("DELETE FROM city_chunks WHERE dimension_id = ?")) {
                del.setString(1, dimensionId);
                del.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO city_chunks(city_id, chunk_long, dimension_id) VALUES(?, ?, ?)")) {
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
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save city chunks to SQLite", exception);
        }
    }

    public synchronized void upsert(UUID cityId, long chunkLong, String dimensionId) {
        if (cityId == null) {
            return;
        }
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO city_chunks(city_id, chunk_long, dimension_id) VALUES(?, ?, ?)")) {
            statement.setString(1, cityId.toString());
            statement.setLong(2, chunkLong);
            statement.setString(3, dimensionId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save city chunk to SQLite", exception);
        }
    }

    public synchronized void deleteChunk(UUID cityId, long chunkLong, String dimensionId) {
        if (cityId == null) return;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM city_chunks WHERE city_id = ? AND chunk_long = ? AND dimension_id = ?")) {
            statement.setString(1, cityId.toString());
            statement.setLong(2, chunkLong);
            statement.setString(3, dimensionId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to delete city chunk from SQLite", exception);
        }
    }

    public synchronized void deleteCity(UUID cityId, String dimensionId) {
        if (cityId == null) {
            return;
        }
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM city_chunks WHERE city_id = ? AND dimension_id = ?")) {
            statement.setString(1, cityId.toString());
            statement.setString(2, dimensionId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to delete city chunks from SQLite", exception);
        }
    }

    public synchronized CompoundTag loadAll(String dimensionId) {
        CompoundTag tag = new CompoundTag();
        ListTag cityTags = new ListTag();
        try (Connection connection = database.openConnection();
             PreparedStatement cityStatement = connection.prepareStatement("SELECT DISTINCT city_id FROM city_chunks WHERE dimension_id = ? ORDER BY city_id")) {
            cityStatement.setString(1, dimensionId);
            try (ResultSet cityResult = cityStatement.executeQuery()) {
                while (cityResult.next()) {
                    String cityId = cityResult.getString("city_id");
                    CompoundTag cityTag = new CompoundTag();
                    cityTag.putUUID("CityId", UUID.fromString(cityId));
                    ListTag chunks = new ListTag();
                    try (PreparedStatement chunkStatement = connection.prepareStatement("SELECT chunk_long FROM city_chunks WHERE city_id = ? AND dimension_id = ? ORDER BY chunk_long")) {
                        chunkStatement.setString(1, cityId);
                        chunkStatement.setString(2, dimensionId);
                        try (ResultSet chunkResult = chunkStatement.executeQuery()) {
                            while (chunkResult.next()) {
                                chunks.add(LongTag.valueOf(chunkResult.getLong("chunk_long")));
                            }
                        }
                    }
                    cityTag.put("Chunks", chunks);
                    cityTags.add(cityTag);
                }
            }
            tag.put("CityChunks", cityTags);
            return cityTags.isEmpty() ? null : tag;
        } catch (SQLException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.error("Failed to load city chunks from SQLite", exception);
            return null;
        }
    }
}
