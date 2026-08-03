package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("Null")
public final class LogisticsSqliteRepository {
    private final SimuSqliteDatabase database;

    public LogisticsSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    public synchronized void saveAll(CompoundTag tag) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            SqliteNbtHelper.clearTables(connection, "logistics_channels", "logistics_ports", "logistics_clients", "logistics_warehouses");
            try {
                ListTag warehouses = tag.getList("Warehouses", CompoundTag.TAG_COMPOUND);
                for (int i = 0; i < warehouses.size(); i++) {
                    saveWarehouse(connection, warehouses.getCompound(i));
                }
                ListTag clients = tag.getList("Clients", CompoundTag.TAG_COMPOUND);
                for (int i = 0; i < clients.size(); i++) {
                    saveClient(connection, clients.getCompound(i));
                }
                ListTag channels = tag.getList("Channels", CompoundTag.TAG_COMPOUND);
                for (int i = 0; i < channels.size(); i++) {
                    saveChannel(connection, channels.getCompound(i));
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save logistics data to SQLite", exception);
        }
    }

    public synchronized CompoundTag loadAll() {
        CompoundTag tag = new CompoundTag();
        ListTag warehouses = new ListTag();
        ListTag clients = new ListTag();
        ListTag channels = new ListTag();
        try (Connection connection = database.openConnection()) {
            loadWarehouses(connection, warehouses);
            loadClients(connection, clients);
            loadChannels(connection, channels);
            tag.put("Warehouses", warehouses);
            tag.put("Clients", clients);
            tag.put("Channels", channels);
            return warehouses.isEmpty() && clients.isEmpty() && channels.isEmpty() ? null : tag;
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to load logistics data from SQLite", exception);
            return null;
        }
    }

    public synchronized void saveDimension(CompoundTag tag, String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            saveAll(tag);
            return;
        }
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                deleteDimension(connection, dimensionId);
                Set<String> warehouseIds = new HashSet<>();
                Set<String> clientIds = new HashSet<>();
                ListTag warehouses = tag.getList("Warehouses", CompoundTag.TAG_COMPOUND);
                for (int i = 0; i < warehouses.size(); i++) {
                    CompoundTag warehouse = warehouses.getCompound(i);
                    if (sameDimension(warehouse, dimensionId)) {
                        saveWarehouse(connection, warehouse);
                        warehouseIds.add(warehouse.getUUID("WarehouseId").toString());
                    }
                }
                ListTag clients = tag.getList("Clients", CompoundTag.TAG_COMPOUND);
                for (int i = 0; i < clients.size(); i++) {
                    CompoundTag client = clients.getCompound(i);
                    if (sameDimension(client, dimensionId)) {
                        saveClient(connection, client);
                        clientIds.add(client.getUUID("ClientId").toString());
                    }
                }
                ListTag channels = tag.getList("Channels", CompoundTag.TAG_COMPOUND);
                for (int i = 0; i < channels.size(); i++) {
                    CompoundTag channel = channels.getCompound(i);
                    if (belongsToDimension(channel, warehouseIds, clientIds)) {
                        saveChannel(connection, channel);
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save logistics dimension '{}' to SQLite", dimensionId, exception);
        }
    }

