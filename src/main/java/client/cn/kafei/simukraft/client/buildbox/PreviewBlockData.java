package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

@OnlyIn(Dist.CLIENT)
public record PreviewBlockData(BlockPos pos, BlockState state, int packedLight, CompoundTag blockEntityData) {
    public PreviewBlockData(BlockPos pos, BlockState state, int packedLight) {
        this(pos, state, packedLight, null);
    }

    public PreviewBlockData {
        blockEntityData = blockEntityData != null ? blockEntityData.copy() : null;
    }

    public CompoundTag copyBlockEntityData() {
        return blockEntityData != null ? blockEntityData.copy() : null;
    }
}
