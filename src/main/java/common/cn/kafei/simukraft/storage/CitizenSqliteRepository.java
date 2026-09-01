package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@SuppressWarnings("null")
public final class CitizenSqliteRepository {
    // 38 列的占位符与整条 SQL 都是常量，只在类加载时拼一次（旧实现每次 save 都重建这条 700+ 字符的 SQL）。
    private static final String UPSERT_SQL =
            "INSERT INTO citizens(uuid, name, gender, age, lifespan, job_type, job_id, status, work_status, work_need_detail, status_label, is_working, npc_id, skin_path, city_id, home_id, workplace_id, workplace_pos_long, health, happiness, sick, child, child_growth_due_day, born_day, dimension_id, family_id, origin_family_id, pregnant, pregnant_since, last_age_growth_day, disease_id, disease_since_day, disease_treatment_ticks, medical_bed_poi_id, postpartum_until_day, last_hospital_meal_day, reserved_baby_bed_poi_id, last_hospital_progress_day_time) VALUES("
                    + String.join(", ", java.util.Collections.nCopies(38, "?"))
                    + ") ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, gender = excluded.gender, age = excluded.age, lifespan = excluded.lifespan, job_type = excluded.job_type, job_id = excluded.job_id, status = excluded.status, work_status = excluded.work_status, work_need_detail = excluded.work_need_detail, status_label = excluded.status_label, is_working = excluded.is_working, npc_id = excluded.npc_id, skin_path = excluded.skin_path, city_id = excluded.city_id, home_id = excluded.home_id, workplace_id = excluded.workplace_id, workplace_pos_long = excluded.workplace_pos_long, health = excluded.health, happiness = excluded.happiness, sick = excluded.sick, child = excluded.child, child_growth_due_day = excluded.child_growth_due_day, born_day = excluded.born_day, dimension_id = excluded.dimension_id, family_id = excluded.family_id, origin_family_id = excluded.origin_family_id, pregnant = excluded.pregnant, pregnant_since = excluded.pregnant_since, last_age_growth_day = excluded.last_age_growth_day, disease_id = excluded.disease_id, disease_since_day = excluded.disease_since_day, disease_treatment_ticks = excluded.disease_treatment_ticks, medical_bed_poi_id = excluded.medical_bed_poi_id, postpartum_until_day = excluded.postpartum_until_day, last_hospital_meal_day = excluded.last_hospital_meal_day, reserved_baby_bed_poi_id = excluded.reserved_baby_bed_poi_id, last_hospital_progress_day_time = excluded.last_hospital_progress_day_time";

    private final SimuSqliteDatabase database;

    public CitizenSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /**
     * saveAll: 把内存中的居民全部 upsert 进库。
     * <p>不再清空 citizens / citizen_skills 再重写。旧实现只有 "内存非空" 这一道保护，
     * 内存里若只剩部分居民（加载失败或只从 SavedData 兜底恢复了一部分），差集就会被当成"已删除"抹掉。
     * 居民的删除只走 {@link #delete(Connection, java.util.UUID)}。
     */
    public void saveAll(Connection connection, CompoundTag tag) throws SQLException {
        ListTag citizens = tag.getList("Citizens", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < citizens.size(); i++) {
            saveCitizen(connection, citizens.getCompound(i));
        }
    }

    public void upsert(Connection connection, CompoundTag citizenTag) throws SQLException {
        saveCitizen(connection, citizenTag);
    }

    public void delete(Connection connection, java.util.UUID citizenId) throws SQLException {
        if (citizenId == null) {
            return;
        }
        // building_tasks / planning_tasks 没有对 citizens 的外键，必须手工清理：
        // 否则被移除居民的未完成任务行永久残留，重启后复活成"幽灵任务"。
        try (PreparedStatement deleteBuildingTasks = connection.prepareStatement("DELETE FROM building_tasks WHERE citizen_id = ?");
             PreparedStatement deletePlanningTasks = connection.prepareStatement("DELETE FROM planning_tasks WHERE citizen_id = ?");
             PreparedStatement statement = connection.prepareStatement("DELETE FROM citizens WHERE uuid = ?")) {
            deleteBuildingTasks.setString(1, citizenId.toString());
            deleteBuildingTasks.executeUpdate();
            deletePlanningTasks.setString(1, citizenId.toString());
            deletePlanningTasks.executeUpdate();
            statement.setString(1, citizenId.toString());
            statement.executeUpdate();
        }
    }

