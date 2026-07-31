package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public record BuildingBlockData(BlockPos relativePos,
                                BlockState state,
                                BlockPos originalStructurePos,
                                CompoundTag blockEntityData) {
    public BuildingBlockData(BlockPos relativePos, BlockState state, BlockPos originalStructurePos) {
        this(relativePos, state, originalStructurePos, null);
    }

    public BuildingBlockData {
        blockEntityData = blockEntityData != null ? blockEntityData.copy() : null;
    }

    public CompoundTag copyBlockEntityData() {
        return blockEntityData != null ? blockEntityData.copy() : null;
    }
}
