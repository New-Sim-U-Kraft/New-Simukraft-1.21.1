package common.cn.kafei.simukraft.medical;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** 居民医疗状态，随 CitizenData 一起写入 SQLite。 */
@SuppressWarnings("null")
public final class MedicalPatientData {
    private DiseaseType disease = DiseaseType.NONE;
    private long diseaseSinceDay;
    private long diseaseTreatmentTicks;
    private UUID medicalBedPoiId;
    private long postpartumUntilDay;
    private long lastHospitalMealDay = -1L;
    private long lastHospitalProgressDayTime;

    /** fromTag：从居民标签读取医疗状态。 */
    public void fromTag(CompoundTag tag) {
        disease = DiseaseType.fromName(tag.getString("DiseaseId"));
        diseaseSinceDay = Math.max(0L, tag.getLong("DiseaseSinceDay"));
        diseaseTreatmentTicks = Math.max(0L, tag.getLong("DiseaseTreatmentTicks"));
        medicalBedPoiId = tag.hasUUID("MedicalBedPoiId") ? tag.getUUID("MedicalBedPoiId") : null;
        postpartumUntilDay = Math.max(0L, tag.getLong("PostpartumUntilDay"));
        lastHospitalMealDay = tag.contains("LastHospitalMealDay") ? Math.max(-1L, tag.getLong("LastHospitalMealDay")) : -1L;
        lastHospitalProgressDayTime = tag.contains("LastHospitalProgressDayTime")
                ? Math.max(0L, tag.getLong("LastHospitalProgressDayTime")) : 0L;
    }

    /** toTag：将医疗状态写入居民标签。 */
    public void toTag(CompoundTag tag) {
        tag.putString("DiseaseId", disease.name());
        tag.putLong("DiseaseSinceDay", diseaseSinceDay);
        tag.putLong("DiseaseTreatmentTicks", diseaseTreatmentTicks);
        if (medicalBedPoiId != null) {
            tag.putUUID("MedicalBedPoiId", medicalBedPoiId);
        }
        tag.putLong("PostpartumUntilDay", postpartumUntilDay);
        tag.putLong("LastHospitalMealDay", lastHospitalMealDay);
        tag.putLong("LastHospitalProgressDayTime", lastHospitalProgressDayTime);
    }

    /** setDisease：设置疾病并重置本次疾病的治疗进度。 */
    public void setDisease(DiseaseType disease, long sinceDay) {
        DiseaseType safe = disease != null ? disease : DiseaseType.NONE;
        if (this.disease != safe) {
            diseaseTreatmentTicks = 0L;
        }
        this.disease = safe;
        this.diseaseSinceDay = Math.max(0L, sinceDay);
    }

    /** clearDisease：清除疾病及其治疗进度。 */
    public void clearDisease() {
        disease = DiseaseType.NONE;
        diseaseSinceDay = 0L;
        diseaseTreatmentTicks = 0L;
    }

    /** clear：清除死亡居民的全部临时医疗状态。 */
    public void clear() {
        clearDisease();
        medicalBedPoiId = null;
        postpartumUntilDay = 0L;
        lastHospitalMealDay = -1L;
        lastHospitalProgressDayTime = 0L;
    }

    public DiseaseType disease() {
        return disease;
    }

    public long diseaseSinceDay() {
        return diseaseSinceDay;
    }

    public long diseaseTreatmentTicks() {
        return diseaseTreatmentTicks;
    }

    public void addDiseaseTreatmentTicks(long ticks) {
        diseaseTreatmentTicks = Math.max(0L, diseaseTreatmentTicks + Math.max(0L, ticks));
    }

    public UUID medicalBedPoiId() {
        return medicalBedPoiId;
    }

    public void setMedicalBedPoiId(UUID medicalBedPoiId) {
        this.medicalBedPoiId = medicalBedPoiId;
    }

    public long postpartumUntilDay() {
        return postpartumUntilDay;
    }

    public void setPostpartumUntilDay(long postpartumUntilDay) {
        this.postpartumUntilDay = Math.max(0L, postpartumUntilDay);
    }

    public long lastHospitalMealDay() {
        return lastHospitalMealDay;
    }

    /** setLastHospitalMealDay：记录住院患者最近一次收到医院餐食的游戏日。 */
    public void setLastHospitalMealDay(long lastHospitalMealDay) {
        this.lastHospitalMealDay = Math.max(-1L, lastHospitalMealDay);
    }

    public long lastHospitalProgressDayTime() {
        return lastHospitalProgressDayTime;
    }

    /** setLastHospitalProgressDayTime：记录上次按世界时间结算住院治疗的 dayTime。 */
    public void setLastHospitalProgressDayTime(long lastHospitalProgressDayTime) {
        this.lastHospitalProgressDayTime = Math.max(0L, lastHospitalProgressDayTime);
    }
}
