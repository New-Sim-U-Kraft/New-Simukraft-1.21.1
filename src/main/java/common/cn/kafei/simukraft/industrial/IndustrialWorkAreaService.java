package common.cn.kafei.simukraft.industrial;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

@SuppressWarnings("null")
public final class IndustrialWorkAreaService {
    private IndustrialWorkAreaService() {
    }

    /** workAreaBounds: 按建筑外圈向外扩展，得到辐射型工业的作业矩形。 */
    public static AABB workAreaBounds(PlacedBuildingRecord building, IndustrialDefinition.WorkAreaDefinition workArea) {
        if (building == null || workArea == null || workArea.radius() <= 0) {
            return buildingBounds(building);
        }
        int minX = Math.min(building.minPos().getX(), building.maxPos().getX());
        int maxX = Math.max(building.minPos().getX(), building.maxPos().getX());
        int minY = Math.min(building.minPos().getY(), building.maxPos().getY()) + workArea.minYOffset();
        int maxY = Math.max(building.minPos().getY(), building.maxPos().getY()) + workArea.maxYOffset();
        int minZ = Math.min(building.minPos().getZ(), building.maxPos().getZ());
        int maxZ = Math.max(building.minPos().getZ(), building.maxPos().getZ());
        int radius = Math.max(0, workArea.radius());
        return new AABB(
                minX - radius,
                minY,
                minZ - radius,
                maxX + radius + 1,
                maxY + 1,
                maxZ + radius + 1
        );
    }

    public static boolean insideBuilding(PlacedBuildingRecord building, BlockPos pos) {
        return IndustrialCoordinateResolver.insideBuilding(building, pos);
    }

    private static AABB buildingBounds(PlacedBuildingRecord building) {
        if (building == null) {
            return new AABB(BlockPos.ZERO);
        }
        int minX = Math.min(building.minPos().getX(), building.maxPos().getX());
        int minY = Math.min(building.minPos().getY(), building.maxPos().getY());
        int minZ = Math.min(building.minPos().getZ(), building.maxPos().getZ());
        int maxX = Math.max(building.minPos().getX(), building.maxPos().getX()) + 1;
        int maxY = Math.max(building.minPos().getY(), building.maxPos().getY()) + 1;
        int maxZ = Math.max(building.minPos().getZ(), building.maxPos().getZ()) + 1;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
