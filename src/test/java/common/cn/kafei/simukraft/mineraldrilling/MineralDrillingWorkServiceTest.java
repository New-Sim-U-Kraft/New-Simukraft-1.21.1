package common.cn.kafei.simukraft.mineraldrilling;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineralDrillingWorkServiceTest {
    /** consumesAtSelectedDrillDepth: 防止用控制箱高度校验地下矿脉，导致误报矿脉枯竭。 */
    @Test
    void consumesAtSelectedDrillDepth() {
        BlockPos controlBox = new BlockPos(7113, 131, -1206);

        assertEquals(new BlockPos(7113, -64, -1206),
                MineralDrillingWorkService.drillTargetPos(controlBox, -64));
    }

    /** splitsProductionAcrossOverlappingVeins: 同一 Y 层命中两个矿脉时每条矿脉仅取得一半产量。 */
    @Test
    void splitsProductionAcrossOverlappingVeins() {
        assertEquals(10, MineralDrillingWorkService.productionAmountPerVein(10, 1));
        assertEquals(5, MineralDrillingWorkService.productionAmountPerVein(10, 2));
        assertEquals(4, MineralDrillingWorkService.productionAmountPerVein(9, 2));
        assertEquals(1, MineralDrillingWorkService.productionAmountPerVein(1, 2));
    }
}
