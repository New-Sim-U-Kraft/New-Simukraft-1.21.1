package common.cn.kafei.simukraft.mineraldrilling;

import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** MineralDrillingOutputService: 将钻井产物写入钻井平台结构内的木桶。 */
@SuppressWarnings("null")
public final class MineralDrillingOutputService {
    private MineralDrillingOutputService() {
    }

    /** canStore: 在扣减矿脉储量前，确认所有产物均可放入平台木桶。 */
    public static boolean canStore(ServerLevel level, PlacedBuildingRecord building, ItemStack stack) {
        return canStoreInContainers(outputContainers(level, building), stack);
    }

    /** store: 将已确认有容量的产物依次写入平台木桶。 */
    public static boolean store(ServerLevel level, PlacedBuildingRecord building, ItemStack stack) {
        return storeInContainers(outputContainers(level, building), stack);
    }

    /** canStoreAll: 在扣减多个重叠矿脉前一次性预检全部产物的总容量。 */
    public static boolean canStoreAll(ServerLevel level,
                                      PlacedBuildingRecord building,
                                      List<ItemStack> stacks) {
        return canStoreAllInContainers(outputContainers(level, building), stacks);
    }

    /** storeAll: 将同一 Y 层多个矿脉的产物合并写入平台木桶。 */
    public static boolean storeAll(ServerLevel level,
                                   PlacedBuildingRecord building,
                                   List<ItemStack> stacks) {
        return storeAllInContainers(outputContainers(level, building), stacks);
    }

    static boolean canStoreInContainers(List<? extends Container> containers, ItemStack stack) {
        if (stack == null || stack.isEmpty() || containers == null || containers.isEmpty()) {
            return false;
        }
        ItemStack remaining = stack.copy();
        for (Container container : containers) {
            remaining = simulateInsert(container, remaining);
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static boolean storeInContainers(List<? extends Container> containers, ItemStack stack) {
        if (!canStoreInContainers(containers, stack)) {
            return false;
        }
        ItemStack remaining = stack.copy();
        for (Container container : containers) {
            remaining = insert(container, remaining);
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static boolean canStoreAllInContainers(List<? extends Container> containers, List<ItemStack> stacks) {
        if (containers == null || containers.isEmpty() || stacks == null || stacks.isEmpty()) {
            return false;
        }
        List<ContainerSnapshot> snapshots = snapshots(containers);
        boolean hasOutput = false;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            hasOutput = true;
            if (!simulateInsert(snapshots, stack).isEmpty()) {
                return false;
            }
        }
        return hasOutput;
    }

    static boolean storeAllInContainers(List<? extends Container> containers, List<ItemStack> stacks) {
        if (!canStoreAllInContainers(containers, stacks)) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (!storeInContainers(containers, stack)) {
                return false;
            }
        }
        return true;
    }

    /** outputContainers: 优先使用钻井 JSON 声明的木桶，兼容旧建筑包的结构扫描。 */
    private static List<Container> outputContainers(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null) {
            return List.of();
        }
        List<Container> barrels = new ArrayList<>();
        MineralDrillingDefinitionLoader.OutputContainerResolution resolution = MineralDrillingDefinitionLoader
                .resolveOutputContainers(building);
        List<BlockPos> positions = resolution.declared()
                ? resolution.positions()
                : legacyBarrelPositions(level, building);
        for (BlockPos pos : positions) {
            if (level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
                barrels.add(barrel);
            }
        }
        return List.copyOf(barrels);
    }

    /** legacyBarrelPositions: 为没有专用 JSON 的旧钻井平台扫描结构木桶。 */
    private static List<BlockPos> legacyBarrelPositions(ServerLevel level, PlacedBuildingRecord building) {
        if (building.blocks() == null || building.blocks().isEmpty()) {
            return List.of();
        }
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (BuildingBlockData block : building.blocks()) {
            if (block == null || block.relativePos() == null || block.state() == null || !block.state().is(Blocks.BARREL)) {
                continue;
            }
            addBarrelPosition(level, positions, block.relativePos());
            if (building.worldOrigin() != null) {
                addBarrelPosition(level, positions, building.worldOrigin().offset(block.relativePos()));
            }
        }
        return List.copyOf(positions);
    }

    /** addBarrelPosition: 仅保留已加载且未被玩家替换的结构木桶。 */
    private static void addBarrelPosition(ServerLevel level, Set<BlockPos> positions, BlockPos candidate) {
        if (level.isLoaded(candidate) && level.getBlockState(candidate).is(Blocks.BARREL)) {
            positions.add(candidate.immutable());
        }
    }

    /** simulateInsert: 使用容器快照计算单个木桶对产物的可容纳数量。 */
    private static ItemStack simulateInsert(Container container, ItemStack stack) {
        if (container == null || stack.isEmpty()) {
            return stack;
        }
        List<ItemStack> slots = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            slots.add(container.getItem(slot).copy());
        }
        ItemStack remaining = stack.copy();
        merge(container, slots, remaining, false);
        merge(container, slots, remaining, true);
        return remaining;
    }

