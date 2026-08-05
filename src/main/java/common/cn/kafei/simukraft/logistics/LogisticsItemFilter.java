package common.cn.kafei.simukraft.logistics;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.industrial.IndustrialItemStackSpec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings("null")
public record LogisticsItemFilter(String itemId, String stackTag) {
    public LogisticsItemFilter {
        itemId = itemId != null ? itemId.trim() : "";
        stackTag = stackTag != null ? stackTag.trim() : "";
    }

    public static LogisticsItemFilter item(String itemId) {
        return new LogisticsItemFilter(itemId, "");
    }

    public boolean exact() {
        return !stackTag.isBlank();
    }

    public boolean valid() {
        return !itemId.isBlank();
    }

    public boolean matches(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty() || itemId.isBlank()) {
            return false;
        }
        if (!exact()) {
            return itemId.equals(stack.getItemHolder().unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse(""));
        }
        ItemStack filterStack = displayStack(registries);
        return !filterStack.isEmpty() && ItemStack.isSameItemSameComponents(stack, filterStack);
    }

    public ItemStack displayStack(HolderLookup.Provider registries) {
        if (itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        if (!stackTag.isBlank() && registries != null) {
            try {
                return ItemStack.parseOptional(registries, TagParser.parseTag(stackTag));
            } catch (Exception exception) {
                SimuKraft.LOGGER.warn("Simukraft: Invalid logistics item filter '{}'", stackTag, exception);
            }
        }
        return IndustrialItemStackSpec.of(itemId, "").stack(1, registries);
    }
}
