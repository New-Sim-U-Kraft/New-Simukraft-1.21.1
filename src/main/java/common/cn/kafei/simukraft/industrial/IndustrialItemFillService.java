package common.cn.kafei.simukraft.industrial;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.material.GenericSlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class IndustrialItemFillService {
    private static final int DEFAULT_TARGET_COUNT = 64;

    private IndustrialItemFillService() {
    }

    public static ActionResult fill(ServerLevel level,
                                    PlacedBuildingRecord building,
                                    IndustrialDefinition definition,
                                    IndustrialDefinition.StepDefinition step,
                                    Vec3 origin) {
        List<FillCandidate> candidates = candidates(level, step);
        if (candidates.isEmpty() || step.slot() < 0) {
            return ActionResult.INVALID_STEP;
        }
        BlockPos targetPos = IndustrialControlBoxService.resolvePoint(building, definition, step.point(), origin);
        if (targetPos == null) {
            return ActionResult.MISSING_TARGET;
        }
        List<BlockPos> sourceContainers = IndustrialControlBoxService.resolveContainerPositions(
                building,
                definition,
                containerName(step.input(), step.container(), "input")
        );
        if (sourceContainers.isEmpty()) {
            return ActionResult.MISSING_INPUTS;
        }
        ItemStack current = GenericSlotAccess.stackAt(level, targetPos, step.slot());
        if (!current.isEmpty() && candidates.stream().noneMatch(candidate -> candidate.matches(level, current))) {
            return ActionResult.TARGET_BLOCKED;
        }
        int targetCount = targetCount(step);
        int currentCount = current.isEmpty() ? 0 : current.getCount();
        if (step.thresholdCount() >= 0 && currentCount > step.thresholdCount()) {
            return ActionResult.SUCCESS;
        }
        int need = Math.max(0, targetCount - currentCount);
        if (need <= 0) {
            return ActionResult.SUCCESS;
        }
        boolean hadAvailableCandidate = false;
        for (FillCandidate candidate : candidates) {
            if (!current.isEmpty() && !candidate.matches(level, current)) {
                continue;
            }
            int available = IndustrialInventoryService.countInput(level, sourceContainers, candidate.spec());
            if (available <= 0) {
                continue;
            }
            hadAvailableCandidate = true;
            ItemStack targetStack = candidate.stack(level, need);
            int insertable = GenericSlotAccess.countInsertable(level, targetPos, step.slot(), targetStack);
            int moveCount = Math.min(need, Math.min(available, insertable));
            if (moveCount <= 0) {
                continue;
            }
            return moveCandidate(level, sourceContainers, targetPos, step.slot(), candidate, moveCount);
        }
        return hadAvailableCandidate ? ActionResult.TARGET_BLOCKED : ActionResult.MISSING_INPUTS;
    }

    private static ActionResult moveCandidate(ServerLevel level,
                                              List<BlockPos> sourceContainers,
                                              BlockPos targetPos,
                                              int slot,
                                              FillCandidate candidate,
                                              int moveCount) {
        Optional<List<ItemStack>> consumed = IndustrialInventoryService.consumeInputStacks(level, sourceContainers, candidate.spec(), moveCount);
        if (consumed.isEmpty()) {
            return ActionResult.MISSING_INPUTS;
        }
        List<ItemStack> leftovers = new ArrayList<>();
        int inserted = 0;
        for (ItemStack stack : consumed.get()) {
            int before = stack.getCount();
            ItemStack remaining = GenericSlotAccess.insert(level, targetPos, slot, stack);
            inserted += before - remaining.getCount();
            if (!remaining.isEmpty()) {
                leftovers.add(remaining);
            }
        }
        if (!leftovers.isEmpty()) {
            IndustrialInventoryService.insertItems(level, sourceContainers, leftovers);
        }
        return inserted > 0 ? ActionResult.SUCCESS : ActionResult.TARGET_BLOCKED;
    }

    private static List<FillCandidate> candidates(ServerLevel level, IndustrialDefinition.StepDefinition step) {
        List<IndustrialItemStackSpec> specs = new ArrayList<>();
        if (step.itemSpecs() != null && !step.itemSpecs().isEmpty()) {
            specs.addAll(step.itemSpecs());
        } else if (step.inputsOverride()) {
            IndustrialInputRequirements.flattenItems(step.inputs()).forEach(input -> specs.add(input.spec()));
        } else if (step.itemSpec() != null && !step.itemSpec().isEmpty()) {
            specs.add(step.itemSpec());
        } else if (!step.item().isBlank()) {
            specs.add(IndustrialItemStackSpec.of(step.item(), ""));
        }
        Set<String> seen = new LinkedHashSet<>();
        List<FillCandidate> candidates = new ArrayList<>();
        for (IndustrialItemStackSpec spec : specs) {
            if (spec == null || spec.isEmpty() || !seen.add(spec.displayKey())) {
                continue;
            }
            FillCandidate candidate = new FillCandidate(spec);
            if (!candidate.stack(level, 1).isEmpty()) {
                candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
    }

    private static int targetCount(IndustrialDefinition.StepDefinition step) {
        if (step.targetCount() > 0) {
            return step.targetCount();
        }
        if (step.count() > 0) {
            return step.count();
        }
        return DEFAULT_TARGET_COUNT;
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

    public enum ActionResult {
        SUCCESS,
        MISSING_TARGET,
        INVALID_STEP,
        MISSING_INPUTS,
        TARGET_BLOCKED
    }

    private record FillCandidate(IndustrialItemStackSpec spec) {
        private ItemStack stack(ServerLevel level, int count) {
            return spec.stack(Math.max(1, count), level.registryAccess());
        }

        private boolean matches(ServerLevel level, ItemStack current) {
            return spec.matches(current, level.registryAccess());
        }
    }
}