    public synchronized CompoundTag loadDimension(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return loadAll();
        }
        CompoundTag tag = new CompoundTag();
        ListTag warehouses = new ListTag();
        ListTag clients = new ListTag();
        ListTag channels = new ListTag();
        try (Connection connection = database.openConnection()) {
            loadWarehouses(connection, warehouses, dimensionId);
            loadClients(connection, clients, dimensionId);
            loadChannels(connection, channels, dimensionId);
            tag.put("Warehouses", warehouses);
            tag.put("Clients", clients);
            tag.put("Channels", channels);
            return warehouses.isEmpty() && clients.isEmpty() && channels.isEmpty() ? null : tag;
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to load logistics dimension '{}' from SQLite", dimensionId, exception);
            return null;
        }
    }

    public synchronized void upsertWarehouse(CompoundTag warehouseTag) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                String warehouseId = warehouseTag.getUUID("WarehouseId").toString();
                deleteWarehousesAt(connection, warehouseTag.getLong("BoxPos"), warehouseId, warehouseTag.getString("DimensionId"));
                try (PreparedStatement deleteContainers = connection.prepareStatement("DELETE FROM logistics_ports WHERE owner_id = ? AND owner_type = 'warehouse'")) {
                    deleteContainers.setString(1, warehouseId);
                    deleteContainers.executeUpdate();
                }
                saveWarehouse(connection, warehouseTag);
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save logistics warehouse to SQLite", exception);
        }
    }

    public synchronized void upsertClient(CompoundTag clientTag) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                String clientId = clientTag.getUUID("ClientId").toString();
                deleteClientsAt(connection, clientTag.getLong("BoxPos"), clientId, clientTag.getString("DimensionId"));
                try (PreparedStatement deletePorts = connection.prepareStatement("DELETE FROM logistics_ports WHERE owner_id = ? AND owner_type = 'client'")) {
                    deletePorts.setString(1, clientId);
                    deletePorts.executeUpdate();
                }
                saveClient(connection, clientTag);
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save logistics client to SQLite", exception);
        }
    }

    public synchronized void upsertChannel(CompoundTag channelTag) {
        try (Connection connection = database.openConnection()) {
            saveChannel(connection, channelTag);
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to save logistics channel to SQLite", exception);
        }
    }

    public synchronized void deleteWarehouse(UUID warehouseId) {
        if (warehouseId == null) {
            return;
        }
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                String id = warehouseId.toString();
                try (PreparedStatement ports = connection.prepareStatement("DELETE FROM logistics_ports WHERE owner_id = ? AND owner_type = 'warehouse'");
                     PreparedStatement channels = connection.prepareStatement("DELETE FROM logistics_channels WHERE warehouse_id = ?");
                     PreparedStatement warehouse = connection.prepareStatement("DELETE FROM logistics_warehouses WHERE warehouse_id = ?")) {
                    ports.setString(1, id);
                    ports.executeUpdate();
                    channels.setString(1, id);
                    channels.executeUpdate();
                    warehouse.setString(1, id);
                    warehouse.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to delete logistics warehouse from SQLite", exception);
        }
    }

    public synchronized void deleteClient(UUID clientId) {
        if (clientId == null) {
            return;
        }
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                String id = clientId.toString();
                try (PreparedStatement ports = connection.prepareStatement("DELETE FROM logistics_ports WHERE owner_id = ? AND owner_type = 'client'");
                     PreparedStatement channels = connection.prepareStatement("DELETE FROM logistics_channels WHERE client_id = ?");
                     PreparedStatement client = connection.prepareStatement("DELETE FROM logistics_clients WHERE client_id = ?")) {
                    ports.setString(1, id);
                    ports.executeUpdate();
                    channels.setString(1, id);
                    channels.executeUpdate();
                    client.setString(1, id);
                    client.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to delete logistics client from SQLite", exception);
        }
    }

    public synchronized void deleteChannel(UUID channelId) {
        if (channelId == null) {
            return;
        }
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM logistics_channels WHERE channel_id = ?")) {
            statement.setString(1, channelId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to delete logistics channel from SQLite", exception);
        }
    }

    private void deleteWarehousesAt(Connection connection, long boxPosLong, String keepWarehouseId, String dimensionId) throws SQLException {
        for (String warehouseId : idsAt(connection, "logistics_warehouses", "warehouse_id", "box_pos_long", boxPosLong, keepWarehouseId, dimensionId)) {
            try (PreparedStatement ports = connection.prepareStatement("DELETE FROM logistics_ports WHERE owner_id = ? AND owner_type = 'warehouse'");
                 PreparedStatement channels = connection.prepareStatement("DELETE FROM logistics_channels WHERE warehouse_id = ?");
                 PreparedStatement warehouse = connection.prepareStatement("DELETE FROM logistics_warehouses WHERE warehouse_id = ?")) {
                ports.setString(1, warehouseId);
                ports.executeUpdate();
                channels.setString(1, warehouseId);
                channels.executeUpdate();
                warehouse.setString(1, warehouseId);
                warehouse.executeUpdate();
            }
        }
    }

    private void deleteClientsAt(Connection connection, long boxPosLong, String keepClientId, String dimensionId) throws SQLException {
        for (String clientId : idsAt(connection, "logistics_clients", "client_id", "box_pos_long", boxPosLong, keepClientId, dimensionId)) {
            try (PreparedStatement ports = connection.prepareStatement("DELETE FROM logistics_ports WHERE owner_id = ? AND owner_type = 'client'");
                 PreparedStatement channels = connection.prepareStatement("DELETE FROM logistics_channels WHERE client_id = ?");
                 PreparedStatement client = connection.prepareStatement("DELETE FROM logistics_clients WHERE client_id = ?")) {
                ports.setString(1, clientId);
                ports.executeUpdate();
                channels.setString(1, clientId);
                channels.executeUpdate();
                client.setString(1, clientId);
                client.executeUpdate();
            }
        }
    }

    private void deleteDimension(Connection connection, String dimensionId) throws SQLException {
        try (PreparedStatement channels = connection.prepareStatement(
                "DELETE FROM logistics_channels WHERE warehouse_id IN (SELECT warehouse_id FROM logistics_warehouses WHERE dimension_id = ?) "
                        + "OR client_id IN (SELECT client_id FROM logistics_clients WHERE dimension_id = ?)");
             PreparedStatement warehousePorts = connection.prepareStatement(
                     "DELETE FROM logistics_ports WHERE owner_type = 'warehouse' AND owner_id IN (SELECT warehouse_id FROM logistics_warehouses WHERE dimension_id = ?)");
             PreparedStatement clientPorts = connection.prepareStatement(
                     "DELETE FROM logistics_ports WHERE owner_type = 'client' AND owner_id IN (SELECT client_id FROM logistics_clients WHERE dimension_id = ?)");
             PreparedStatement clients = connection.prepareStatement("DELETE FROM logistics_clients WHERE dimension_id = ?");
             PreparedStatement warehouses = connection.prepareStatement("DELETE FROM logistics_warehouses WHERE dimension_id = ?")) {
            channels.setString(1, dimensionId);
            channels.setString(2, dimensionId);
            channels.executeUpdate();
            warehousePorts.setString(1, dimensionId);
            warehousePorts.executeUpdate();
            clientPorts.setString(1, dimensionId);
            clientPorts.executeUpdate();
            clients.setString(1, dimensionId);
            clients.executeUpdate();
            warehouses.setString(1, dimensionId);
            warehouses.executeUpdate();
        }
    }

    private List<String> idsAt(Connection connection, String table, String idColumn, String posColumn, long boxPosLong, String keepId, String dimensionId) throws SQLException {
        List<String> ids = new ArrayList<>();
        String sql = dimensionId == null || dimensionId.isBlank()
                ? "SELECT " + idColumn + " FROM " + table + " WHERE " + posColumn + " = ? AND " + idColumn + " <> ?"
                : "SELECT " + idColumn + " FROM " + table + " WHERE " + posColumn + " = ? AND " + idColumn + " <> ? AND dimension_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, boxPosLong);
            statement.setString(2, keepId);
            if (dimensionId != null && !dimensionId.isBlank()) {
                statement.setString(3, dimensionId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getString(idColumn));
                }
            }
        }
        return ids;
    }

    private void rollbackQuietly(Connection connection, SQLException exception) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            exception.addSuppressed(rollbackException);
        }
    }

    private boolean sameDimension(CompoundTag tag, String dimensionId) {
        String tagDimension = tag.getString("DimensionId");
        return tagDimension.isBlank() || dimensionId.equals(tagDimension);
    }

    private boolean belongsToDimension(CompoundTag channel, Set<String> warehouseIds, Set<String> clientIds) {
        return channel.hasUUID("WarehouseId") && warehouseIds.contains(channel.getUUID("WarehouseId").toString())
                || channel.hasUUID("ClientId") && clientIds.contains(channel.getUUID("ClientId").toString());
    }

    private void saveWarehouse(Connection connection, CompoundTag tag) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO logistics_warehouses(warehouse_id, box_pos_long, city_id, dimension_id, updated_at) VALUES(?, ?, ?, ?, ?) "
                        + "ON CONFLICT(warehouse_id) DO UPDATE SET box_pos_long = excluded.box_pos_long, city_id = excluded.city_id, dimension_id = excluded.dimension_id, updated_at = excluded.updated_at")) {
            statement.setString(1, tag.getUUID("WarehouseId").toString());
            statement.setLong(2, tag.getLong("BoxPos"));
            SqliteNbtHelper.setNullableString(statement, 3, tag.hasUUID("CityId") ? tag.getUUID("CityId").toString() : null);
            statement.setString(4, tag.getString("DimensionId"));
            statement.setLong(5, tag.getLong("UpdatedAt"));
            statement.executeUpdate();
        }
        ListTag containers = tag.getList("Containers", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < containers.size(); i++) {
            CompoundTag container = containers.getCompound(i);
            savePort(connection, tag.getUUID("WarehouseId").toString(), "warehouse", "container_" + i, "container", "warehouse", BlockPos.of(container.getLong("Pos")));
        }
    }

    private void saveClient(Connection connection, CompoundTag tag) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO logistics_clients(client_id, box_pos_long, city_id, dimension_id, name, automatic, source_type, source_id, updated_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(client_id) DO UPDATE SET box_pos_long = excluded.box_pos_long, city_id = excluded.city_id, dimension_id = excluded.dimension_id, name = excluded.name, automatic = excluded.automatic, source_type = excluded.source_type, source_id = excluded.source_id, updated_at = excluded.updated_at")) {
            statement.setString(1, tag.getUUID("ClientId").toString());
            statement.setLong(2, tag.getLong("BoxPos"));
            SqliteNbtHelper.setNullableString(statement, 3, tag.hasUUID("CityId") ? tag.getUUID("CityId").toString() : null);
            statement.setString(4, tag.getString("DimensionId"));
            statement.setString(5, tag.getString("Name"));
            statement.setInt(6, tag.getBoolean("Automatic") ? 1 : 0);
            statement.setString(7, tag.getString("SourceType"));
            statement.setString(8, tag.getString("SourceId"));
            statement.setLong(9, tag.getLong("UpdatedAt"));
            statement.executeUpdate();
        }
        ListTag ports = tag.getList("Ports", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < ports.size(); i++) {
            CompoundTag port = ports.getCompound(i);
            savePort(connection, tag.getUUID("ClientId").toString(), "client", port.getString("Id"), port.getString("Name"), port.getString("Kind"), BlockPos.of(port.getLong("Pos")));
        }
    }

    private void savePort(Connection connection, String ownerId, String ownerType, String portId, String name, String kind, BlockPos pos) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO logistics_ports(owner_id, owner_type, port_id, name, kind, pos_long) VALUES(?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(owner_id, owner_type, port_id) DO UPDATE SET name = excluded.name, kind = excluded.kind, pos_long = excluded.pos_long")) {
            statement.setString(1, ownerId);
            statement.setString(2, ownerType);
            statement.setString(3, portId);
            statement.setString(4, name);
            statement.setString(5, kind);
            statement.setLong(6, pos.asLong());
            statement.executeUpdate();
        }
    }

    private void saveChannel(Connection connection, CompoundTag tag) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO logistics_channels(channel_id, warehouse_id, client_id, direction, name, enabled, filters, updated_at, keep_quantity, keep_source) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(channel_id) DO UPDATE SET warehouse_id = excluded.warehouse_id, client_id = excluded.client_id, direction = excluded.direction, name = excluded.name, enabled = excluded.enabled, filters = excluded.filters, updated_at = excluded.updated_at, keep_quantity = excluded.keep_quantity, keep_source = excluded.keep_source")) {
            statement.setString(1, tag.getUUID("ChannelId").toString());
            statement.setString(2, tag.hasUUID("WarehouseId") ? tag.getUUID("WarehouseId").toString() : "");
            statement.setString(3, tag.hasUUID("ClientId") ? tag.getUUID("ClientId").toString() : "");
            statement.setString(4, tag.getString("Direction"));
            statement.setString(5, tag.getString("Name"));
            statement.setInt(6, tag.getBoolean("Enabled") ? 1 : 0);
            statement.setString(7, tag.getList("Filters", CompoundTag.TAG_COMPOUND).toString());
            statement.setLong(8, tag.getLong("UpdatedAt"));
            statement.setInt(9, tag.contains("KeepQuantity") ? tag.getInt("KeepQuantity") : 0);
            statement.setInt(10, tag.contains("KeepSourceQuantity") ? tag.getInt("KeepSourceQuantity") : 0);
            statement.executeUpdate();
        }
    }

    private void loadWarehouses(Connection connection, ListTag output) throws SQLException {
        loadWarehouses(connection, output, null);
    }

    private void loadWarehouses(Connection connection, ListTag output, String dimensionId) throws SQLException {
        String sql = dimensionId == null
                ? "SELECT * FROM logistics_warehouses ORDER BY box_pos_long"
                : "SELECT * FROM logistics_warehouses WHERE dimension_id = ? ORDER BY box_pos_long";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (dimensionId != null) {
                statement.setString(1, dimensionId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CompoundTag tag = new CompoundTag();
                    String warehouseId = resultSet.getString("warehouse_id");
                    tag.putUUID("WarehouseId", UUID.fromString(warehouseId));
                    tag.putLong("BoxPos", resultSet.getLong("box_pos_long"));
                    SqliteNbtHelper.putNullableUuid(tag, "CityId", resultSet.getString("city_id"));
                    tag.putString("DimensionId", resultSet.getString("dimension_id"));
                    tag.putLong("UpdatedAt", resultSet.getLong("updated_at"));
                    tag.put("Containers", loadPorts(connection, warehouseId, "warehouse"));
                    output.add(tag);
                }
            }
        }
    }

    private void loadClients(Connection connection, ListTag output) throws SQLException {
        loadClients(connection, output, null);
    }

    private void loadClients(Connection connection, ListTag output, String dimensionId) throws SQLException {
        String sql = dimensionId == null
                ? "SELECT * FROM logistics_clients WHERE automatic = 0 ORDER BY box_pos_long"
                : "SELECT * FROM logistics_clients WHERE automatic = 0 AND dimension_id = ? ORDER BY box_pos_long";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (dimensionId != null) {
                statement.setString(1, dimensionId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CompoundTag tag = new CompoundTag();
                    String clientId = resultSet.getString("client_id");
                    tag.putUUID("ClientId", UUID.fromString(clientId));
                    tag.putLong("BoxPos", resultSet.getLong("box_pos_long"));
                    SqliteNbtHelper.putNullableUuid(tag, "CityId", resultSet.getString("city_id"));
                    tag.putString("DimensionId", resultSet.getString("dimension_id"));
                    tag.putString("Name", resultSet.getString("name"));
                    tag.putBoolean("Automatic", resultSet.getInt("automatic") != 0);
                    tag.putString("SourceType", resultSet.getString("source_type"));
                    tag.putString("SourceId", resultSet.getString("source_id"));
                    tag.putLong("UpdatedAt", resultSet.getLong("updated_at"));
                    tag.put("Ports", loadPorts(connection, clientId, "client"));
                    output.add(tag);
                }
            }
        }
    }

    private ListTag loadPorts(Connection connection, String ownerId, String ownerType) throws SQLException {
        ListTag ports = new ListTag();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM logistics_ports WHERE owner_id = ? AND owner_type = ? ORDER BY port_id")) {
            statement.setString(1, ownerId);
            statement.setString(2, ownerType);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CompoundTag tag = new CompoundTag();
                    if ("warehouse".equals(ownerType)) {
                        tag.putLong("Pos", resultSet.getLong("pos_long"));
                    } else {
                        tag.putString("Id", resultSet.getString("port_id"));
                        tag.putString("Name", resultSet.getString("name"));
                        tag.putString("Kind", resultSet.getString("kind"));
                        tag.putLong("Pos", resultSet.getLong("pos_long"));
                    }
                    ports.add(tag);
                }
            }
        }
        return ports;
    }

    private void loadChannels(Connection connection, ListTag output) throws SQLException {
        loadChannels(connection, output, null);
    }

    private void loadChannels(Connection connection, ListTag output, String dimensionId) throws SQLException {
        String sql = dimensionId == null
                ? "SELECT * FROM logistics_channels ORDER BY updated_at, channel_id"
                : "SELECT channels.* FROM logistics_channels channels "
                + "LEFT JOIN logistics_warehouses warehouses ON warehouses.warehouse_id = channels.warehouse_id "
                + "LEFT JOIN logistics_clients clients ON clients.client_id = channels.client_id "
                + "WHERE warehouses.dimension_id = ? OR clients.dimension_id = ? "
                + "ORDER BY channels.updated_at, channels.channel_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (dimensionId != null) {
                statement.setString(1, dimensionId);
                statement.setString(2, dimensionId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CompoundTag tag = new CompoundTag();
                    tag.putUUID("ChannelId", UUID.fromString(resultSet.getString("channel_id")));
                    SqliteNbtHelper.putNullableUuid(tag, "WarehouseId", resultSet.getString("warehouse_id"));
                    SqliteNbtHelper.putNullableUuid(tag, "ClientId", resultSet.getString("client_id"));
                    tag.putString("Direction", resultSet.getString("direction"));
                    tag.putString("Name", resultSet.getString("name"));
                    tag.putBoolean("Enabled", resultSet.getInt("enabled") != 0);
                    tag.putLong("UpdatedAt", resultSet.getLong("updated_at"));
                    tag.putInt("KeepQuantity", resultSet.getInt("keep_quantity"));
                    tag.putInt("KeepSourceQuantity", resultSet.getInt("keep_source"));
                    try {
                        tag.put("Filters", net.minecraft.nbt.TagParser.parseTag("{Filters:" + resultSet.getString("filters") + "}").getList("Filters", CompoundTag.TAG_COMPOUND));
                    } catch (Exception exception) {
                        tag.put("Filters", new ListTag());
                    }
                    output.add(tag);
                }
            }
        }
    }
}
