package common.cn.kafei.simukraft.industrial;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.SoundActions;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public final class IndustrialBlockActionService {
    private IndustrialBlockActionService() {
    }

    /**
     * placeBlock：按 JSON 指定的结构坐标放置方块，可选从容器消耗材料。
     */
    public static ActionResult placeBlock(ServerLevel level,
                                          PlacedBuildingRecord building,
                                          IndustrialDefinition definition,
                                          IndustrialDefinition.StepDefinition step,
                                          CitizenEntity entity) {
        Block block = resolveBlock(!step.block().isBlank() ? step.block() : step.item());
        if (block == null || block == Blocks.AIR) {
            return ActionResult.INVALID_BLOCK;
        }
        List<BlockPos> targets = targets(building, definition, step);
        if (targets.isEmpty()) {
            return ActionResult.MISSING_TARGET;
        }
        BlockState targetState = block.defaultBlockState();
        List<BlockPos> changes = new ArrayList<>();
        for (BlockPos target : targets) {
            if (!level.isLoaded(target)) {
                return ActionResult.BLOCKED;
            }
            BlockState current = level.getBlockState(target);
            if (current.is(block)) {
                continue;
            }
            if (!step.replace() && !current.isAir() && current.getFluidState().isEmpty()) {
                return ActionResult.BLOCKED;
            }
            changes.add(target);
        }
        if (!consumeMaterial(level, building, definition, step, materialItemId(step, block), changes.size())) {
            return ActionResult.MISSING_INPUTS;
        }
        for (BlockPos target : changes) {
            level.setBlock(target, targetState, 3);
        }
        swing(entity, step, !changes.isEmpty());
        return ActionResult.SUCCESS;
    }

    /**
     * placeFluid：按 JSON 指定的结构坐标放置液体，可选从容器消耗物品。
     */
    public static ActionResult placeFluid(ServerLevel level,
                                          PlacedBuildingRecord building,
                                          IndustrialDefinition definition,
                                          IndustrialDefinition.StepDefinition step,
                                          CitizenEntity entity) {
        Fluid fluid = resolveFluid(step.fluid());
        if (fluid == null || fluid == Fluids.EMPTY || !(fluid instanceof FlowingFluid flowingFluid)) {
            return ActionResult.INVALID_FLUID;
        }
        List<BlockPos> targets = targets(building, definition, step);
        if (targets.isEmpty()) {
            return ActionResult.MISSING_TARGET;
        }
        List<BlockPos> changes = new ArrayList<>();
        for (BlockPos target : targets) {
            if (!level.isLoaded(target)) {
                return ActionResult.BLOCKED;
            }
            BlockState current = level.getBlockState(target);
            if (current.getFluidState().getType().isSame(fluid) && current.getFluidState().isSource()) {
                continue;
            }
            if (!canPlaceFluid(level, target, current, fluid, step.replace())) {
                return ActionResult.BLOCKED;
            }
            changes.add(target);
        }
        if (!consumeMaterial(level, building, definition, step, fluidItemId(step, fluid), changes.size())) {
            return ActionResult.MISSING_INPUTS;
        }
        for (BlockPos target : changes) {
            if (!placeFluidLikeBucket(level, target, level.getBlockState(target), fluid, flowingFluid, step.replace(), entity)) {
                return ActionResult.BLOCKED;
            }
        }
        swing(entity, step, !changes.isEmpty());
        return ActionResult.SUCCESS;
    }

    /**
     * destroyBlock：按 JSON 指定的结构坐标破坏方块，默认不生成掉落物。
     */
    public static ActionResult destroyBlock(ServerLevel level,
                                            PlacedBuildingRecord building,
                                            IndustrialDefinition definition,
                                            IndustrialDefinition.StepDefinition step,
                                            CitizenEntity entity) {
        List<BlockPos> targets = targets(building, definition, step);
        if (targets.isEmpty()) {
            return ActionResult.MISSING_TARGET;
        }
        boolean changed = false;
        for (BlockPos target : targets) {
            if (!level.isLoaded(target) || IndustrialControlBoxService.isIndustrialControlBox(level, target)) {
                return ActionResult.BLOCKED;
            }
            if (level.getBlockState(target).isAir()) {
                continue;
            }
            level.destroyBlock(target, step.dropItems());
            changed = true;
        }
        swing(entity, step, changed);
        return ActionResult.SUCCESS;
    }

    /**
     * requireBlock：等待指定结构坐标中出现目标方块。
     */
    public static ActionResult requireBlock(ServerLevel level,
                                            PlacedBuildingRecord building,
                                            IndustrialDefinition definition,
                                            IndustrialDefinition.StepDefinition step) {
        Block block = resolveBlock(step.block());
        if (block == null || block == Blocks.AIR) {
            return ActionResult.INVALID_BLOCK;
        }
        List<BlockPos> targets = targets(building, definition, step);
        if (targets.isEmpty()) {
            return ActionResult.MISSING_TARGET;
        }
        int requiredCount = Math.max(1, step.count());
        int matches = 0;
        for (BlockPos target : targets) {
            if (!level.isLoaded(target)) {
                return ActionResult.BLOCKED;
            }
            if (level.getBlockState(target).is(block) && ++matches >= requiredCount) {
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.BLOCKED;
    }

    private static List<BlockPos> targets(PlacedBuildingRecord building, IndustrialDefinition definition, IndustrialDefinition.StepDefinition step) {
        if (!step.positions().isEmpty()) {
            return IndustrialCoordinateResolver.resolvePositions(building, step.positions());
        }
        if (definition == null || step.point().isBlank()) {
            return List.of();
        }
        IndustrialDefinition.PointDefinition point = definition.points().get(step.point());
        if (point == null || !"structure_pos".equalsIgnoreCase(point.type())) {
            return List.of();
        }
        return IndustrialCoordinateResolver.resolvePositions(building, point.positions());
    }

    private static boolean consumeMaterial(ServerLevel level,
                                           PlacedBuildingRecord building,
                                           IndustrialDefinition definition,
                                           IndustrialDefinition.StepDefinition step,
                                           String itemId,
                                           int count) {
        if (!step.consume() || count <= 0) {
            return true;
        }
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        List<BlockPos> containers = IndustrialControlBoxService.resolveContainerPositions(building, definition,
                containerName(step.container(), step.input(), "input"));
        return !containers.isEmpty() && IndustrialInventoryService.consumeInput(level, containers, itemId, "", count);
    }

    private static String materialItemId(IndustrialDefinition.StepDefinition step, Block block) {
        if (!step.item().isBlank()) {
            return step.item();
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null ? id.toString() : "";
    }

    private static String fluidItemId(IndustrialDefinition.StepDefinition step, Fluid fluid) {
        if (!step.item().isBlank()) {
            return step.item();
        }
        Item bucket = fluid.getBucket();
        if (bucket == null || bucket == Items.AIR) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(bucket);
        return id != null ? id.toString() : "";
    }

    private static boolean canPlaceFluid(ServerLevel level, BlockPos target, BlockState current, Fluid fluid, boolean forceReplace) {
        if (current.getFluidState().getType().isSame(fluid) && !current.getFluidState().isSource()) {
            return true;
        }
        if (current.isAir() || current.canBeReplaced(fluid)) {
            return true;
        }
        if (current.getBlock() instanceof LiquidBlockContainer container
                && container.canPlaceLiquid(null, level, target, current, fluid)) {
            return true;
        }
        return forceReplace && !IndustrialControlBoxService.isIndustrialControlBox(level, target);
    }

    private static boolean placeFluidLikeBucket(ServerLevel level,
                                                BlockPos target,
                                                BlockState current,
                                                Fluid fluid,
                                                FlowingFluid flowingFluid,
                                                boolean forceReplace,
                                                CitizenEntity entity) {
        Block block = current.getBlock();
        if (block instanceof LiquidBlockContainer container && container.canPlaceLiquid(null, level, target, current, fluid)) {
            container.placeLiquid(level, target, current, flowingFluid.getSource(false));
            playFluidEmptySound(level, target, fluid, entity);
            return true;
        }
        if (level.dimensionType().ultraWarm() && fluid.defaultFluidState().is(FluidTags.WATER)) {
            vaporizeWater(level, target);
            return true;
        }
        if (!current.isAir() && (current.canBeReplaced(fluid) || forceReplace) && current.getFluidState().isEmpty()) {
            level.destroyBlock(target, current.canBeReplaced(fluid));
        }
        if (!level.setBlock(target, fluid.defaultFluidState().createLegacyBlock(), 11) && !current.getFluidState().isSource()) {
            return false;
        }
        playFluidEmptySound(level, target, fluid, entity);
        return true;
    }

    private static void playFluidEmptySound(ServerLevel level, BlockPos target, Fluid fluid, CitizenEntity entity) {
        SoundEvent sound = fluid.getFluidType().getSound(null, level, target, SoundActions.BUCKET_EMPTY);
        level.playSound(null, target, sound != null ? sound : SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(entity, net.minecraft.world.level.gameevent.GameEvent.FLUID_PLACE, target);
    }

    private static void vaporizeWater(ServerLevel level, BlockPos target) {
        level.playSound(null, target, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                0.5F, 2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);
        for (int index = 0; index < 8; index++) {
            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    target.getX() + Math.random(),
                    target.getY() + Math.random(),
                    target.getZ() + Math.random(),
                    0.0D,
                    0.0D,
                    0.0D);
        }
    }

    private static String containerName(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary;
        }
        return fallback;
    }

    private static Block resolveBlock(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        return id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
    }

    private static Fluid resolveFluid(String fluidId) {
        ResourceLocation id = ResourceLocation.tryParse(fluidId);
        return id == null ? null : BuiltInRegistries.FLUID.getOptional(id).orElse(null);
    }

    private static void swing(CitizenEntity entity, IndustrialDefinition.StepDefinition step, boolean changed) {
        if (changed && entity != null && step.swing()) {
            entity.triggerWorkSwing(InteractionHand.MAIN_HAND);
        }
    }

    public enum ActionResult {
        SUCCESS,
        MISSING_TARGET,
        INVALID_BLOCK,
        INVALID_FLUID,
        MISSING_INPUTS,
        BLOCKED
    }
}
