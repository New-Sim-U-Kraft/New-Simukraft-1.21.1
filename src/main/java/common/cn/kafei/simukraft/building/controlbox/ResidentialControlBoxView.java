package common.cn.kafei.simukraft.building.controlbox;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public record ResidentialControlBoxView(BlockPos controlBoxPos,
                                        String buildingName,
                                        String buildingTypeKey,
                                        int residentCount,
                                        int capacity,
                                        List<ResidentEntry> residents,
                                        boolean hasBuildingBounds,
                                        BlockPos boundsMin,
                                        BlockPos boundsMax,
                                        List<BlockPos> residentialPoiPositions,
                                        boolean integrityAvailable,
                                        double integrityPercent,
                                        int integrityRepairableBlocks,
                                        int integrityManualRepairBlocks,
                                        double integrityRepairCost) {
    public record ResidentEntry(UUID citizenId, String name) {
    }
}
