package common.cn.kafei.simukraft.logistics.menu;

import common.cn.kafei.simukraft.logistics.LogisticsWarehouseInventoryService;
import common.cn.kafei.simukraft.network.logistics.LogisticsServerBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsWarehouseGridExtractPacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsWarehouseGridInsertPacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsWarehouseGridShiftClickPacket;
import common.cn.kafei.simukraft.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
public final class LogisticsWarehouseGridMenu extends AbstractContainerMenu {
    public static final int GRID_ROWS = 6;
    public static final int GRID_COLS = 9;
    public static final int WAREHOUSE_SLOTS = GRID_ROWS * GRID_COLS;
    public static final int PLAYER_INVENTORY_START = WAREHOUSE_SLOTS;
    public static final int TOTAL_VISIBLE_SLOTS = WAREHOUSE_SLOTS + 36;

    private final BlockPos warehousePos;
    private final Inventory inventory;
    private final SimpleContainer warehouseDisplay = new SimpleContainer(WAREHOUSE_SLOTS);
    private final List<ItemStack> displayItems = new ArrayList<>();
    private final List<Integer> actualCounts = new ArrayList<>();
    private String searchFilter = "";
    private int scrollRow;

    public LogisticsWarehouseGridMenu(int containerId, Inventory inv, BlockPos pos, LogisticsServerBoxOpenResponsePacket snapshot) {
        super(ModMenuTypes.LOGISTICS_WAREHOUSE_GRID.get(), containerId);
        this.inventory = inv;
        this.warehousePos = (pos != null ? pos : BlockPos.ZERO).immutable();
        addWarehouseSlots();
        addPlayerSlots(inv);
        refreshWarehouseDisplay();
    }

    /** createClientMenu: 从服务端打开菜单数据创建客户端仓库菜单。 */
    public static LogisticsWarehouseGridMenu createClientMenu(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        LogisticsServerBoxOpenResponsePacket snapshot = LogisticsServerBoxOpenResponsePacket.decode(buf);
        return new LogisticsWarehouseGridMenu(containerId, inv, pos, snapshot);
    }

    /** getWarehousePos: 返回物流服务端箱坐标。 */
    public BlockPos getWarehousePos() {
        return warehousePos;
    }

