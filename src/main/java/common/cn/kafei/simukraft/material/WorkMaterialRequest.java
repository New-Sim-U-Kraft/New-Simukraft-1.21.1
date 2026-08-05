package common.cn.kafei.simukraft.material;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@SuppressWarnings("null")
public record WorkMaterialRequest(ItemStack displayStack, Set<Item> acceptedItems, Predicate<ItemStack> matcher) {
    public static final WorkMaterialRequest EMPTY = new WorkMaterialRequest(ItemStack.EMPTY, Set.of(), ItemStack::isEmpty);

    public WorkMaterialRequest {
        displayStack = displayStack != null ? displayStack.copyWithCount(Math.max(1, displayStack.getCount())) : ItemStack.EMPTY;
        acceptedItems = acceptedItems != null ? Set.copyOf(acceptedItems) : Set.of();
        matcher = Objects.requireNonNull(matcher, "matcher");
    }

    public static WorkMaterialRequest exact(Item item) {
        return item == null ? EMPTY : exact(new ItemStack(item));
    }

    public static WorkMaterialRequest exact(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EMPTY;
        }
        ItemStack expected = stack.copyWithCount(1);
        return new WorkMaterialRequest(expected, Set.of(expected.getItem()), candidate -> ItemStack.isSameItem(candidate, expected));
    }

    public static WorkMaterialRequest exactStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EMPTY;
        }
        ItemStack expected = stack.copyWithCount(1);
        return new WorkMaterialRequest(expected, Set.of(expected.getItem()), candidate -> ItemStack.isSameItemSameComponents(candidate, expected));
    }

    public static WorkMaterialRequest matching(ItemStack displayStack, List<Item> acceptedItems, Predicate<ItemStack> matcher) {
        LinkedHashSet<Item> itemSet = new LinkedHashSet<>();
        if (acceptedItems != null) {
            acceptedItems.stream().filter(Objects::nonNull).forEach(itemSet::add);
        }
        return new WorkMaterialRequest(displayStack, itemSet, matcher);
    }

    public boolean isEmpty() {
        return displayStack.isEmpty() && acceptedItems.isEmpty();
    }

    public boolean matches(ItemStack stack) {
        return stack != null && !stack.isEmpty() && matcher.test(stack);
    }
}
