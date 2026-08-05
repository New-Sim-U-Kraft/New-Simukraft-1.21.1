package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BuildingAbandonmentRepository {
    private final SimuSqliteDatabase database;

    public BuildingAbandonmentRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    public synchronized Map<UUID, int[]> loadAll() {
        Map<UUID, int[]> result = new HashMap<>();
        try (Connection connection = database.borrowConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT building_id, abandonment_index, last_tick_day FROM building_abandonment");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("building_id"));
                result.put(id, new int[]{ rs.getInt("abandonment_index"), rs.getInt("last_tick_day") });
            }
        } catch (SQLException | IllegalArgumentException e) {
            database.markDegraded("loadAll(buildingAbandonment)", e);
            SimuKraft.LOGGER.error("Failed to load building abandonment from SQLite", e);
        }
        return result;
    }

    // upsert/delete 现在都在写队列线程上被调用，不再自己 submitWrite，否则会出现队列套队列且顺序不可控。
    public void upsert(Connection connection, UUID buildingId, UUID cityId, int abandonmentIndex, long lastTickDay) throws SQLException {
        if (buildingId == null) return;
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO building_abandonment(building_id, city_id, abandonment_index, last_tick_day) " +
                "VALUES(?, ?, ?, ?) ON CONFLICT(building_id) DO UPDATE SET " +
                "city_id = excluded.city_id, abandonment_index = excluded.abandonment_index, last_tick_day = excluded.last_tick_day")) {
            stmt.setString(1, buildingId.toString());
            stmt.setString(2, cityId != null ? cityId.toString() : "");
            stmt.setInt(3, abandonmentIndex);
            stmt.setLong(4, lastTickDay);
            stmt.executeUpdate();
        }
    }

    public void delete(Connection connection, UUID buildingId) throws SQLException {
        if (buildingId == null) return;
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM building_abandonment WHERE building_id = ?")) {
            stmt.setString(1, buildingId.toString());
            stmt.executeUpdate();
        }
    }
}
