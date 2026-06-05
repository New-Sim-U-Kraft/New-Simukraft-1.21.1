package common.cn.kafei.simukraft.commercial;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public record CommercialControlBoxView(BlockPos boxPos,
                                       boolean hasBuilding,
                                       String buildingName,
                                       boolean definitionValid,
                                       String definitionName,
                                       String statusKey,
                                       String statusText,
                                       boolean running,
                                       boolean hasWorker,
                                       UUID workerId,
                                       String workerName,
                                       double cityBalance) {
}
