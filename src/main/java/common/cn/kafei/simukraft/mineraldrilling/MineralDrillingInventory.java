package common.cn.kafei.simukraft.mineraldrilling;

import common.cn.kafei.simukraft.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** MineralDrillingInventory: 保存钻杆与钻头两个真实槽位，并在容器层校验物品类型。 */
@SuppressWarnings("null")
public final class MineralDrillingInventory extends SimpleContainer {
    public static final int DRILL_ROD_SLOT = 0;
    public static final int DRILL_BIT_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    private boolean loading;
    private Runnable changeListener = () -> {
    };

    /** MineralDrillingInventory: 创建固定为两格的空钻井库存。 */
    public MineralDrillingInventory() {
        super(SLOT_COUNT);
    }

    /** setChangeListener: 注册槽位变化后的持久化通知。 */
    public synchronized void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener != null ? changeListener : () -> {
        };
    }

    /** saveToTag: 使用原版 ItemStack 编解码保存两个槽位及数据组件。 */
    public synchronized CompoundTag saveToTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, getItems(), registries);
        return tag;
    }

    /** loadFromTag: 从 NBT 恢复槽位，并丢弃不符合当前钻井槽位规则的数据。 */
    public synchronized void loadFromTag(CompoundTag tag, HolderLookup.Provider registries) {
        loading = true;
        try {
            getItems().clear();
            if (tag != null && !tag.isEmpty()) {
                ContainerHelper.loadAllItems(tag, getItems(), registries);
            }
            sanitizeLoadedItems();
        } finally {
            loading = false;
        }
        setChanged();
    }

    /** copyFrom: 复制另一份两格库存，用于客户端菜单镜像初始化。 */
    public void copyFrom(MineralDrillingInventory source) {
        if (source == this) {
            return;
        }
        ItemStack rod = ItemStack.EMPTY;
        ItemStack bit = ItemStack.EMPTY;
        if (source != null) {
            synchronized (source) {
                rod = source.getItem(DRILL_ROD_SLOT).copy();
                bit = source.getItem(DRILL_BIT_SLOT).copy();
            }
        }
        synchronized (this) {
            loading = true;
            try {
                super.setItem(DRILL_ROD_SLOT, normalizedStack(DRILL_ROD_SLOT, rod));
                super.setItem(DRILL_BIT_SLOT, normalizedStack(DRILL_BIT_SLOT, bit));
            } finally {
                loading = false;
            }
            setChanged();
        }
    }

    /** slotLimit: 返回指定槽位允许的最大堆叠数量。 */
    public static int slotLimit(int slot) {
        return switch (slot) {
            case DRILL_ROD_SLOT -> 64;
            case DRILL_BIT_SLOT -> 1;
            default -> 0;
        };
    }

    /** canPlaceItem: 钻杆槽只收钻杆，钻头槽只收浅层或深层钻头。 */
    @Override
    public synchronized boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return false;
        }
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        return switch (slot) {
            case DRILL_ROD_SLOT -> stack.is(ModItems.DRILL_ROD_SEGMENT.get());
            case DRILL_BIT_SLOT -> stack.is(ModItems.SHALLOW_DRILL_BIT.get())
                    || stack.is(ModItems.DEEP_DRILL_BIT.get());
            default -> false;
        };
    }

    /** isEmpty: 在线程安全边界内检查两个工具槽是否均为空。 */
    @Override
    public synchronized boolean isEmpty() {
        return super.isEmpty();
    }

    /** getItem: 在线程安全边界内读取指定槽位。 */
    @Override
    public synchronized ItemStack getItem(int slot) {
        return super.getItem(slot);
    }

    /** removeItem: 在线程安全边界内按数量提取物品。 */
    @Override
    public synchronized ItemStack removeItem(int slot, int amount) {
        return super.removeItem(slot, amount);
    }

    /** removeItemNoUpdate: 无额外更新地原子移除整格物品。 */
    @Override
    public synchronized ItemStack removeItemNoUpdate(int slot) {
        return super.removeItemNoUpdate(slot);
    }

    /** setItem: 校验类型与堆叠上限后写入槽位。 */
    @Override
    public synchronized void setItem(int slot, ItemStack stack) {
        super.setItem(slot, normalizedStack(slot, stack));
    }

    /** clearContent: 原子清空两个钻井槽位。 */
    @Override
    public synchronized void clearContent() {
        super.clearContent();
    }

    /** setChanged: 将原版容器变更转发给持久化监听器。 */
    @Override
    public synchronized void setChanged() {
        super.setChanged();
        if (!loading) {
            changeListener.run();
        }
    }

    /** sanitizeLoadedItems: 清理存档中类型错误或超过槽位上限的物品。 */
    private void sanitizeLoadedItems() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack normalized = normalizedStack(slot, super.getItem(slot));
            super.setItem(slot, normalized);
        }
    }

    /** normalizedStack: 生成符合指定钻井槽规则的独立堆栈。 */
    private ItemStack normalizedStack(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !canPlaceItem(slot, stack)) {
            return ItemStack.EMPTY;
        }
        int count = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), slotLimit(slot)));
        return count > 0 ? stack.copyWithCount(count) : ItemStack.EMPTY;
    }
}
