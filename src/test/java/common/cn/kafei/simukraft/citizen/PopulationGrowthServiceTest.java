package common.cn.kafei.simukraft.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopulationGrowthServiceTest {
    /** growthCheckRunsOnceAfterNoon：验证每日到达或越过中午后仅检查一次。 */
    @Test
    void growthCheckRunsOnceAfterNoon() {
        assertFalse(PopulationGrowthService.shouldRunGrowth(5_999L, -1L));
        assertTrue(PopulationGrowthService.shouldRunGrowth(6_000L, -1L));
        assertTrue(PopulationGrowthService.shouldRunGrowth(6_001L, -1L));
        assertFalse(PopulationGrowthService.shouldRunGrowth(6_001L, 0L));
        assertTrue(PopulationGrowthService.shouldRunGrowth(30_000L, 0L));
    }
}
