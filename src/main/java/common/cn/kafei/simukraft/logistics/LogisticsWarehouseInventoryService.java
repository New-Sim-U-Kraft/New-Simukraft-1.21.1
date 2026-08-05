package common.cn.kafei.simukraft.logistics;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.material.GenericSlotAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("null")
public final class LogisticsWarehouseInventoryService {
    private LogisticsWarehouseInventoryService() {
    }

    /** aggregate: 汇总物流仓库绑定容器中的物品种类和真实数量。 */
    public static List<WarehouseItem> aggregate(ServerLevel level, BlockPos boxPos) {
        LogisticsWarehouseData warehouse = warehouse(level, boxPos);
        if (warehouse == null || warehouse.containers().isEmpty()) {
            return List.of();
        }
        List<ItemCounter> counters = new ArrayList<>();
        for (BlockPos container : usableContainers(level, warehouse.containers())) {
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, container)) {
                merge(counters, snapshot.stack());
            }
        }
        return counters.stream()
                .sorted(Comparator.comparing(counter -> itemId(counter.prototype)))
                .map(LogisticsWarehouseInventoryService::toWarehouseItem)
                .toList();
    }

    /** containers: 返回当前仓库绑定容器快照。 */
    public static List<BlockPos> containers(ServerLevel level, BlockPos boxPos) {
        LogisticsWarehouseData warehouse = warehouse(level, boxPos);
        return warehouse != null ? warehouse.containers() : List.of();
    }

    /** slotAddresses: 返回仓库绑定容器的真实槽位地址，用于原版容器交互。 */
    public static List<WarehouseSlotAddress> slotAddresses(ServerLevel level, BlockPos boxPos) {
        LogisticsWarehouseData warehouse = warehouse(level, boxPos);
        if (warehouse == null || warehouse.containers().isEmpty()) {
            return List.of();
        }
        List<WarehouseSlotAddress> addresses = new ArrayList<>();
        for (BlockPos container : usableContainers(level, warehouse.containers())) {
            int slots = GenericSlotAccess.slotCount(level, container);
            for (int slot = 0; slot < slots; slot++) {
                addresses.add(new WarehouseSlotAddress(container, slot));
            }
        }
        return List.copyOf(addresses);
    }

    /** extract: 按显示物品原型从仓库中提取指定数量。 */
    public static ItemStack extract(ServerLevel level, BlockPos boxPos, ItemStack target, int count) {
        LogisticsWarehouseData warehouse = warehouse(level, boxPos);
        if (warehouse == null || target == null || target.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = ItemStack.EMPTY;
        int remaining = Math.min(count, Math.max(1, target.getMaxStackSize()));
        for (BlockPos container : usableContainers(level, warehouse.containers())) {
            if (remaining <= 0) {
                break;
            }
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (remaining <= 0) {
                    break;
                }
                ItemStack current = snapshot.stack();
                if (!matches(current, target)) {
                    continue;
                }
                int amount = Math.min(remaining, current.getCount());
                ItemStack extracted = GenericContainerAccess.extractFromSlot(level, container, snapshot.slot(), snapshot.access(), snapshot.side(), amount,
                        stack -> matches(stack, target));
                if (extracted.isEmpty()) {
                    continue;
                }
                result = mergeExtracted(result, extracted);
                remaining -= extracted.getCount();
            }
        }
        return result;
    }

    /** insert: 将物品按绑定容器顺序存入仓库，返回未放入的剩余物。 */
    public static ItemStack insert(ServerLevel level, BlockPos boxPos, ItemStack stack) {
        LogisticsWarehouseData warehouse = warehouse(level, boxPos);
        if (warehouse == null || stack == null || stack.isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        ItemStack remaining = stack.copy();
        for (BlockPos container : usableContainers(level, warehouse.containers())) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = GenericContainerAccess.insert(level, container, remaining);
        }
        return remaining;
    }

    /** insertIntoPlayerInventory: Shift 取出时按原版背包合并规则放入玩家背包。 */
    public static ItemStack insertIntoPlayerInventory(Inventory inventory, ItemStack stack) {
        if (inventory == null || stack == null || stack.isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        ItemStack remaining = stack.copy();
        // slots 0-8 hotbar, 9-35 main inventory; 36+ are armor/offhand — exclude them
        for (int slot = 0; slot < 36 && !remaining.isEmpty(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty() || !stacksMatchExactly(existing, remaining)) {
                continue;
            }
            int maxStack = Math.min(inventory.getMaxStackSize(), existing.getMaxStackSize());
            int movable = Math.min(remaining.getCount(), maxStack - existing.getCount());
            if (movable > 0) {
                existing.grow(movable);
                remaining.shrink(movable);
            }
        }
        for (int slot = 0; slot < 36 && !remaining.isEmpty(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) {
                continue;
            }
            int movable = Math.min(remaining.getCount(), Math.min(inventory.getMaxStackSize(), remaining.getMaxStackSize()));
            inventory.setItem(slot, remaining.copyWithCount(movable));
            remaining.shrink(movable);
        }
        inventory.setChanged();
        return remaining;
    }

    /** itemId: 生成稳定排序和展示用物品 ID。 */
    public static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "" : stack.getItemHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse("");
    }

    private static LogisticsWarehouseData warehouse(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return null;
        }
        return LogisticsManager.get(level).warehouseAt(boxPos);
    }

    private static void merge(List<ItemCounter> counters, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (ItemCounter counter : counters) {
            if (stacksMatchExactly(counter.prototype, stack)) {
                counter.count += stack.getCount();
                return;
            }
        }
        counters.add(new ItemCounter(stack.copyWithCount(1), stack.getCount()));
    }

    private static WarehouseItem toWarehouseItem(ItemCounter counter) {
        int safeCount = counter.count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) counter.count;
        ItemStack display = counter.prototype.copyWithCount(Math.min(safeCount, counter.prototype.getMaxStackSize()));
        return new WarehouseItem(display, safeCount);
    }

    private static ItemStack mergeExtracted(ItemStack current, ItemStack extracted) {
        if (current.isEmpty()) {
            return extracted.copy();
        }
        if (stacksMatchExactly(current, extracted)) {
            current.grow(extracted.getCount());
            return current;
        }
        return current;
    }

    /** usableContainers: 按规范坐标去重并跳过会重复暴露库存的副箱。 */
    private static List<BlockPos> usableContainers(ServerLevel level, List<BlockPos> containers) {
        if (level == null || containers == null || containers.isEmpty()) {
            return List.of();
        }
        Set<BlockPos> unique = new LinkedHashSet<>();
        for (BlockPos container : containers) {
            if (container == null) {
                continue;
            }
            BlockPos canonical = GenericContainerAccess.canonicalContainerPos(level, container);
            if (isSophisticatedStorageSubChest(level, canonical)) {
                continue;
            }
            unique.add(canonical.immutable());
        }
        return List.copyOf(unique);
    }

    private static boolean matches(ItemStack current, ItemStack target) {
        return current != null && target != null && !current.isEmpty() && stacksMatchExactly(current, target);
    }

    /** stacksMatchExactly: 1.21.1 下按物品和组件精确匹配，等价于旧版 NBT 精确匹配。 */
    private static boolean stacksMatchExactly(ItemStack first, ItemStack second) {
        return first != null && second != null && !first.isEmpty() && !second.isEmpty()
                && ItemStack.isSameItemSameComponents(first, second);
    }

    private static boolean isSophisticatedStorageSubChest(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !blockEntity.getClass().getName().contains("sophisticatedstorage")) {
            return false;
        }
        try {
            java.lang.reflect.Field field = blockEntity.getClass().getDeclaredField("doubleMainPos");
            field.setAccessible(true);
            return field.get(blockEntity) != null;
        } catch (ReflectiveOperationException exception) {
            SimuKraft.LOGGER.debug("Simukraft: Sophisticated Storage double chest check skipped at {}", pos);
            return false;
        }
    }

    public record WarehouseItem(ItemStack displayStack, int count) {
    }

    public record WarehouseSlotAddress(BlockPos pos, int slot) {
        public WarehouseSlotAddress {
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
        }
    }

    private static final class ItemCounter {
        private final ItemStack prototype;
        private long count;

        private ItemCounter(ItemStack prototype, long count) {
            this.prototype = prototype;
            this.count = count;
        }
    }
}
