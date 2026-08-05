package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.family.FamilyData;
import common.cn.kafei.simukraft.citizen.family.FamilyStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FamilySqliteRepository {
    private final SimuSqliteDatabase database;

    public FamilySqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    public void upsert(Connection connection, FamilyData family) throws SQLException {
        saveFamily(connection, family);
    }

    public void delete(Connection connection, UUID familyId) throws SQLException {
        if (familyId == null) return;
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM family_members WHERE family_id = ?")) {
            stmt.setString(1, familyId.toString());
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM families WHERE family_id = ?")) {
            stmt.setString(1, familyId.toString());
            stmt.executeUpdate();
        }
    }

    public synchronized List<FamilyData> loadAll() {
        List<FamilyData> result = new ArrayList<>();
        try (Connection connection = database.borrowConnection()) {
            // Load child members grouped by family_id
            java.util.Map<String, List<String>> childIdsByFamily = new java.util.HashMap<>();
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT family_id, citizen_id FROM family_members WHERE role = 'CHILD'");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    childIdsByFamily.computeIfAbsent(rs.getString("family_id"), k -> new ArrayList<>())
                            .add(rs.getString("citizen_id"));
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM families");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID familyId = UUID.fromString(rs.getString("family_id"));
                    UUID cityId = parseUuid(rs.getString("city_id"));
                    FamilyData family = new FamilyData(familyId, cityId);
                    family.setHusbandId(parseUuid(rs.getString("husband_id")));
                    family.setWifeId(parseUuid(rs.getString("wife_id")));
                    family.setPaternalFamilyId(parseUuid(rs.getString("paternal_family_id")));
                    family.setMaternalFamilyId(parseUuid(rs.getString("maternal_family_id")));
                    family.setGeneration(rs.getInt("generation"));
                    family.setStatus(FamilyStatus.fromName(rs.getString("status")));
                    List<String> childIds = childIdsByFamily.getOrDefault(familyId.toString(), List.of());
                    for (String childId : childIds) {
                        family.addChild(UUID.fromString(childId));
                    }
                    result.add(family);
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            database.markDegraded("loadAll(families)", exception);
            SimuKraft.LOGGER.error("Failed to load families from SQLite", exception);
            // 失败必须返回 null 而不是部分结果：调用方据此区分"没有家庭"与"库故障"，避免按残缺图谱重复建家庭。
            return null;
        }
        return result;
    }

    private void saveFamily(Connection connection, FamilyData family) throws SQLException {
        String familyId = family.familyId().toString();
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO families(family_id, city_id, husband_id, wife_id, paternal_family_id, maternal_family_id, generation, status) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(family_id) DO UPDATE SET " +
                "city_id = excluded.city_id, husband_id = excluded.husband_id, wife_id = excluded.wife_id, " +
                "paternal_family_id = excluded.paternal_family_id, maternal_family_id = excluded.maternal_family_id, " +
                "generation = excluded.generation, status = excluded.status")) {
            stmt.setString(1, familyId);
            SqliteNbtHelper.setNullableString(stmt, 2, family.cityId() != null ? family.cityId().toString() : null);
            SqliteNbtHelper.setNullableString(stmt, 3, family.husbandId() != null ? family.husbandId().toString() : null);
            SqliteNbtHelper.setNullableString(stmt, 4, family.wifeId() != null ? family.wifeId().toString() : null);
            SqliteNbtHelper.setNullableString(stmt, 5, family.paternalFamilyId() != null ? family.paternalFamilyId().toString() : null);
            SqliteNbtHelper.setNullableString(stmt, 6, family.maternalFamilyId() != null ? family.maternalFamilyId().toString() : null);
            stmt.setInt(7, family.generation());
            stmt.setString(8, family.status().name());
            stmt.executeUpdate();
        }
        // Sync child members
        try (PreparedStatement del = connection.prepareStatement(
                "DELETE FROM family_members WHERE family_id = ? AND role = 'CHILD'")) {
            del.setString(1, familyId);
            del.executeUpdate();
        }
        if (!family.childIds().isEmpty()) {
            try (PreparedStatement ins = connection.prepareStatement(
                    "INSERT OR IGNORE INTO family_members(family_id, citizen_id, role) VALUES(?, ?, 'CHILD')")) {
                for (UUID childId : family.childIds()) {
                    ins.setString(1, familyId);
                    ins.setString(2, childId.toString());
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