    public synchronized CompoundTag loadAll() {
        CompoundTag tag = new CompoundTag();
        ListTag citizens = new ListTag();
        try (Connection connection = database.borrowConnection()) {
            // Bulk-load all skills in one query, group by citizen_id
            java.util.Map<String, CompoundTag> skillsByUuid = new java.util.HashMap<>();
            try (PreparedStatement skillStmt = connection.prepareStatement("SELECT citizen_id, skill_key, skill_value FROM citizen_skills");
                 ResultSet skillRs = skillStmt.executeQuery()) {
                while (skillRs.next()) {
                    skillsByUuid.computeIfAbsent(skillRs.getString("citizen_id"), k -> new CompoundTag())
                            .putInt(skillRs.getString("skill_key"), skillRs.getInt("skill_value"));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM citizens ORDER BY uuid");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String uuid = resultSet.getString("uuid");
                    CompoundTag citizen = new CompoundTag();
                    citizen.putUUID("Uuid", java.util.UUID.fromString(uuid));
                    citizen.putString("Name", resultSet.getString("name"));
                    citizen.putString("Gender", resultSet.getString("gender"));
                    citizen.putInt("Age", resultSet.getInt("age"));
                    citizen.putInt("Lifespan", resultSet.getInt("lifespan"));
                    citizen.putString("JobType", resultSet.getString("job_type"));
                    citizen.putString("JobId", resultSet.getString("job_id"));
                    citizen.putString("Status", resultSet.getString("status"));
                    citizen.putString("WorkStatus", resultSet.getString("work_status"));
                    citizen.putString("WorkNeedDetail", resultSet.getString("work_need_detail"));
                    citizen.putString("StatusLabel", resultSet.getString("status_label"));
                    citizen.putBoolean("IsWorking", resultSet.getInt("is_working") != 0);
                    citizen.putInt("NpcId", resultSet.getInt("npc_id"));
                    citizen.putString("SkinPath", resultSet.getString("skin_path"));
                    SqliteNbtHelper.putNullableUuid(citizen, "CityId", resultSet.getString("city_id"));
                    SqliteNbtHelper.putNullableUuid(citizen, "HomeId", resultSet.getString("home_id"));
                    SqliteNbtHelper.putNullableUuid(citizen, "WorkplaceId", resultSet.getString("workplace_id"));
                    long workplacePosLong = resultSet.getLong("workplace_pos_long");
                    if (!resultSet.wasNull()) {
                        citizen.putLong("WorkplacePos", workplacePosLong);
                    }
                    citizen.putDouble("Health", resultSet.getDouble("health"));
                    citizen.putDouble("Happiness", resultSet.getDouble("happiness"));
                    citizen.putBoolean("Sick", resultSet.getInt("sick") != 0);
                    citizen.putBoolean("Child", resultSet.getInt("child") != 0);
                    citizen.putLong("ChildGrowthDueDay", resultSet.getLong("child_growth_due_day"));
                    citizen.putLong("BornDay", resultSet.getLong("born_day"));
                    String dimId = resultSet.getString("dimension_id");
                    citizen.putString("DimensionId", dimId != null ? dimId : "minecraft:overworld");
                    SqliteNbtHelper.putNullableUuid(citizen, "FamilyId", resultSet.getString("family_id"));
                    SqliteNbtHelper.putNullableUuid(citizen, "OriginFamilyId", resultSet.getString("origin_family_id"));
                    citizen.putBoolean("Pregnant", resultSet.getInt("pregnant") != 0);
                    citizen.putLong("PregnantSince", resultSet.getLong("pregnant_since"));
                    SqliteNbtHelper.putNullableUuid(citizen, "ReservedBabyBedPoiId", resultSet.getString("reserved_baby_bed_poi_id"));
                    citizen.putLong("LastAgeGrowthDay", resultSet.getLong("last_age_growth_day"));
                    citizen.putString("DiseaseId", resultSet.getString("disease_id"));
                    citizen.putLong("DiseaseSinceDay", resultSet.getLong("disease_since_day"));
                    citizen.putLong("DiseaseTreatmentTicks", resultSet.getLong("disease_treatment_ticks"));
                    SqliteNbtHelper.putNullableUuid(citizen, "MedicalBedPoiId", resultSet.getString("medical_bed_poi_id"));
                    citizen.putLong("PostpartumUntilDay", resultSet.getLong("postpartum_until_day"));
                    citizen.putLong("LastHospitalMealDay", resultSet.getLong("last_hospital_meal_day"));
                    citizen.putLong("LastHospitalProgressDayTime", resultSet.getLong("last_hospital_progress_day_time"));
                    citizen.put("Skills", skillsByUuid.getOrDefault(uuid, new CompoundTag()));
                    citizens.add(citizen);
                }
            }
            tag.put("Citizens", citizens);
            return tag;
        } catch (SQLException | IllegalArgumentException exception) {
            database.markDegraded("loadAll(citizens)", exception);
            SimuKraft.LOGGER.error("Failed to load citizens from SQLite", exception);
            return null;
        }
    }

