package common.cn.kafei.simukraft.industrial;

import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("null")
public final class IndustrialInventoryService {
    private IndustrialInventoryService() {
    }

    public static boolean hasInputs(ServerLevel level, List<BlockPos> containers, List<IndustrialDefinition.InputRequirement> inputs) {
        return planInputs(level, containers, inputs, 1).isPresent();
    }

    public static boolean hasOutputSpace(ServerLevel level, List<BlockPos> containers, List<ItemStack> outputs) {
        List<ItemStack> remaining = outputs.stream().map(ItemStack::copy).toList();
        for (BlockPos container : containers) {
            if (!GenericContainerAccess.isContainer(level, container)) {
                continue;
            }
            remaining = GenericContainerAccess.simulateInsert(level, container, remaining);
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return remaining.isEmpty();
    }

    public static boolean craftRecipe(ServerLevel level,
                                      List<BlockPos> inputContainers,
                                      List<BlockPos> outputContainers,
                                      IndustrialDefinition.RecipeDefinition recipe,
                                      double outputMultiplier,
                                      RandomSource random) {
        return craftRecipe(level, inputContainers, outputContainers, recipe, outputMultiplier, random, 1);
    }

    /**
     * craftAvailableRecipe: 按当前可消耗输入数量批量执行同一个配方。
     */
    public static boolean craftAvailableRecipe(ServerLevel level,
                                               List<BlockPos> inputContainers,
                                               List<BlockPos> outputContainers,
                                               IndustrialDefinition.RecipeDefinition recipe,
                                               double outputMultiplier,
                                               RandomSource random) {
        int craftCount = availableCraftCount(level, inputContainers, recipe);
        return craftCount > 0 && craftRecipe(level, inputContainers, outputContainers, recipe, outputMultiplier, random, craftCount);
    }

    private static boolean craftRecipe(ServerLevel level,
                                       List<BlockPos> inputContainers,
                                       List<BlockPos> outputContainers,
                                       IndustrialDefinition.RecipeDefinition recipe,
                                       double outputMultiplier,
                                       RandomSource random,
                                       int craftCount) {
        if (level == null || recipe == null || craftCount <= 0) {
            return false;
        }
        Optional<IndustrialInputPlan> inputPlan = planInputs(level, inputContainers, recipe.inputs(), craftCount);
        if (inputPlan.isEmpty()) {
            return false;
        }
        List<ItemStack> outputs = buildOutputs(recipe, outputMultiplier, random, craftCount, level.registryAccess());
        Optional<List<ItemStack>> consumed = consumePlannedInputs(level, inputContainers, inputPlan.get());
        if (consumed.isEmpty()) {
            return false;
        }
        if (!hasOutputSpace(level, outputContainers, outputs)) {
            rollbackInputs(level, inputContainers, consumed.get());
            return false;
        }
        if (!insertOutputs(level, outputContainers, outputs)) {
            rollbackInputs(level, inputContainers, consumed.get());
            return false;
        }
        return true;
    }

    public static List<ItemStack> buildOutputs(IndustrialDefinition.RecipeDefinition recipe, double outputMultiplier, RandomSource random) {
        return buildOutputs(recipe, outputMultiplier, random, 1, null);
    }

    private static List<ItemStack> buildOutputs(IndustrialDefinition.RecipeDefinition recipe,
                                                double outputMultiplier,
                                                RandomSource random,
                                                int craftCount,
                                                HolderLookup.Provider registries) {
        List<ItemStack> outputs = new ArrayList<>();
        RandomSource safeRandom = random != null ? random : RandomSource.create();
        double safeMultiplier = Math.max(0.0D, outputMultiplier);
        for (int craft = 0; craft < craftCount; craft++) {
            for (IndustrialDefinition.ProductOutput output : recipe.outputs()) {
                if (safeRandom.nextDouble() > output.probability()) {
                    continue;
                }
                int randomBonus = output.randomRange() > 0 ? safeRandom.nextInt(output.randomRange()) : 0;
                int amount = output.baseAmount() + randomBonus;
                if (!output.ignoreMultiplier()) {
                    amount = Math.max(1, (int) Math.floor(amount * safeMultiplier));
                }
                ItemStack stack = output.spec().stack(amount, registries);
                if (!stack.isEmpty()) {
                    addOutputStack(outputs, stack);
                }
            }
        }
        return List.copyOf(outputs);
    }

    public static ItemStack stackForItem(String itemId, int count) {
        return IndustrialItemStackSpec.of(itemId, "").stack(count);
    }

    public static ItemStack stackForItem(String itemId, String potionId, int count) {
        return IndustrialItemStackSpec.of(itemId, potionId).stack(count);
    }

    public static ItemStack stackForSpec(IndustrialItemStackSpec spec, HolderLookup.Provider registries, int count) {
        return spec != null ? spec.stack(count, registries) : ItemStack.EMPTY;
    }

    public static boolean consumeInput(ServerLevel level, List<BlockPos> containers, String itemId, String potionId, int count) {
        return consumeInput(level, containers, IndustrialItemStackSpec.of(itemId, potionId), count);
    }

    public static boolean consumeInput(ServerLevel level, List<BlockPos> containers, IndustrialItemStackSpec spec, int count) {
        return consumeInputStacks(level, containers, spec, count).isPresent();
    }

    public static Optional<List<ItemStack>> consumeInputStacks(ServerLevel level, List<BlockPos> containers, IndustrialItemStackSpec spec, int count) {
        if (count <= 0) {
            return Optional.of(List.of());
        }
        IndustrialDefinition.ItemRequirement request = new IndustrialDefinition.ItemRequirement(spec, count, true);
        Optional<IndustrialInputPlan> plan = planInputs(level, containers, List.<IndustrialDefinition.InputRequirement>of(request), 1);
        return plan.flatMap(inputPlan -> consumePlannedInputs(level, containers, inputPlan));
    }

    public static int countInput(ServerLevel level, List<BlockPos> containers, String itemId, String potionId) {
        return countInput(level, containers, IndustrialItemStackSpec.of(itemId, potionId));
    }

    public static int countInput(ServerLevel level, List<BlockPos> containers, IndustrialItemStackSpec spec) {
        IndustrialDefinition.ItemRequirement request = new IndustrialDefinition.ItemRequirement(spec, 1, true);
        return countItem(level, containers, request);
    }

    public static boolean insertItem(ServerLevel level, List<BlockPos> containers, ItemStack stack) {
        if (level == null || containers == null || containers.isEmpty() || stack == null || stack.isEmpty()) {
            return false;
        }
        return insertOutputs(level, containers, List.of(stack.copy()));
    }

    public static boolean insertItems(ServerLevel level, List<BlockPos> containers, List<ItemStack> stacks) {
        if (level == null || containers == null || containers.isEmpty() || stacks == null || stacks.isEmpty()) {
            return false;
        }
        List<ItemStack> outputs = stacks.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
        return !outputs.isEmpty() && hasOutputSpace(level, containers, outputs) && insertOutputs(level, containers, outputs);
    }

    public static Optional<IndustrialInputPlan> planInputs(ServerLevel level,
                                                           List<BlockPos> containers,
                                                           List<IndustrialDefinition.InputRequirement> inputs,
                                                           int craftCount) {
        return IndustrialInputPlanner.plan(level, containers, inputs, craftCount);
    }

    public static Optional<List<ItemStack>> consumePlannedInputs(ServerLevel level,
                                                                 List<BlockPos> containers,
                                                                 IndustrialInputPlan plan) {
        if (plan == null) {
            return Optional.empty();
        }
        List<ItemStack> consumed = new ArrayList<>();
        for (IndustrialDefinition.ItemRequirement input : plan.consumptions()) {
            Optional<List<ItemStack>> removed = consumeItem(level, input, containers, Math.max(1, input.count()));
            if (removed.isEmpty()) {
                rollbackInputs(level, containers, consumed);
                return Optional.empty();
            }
            for (ItemStack stack : removed.get()) {
                addOutputStack(consumed, stack);
            }
        }
        return Optional.of(List.copyOf(consumed));
    }

    private static int countItem(ServerLevel level, List<BlockPos> containers, IndustrialDefinition.ItemRequirement input) {
        IndustrialItemStackSpec spec = input.spec();
        int count = 0;
        for (BlockPos container : containers) {
            if (!GenericContainerAccess.isContainer(level, container)) {
                continue;
            }
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (spec.matches(snapshot.stack(), level.registryAccess())) {
                    count += snapshot.stack().getCount();
                }
            }
        }
        return count;
    }

