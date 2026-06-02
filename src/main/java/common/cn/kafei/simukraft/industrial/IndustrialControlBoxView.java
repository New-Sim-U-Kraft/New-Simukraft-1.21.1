package common.cn.kafei.simukraft.industrial;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public record IndustrialControlBoxView(BlockPos boxPos,
                                       boolean hasBuilding,
                                       String buildingName,
                                       boolean definitionValid,
                                       String definitionName,
                                       String statusKey,
                                       String statusText,
                                       boolean running,
                                       String selectedRecipeId,
                                       boolean hasWorker,
                                       UUID workerId,
                                       String workerName,
                                       boolean hasBuildingBounds,
                                       BlockPos boundsMin,
                                       BlockPos boundsMax,
                                       boolean integrityAvailable,
                                       double integrityPercent,
                                       int integrityRepairableBlocks,
                                       int integrityManualRepairBlocks,
                                       double integrityRepairCost,
                                       List<PointMarker> pointMarkers,
                                       List<RecipeEntry> recipes) {
    public record RecipeEntry(String id, String name, List<ItemEntry> inputs, List<ItemEntry> outputs) {
    }

    public record ItemEntry(String itemId, String potionId, int count) {
    }

    public record PointMarker(String id, String kind, BlockPos pos, int color) {
    }
}
