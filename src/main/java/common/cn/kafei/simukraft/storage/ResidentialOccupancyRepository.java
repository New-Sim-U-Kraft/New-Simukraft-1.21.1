package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** ResidentialOccupancyRepository: 持久化住宅建筑是否允许被分配入住。 */
public final class ResidentialOccupancyRepository {
    private final SimuSqliteDatabase database;

    public ResidentialOccupancyRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /** loadClosedBuildingIds: 读取禁止分配入住的建筑 ID。 */
    public synchronized Set<UUID> loadClosedBuildingIds() {
        Set<UUID> closed = new HashSet<>();
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT building_id FROM residential_occupancy WHERE occupancy_allowed = 0");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                closed.add(UUID.fromString(resultSet.getString("building_id")));
            }
        } catch (SQLException | IllegalArgumentException exception) {
            database.markDegraded("loadClosedBuildingIds(residential_occupancy)", exception);
            SimuKraft.LOGGER.error("Failed to load residential occupancy flags from SQLite", exception);
        }
        return closed;
    }

    /** upsert: 写入一座住宅的入住开关。 */
    public void upsert(Connection connection, UUID buildingId, boolean occupancyAllowed) throws SQLException {
        if (buildingId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO residential_occupancy(building_id, occupancy_allowed, updated_at) VALUES(?, ?, ?) "
                        + "ON CONFLICT(building_id) DO UPDATE SET occupancy_allowed = excluded.occupancy_allowed, "
                        + "updated_at = excluded.updated_at")) {
            statement.setString(1, buildingId.toString());
            statement.setInt(2, occupancyAllowed ? 1 : 0);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /** delete: 拆除建筑后删除入住开关。 */
    public void delete(Connection connection, UUID buildingId) throws SQLException {
        if (buildingId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM residential_occupancy WHERE building_id = ?")) {
            statement.setString(1, buildingId.toString());
            statement.executeUpdate();
        }
    }
}