    /**
     * availableCraftCount: 计算所有输入共同允许的最大合成次数。
     */
    private static int availableCraftCount(ServerLevel level, List<BlockPos> containers, IndustrialDefinition.RecipeDefinition recipe) {
        if (level == null || recipe == null || recipe.inputs().isEmpty()) {
            return 0;
        }
        return IndustrialInputPlanner.availableCraftCount(level, containers, recipe.inputs());
    }

    private static Optional<List<ItemStack>> consumeItem(ServerLevel level, IndustrialDefinition.ItemRequirement input, List<BlockPos> containers, int count) {
        if (count <= 0) {
            return Optional.empty();
        }
        IndustrialItemStackSpec spec = input.spec();
        int remaining = count;
        List<ItemStack> consumed = new ArrayList<>();
        for (BlockPos container : containers) {
            if (!GenericContainerAccess.isContainer(level, container)) {
                continue;
            }
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (!spec.matches(snapshot.stack(), level.registryAccess())) {
                    continue;
                }
                int slotCount = Math.min(snapshot.stack().getCount(), remaining);
                for (int i = 0; i < slotCount; i++) {
                    ItemStack consumedSingle = snapshot.stack().copyWithCount(1);
                    if (!GenericContainerAccess.consumeSingleItemAtSlot(level, container, snapshot.slot(), snapshot.access(), snapshot.side(),
                            stack -> spec.matches(stack, level.registryAccess()))) {
                        rollbackInputs(level, containers, consumed);
                        return Optional.empty();
                    }
                    addOutputStack(consumed, consumedSingle);
                    remaining--;
                    if (remaining <= 0) {
                        return Optional.of(List.copyOf(consumed));
                    }
                }
            }
        }
        rollbackInputs(level, containers, consumed);
        return Optional.empty();
    }

    private static void rollbackInputs(ServerLevel level, List<BlockPos> containers, List<ItemStack> consumed) {
        if (consumed != null && !consumed.isEmpty()) {
            insertItems(level, containers, consumed);
        }
    }

    private static void addOutputStack(List<ItemStack> outputs, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (ItemStack existing : outputs) {
            if (remaining.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
                continue;
            }
            int movable = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (movable > 0) {
                existing.grow(movable);
                remaining.shrink(movable);
            }
        }
        while (!remaining.isEmpty()) {
            int amount = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            outputs.add(remaining.copyWithCount(amount));
            remaining.shrink(amount);
        }
    }

    private static boolean insertOutputs(ServerLevel level, List<BlockPos> containers, List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            ItemStack remaining = output.copy();
            for (BlockPos container : containers) {
                if (remaining.isEmpty()) {
                    break;
                }
                if (GenericContainerAccess.isContainer(level, container)) {
                    remaining = GenericContainerAccess.insert(level, container, remaining);
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

}
