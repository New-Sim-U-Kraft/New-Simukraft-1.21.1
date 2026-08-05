package common.cn.kafei.simukraft.material;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;

@SuppressWarnings("null")
public final class GenericSlotAccess {
    private GenericSlotAccess() {
    }

    /** slotCount: 返回指定容器暴露的真实槽位数量。 */
    public static int slotCount(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return 0;
        }
        try {
            SlotTarget target = resolve(level, pos);
            return target != null ? target.slotCount() : 0;
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to count slots at {}", pos, exception);
            return 0;
        }
    }

    public static ItemStack stackAt(ServerLevel level, BlockPos pos, int slot) {
        if (level == null || pos == null || slot < 0 || !level.isLoaded(pos)) {
            return ItemStack.EMPTY;
        }
        try {
            SlotTarget target = resolve(level, pos);
            if (target == null || !target.validSlot(slot)) {
                return ItemStack.EMPTY;
            }
            return target.stackAt(slot);
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read slot {} at {}", slot, pos, exception);
            return ItemStack.EMPTY;
        }
    }

    /** canPlace: 判断物品能否放入指定真实槽位。 */
    public static boolean canPlace(ServerLevel level, BlockPos pos, int slot, ItemStack stack) {
        if (level == null || pos == null || slot < 0 || stack == null || stack.isEmpty() || !level.isLoaded(pos)) {
            return false;
        }
        try {
            SlotTarget target = resolve(level, pos);
            return target != null && target.validSlot(slot) && target.canPlace(slot, stack);
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to check slot insertion into {} at {}", slot, pos, exception);
            return false;
        }
    }

    /** slotLimit: 返回指定真实槽位对该物品的最大堆叠上限。 */
    public static int slotLimit(ServerLevel level, BlockPos pos, int slot, ItemStack stack) {
        if (level == null || pos == null || slot < 0 || !level.isLoaded(pos)) {
            return 0;
        }
        try {
            SlotTarget target = resolve(level, pos);
            return target != null && target.validSlot(slot) ? target.slotLimit(slot, stack) : 0;
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read slot limit {} at {}", slot, pos, exception);
            return 0;
        }
    }

    public static int countInsertable(ServerLevel level, BlockPos pos, int slot, ItemStack stack) {
        if (level == null || pos == null || slot < 0 || stack == null || stack.isEmpty() || !level.isLoaded(pos)) {
            return 0;
        }
        try {
            SlotTarget target = resolve(level, pos);
            if (target == null || !target.validSlot(slot)) {
                return 0;
            }
            return target.countInsertable(slot, stack.copy());
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to simulate slot insertion into {} at {}", slot, pos, exception);
            return 0;
        }
    }

    public static ItemStack insert(ServerLevel level, BlockPos pos, int slot, ItemStack stack) {
        if (level == null || pos == null || slot < 0 || stack == null || stack.isEmpty() || !level.isLoaded(pos)) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        try {
            SlotTarget target = resolve(level, pos);
            if (target == null || !target.validSlot(slot)) {
                return stack;
            }
            return target.insert(slot, stack.copy());
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to insert item into slot {} at {}", slot, pos, exception);
            return stack;
        }
    }

    /** extract: 从指定真实槽位取出物品。 */
    public static ItemStack extract(ServerLevel level, BlockPos pos, int slot, int amount) {
        if (level == null || pos == null || slot < 0 || amount <= 0 || !level.isLoaded(pos)) {
            return ItemStack.EMPTY;
        }
        try {
            SlotTarget target = resolve(level, pos);
            if (target == null || !target.validSlot(slot)) {
                return ItemStack.EMPTY;
            }
            return target.extract(slot, amount);
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to extract from slot {} at {}", slot, pos, exception);
            return ItemStack.EMPTY;
        }
    }

    /** setStack: 设置指定真实槽位内容。 */
    public static void setStack(ServerLevel level, BlockPos pos, int slot, ItemStack stack) {
        if (level == null || pos == null || slot < 0 || stack == null || !level.isLoaded(pos)) {
            return;
        }
        try {
            SlotTarget target = resolve(level, pos);
            if (target != null && target.validSlot(slot)) {
                target.setStack(slot, stack.copy());
            }
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to set slot {} at {}", slot, pos, exception);
        }
    }

    @Nullable
    private static SlotTarget resolve(ServerLevel level, BlockPos pos) {
        Container container = resolveContainer(level, pos);
        if (container != null) {
            return new ContainerSlotTarget(level, pos, container);
        }
        ItemHandlerAccess handlerAccess = resolveItemHandler(level, pos);
        return handlerAccess != null ? new ItemHandlerSlotTarget(handlerAccess.handler()) : null;
    }

    @Nullable
    private static Container resolveContainer(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            Container chest = ChestBlock.getContainer(chestBlock, state, level, pos, true);
            if (chest != null) {
                return chest;
            }
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container ? container : null;
    }

    @Nullable
    private static ItemHandlerAccess resolveItemHandler(ServerLevel level, BlockPos pos) {
        IItemHandler unsided = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (hasSlots(unsided)) {
            return new ItemHandlerAccess(unsided);
        }
        for (Direction side : Direction.values()) {
            IItemHandler sided = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
            if (hasSlots(sided)) {
                return new ItemHandlerAccess(sided);
            }
        }
        return null;
    }

    private static boolean hasSlots(@Nullable IItemHandler handler) {
        return handler != null && handler.getSlots() > 0;
    }

    private interface SlotTarget {
        int slotCount();

        boolean validSlot(int slot);

        ItemStack stackAt(int slot);

        boolean canPlace(int slot, ItemStack stack);

        int slotLimit(int slot, ItemStack stack);

        int countInsertable(int slot, ItemStack stack);

        ItemStack insert(int slot, ItemStack stack);

        ItemStack extract(int slot, int amount);

        void setStack(int slot, ItemStack stack);
    }

    private record ContainerSlotTarget(ServerLevel level, BlockPos pos, Container container) implements SlotTarget {
        @Override
        public int slotCount() {
            return container.getContainerSize();
        }

        @Override
        public boolean validSlot(int slot) {
            return slot >= 0 && slot < container.getContainerSize();
        }

        @Override
        public ItemStack stackAt(int slot) {
            return container.getItem(slot).copy();
        }

        @Override
        public boolean canPlace(int slot, ItemStack stack) {
            return validSlot(slot) && container.canPlaceItem(slot, stack);
        }

        @Override
        public int slotLimit(int slot, ItemStack stack) {
            if (!validSlot(slot)) {
                return 0;
            }
            int baseLimit = container.getMaxStackSize();
            return stack == null || stack.isEmpty() ? baseLimit : Math.min(baseLimit, stack.getMaxStackSize());
        }

        @Override
        public int countInsertable(int slot, ItemStack stack) {
            return stack.getCount() - insertIntoContainer(slot, stack, true).getCount();
        }

        @Override
        public ItemStack insert(int slot, ItemStack stack) {
            return insertIntoContainer(slot, stack, false);
        }

        @Override
        public ItemStack extract(int slot, int amount) {
            if (!validSlot(slot) || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack removed = container.removeItem(slot, amount);
            if (!removed.isEmpty()) {
                markChanged();
            }
            return removed;
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            if (!validSlot(slot)) {
                return;
            }
            container.setItem(slot, stack);
            markChanged();
        }

        private ItemStack insertIntoContainer(int slot, ItemStack stack, boolean simulate) {
            if (!validSlot(slot) || !container.canPlaceItem(slot, stack)) {
                return stack;
            }
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) {
                return stack;
            }
            int maxStack = existing.isEmpty()
                    ? Math.min(container.getMaxStackSize(), stack.getMaxStackSize())
                    : Math.min(container.getMaxStackSize(), existing.getMaxStackSize());
            int free = existing.isEmpty() ? maxStack : maxStack - existing.getCount();
            int movable = Math.max(0, Math.min(stack.getCount(), free));
            if (movable <= 0) {
                return stack;
            }
            ItemStack remaining = stack.copy();
            remaining.shrink(movable);
            if (!simulate) {
                if (existing.isEmpty()) {
                    container.setItem(slot, stack.copyWithCount(movable));
                } else {
                    existing.grow(movable);
                }
                markChanged();
            }
            return remaining;
        }

        private void markChanged() {
            container.setChanged();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntity.setChanged();
            }
        }
    }

    private record ItemHandlerSlotTarget(IItemHandler handler) implements SlotTarget {
        @Override
        public int slotCount() {
            return handler.getSlots();
        }

        @Override
        public boolean validSlot(int slot) {
            return slot >= 0 && slot < handler.getSlots();
        }

        @Override
        public ItemStack stackAt(int slot) {
            return handler.getStackInSlot(slot).copy();
        }

        @Override
        public boolean canPlace(int slot, ItemStack stack) {
            if (!validSlot(slot) || stack == null || stack.isEmpty() || !handler.isItemValid(slot, stack)) {
                return false;
            }
            if (handler instanceof IItemHandlerModifiable) {
                return true;
            }
            ItemStack current = handler.getStackInSlot(slot);
            return current.isEmpty() || ItemStack.isSameItemSameComponents(current, stack);
        }

        @Override
        public int slotLimit(int slot, ItemStack stack) {
            if (!validSlot(slot)) {
                return 0;
            }
            int baseLimit = handler.getSlotLimit(slot);
            return stack == null || stack.isEmpty() ? baseLimit : Math.min(baseLimit, stack.getMaxStackSize());
        }

        @Override
        public int countInsertable(int slot, ItemStack stack) {
            return stack.getCount() - handler.insertItem(slot, stack.copy(), true).getCount();
        }

        @Override
        public ItemStack insert(int slot, ItemStack stack) {
            return handler.insertItem(slot, stack.copy(), false);
        }

        @Override
        public ItemStack extract(int slot, int amount) {
            return validSlot(slot) && amount > 0 ? handler.extractItem(slot, amount, false) : ItemStack.EMPTY;
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            if (!validSlot(slot)) {
                return;
            }
            if (handler instanceof IItemHandlerModifiable modifiable) {
                modifiable.setStackInSlot(slot, stack);
                return;
            }
            setStackWithoutDirectAccess(slot, stack);
        }

        private void setStackWithoutDirectAccess(int slot, ItemStack stack) {
            ItemStack current = handler.getStackInSlot(slot).copy();
            if (ItemStack.matches(current, stack)) {
                return;
            }
            if (stack.isEmpty()) {
                extract(slot, current.getCount());
                return;
            }
            if (current.isEmpty()) {
                handler.insertItem(slot, stack.copy(), false);
                return;
            }
            if (ItemStack.isSameItemSameComponents(current, stack)) {
                int delta = stack.getCount() - current.getCount();
                if (delta > 0) {
                    handler.insertItem(slot, stack.copyWithCount(delta), false);
                } else if (delta < 0) {
                    extract(slot, -delta);
                }
            }
        }
    }

    private record ItemHandlerAccess(IItemHandler handler) {
    }
}
