package common.cn.kafei.simukraft.citizen;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** NPC 的真实物品栏；普通背包为 7x2，装备与双手使用独立槽位。 */
@SuppressWarnings("null")
public final class CitizenInventory extends SimpleContainer {
    public static final int BACKPACK_COLUMNS = 7;
    public static final int BACKPACK_ROWS = 2;
    public static final int BACKPACK_SIZE = BACKPACK_COLUMNS * BACKPACK_ROWS;
    public static final int HEAD_SLOT = BACKPACK_SIZE;
    public static final int CHEST_SLOT = HEAD_SLOT + 1;
    public static final int LEGS_SLOT = CHEST_SLOT + 1;
    public static final int FEET_SLOT = LEGS_SLOT + 1;
    public static final int MAIN_HAND_SLOT = FEET_SLOT + 1;
    public static final int OFF_HAND_SLOT = MAIN_HAND_SLOT + 1;
    public static final int TOTAL_SIZE = OFF_HAND_SLOT + 1;

    private boolean loading;

    public CitizenInventory() {
        super(TOTAL_SIZE);
    }

    /** saveToTag：使用原版 ItemStack NBT 编解码保存全部槽位。 */
    public synchronized CompoundTag saveToTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, getItems(), registries);
        return tag;
    }

    /** loadFromTag：从原版 NBT 恢复槽位，并在完成后只发送一次变更通知。 */
    public synchronized void loadFromTag(CompoundTag tag, HolderLookup.Provider registries) {
        loading = true;
        try {
            getItems().clear();
            if (tag != null) {
                ContainerHelper.loadAllItems(tag, getItems(), registries);
            }
        } finally {
            loading = false;
        }
        setChanged();
    }

    /** insertBackpackAll：仅在全部物品都能放入普通背包时原子提交。 */
    public synchronized boolean insertBackpackAll(List<ItemStack> additions) {
        List<ItemStack> simulated = mutableBackpackSnapshot();
        if (!mergeAll(simulated, additions)) {
            return false;
        }
        replaceBackpack(simulated);
        return true;
    }

    /** replaceBackpack：批量替换普通背包，并合并为一次 NBT 灾备通知。 */
    public synchronized void replaceBackpack(List<ItemStack> stacks) {
        loading = true;
        try {
            for (int slot = 0; slot < BACKPACK_SIZE; slot++) {
                ItemStack stack = stacks != null && slot < stacks.size() && stacks.get(slot) != null
                        ? stacks.get(slot).copy()
                        : ItemStack.EMPTY;
                super.setItem(slot, stack);
            }
        } finally {
            loading = false;
        }
        setChanged();
    }

    /** canInsertBackpackAll：无副作用预判一组物品是否能全部进入普通背包。 */
    public synchronized boolean canInsertBackpackAll(List<ItemStack> additions) {
        return mergeAll(mutableBackpackSnapshot(), additions);
    }

    /** extractFirstBackpack：从普通背包提取首个匹配物品。 */
    public synchronized Optional<ItemStack> extractFirstBackpack(Predicate<ItemStack> matcher) {
        if (matcher == null) {
            return Optional.empty();
        }
        for (int slot = 0; slot < BACKPACK_SIZE; slot++) {
            ItemStack stack = getItem(slot);
            if (stack.isEmpty() || !matcher.test(stack)) {
                continue;
            }
            return Optional.of(removeItem(slot, 1));
        }
        return Optional.empty();
    }

    /** backpackSnapshot：返回普通背包的不可变副本。 */
    public synchronized List<ItemStack> backpackSnapshot() {
        return List.copyOf(mutableBackpackSnapshot());
    }

    private List<ItemStack> mutableBackpackSnapshot() {
        List<ItemStack> result = new ArrayList<>(BACKPACK_SIZE);
        for (int slot = 0; slot < BACKPACK_SIZE; slot++) {
            result.add(getItem(slot).copy());
        }
        return result;
    }

    /** occupiedBackpackSlots：统计普通背包已占用的堆栈槽位。 */
    public synchronized int occupiedBackpackSlots() {
        int occupied = 0;
        for (int slot = 0; slot < BACKPACK_SIZE; slot++) {
            if (!getItem(slot).isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    /** hasBackpackItems：判断普通背包是否存在物品。 */
    public synchronized boolean hasBackpackItems() {
        return occupiedBackpackSlots() > 0;
    }

    /** equipmentIndex：把原版装备槽映射到 NPC 物品栏槽位。 */
    public static int equipmentIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> HEAD_SLOT;
            case CHEST -> CHEST_SLOT;
            case LEGS -> LEGS_SLOT;
            case FEET -> FEET_SLOT;
            case MAINHAND -> MAIN_HAND_SLOT;
            case OFFHAND -> OFF_HAND_SLOT;
            default -> -1;
        };
    }

    @Override
    public synchronized ItemStack getItem(int index) {
        return super.getItem(index);
    }

    @Override
    public synchronized ItemStack removeItem(int index, int count) {
        return super.removeItem(index, count);
    }

    @Override
    public synchronized ItemStack removeItemNoUpdate(int index) {
        return super.removeItemNoUpdate(index);
    }

    @Override
    public synchronized void setItem(int index, ItemStack stack) {
        super.setItem(index, stack != null ? stack : ItemStack.EMPTY);
    }

    @Override
    public synchronized void clearContent() {
        super.clearContent();
    }

    @Override
    public synchronized void setChanged() {
        if (!loading) {
            super.setChanged();
        }
    }

    private static boolean mergeAll(List<ItemStack> slots, List<ItemStack> additions) {
        if (additions == null) {
            return true;
        }
        for (ItemStack addition : additions) {
            if (addition == null || addition.isEmpty()) {
                continue;
            }
            ItemStack remaining = addition.copy();
            for (ItemStack existing : slots) {
                if (remaining.isEmpty() || existing.isEmpty()
                        || !ItemStack.isSameItemSameComponents(existing, remaining)) {
                    continue;
                }
                int movable = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (movable > 0) {
                    existing.grow(movable);
                    remaining.shrink(movable);
                }
            }
            for (int slot = 0; slot < slots.size() && !remaining.isEmpty(); slot++) {
                if (!slots.get(slot).isEmpty()) {
                    continue;
                }
                int amount = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                slots.set(slot, remaining.copyWithCount(amount));
                remaining.shrink(amount);
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
