package common.cn.kafei.simukraft.mineraldrilling;

import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.job.CityJobMobilityService;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.industrial.IndustrialConstants;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class MineralDrillingUiContractTest {
    /** keepsReferenceSizeAndPanelsInsideWorkspace: 固定参考图上限并验证主要区域不越界。 */
    @Test
    void keepsReferenceSizeAndPanelsInsideWorkspace() {
        MineralDrillingUiMetrics maximum = MineralDrillingUiMetrics.fit(1920, 1080);
        assertEquals(MineralDrillingUiMetrics.MAX_WIDTH, maximum.width());
        assertEquals(MineralDrillingUiMetrics.MAX_HEIGHT, maximum.height());
        assertEquals(204, maximum.topHeight());
        assertEquals(173, maximum.actionWidth());
        assertEquals(194, maximum.leftPanelWidth());
        assertEquals(87, maximum.depthPanelWidth());
        assertEquals(maximum.width(), maximum.panelPadding() * 2
                + maximum.leftPanelWidth() + maximum.middlePanelWidth() + maximum.depthPanelWidth()
                + maximum.contentGap() * 2);
        assertEquals(maximum.width(), maximum.inventoryPanelX()
                + maximum.inventoryPanelWidth() + maximum.panelPadding());
        assertTrue(maximum.playerSlotsX() >= maximum.inventoryPanelX());
        assertTrue(maximum.playerSlotsX() + MineralDrillingUiMetrics.PLAYER_SLOTS_WIDTH
                <= maximum.inventoryPanelX() + maximum.inventoryPanelWidth());

        MineralDrillingUiMetrics minimum = MineralDrillingUiMetrics.fit(320, 232);
        assertEquals(MineralDrillingUiMetrics.MIN_WIDTH, minimum.width());
        assertEquals(MineralDrillingUiMetrics.MIN_HEIGHT, minimum.height());
        assertTrue(minimum.middlePanelWidth() > 0);
        assertTrue(minimum.bottomHeight() >= MineralDrillingUiMetrics.PLAYER_SLOTS_HEIGHT);
    }

    /** letsControlsReceiveClicksOutsideInventorySlots: 全屏槽位容器不能遮挡下层按钮和深度滑块。 */
    @Test
    void letsControlsReceiveClicksOutsideInventorySlots() {
        UIElement slotLayer = MineralDrillingSlotLayout.create(
                new MineralDrillingInventory(), MineralDrillingUiMetrics.maximum());

        assertFalse(slotLayer.isAllowHitTest());
        assertEquals(3, slotLayer.getChildren().size());
        assertTrue(slotLayer.getChildren().stream().allMatch(UIElement::isAllowHitTest));
    }

    /** mapsReleasedScrollerValueToWorldDepth: 释放滑块时保留范围边界并还原反向的世界 Y 坐标。 */
    @Test
    void mapsReleasedScrollerValueToWorldDepth() {
        assertEquals(-64, MineralDrillingDepthPanel.selectedDepth(64.0F, -64, 80));
        assertEquals(80, MineralDrillingDepthPanel.selectedDepth(-80.0F, -64, 80));
        assertEquals(-64, MineralDrillingDepthPanel.selectedDepth(128.0F, -64, 80));
        assertEquals(80, MineralDrillingDepthPanel.selectedDepth(-128.0F, -64, 80));
    }

    /** limitsAndPrioritizesVeinMarkers: 菜单只同步两个矿脉并优先保留当前矿脉。 */
    @Test
    void limitsAndPrioritizesVeinMarkers() {
        MineralDrillingControlBoxView view = new MineralDrillingControlBoxView(
                new BlockPos(4, 70, -3), true, "测试钻井", true, 75.0D,
                1, 0, 2.0D, false, null, "", false,
                -20, -64, 70, "gui.simukraft.mineral_drilling.status.idle", "",
                "铁矿", "minecraft:raw_iron",
                List.of(
                        new MineralDrillingControlBoxView.VeinMarker(
                                "coal", "煤矿", "minecraft:coal", 20, 50, 100, false),
                        new MineralDrillingControlBoxView.VeinMarker(
                                "gold", "金矿", "minecraft:raw_gold", -40, -10, 80, false),
                        new MineralDrillingControlBoxView.VeinMarker(
                                "iron", "铁矿", "minecraft:raw_iron", -30, 15, 120, true)),
                true, new BlockPos(0, 60, -8), new BlockPos(8, 80, 2), true, true);

        MineralDrillingMenuSnapshot snapshot = MineralDrillingMenuSnapshot.fromView(view);
        assertEquals(0.75F, snapshot.integrityPercent());
        assertEquals(2, snapshot.markers().size());
        assertEquals("iron", snapshot.markers().getFirst().veinId());
        assertEquals("coal", snapshot.markers().get(1).veinId());
    }

    /** keepsDrillingWorkplaceDistinctFromIndustry: 钻井复用工业职业但保持独立稳定岗位。 */
    @Test
    void keepsDrillingWorkplaceDistinctFromIndustry() {
        BlockPos boxPos = new BlockPos(12, 64, 12);
        UUID drillingWorkplace = CitizenEmploymentService.workplaceId(
                MineralDrillingConstants.HIRE_SOURCE_TYPE, MineralDrillingConstants.HIRE_ROLE, boxPos);
        UUID industrialWorkplace = CitizenEmploymentService.workplaceId(
                IndustrialConstants.HIRE_SOURCE_TYPE, IndustrialConstants.HIRE_ROLE, boxPos);

        assertNotEquals(industrialWorkplace, drillingWorkplace);
        assertEquals(CityJobType.INDUSTRIAL_WORKER,
                CityJobMobilityService.resolveHireRole(MineralDrillingConstants.HIRE_ROLE));
        assertEquals(CityJobType.INDUSTRIAL_WORKER,
                CitizenEmploymentService.expectedStableWorkplaceJob(drillingWorkplace, boxPos));
    }
}