    private static ItemStack simulateInsert(List<ContainerSnapshot> snapshots, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (ContainerSnapshot snapshot : snapshots) {
            remaining = mergeSnapshot(snapshot, remaining, false);
            remaining = mergeSnapshot(snapshot, remaining, true);
            if (remaining.isEmpty()) {
                break;
            }
        }
        return remaining;
    }

    private static ItemStack mergeSnapshot(ContainerSnapshot snapshot,
                                            ItemStack remaining,
                                            boolean emptySlots) {
        merge(snapshot.container(), snapshot.slots(), remaining, emptySlots);
        return remaining;
    }

    private static List<ContainerSnapshot> snapshots(List<? extends Container> containers) {
        List<ContainerSnapshot> snapshots = new ArrayList<>(containers.size());
        for (Container container : containers) {
            if (container == null) {
                continue;
            }
            List<ItemStack> slots = new ArrayList<>(container.getContainerSize());
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                slots.add(container.getItem(slot).copy());
            }
            snapshots.add(new ContainerSnapshot(container, slots));
        }
        return snapshots;
    }

    /** insert: 先合并同类物品，再填充空槽，保持木桶库存紧凑。 */
    private static ItemStack insert(Container container, ItemStack stack) {
        if (container == null || stack.isEmpty()) {
            return stack;
        }
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)
                    || !container.canPlaceItem(slot, remaining)) {
                continue;
            }
            int capacity = Math.min(container.getMaxStackSize(), existing.getMaxStackSize()) - existing.getCount();
            int moved = Math.min(remaining.getCount(), Math.max(0, capacity));
            if (moved > 0) {
                existing.grow(moved);
                remaining.shrink(moved);
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, remaining)) {
                continue;
            }
            int moved = Math.min(remaining.getCount(), Math.min(container.getMaxStackSize(), remaining.getMaxStackSize()));
            if (moved > 0) {
                container.setItem(slot, remaining.copyWithCount(moved));
                remaining.shrink(moved);
            }
        }
        container.setChanged();
        return remaining;
    }

    /** merge: 将产物合并进虚拟槽位，用于无副作用容量预检。 */
    private static void merge(Container container, List<ItemStack> slots, ItemStack remaining, boolean emptySlots) {
        for (int slot = 0; slot < slots.size() && !remaining.isEmpty(); slot++) {
            ItemStack existing = slots.get(slot);
            if (emptySlots != existing.isEmpty() || !container.canPlaceItem(slot, remaining)) {
                continue;
            }
            if (existing.isEmpty()) {
                int moved = Math.min(remaining.getCount(), Math.min(container.getMaxStackSize(), remaining.getMaxStackSize()));
                if (moved > 0) {
                    slots.set(slot, remaining.copyWithCount(moved));
                    remaining.shrink(moved);
                }
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(existing, remaining)) {
                continue;
            }
            int capacity = Math.min(container.getMaxStackSize(), existing.getMaxStackSize()) - existing.getCount();
            int moved = Math.min(remaining.getCount(), Math.max(0, capacity));
            if (moved > 0) {
                existing.grow(moved);
                remaining.shrink(moved);
            }
        }
    }

    private record ContainerSnapshot(Container container, List<ItemStack> slots) {
    }
}
