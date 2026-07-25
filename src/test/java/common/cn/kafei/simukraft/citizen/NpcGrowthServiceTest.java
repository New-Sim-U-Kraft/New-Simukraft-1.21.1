package common.cn.kafei.simukraft.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcGrowthServiceTest {
    @Test
    void ageAdvancesOncePerFourGameDays() {
        assertEquals(0L, NpcGrowthService.completedYears(10L, 13L, 4L));
        assertEquals(1L, NpcGrowthService.completedYears(10L, 14L, 4L));
        assertEquals(3L, NpcGrowthService.completedYears(10L, 22L, 4L));
    }

    @Test
    void childAgeAdvancesOncePerGameDay() {
        assertEquals(0L, NpcGrowthService.completedYears(10L, 10L, 1L));
        assertEquals(1L, NpcGrowthService.completedYears(10L, 11L, 1L));
        assertEquals(5L, NpcGrowthService.completedYears(10L, 15L, 1L));
    }

    @Test
    void invalidOrRewoundDatesDoNotAdvanceAge() {
        assertEquals(0L, NpcGrowthService.completedYears(-1L, 20L, 4L));
        assertEquals(0L, NpcGrowthService.completedYears(20L, 19L, 4L));
    }
}
