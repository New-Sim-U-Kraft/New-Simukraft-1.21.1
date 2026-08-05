package common.cn.kafei.simukraft.mineraldrilling;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

/** MineralDrillingControlBoxView: 供钻井容器界面读取的服务端不可变快照。 */
public record MineralDrillingControlBoxView(
        BlockPos boxPos,
        boolean hasBuilding,
        String buildingName,
        boolean integrityAvailable,
        double integrityPercent,
        int repairableBlocks,
        int manualRepairBlocks,
        double repairCost,
        boolean hasWorker,
        UUID workerId,
        String workerName,
        boolean running,
        int drillDepth,
        int minDepth,
        int maxDepth,
        String statusKey,
        String statusText,
        String selectedMineralName,
        String selectedProductId,
        List<VeinMarker> veinMarkers,
        boolean hasBuildingBounds,
        BlockPos boundsMin,
        BlockPos boundsMax,
        boolean hasDrillRod,
        boolean hasDrillBit
) {
    public MineralDrillingControlBoxView {
        boxPos = boxPos == null ? BlockPos.ZERO : boxPos.immutable();
        buildingName = safe(buildingName);
        statusKey = safe(statusKey);
        statusText = safe(statusText);
        selectedMineralName = safe(selectedMineralName);
        selectedProductId = safe(selectedProductId);
        veinMarkers = veinMarkers == null ? List.of() : List.copyOf(veinMarkers);
        boundsMin = boundsMin == null ? BlockPos.ZERO : boundsMin.immutable();
        boundsMax = boundsMax == null ? BlockPos.ZERO : boundsMax.immutable();
        integrityPercent = Math.max(0.0D, Math.min(100.0D, integrityPercent));
        minDepth = Math.min(minDepth, maxDepth);
        drillDepth = Math.max(minDepth, Math.min(maxDepth, drillDepth));
    }

    /** veins: 提供兼容别名，使界面布局不依赖网络快照字段命名。 */
    public List<VeinMarker> veins() {
        return veinMarkers;
    }

    /** markers: 返回矿脉标记兼容别名，供旧版界面绑定读取。 */
    public List<VeinMarker> markers() {
        return veinMarkers;
    }

    /** depthMin: 返回深度范围下界兼容别名。 */
    public int depthMin() {
        return minDepth;
    }

    /** depthMax: 返回深度范围上界兼容别名。 */
    public int depthMax() {
        return maxDepth;
    }

    /** hasBounds: 返回是否存在可显示的建筑边界。 */
    public boolean hasBounds() {
        return hasBuildingBounds;
    }

    /** selectedVeinName: 返回当前选中矿脉名称兼容别名。 */
    public String selectedVeinName() {
        return selectedMineralName;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record VeinMarker(
            String veinId,
            String displayName,
            String productId,
            int minY,
            int maxY,
            int remainingReserve,
            boolean selected
    ) {
        public VeinMarker {
            veinId = safe(veinId);
            displayName = safe(displayName);
            productId = safe(productId);
            if (minY > maxY) {
                int swap = minY;
                minY = maxY;
                maxY = swap;
            }
            remainingReserve = Math.max(0, remainingReserve);
        }

        /** y: 返回矿脉范围的中心深度，供标记定位使用。 */
        public int y() {
            return minY + (maxY - minY) / 2;
        }
    }
}
