package common.cn.kafei.simukraft.medical;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalServiceTest {
    @Test
    void ignoresNullMedicalBedIdsWhenMatchingPatients() {
        UUID bedId = UUID.randomUUID();
        Set<UUID> bedIds = Set.of(bedId);

        assertFalse(MedicalService.containsMedicalBed(bedIds, null));
        assertFalse(MedicalService.containsMedicalBed(bedIds, UUID.randomUUID()));
        assertTrue(MedicalService.containsMedicalBed(bedIds, bedId));
    }

    @Test
    void diseaseBypassesResidentialCoverageForAdmission() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());

        assertFalse(MedicalService.canBypassResidentialCoverage(citizen, 8.0D));
        citizen.setDisease(DiseaseType.COLD, 1L);
        assertTrue(MedicalService.canBypassResidentialCoverage(citizen, 8.0D));
    }

    @Test
    void lowHealthBypassesResidentialCoverageForAdmission() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());
        citizen.setHealth(8.0D);

        assertTrue(MedicalService.canBypassResidentialCoverage(citizen, 8.0D));
        assertFalse(MedicalService.canBypassResidentialCoverage(citizen, 7.9D));
    }

    @Test
    void lowHealthPutsCitizenOnMedicalLeaveBeforeAdmission() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());
        citizen.setHealth(8.0D);

        assertTrue(MedicalService.isOnMedicalLeave(citizen, 0L, 8.0D));
        assertFalse(MedicalService.isOnMedicalLeave(citizen, 0L, 7.9D));
    }

    @Test
    void recoveredCitizenClearsUnadmittedMedicalLeave() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());
        citizen.setHealth(20.0D);
        citizen.setWorkStatus(CitizenWorkStatus.RESTING);
        citizen.setWorkNeedDetail(MedicalService.MEDICAL_CARE_MARKER);
        citizen.setStatusLabel("medical.low_health");

        assertTrue(MedicalService.shouldClearMedicalLeave(citizen, 0L, 8.0D));

        citizen.setPregnant(true);
        citizen.setPregnantSince(0L);
        assertFalse(MedicalService.shouldClearMedicalLeave(citizen, 0L, 8.0D, 3));
        assertFalse(MedicalService.shouldClearMedicalLeave(citizen, 2L, 8.0D, 3));
    }

    @Test
    void anyPregnancyStageNeedsCareWhenHealthy() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());
        citizen.setHealth(20.0D);
        citizen.setPregnant(true);
        citizen.setPregnantSince(5L);

        assertTrue(MedicalService.needsCare(null, citizen, 5L, 8.0D, 3));
        assertTrue(MedicalService.needsCare(null, citizen, 6L, 8.0D, 3));
        assertTrue(MedicalService.needsCare(null, citizen, 7L, 8.0D, 3));
        assertTrue(MedicalService.isOnMedicalLeave(citizen, 5L, 8.0D, 3));
        assertTrue(MedicalService.isOnMedicalLeave(citizen, 7L, 8.0D, 3));
    }

    @Test
    void postpartumAndLowHealthStillNeedCare() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());
        citizen.setHealth(20.0D);
        citizen.medical().setPostpartumUntilDay(4L);
        assertTrue(MedicalService.needsCare(null, citizen, 3L, 8.0D, 3));
        assertFalse(MedicalService.needsCare(null, citizen, 4L, 8.0D, 3));

        citizen.medical().setPostpartumUntilDay(0L);
        citizen.setHealth(8.0D);
        assertTrue(MedicalService.needsCare(null, citizen, 4L, 8.0D, 3));
    }

    @Test
    void hospitalWorldTimeCountsSleepSkip() {
        assertEquals(0L, MedicalService.hospitalWorldTimeElapsed(0L, 24_000L));
        assertEquals(0L, MedicalService.hospitalWorldTimeElapsed(18_000L, 18_000L));
        assertEquals(0L, MedicalService.hospitalWorldTimeElapsed(24_000L, 18_000L));
        assertEquals(6_000L, MedicalService.hospitalWorldTimeElapsed(18_000L, 24_000L));
    }

    @Test
    void hospitalHealPulsesCountSleepSkip() {
        assertEquals(0L, MedicalService.hospitalHealPulses(0L, 24_000L, 100));
        assertEquals(0L, MedicalService.hospitalHealPulses(100L, 199L, 100));
        assertEquals(1L, MedicalService.hospitalHealPulses(99L, 100L, 100));
        assertEquals(60L, MedicalService.hospitalHealPulses(18_000L, 24_000L, 100));
    }
}
