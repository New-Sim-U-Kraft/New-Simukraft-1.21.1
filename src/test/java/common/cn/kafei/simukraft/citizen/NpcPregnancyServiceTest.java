package common.cn.kafei.simukraft.citizen;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcPregnancyServiceTest {
    @Test
    void healthyAdultWifeCanStartPregnancy() {
        CitizenData wife = adultWife();
        assertTrue(NpcPregnancyService.canStartPregnancy(wife, 10L, 8.0D));
    }

    @Test
    void postpartumBlocksNewPregnancyUntilRecoveryDay() {
        CitizenData wife = adultWife();
        wife.medical().setPostpartumUntilDay(12L);
        assertFalse(NpcPregnancyService.canStartPregnancy(wife, 10L, 8.0D));
        assertFalse(NpcPregnancyService.canStartPregnancy(wife, 11L, 8.0D));
        assertTrue(NpcPregnancyService.canStartPregnancy(wife, 12L, 8.0D));
    }

    @Test
    void hospitalAdmissionAndLowHealthBlockNewPregnancy() {
        CitizenData wife = adultWife();
        wife.medical().setMedicalBedPoiId(UUID.randomUUID());
        assertFalse(NpcPregnancyService.canStartPregnancy(wife, 10L, 8.0D));

        wife.medical().setMedicalBedPoiId(null);
        wife.setHealth(8.0D);
        assertFalse(NpcPregnancyService.canStartPregnancy(wife, 10L, 8.0D));
    }

    @Test
    void alreadyPregnantOrMaleCannotStartPregnancy() {
        CitizenData wife = adultWife();
        wife.setPregnant(true);
        assertFalse(NpcPregnancyService.canStartPregnancy(wife, 10L, 8.0D));

        CitizenData husband = adultWife();
        husband.setGender("male");
        assertFalse(NpcPregnancyService.canStartPregnancy(husband, 10L, 8.0D));
    }

    private static CitizenData adultWife() {
        CitizenData wife = new CitizenData(UUID.randomUUID());
        wife.setGender("female");
        wife.setHealth(20.0D);
        return wife;
    }
}
