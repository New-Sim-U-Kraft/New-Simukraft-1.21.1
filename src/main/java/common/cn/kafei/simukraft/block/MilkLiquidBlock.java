package common.cn.kafei.simukraft.block;

import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

@SuppressWarnings("null")
public class MilkLiquidBlock extends LiquidBlock {
    private static final int COAGULATION_TICKS = 1200;

    public MilkLiquidBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, COAGULATION_TICKS);
            reactWithNeighbors(state, level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide()) {
            reactWithNeighbors(state, level, pos);
        }
    }

    private void reactWithNeighbors(BlockState state, Level level, BlockPos pos) {
        ServerLevel serverLevel = (ServerLevel) level;
        for (Direction dir : Direction.values()) {
            FluidState adj = level.getFluidState(pos.relative(dir));

            if (adj.is(Fluids.LAVA) || adj.is(Fluids.FLOWING_LAVA)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.05);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                        0.5f, 2.6f + (level.random.nextFloat() - level.random.nextFloat()) * 0.8f);
                return;
            }

            if (adj.is(Fluids.WATER) || adj.is(Fluids.FLOWING_WATER)) {
                level.setBlockAndUpdate(pos, adj.isSource() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                return;
            }
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (state.is(this) && state.getFluidState().isSource()) {
            level.scheduleTick(pos, this, COAGULATION_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (state.is(this) && state.getFluidState().isSource()) {
            level.setBlockAndUpdate(pos, ModBlocks.CHEESE_BLOCK.get().defaultBlockState());
        }
    }
}
