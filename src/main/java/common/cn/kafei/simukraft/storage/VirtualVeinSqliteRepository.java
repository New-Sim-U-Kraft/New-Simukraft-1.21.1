package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinConsumption;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinFieldKey;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinFieldProfile;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlot;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlotState;
import net.minecraft.resources.ResourceLocation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** VirtualVeinSqliteRepository: 持久化矿区档案和共享储量。 */
@SuppressWarnings("null")
public final class VirtualVeinSqliteRepository {
    private static final String TABLE = "virtual_vein_fields";
    private static final String FIND_SQL = "SELECT * FROM " + TABLE + " WHERE dimension_id = ? AND field_cell_x = ? AND field_cell_z = ? AND field_biome_id = ?";
    private static final String INSERT_SQL = "INSERT OR IGNORE INTO " + TABLE + "(dimension_id, field_cell_x, field_cell_z, field_biome_id, center_x, center_z, center_biome_id, created_game_time, vein_count, "
            + "slot0_vein_id, slot0_display_name, slot0_product_id, slot0_min_y, slot0_max_y, slot0_amount, slot0_period_ticks, slot0_initial_reserve, slot0_remaining_reserve, slot0_state, "
            + "slot1_vein_id, slot1_display_name, slot1_product_id, slot1_min_y, slot1_max_y, slot1_amount, slot1_period_ticks, slot1_initial_reserve, slot1_remaining_reserve, slot1_state) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_LEGACY_EMPTY_SQL = "DELETE FROM " + TABLE
            + " WHERE dimension_id = ? AND field_cell_x = ? AND field_cell_z = ? AND field_biome_id = ? AND generation_version IN (1, 2) AND vein_count = 0";

    private final SimuSqliteDatabase database;

    public VirtualVeinSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    public synchronized Optional<VirtualVeinFieldProfile> find(String dimensionId, VirtualVeinFieldKey key) {
        try (Connection connection = database.borrowConnection()) {
            return find(connection, dimensionId, key);
        } catch (SQLException | RuntimeException exception) {
            SimuKraft.LOGGER.error("Failed to load virtual vein field {},{}", key.cellX(), key.cellZ(), exception);
            return Optional.empty();
        }
    }

    /** createIfAbsent: 原子建立矿区档案。在写线程执行并同步等待结果。 */
    public Optional<VirtualVeinFieldProfile> createIfAbsent(VirtualVeinFieldProfile profile) {
        Optional<VirtualVeinFieldProfile> stored = database.callSync(connection -> createIfAbsent(connection, profile));
        return stored != null ? stored : Optional.empty();
    }

