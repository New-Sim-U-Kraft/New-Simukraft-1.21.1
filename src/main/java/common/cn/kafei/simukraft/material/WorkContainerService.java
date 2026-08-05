package common.cn.kafei.simukraft.material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("null")
public final class WorkContainerService {
    private WorkContainerService() {
    }

    // adjacentContainers: 收集工作方块六面紧贴容器，并规范化大箱子坐标去重。
    public static List<BlockPos> adjacentContainers(ServerLevel level, BlockPos workBlockPos) {
        if (level == null || workBlockPos == null) {
            return List.of();
        }
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos candidate = workBlockPos.relative(direction);
            if (GenericContainerAccess.isContainer(level, candidate)) {
                positions.add(GenericContainerAccess.canonicalContainerPos(level, candidate));
            }
        }
        return List.copyOf(positions);
    }

    // orderedAdjacentContainers: 将玩家选中的容器排在首位，其它相邻容器作为备用。
    public static List<BlockPos> orderedAdjacentContainers(ServerLevel level, BlockPos workBlockPos, @Nullable BlockPos preferredPos) {
        List<BlockPos> adjacent = adjacentContainers(level, workBlockPos);
        if (level == null || preferredPos == null || !GenericContainerAccess.isContainer(level, preferredPos)) {
            return adjacent;
        }
        BlockPos preferredCanonical = GenericContainerAccess.canonicalContainerPos(level, preferredPos);
        if (!adjacent.contains(preferredCanonical)) {
            return adjacent;
        }
        List<BlockPos> ordered = new ArrayList<>();
        ordered.add(preferredCanonical);
        for (BlockPos containerPos : adjacent) {
            if (!containerPos.equals(preferredCanonical)) {
                ordered.add(containerPos);
            }
        }
        return List.copyOf(ordered);
    }

    // firstAdjacentContainer: 返回第一个紧贴工作方块的容器，用于旧界面展示和兼容判断。
    @Nullable
    public static BlockPos firstAdjacentContainer(ServerLevel level, BlockPos workBlockPos) {
        List<BlockPos> containers = adjacentContainers(level, workBlockPos);
        return containers.isEmpty() ? null : containers.getFirst();
    }

    // hasItem: 判断候选容器中是否存在指定物品。
    public static boolean hasItem(ServerLevel level, List<BlockPos> containerPositions, Item item) {
        if (level == null || item == null || containerPositions == null || containerPositions.isEmpty()) {
            return false;
        }
        for (BlockPos containerPos : containerPositions) {
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, containerPos)) {
                if (slot.stack().getItem() == item) {
                    return true;
                }
            }
        }
        return false;
    }

    // consumeItem: 从候选容器中按顺序消耗一个指定物品。
    public static boolean consumeItem(ServerLevel level, List<BlockPos> containerPositions, Item item) {
        if (level == null || item == null || containerPositions == null || containerPositions.isEmpty()) {
            return false;
        }
        for (BlockPos containerPos : containerPositions) {
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, containerPos)) {
                if (slot.stack().getItem() == item
                        && GenericContainerAccess.consumeSingleItemAtSlot(level, containerPos, slot.slot(), slot.access(), slot.side(), item)) {
                    return true;
                }
            }
        }
        return false;
    }

    // insertIntoAny: 按顺序把物品塞入候选容器，返回仍未放入的剩余堆。
    public static ItemStack insertIntoAny(ServerLevel level, List<BlockPos> containerPositions, ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty() || containerPositions == null || containerPositions.isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        ItemStack leftover = stack;
        for (BlockPos containerPos : containerPositions) {
            if (leftover.isEmpty()) {
                break;
            }
            leftover = GenericContainerAccess.insert(level, containerPos, leftover);
        }
        return leftover;
    }

    // depositDropsOrDrop: 掉落物优先入候选容器，放不下时回落到世界掉落。
    public static void depositDropsOrDrop(ServerLevel level, List<BlockPos> containerPositions, List<ItemStack> drops, BlockPos fallbackPos) {
        if (level == null || drops == null || fallbackPos == null) {
            return;
        }
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            ItemStack leftover = insertIntoAny(level, containerPositions, drop);
            if (!leftover.isEmpty()) {
                Block.popResource(level, fallbackPos, leftover);
            }
        }
    }
}
