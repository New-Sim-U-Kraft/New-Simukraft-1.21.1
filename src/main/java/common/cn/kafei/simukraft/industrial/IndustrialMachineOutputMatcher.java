package common.cn.kafei.simukraft.industrial;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

@SuppressWarnings("null")
final class IndustrialMachineOutputMatcher {
    private IndustrialMachineOutputMatcher() {
    }

    static Map<String, Integer> countMatchingStack(ItemStack stack,
                                                   Map<String, IndustrialItemStackSpec> expectedOutputs,
                                                   HolderLookup.Provider registries) {
        String key = matchingKey(stack, expectedOutputs, registries);
        if (key.isBlank()) {
            return Map.of();
        }
        return Map.of(key, stack.getCount());
    }

    static String matchingKey(ItemStack stack,
                              Map<String, IndustrialItemStackSpec> expectedOutputs,
                              HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        for (Map.Entry<String, IndustrialItemStackSpec> entry : expectedOutputs.entrySet()) {
            if (entry.getValue().matches(stack, registries)) {
                return entry.getKey();
            }
        }
        return "";
    }

    static void addCollected(List<ItemStack> collected, ItemStack stack) {
        for (ItemStack existing : collected) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                existing.grow(stack.getCount());
                return;
            }
        }
        collected.add(stack.copy());
    }
}
