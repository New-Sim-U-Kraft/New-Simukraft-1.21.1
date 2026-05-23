package common.cn.kafei.simukraft.block;

import common.cn.kafei.simukraft.registry.ModSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

@SuppressWarnings("null")
public final class FarmlandBoxBlock extends Block {
    public FarmlandBoxBlock() {
        super(BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            level.playSound(null, pos, ModSoundEvents.FARMLAND_BOX_PLACE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            level.playSound(null, pos, ModSoundEvents.FARMLAND_BOX_BREAK.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