    private Optional<VirtualVeinFieldProfile> createIfAbsent(Connection connection, VirtualVeinFieldProfile profile) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            bindProfile(statement, profile);
            statement.executeUpdate();
        }
        return find(connection, profile.dimensionId(), profile.key());
    }

    /** replaceLegacyEmptyProfile: 仅替换旧匹配策略错误生成的空矿区档案。在写线程执行并同步等待结果。 */
    public Optional<VirtualVeinFieldProfile> replaceLegacyEmptyProfile(VirtualVeinFieldProfile profile) {
        Optional<VirtualVeinFieldProfile> stored = database.callSync(connection -> replaceLegacyEmptyProfile(connection, profile));
        return stored != null ? stored : Optional.empty();
    }

    private Optional<VirtualVeinFieldProfile> replaceLegacyEmptyProfile(Connection connection, VirtualVeinFieldProfile profile) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(DELETE_LEGACY_EMPTY_SQL)) {
            bindKey(delete, profile.dimensionId(), profile.key());
            if (delete.executeUpdate() == 1) {
                try (PreparedStatement insert = connection.prepareStatement(INSERT_SQL)) {
                    bindProfile(insert, profile);
                    insert.executeUpdate();
                }
            }
        }
        return find(connection, profile.dimensionId(), profile.key());
    }

    /** consume: 原子扣减指定槽位的储量。在写线程执行并同步等待结果。 */
    public Optional<VirtualVeinConsumption> consume(String dimensionId, VirtualVeinFieldKey key, int slotIndex, int requestedAmount) {
        if (slotIndex < 0 || slotIndex > 1 || requestedAmount <= 0) {
            return Optional.empty();
        }
        Optional<VirtualVeinConsumption> consumption = database.callSync(connection -> consume(connection, dimensionId, key, slotIndex, requestedAmount));
        return consumption != null ? consumption : Optional.empty();
    }

    private Optional<VirtualVeinConsumption> consume(Connection connection, String dimensionId, VirtualVeinFieldKey key, int slotIndex, int requestedAmount) throws SQLException {
        String remainingColumn = "slot" + slotIndex + "_remaining_reserve";
        String stateColumn = "slot" + slotIndex + "_state";
        try (PreparedStatement select = connection.prepareStatement("SELECT " + remainingColumn + ", " + stateColumn + " FROM " + TABLE + " WHERE dimension_id = ? AND field_cell_x = ? AND field_cell_z = ? AND field_biome_id = ?")) {
            bindKey(select, dimensionId, key);
            try (ResultSet resultSet = select.executeQuery()) {
                if (!resultSet.next() || VirtualVeinSlotState.valueOf(resultSet.getString(stateColumn)) != VirtualVeinSlotState.ACTIVE) {
                    return Optional.of(new VirtualVeinConsumption(0, 0, true));
                }
                int remaining = resultSet.getInt(remainingColumn);
                int consumed = Math.min(remaining, requestedAmount);
                int updatedRemaining = remaining - consumed;
                VirtualVeinSlotState updatedState = updatedRemaining == 0 ? VirtualVeinSlotState.DEPLETED : VirtualVeinSlotState.ACTIVE;
                try (PreparedStatement update = connection.prepareStatement("UPDATE " + TABLE + " SET " + remainingColumn + " = ?, " + stateColumn + " = ? WHERE dimension_id = ? AND field_cell_x = ? AND field_cell_z = ? AND field_biome_id = ?")) {
                    update.setInt(1, updatedRemaining);
                    update.setString(2, updatedState.name());
                    bindKey(update, 3, dimensionId, key);
                    update.executeUpdate();
                }
                return Optional.of(new VirtualVeinConsumption(consumed, updatedRemaining, updatedState == VirtualVeinSlotState.DEPLETED));
            }
        }
    }

    private Optional<VirtualVeinFieldProfile> find(Connection connection, String dimensionId, VirtualVeinFieldKey key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_SQL)) {
            bindKey(statement, dimensionId, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readProfile(resultSet)) : Optional.empty();
            }
        }
    }

    private static VirtualVeinFieldProfile readProfile(ResultSet resultSet) throws SQLException {
        VirtualVeinFieldKey key = new VirtualVeinFieldKey(
                resultSet.getInt("field_cell_x"),
                resultSet.getInt("field_cell_z"),
                resultSet.getInt("center_x"),
                resultSet.getInt("center_z"),
                resultSet.getString("field_biome_id")
        );
        List<VirtualVeinSlot> slots = new ArrayList<>(2);
        readSlot(resultSet, 0).ifPresent(slots::add);
        readSlot(resultSet, 1).ifPresent(slots::add);
        return new VirtualVeinFieldProfile(
                resultSet.getString("dimension_id"),
                key,
                resultSet.getString("center_biome_id"),
                resultSet.getLong("created_game_time"),
                slots
        );
    }

    private static Optional<VirtualVeinSlot> readSlot(ResultSet resultSet, int index) throws SQLException {
        String prefix = "slot" + index + "_";
        String veinId = resultSet.getString(prefix + "vein_id");
        if (veinId == null || veinId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new VirtualVeinSlot(
                veinId,
                resultSet.getString(prefix + "display_name"),
                ResourceLocation.parse(resultSet.getString(prefix + "product_id")),
                resultSet.getInt(prefix + "min_y"),
                resultSet.getInt(prefix + "max_y"),
                resultSet.getInt(prefix + "amount"),
                resultSet.getInt(prefix + "period_ticks"),
                resultSet.getInt(prefix + "initial_reserve"),
                resultSet.getInt(prefix + "remaining_reserve"),
                VirtualVeinSlotState.valueOf(resultSet.getString(prefix + "state"))
        ));
    }

    private static void bindProfile(PreparedStatement statement, VirtualVeinFieldProfile profile) throws SQLException {
        VirtualVeinFieldKey key = profile.key();
        statement.setString(1, profile.dimensionId());
        statement.setInt(2, key.cellX());
        statement.setInt(3, key.cellZ());
        statement.setString(4, key.biomeId());
        statement.setInt(5, key.centerX());
        statement.setInt(6, key.centerZ());
        statement.setString(7, profile.centerBiomeId());
        statement.setLong(8, profile.createdGameTime());
        statement.setInt(9, profile.slots().size());
        bindSlot(statement, 10, profile.slots(), 0);
        bindSlot(statement, 20, profile.slots(), 1);
    }

    private static void bindSlot(PreparedStatement statement, int startIndex, List<VirtualVeinSlot> slots, int slotIndex) throws SQLException {
        if (slotIndex >= slots.size()) {
            statement.setString(startIndex, "");
            statement.setString(startIndex + 1, "");
            statement.setString(startIndex + 2, "");
            for (int index = 3; index <= 8; index++) {
                statement.setInt(startIndex + index, 0);
            }
            statement.setString(startIndex + 9, VirtualVeinSlotState.EMPTY.name());
            return;
        }
        VirtualVeinSlot slot = slots.get(slotIndex);
        statement.setString(startIndex, slot.veinId());
        statement.setString(startIndex + 1, slot.displayName());
        statement.setString(startIndex + 2, slot.productId().toString());
        statement.setInt(startIndex + 3, slot.minY());
        statement.setInt(startIndex + 4, slot.maxY());
        statement.setInt(startIndex + 5, slot.amount());
        statement.setInt(startIndex + 6, slot.periodTicks());
        statement.setInt(startIndex + 7, slot.initialReserve());
        statement.setInt(startIndex + 8, slot.remainingReserve());
        statement.setString(startIndex + 9, slot.state().name());
    }

    private static void bindKey(PreparedStatement statement, String dimensionId, VirtualVeinFieldKey key) throws SQLException {
        bindKey(statement, 1, dimensionId, key);
    }

    private static void bindKey(PreparedStatement statement, int startIndex, String dimensionId, VirtualVeinFieldKey key) throws SQLException {
        statement.setString(startIndex, dimensionId);
        statement.setInt(startIndex + 1, key.cellX());
        statement.setInt(startIndex + 2, key.cellZ());
        statement.setString(startIndex + 3, key.biomeId());
    }
}
