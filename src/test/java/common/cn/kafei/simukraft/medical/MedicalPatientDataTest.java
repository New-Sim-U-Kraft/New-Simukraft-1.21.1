package common.cn.kafei.simukraft.medical;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MedicalPatientDataTest {
    @Test
    void medicalStateRoundTripsThroughNbt() {
        UUID bedId = UUID.randomUUID();
        MedicalPatientData source = new MedicalPatientData();
        source.setDisease(DiseaseType.FLU, 7L);
        source.addDiseaseTreatmentTicks(120L);
        source.setMedicalBedPoiId(bedId);
        source.setPostpartumUntilDay(11L);
        source.setLastHospitalMealDay(9L);
        source.setLastHospitalProgressDayTime(18_000L);

        CompoundTag tag = new CompoundTag();
        source.toTag(tag);
        MedicalPatientData loaded = new MedicalPatientData();
        loaded.fromTag(tag);

        assertEquals(DiseaseType.FLU, loaded.disease());
        assertEquals(7L, loaded.diseaseSinceDay());
        assertEquals(120L, loaded.diseaseTreatmentTicks());
        assertEquals(bedId, loaded.medicalBedPoiId());
        assertEquals(11L, loaded.postpartumUntilDay());
        assertEquals(9L, loaded.lastHospitalMealDay());
        assertEquals(18_000L, loaded.lastHospitalProgressDayTime());
    }

    @Test
    void clearRemovesTemporaryMedicalState() {
        MedicalPatientData data = new MedicalPatientData();
        data.setDisease(DiseaseType.COLD, 2L);
        data.setMedicalBedPoiId(UUID.randomUUID());
        data.setPostpartumUntilDay(4L);
        data.setLastHospitalMealDay(3L);
        data.setLastHospitalProgressDayTime(12_542L);

        data.clear();

        assertEquals(DiseaseType.NONE, data.disease());
        assertNull(data.medicalBedPoiId());
        assertEquals(0L, data.postpartumUntilDay());
        assertEquals(-1L, data.lastHospitalMealDay());
        assertEquals(0L, data.lastHospitalProgressDayTime());
    }
}
