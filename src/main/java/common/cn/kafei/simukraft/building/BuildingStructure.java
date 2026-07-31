package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;

import java.util.List;

public record BuildingStructure(String category,
                                String displayName,
                                String fileName,
                                String amount,
                                String structureFileName,
                                String author,
                                String sizeText,
                                BlockPos size,
                                List<BuildingBlockData> blocks,
                                List<BuildingEntityData> entities,
                                List<BuildingPoiDefinition> poiDefinitions,
                                BlockPos anchor,
                                int blockCount) {
}
