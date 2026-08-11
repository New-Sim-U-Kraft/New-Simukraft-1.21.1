package common.cn.kafei.simukraft.medical;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

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
        assertFalse(MedicalService.shouldClearMedicalLeave(citizen, 0L, 8.0D));
    }
}