    private void saveCitizen(Connection connection, CompoundTag citizen) throws SQLException {
        String uuid = citizen.getUUID("Uuid").toString();
        try (PreparedStatement citizenStatement = connection.prepareStatement(UPSERT_SQL);
             PreparedStatement deleteSkills = connection.prepareStatement("DELETE FROM citizen_skills WHERE citizen_id = ?");
             PreparedStatement skillStatement = connection.prepareStatement("INSERT INTO citizen_skills(citizen_id, skill_key, skill_value) VALUES(?, ?, ?)")) {
            citizenStatement.setString(1, uuid);
            citizenStatement.setString(2, citizen.getString("Name"));
            citizenStatement.setString(3, citizen.getString("Gender"));
            citizenStatement.setInt(4, citizen.getInt("Age"));
            citizenStatement.setInt(5, citizen.getInt("Lifespan"));
            citizenStatement.setString(6, citizen.getString("JobType"));
            citizenStatement.setString(7, citizen.getString("JobId"));
            citizenStatement.setString(8, citizen.getString("Status"));
            citizenStatement.setString(9, citizen.getString("WorkStatus"));
            citizenStatement.setString(10, citizen.getString("WorkNeedDetail"));
            citizenStatement.setString(11, citizen.getString("StatusLabel"));
            citizenStatement.setInt(12, citizen.getBoolean("IsWorking") ? 1 : 0);
            citizenStatement.setInt(13, citizen.getInt("NpcId"));
            citizenStatement.setString(14, citizen.getString("SkinPath"));
            SqliteNbtHelper.setNullableString(citizenStatement, 15, citizen.hasUUID("CityId") ? citizen.getUUID("CityId").toString() : null);
            SqliteNbtHelper.setNullableString(citizenStatement, 16, citizen.hasUUID("HomeId") ? citizen.getUUID("HomeId").toString() : null);
            SqliteNbtHelper.setNullableString(citizenStatement, 17, citizen.hasUUID("WorkplaceId") ? citizen.getUUID("WorkplaceId").toString() : null);
            if (citizen.contains("WorkplacePos")) {
                citizenStatement.setLong(18, citizen.getLong("WorkplacePos"));
            } else {
                citizenStatement.setObject(18, null);
            }
            citizenStatement.setDouble(19, citizen.getDouble("Health"));
            citizenStatement.setDouble(20, citizen.getDouble("Happiness"));
            citizenStatement.setInt(21, citizen.getBoolean("Sick") ? 1 : 0);
            citizenStatement.setInt(22, citizen.getBoolean("Child") ? 1 : 0);
            citizenStatement.setLong(23, citizen.getLong("ChildGrowthDueDay"));
            citizenStatement.setLong(24, citizen.getLong("BornDay"));
            String dimId = citizen.contains("DimensionId") ? citizen.getString("DimensionId") : "minecraft:overworld";
            citizenStatement.setString(25, dimId.isBlank() ? "minecraft:overworld" : dimId);
            SqliteNbtHelper.setNullableString(citizenStatement, 26, citizen.hasUUID("FamilyId") ? citizen.getUUID("FamilyId").toString() : null);
            SqliteNbtHelper.setNullableString(citizenStatement, 27, citizen.hasUUID("OriginFamilyId") ? citizen.getUUID("OriginFamilyId").toString() : null);
            citizenStatement.setInt(28, citizen.getBoolean("Pregnant") ? 1 : 0);
            citizenStatement.setLong(29, citizen.getLong("PregnantSince"));
            citizenStatement.setLong(30, citizen.getLong("LastAgeGrowthDay"));
            citizenStatement.setString(31, citizen.getString("DiseaseId"));
            citizenStatement.setLong(32, citizen.getLong("DiseaseSinceDay"));
            citizenStatement.setLong(33, citizen.getLong("DiseaseTreatmentTicks"));
            SqliteNbtHelper.setNullableString(citizenStatement, 34, citizen.hasUUID("MedicalBedPoiId") ? citizen.getUUID("MedicalBedPoiId").toString() : null);
            citizenStatement.setLong(35, citizen.getLong("PostpartumUntilDay"));
            citizenStatement.setLong(36, citizen.contains("LastHospitalMealDay") ? citizen.getLong("LastHospitalMealDay") : -1L);
            SqliteNbtHelper.setNullableString(citizenStatement, 37,
                    citizen.hasUUID("ReservedBabyBedPoiId") ? citizen.getUUID("ReservedBabyBedPoiId").toString() : null);
            citizenStatement.setLong(38,
                    citizen.contains("LastHospitalProgressDayTime") ? citizen.getLong("LastHospitalProgressDayTime") : 0L);
            citizenStatement.executeUpdate();
            deleteSkills.setString(1, uuid);
            deleteSkills.executeUpdate();
            CompoundTag skills = citizen.getCompound("Skills");
            for (String key : skills.getAllKeys()) {
                skillStatement.setString(1, uuid);
                skillStatement.setString(2, key);
                skillStatement.setInt(3, skills.getInt(key));
                skillStatement.addBatch();
            }
            skillStatement.executeBatch();
        }
    }
}
