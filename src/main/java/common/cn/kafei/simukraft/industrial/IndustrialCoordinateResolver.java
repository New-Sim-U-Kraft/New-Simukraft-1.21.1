package common.cn.kafei.simukraft.industrial;

import common.cn.kafei.simukraft.building.BuildingTransform;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
public final class IndustrialCoordinateResolver {
    private IndustrialCoordinateResolver() {
    }

    /** resolvePositions: 将工业/商业 JSON 中的容器或工作点结构坐标解析为世界坐标。 */
    public static List<BlockPos> resolvePositions(PlacedBuildingRecord building, List<BlockPos> structurePositions) {
        if (building == null || structurePositions == null || structurePositions.isEmpty()) {
            return List.of();
        }
        int rotation = rotationDegrees(building.facing());
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos structurePos : structurePositions) {
            if (structurePos == null) {
                continue;
            }
            BlockPos worldPos = resolvePosition(building, structurePos, rotation);
            if (worldPos != null) {
                positions.add(worldPos.immutable());
            }
        }
        return List.copyOf(positions);
    }

    /** selectPoint: 根据选择模式返回固定点或距离 NPC 最近的工作点。 */
    public static BlockPos selectPoint(PlacedBuildingRecord building, IndustrialDefinition.PointDefinition point, Vec3 origin) {
        List<BlockPos> positions = resolvePositions(building, point != null ? point.positions() : List.of());
        if (positions.isEmpty()) {
            return null;
        }
        if (point.selectionMode() == IndustrialDefinition.SelectionMode.ORDERED || origin == null) {
            return positions.getFirst();
        }
        return positions.stream()
                .min(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(origin)))
                .orElse(positions.getFirst());
    }

    /** insideBuilding: 判断坐标是否落在已建建筑记录范围内。 */
    public static boolean insideBuilding(PlacedBuildingRecord building, BlockPos pos) {
        if (building == null || pos == null) {
            return false;
        }
        return pos.getX() >= Math.min(building.minPos().getX(), building.maxPos().getX())
                && pos.getX() <= Math.max(building.minPos().getX(), building.maxPos().getX())
                && pos.getY() >= Math.min(building.minPos().getY(), building.maxPos().getY())
                && pos.getY() <= Math.max(building.minPos().getY(), building.maxPos().getY())
                && pos.getZ() >= Math.min(building.minPos().getZ(), building.maxPos().getZ())
                && pos.getZ() <= Math.max(building.minPos().getZ(), building.maxPos().getZ());
    }

    /** rotationDegrees: 将已建建筑朝向文本转换为结构旋转角度。 */
    private static int rotationDegrees(String facing) {
        String normalized = facing == null ? "" : facing.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "east" -> 90;
            case "south" -> 180;
            case "west" -> 270;
            default -> 0;
        };
    }

    /** resolvePosition: 兼容旧版世界坐标、旋转结构坐标和早期未旋转相对坐标。 */
    private static BlockPos resolvePosition(PlacedBuildingRecord building, BlockPos storedPos, int rotation) {
        if (insideBuilding(building, storedPos)) {
            return storedPos;
        }
        BlockPos rotated = building.worldOrigin().offset(BuildingTransform.rotatePosition(storedPos, rotation));
        if (insideBuilding(building, rotated)) {
            return rotated;
        }
        BlockPos unrotated = building.worldOrigin().offset(storedPos);
        return insideBuilding(building, unrotated) ? unrotated : rotated;
    }
}
