package client.cn.kafei.simukraft.client.mineraldrilling;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineralDrillingUiCleanupTest {
    /** clearsBoundsWhenDemolitionIsRequested: 拆除请求清除当前控制箱的边界映射。 */
    @Test
    void clearsBoundsWhenDemolitionIsRequested() {
        BlockPos boxPos = new BlockPos(7, 64, -4);
        BuildingBoundsRenderer.setBuildingBoundsVisible(boxPos, new AABB(boxPos), true);
        assertTrue(BuildingBoundsRenderer.isBuildingBoundsVisible(boxPos));

        MineralDrillingUiFactory.clearBounds(boxPos);

        assertFalse(BuildingBoundsRenderer.isBuildingBoundsVisible(boxPos));
    }
}
