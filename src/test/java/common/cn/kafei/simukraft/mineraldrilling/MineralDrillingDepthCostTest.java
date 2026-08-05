package common.cn.kafei.simukraft.mineraldrilling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineralDrillingDepthCostTest {
    /** chargesOnlyNewDownwardSegments: 验证下移跨越十格边界才新增消耗且回升不返还。 */
    @Test
    void chargesOnlyNewDownwardSegments() {
        assertEquals(0, MineralDrillingDepthCost.segmentsForDepth(100, 100));
        assertEquals(1, MineralDrillingDepthCost.segmentsForDepth(100, 91));
        assertEquals(1, MineralDrillingDepthCost.segmentsForDepth(100, 90));
        assertEquals(2, MineralDrillingDepthCost.segmentsForDepth(100, 89));
        assertEquals(0, MineralDrillingDepthCost.additionalSegments(100, 91, 95));
        assertEquals(1, MineralDrillingDepthCost.additionalSegments(100, 91, 89));
        assertEquals(0, MineralDrillingDepthCost.additionalSegments(100, 80, 89));
    }
}