    /** updateClientItems: 接收服务端聚合快照并刷新超级堆叠展示。 */
    public void updateClientItems(List<ItemStack> items, List<Integer> counts) {
        displayItems.clear();
        actualCounts.clear();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                displayItems.add(stack.copyWithCount(1));
                int count = counts != null && i < counts.size() ? counts.get(i) : stack.getCount();
                actualCounts.add(Math.max(1, count));
            }
        }
        scrollRow = Math.min(scrollRow, maxScroll());
        refreshVisibleSlots();
    }

    /** refreshWarehouseDisplay: 服务端打开菜单时生成一次聚合快照。 */
    public void refreshWarehouseDisplay() {
        if (!(inventory.player.level() instanceof ServerLevel level)) {
            return;
        }
        List<LogisticsWarehouseInventoryService.WarehouseItem> aggregate = LogisticsWarehouseInventoryService.aggregate(level, warehousePos);
        updateClientItems(
                aggregate.stream().map(LogisticsWarehouseInventoryService.WarehouseItem::displayStack).toList(),
                aggregate.stream().map(LogisticsWarehouseInventoryService.WarehouseItem::count).toList());
    }

    /** setSearchFilter: 设置客户端搜索词并移除不匹配条目。 */
    public void setSearchFilter(String filter) {
        searchFilter = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        scrollRow = Math.min(scrollRow, maxScroll());
        refreshVisibleSlots();
    }

    /** setScrollOffset: 设置客户端聚合列表滚动行。 */
    public void setScrollOffset(int offset) {
        scrollRow = Math.max(0, Math.min(maxScroll(), offset));
        refreshVisibleSlots();
    }

    /** scrollOffset: 返回当前聚合列表滚动行。 */
    public int scrollOffset() {
        return scrollRow;
    }

    /** totalRows: 返回过滤后的非空聚合行数。 */
    public int totalRows() {
        return Math.max(GRID_ROWS, (int) Math.ceil(filteredEntries().size() / (double) GRID_COLS));
    }

    /** maxScroll: 返回最大滚动行。 */
    public int maxScroll() {
        return Math.max(0, totalRows() - GRID_ROWS);
    }

    /** actualCountAtVisibleSlot: 返回可见超级堆叠槽的真实总数。 */
    public int actualCountAtVisibleSlot(int slot) {
        Entry entry = visibleEntry(slot);
        return entry != null ? entry.count() : 0;
    }

    /** targetStackAtVisibleSlot: 返回点击聚合槽时发给服务端的完整组件物品原型。 */
    public ItemStack targetStackAtVisibleSlot(int slot) {
        Entry entry = visibleEntry(slot);
        if (entry == null) {
            return ItemStack.EMPTY;
        }
        return entry.stack().copyWithCount(Math.min(entry.count(), entry.stack().getMaxStackSize()));
    }

    /** clicked: 仓库超级堆叠槽只发送精确请求，避免客户端直接改虚拟库存。 */
    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < WAREHOUSE_SLOTS) {
            if (clickType == ClickType.PICKUP_ALL || clickType == ClickType.CLONE) {
                return;
            }
            handleWarehouseClick(slotId, dragType, clickType, player);
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    /** canDragTo: 禁止把原版拖拽分配直接应用到聚合展示槽。 */
    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof WarehouseDisplaySlot) && super.canDragTo(slot);
    }

    /** canTakeItemForPickAll: 禁止双击收集聚合展示槽，避免重复提取。 */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof WarehouseDisplaySlot) && super.canTakeItemForPickAll(stack, slot);
    }

    /** quickMoveStack: Shift 点击玩家背包时存入仓库。 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < WAREHOUSE_SLOTS || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        ItemStack remaining = insertToWarehouse(stack.copy());
        int inserted = stack.getCount() - remaining.getCount();
        if (inserted <= 0) {
            return ItemStack.EMPTY;
        }
        stack.shrink(inserted);
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        refreshWarehouseDisplay();
        return original;
    }

    /** stillValid: 保持玩家在物流服务端箱附近时菜单有效。 */
    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(warehousePos.getX() + 0.5D, warehousePos.getY() + 0.5D, warehousePos.getZ() + 0.5D) <= 64.0D;
    }

    /** addWarehouseSlots: 添加 54 个只读超级堆叠展示槽。 */
    private void addWarehouseSlots() {
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int slot = row * GRID_COLS + col;
                addSlot(new WarehouseDisplaySlot(warehouseDisplay, slot, 8 + col * 18, 18 + row * 18));
            }
        }
    }

    /** addPlayerSlots: 添加玩家背包和快捷栏真实槽位。 */
    private void addPlayerSlots(Inventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, 9 + row * 9 + col, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 198));
        }
    }

    /** insertToWarehouse: 服务端把玩家物品存入仓库整体库存。 */
    private ItemStack insertToWarehouse(ItemStack stack) {
        return inventory.player.level() instanceof ServerLevel level
                ? LogisticsWarehouseInventoryService.insert(level, warehousePos, stack)
                : stack;
    }

    /** handleWarehouseClick: 客户端点击聚合槽时发送精确物品操作请求。 */
    private void handleWarehouseClick(int slotId, int dragType, ClickType clickType, Player player) {
        if (!player.level().isClientSide()) {
            return;
        }
        ItemStack carried = getCarried();
        if (clickType == ClickType.PICKUP && !carried.isEmpty()) {
            PacketDistributor.sendToServer(new LogisticsWarehouseGridInsertPacket(warehousePos));
            return;
        }
        ItemStack target = targetStackAtVisibleSlot(slotId);
        if (target.isEmpty()) {
            return;
        }
        if (clickType == ClickType.PICKUP && carried.isEmpty()) {
            int count = dragType == 1 ? Math.max(1, (target.getCount() + 1) / 2) : target.getCount();
            PacketDistributor.sendToServer(new LogisticsWarehouseGridExtractPacket(warehousePos, target, count));
        } else if (clickType == ClickType.QUICK_MOVE && carried.isEmpty()) {
            PacketDistributor.sendToServer(new LogisticsWarehouseGridShiftClickPacket(warehousePos, target));
        }
    }

    /** refreshVisibleSlots: 根据搜索和滚动刷新 54 个紧凑展示槽。 */
    private void refreshVisibleSlots() {
        List<Entry> filtered = filteredEntries();
        int start = scrollRow * GRID_COLS;
        for (int slot = 0; slot < WAREHOUSE_SLOTS; slot++) {
            int index = start + slot;
            ItemStack stack = index < filtered.size() ? filtered.get(index).stack().copyWithCount(1) : ItemStack.EMPTY;
            warehouseDisplay.setItem(slot, stack);
        }
        broadcastChanges();
    }

    /** visibleEntry: 获取可见聚合槽对应的过滤条目。 */
    private Entry visibleEntry(int visibleSlot) {
        if (visibleSlot < 0 || visibleSlot >= WAREHOUSE_SLOTS) {
            return null;
        }
        List<Entry> filtered = filteredEntries();
        int index = scrollRow * GRID_COLS + visibleSlot;
        return index >= 0 && index < filtered.size() ? filtered.get(index) : null;
    }

    /** filteredEntries: 生成搜索过滤后的非空超级堆叠条目。 */
    private List<Entry> filteredEntries() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < displayItems.size(); i++) {
            ItemStack stack = displayItems.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            int count = i < actualCounts.size() ? actualCounts.get(i) : stack.getCount();
            if (searchFilter.isEmpty() || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(searchFilter)) {
                entries.add(new Entry(stack, Math.max(1, count)));
            }
        }
        return entries;
    }

    private record Entry(ItemStack stack, int count) {
    }

    private static final class WarehouseDisplaySlot extends Slot {
        private WarehouseDisplaySlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        /** mayPlace: 超级堆叠展示槽不接受原版直接放入。 */
        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        /** mayPickup: 超级堆叠展示槽由点击逻辑请求服务端提取。 */
        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
