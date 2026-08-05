package common.cn.kafei.simukraft.fluid;

import common.cn.kafei.simukraft.registry.ModFluids;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.SoundActions;

@SuppressWarnings("null")
public final class MilkBucketPlacementService {
    private MilkBucketPlacementService() {
    }

    public static InteractionResultHolder<ItemStack> tryPourMilk(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        BlockHitResult hit = Item.getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemStack);
        }
        BlockPos clickedPos = hit.getBlockPos();
        BlockPos targetPos = targetPos(level, player, hit);
        if (!level.mayInteract(player, clickedPos) || !player.mayUseItemAt(targetPos, hit.getDirection(), itemStack)) {
            return InteractionResultHolder.fail(itemStack);
        }
        if (!emptyMilkContents(player, level, targetPos, hit)) {
            return InteractionResultHolder.fail(itemStack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, targetPos, itemStack);
        }
        player.awardStat(Stats.ITEM_USED.get(Items.MILK_BUCKET));
        ItemStack resultStack = BucketItem.getEmptySuccessItem(itemStack, player);
        return InteractionResultHolder.sidedSuccess(resultStack, level.isClientSide());
    }

    private static BlockPos targetPos(Level level, Player player, BlockHitResult hit) {
        BlockPos clickedPos = hit.getBlockPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        return canBlockContainMilk(level, player, clickedPos, clickedState) ? clickedPos : clickedPos.relative(hit.getDirection());
    }

    private static boolean emptyMilkContents(Player player, Level level, BlockPos pos, BlockHitResult hit) {
        Fluid fluid = ModFluids.SOURCE_MILK.get();
        if (!(fluid instanceof FlowingFluid flowingFluid)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        boolean replaceable = state.canBeReplaced(fluid);
        boolean sameFluidNonSource = state.getFluidState().getType().isSame(fluid) && !state.getFluidState().isSource();
        boolean canPlace = state.isAir()
                || replaceable
                || sameFluidNonSource
                || block instanceof LiquidBlockContainer container && container.canPlaceLiquid(player, level, pos, state, fluid);
        if (!canPlace) {
            return hit != null && emptyMilkContents(player, level, hit.getBlockPos().relative(hit.getDirection()), null);
        }
        if (block instanceof LiquidBlockContainer container && container.canPlaceLiquid(player, level, pos, state, fluid)) {
            container.placeLiquid(level, pos, state, flowingFluid.getSource(false));
            playEmptySound(player, level, pos, fluid);
            return true;
        }
        if (!level.isClientSide && replaceable && state.getFluidState().isEmpty()) {
            level.destroyBlock(pos, true);
        }
        if (!level.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), 11) && !state.getFluidState().isSource()) {
            return false;
        }
        playEmptySound(player, level, pos, fluid);
        return true;
    }

    private static boolean canBlockContainMilk(Level level, Player player, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof LiquidBlockContainer container
                && container.canPlaceLiquid(player, level, pos, state, ModFluids.SOURCE_MILK.get());
    }

    private static void playEmptySound(Player player, Level level, BlockPos pos, Fluid fluid) {
        SoundEvent sound = fluid.getFluidType().getSound(player, level, pos, SoundActions.BUCKET_EMPTY);
        level.playSound(player, pos, sound != null ? sound : SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
    }
}
