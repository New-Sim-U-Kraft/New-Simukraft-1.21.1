package common.cn.kafei.simukraft.material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * NPC 工作材料缓存：只读取紧贴工作方块的容器，建造/生产时按缓存槽位定点扣料。
 */

public final class WorkMaterialCache {
    private static final long CONTAINER_DISCOVERY_INTERVAL_TICKS = 40L;
    private static final long INVENTORY_REFRESH_INTERVAL_TICKS = 20L;
    private static final long SIGNATURE_CHECK_INTERVAL_TICKS = 60L;

    private final BlockPos workBlockPos;
    private final List<BlockPos> cachedContainerPositions = new ArrayList<>();
    private final ConcurrentMap<Item, Integer> cachedItemTotals = new ConcurrentHashMap<>();
    private final ConcurrentMap<Item, ArrayDeque<CachedSlotRef>> cachedItemSlots = new ConcurrentHashMap<>();
    private final ConcurrentMap<BlockPos, ContainerSignature> containerSignatures = new ConcurrentHashMap<>();

    private long lastContainerDiscoveryTick = Long.MIN_VALUE;
    private long lastInventoryRefreshTick = Long.MIN_VALUE;
    private long lastSignatureCheckTick = Long.MIN_VALUE;
    private boolean inventoryDirty = true;

    public WorkMaterialCache(BlockPos workBlockPos) {
        this.workBlockPos = Objects.requireNonNull(workBlockPos, "workBlockPos").immutable();
    }

    public ConsumeResult tryConsumeOne(ServerLevel level, WorkMaterialRequest request) {
        if (level == null || request == null || request.isEmpty()) {
            return ConsumeResult.MISSING;
        }
        refreshContainerPositionsIfNeeded(level);
        checkSignaturesIfNeeded(level);
        refreshInventoryIfNeeded(level, false);

        ConsumeResult firstAttempt = consumeFromCache(level, request);
        if (firstAttempt == ConsumeResult.CONSUMED) {
            return ConsumeResult.CONSUMED;
        }
        if (firstAttempt == ConsumeResult.DESYNCED || shouldForceRefresh(level)) {
            refreshInventoryIfNeeded(level, true);
            return consumeFromCache(level, request);
        }
        return ConsumeResult.MISSING;
    }

    public int countAvailable(ServerLevel level, WorkMaterialRequest request) {
        if (level == null || request == null || request.isEmpty()) {
            return 0;
        }
        refreshContainerPositionsIfNeeded(level);
        checkSignaturesIfNeeded(level);
        refreshInventoryIfNeeded(level, false);
        return candidateItems(request).stream()
                .mapToInt(item -> cachedItemTotals.getOrDefault(item, 0))
                .sum();
    }

    public void markDirty() {
        inventoryDirty = true;
    }

    public List<BlockPos> getContainerPositions() {
        return List.copyOf(cachedContainerPositions);
    }

    public boolean tracksContainer(ServerLevel level, BlockPos containerPos) {
        if (level == null || containerPos == null) {
            return false;
        }
        refreshContainerPositionsIfNeeded(level);
        BlockPos canonicalPos = GenericContainerAccess.canonicalContainerPos(level, containerPos);
        return cachedContainerPositions.contains(canonicalPos);
    }

    private boolean shouldForceRefresh(ServerLevel level) {
        return inventoryDirty || level.getGameTime() - lastInventoryRefreshTick >= INVENTORY_REFRESH_INTERVAL_TICKS;
    }

    private void refreshContainerPositionsIfNeeded(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (lastContainerDiscoveryTick != Long.MIN_VALUE && gameTime - lastContainerDiscoveryTick < CONTAINER_DISCOVERY_INTERVAL_TICKS) {
            return;
        }

        List<BlockPos> refreshedPositions = discoverAdjacentContainers(level);
        if (!refreshedPositions.equals(cachedContainerPositions)) {
            cachedContainerPositions.clear();
            cachedContainerPositions.addAll(refreshedPositions);
            containerSignatures.keySet().retainAll(refreshedPositions);
            inventoryDirty = true;
        }
        lastContainerDiscoveryTick = gameTime;
    }

    private List<BlockPos> discoverAdjacentContainers(ServerLevel level) {
        return WorkContainerService.adjacentContainers(level, workBlockPos);
    }

    private void refreshInventoryIfNeeded(ServerLevel level, boolean forceRefresh) {
        long gameTime = level.getGameTime();
        if (!forceRefresh && !inventoryDirty && gameTime - lastInventoryRefreshTick < INVENTORY_REFRESH_INTERVAL_TICKS) {
            return;
        }

        rebuildInventoryCache(level);
        lastInventoryRefreshTick = gameTime;
        inventoryDirty = false;
    }

    private void rebuildInventoryCache(ServerLevel level) {
        cachedItemTotals.clear();
        cachedItemSlots.clear();
        containerSignatures.clear();

        for (BlockPos containerPos : cachedContainerPositions) {
            if (!GenericContainerAccess.isContainer(level, containerPos)) {
                inventoryDirty = true;
                continue;
            }
            List<GenericContainerAccess.SlotSnapshot> snapshots = GenericContainerAccess.snapshotSlots(level, containerPos);
            containerSignatures.put(containerPos, buildSignature(snapshots));
            for (GenericContainerAccess.SlotSnapshot snapshot : snapshots) {
                Item item = snapshot.stack().getItem();
                int count = snapshot.stack().getCount();
                if (count <= 0) {
                    continue;
                }
                cachedItemTotals.merge(item, count, Integer::sum);
                cachedItemSlots
                        .computeIfAbsent(item, ignored -> new ArrayDeque<>())
                        .addLast(new CachedSlotRef(containerPos, snapshot.slot(), snapshot.access(), snapshot.side(), count));
            }
        }
    }

    private void checkSignaturesIfNeeded(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime - lastSignatureCheckTick < SIGNATURE_CHECK_INTERVAL_TICKS) {
            return;
        }

        for (BlockPos containerPos : cachedContainerPositions) {
            if (!GenericContainerAccess.isContainer(level, containerPos)) {
                inventoryDirty = true;
                continue;
            }
            ContainerSignature latest = buildSignature(GenericContainerAccess.snapshotSlots(level, containerPos));
            ContainerSignature previous = containerSignatures.get(containerPos);
            if (!latest.equals(previous)) {
                containerSignatures.put(containerPos, latest);
                inventoryDirty = true;
            }
        }
        lastSignatureCheckTick = gameTime;
    }

    private ContainerSignature buildSignature(List<GenericContainerAccess.SlotSnapshot> snapshots) {
        int totalItems = 0;
        int nonEmptySlots = 0;
        for (GenericContainerAccess.SlotSnapshot snapshot : snapshots) {
            if (snapshot.stack().isEmpty()) {
                continue;
            }
            totalItems += snapshot.stack().getCount();
            nonEmptySlots++;
        }
        return new ContainerSignature(totalItems, nonEmptySlots);
    }

    private ConsumeResult consumeFromCache(ServerLevel level, WorkMaterialRequest request) {
        List<Item> candidateItems = candidateItems(request);
        int cachedAmount = candidateItems.stream()
                .mapToInt(item -> cachedItemTotals.getOrDefault(item, 0))
                .sum();
        if (cachedAmount <= 0) {
            return ConsumeResult.MISSING;
        }

        for (Item item : candidateItems) {
            ArrayDeque<CachedSlotRef> slotRefs = cachedItemSlots.get(item);
            if (slotRefs == null || slotRefs.isEmpty()) {
                continue;
            }
            ConsumeResult result = consumeFromSlotQueue(level, request, item, slotRefs);
            if (result == ConsumeResult.CONSUMED || result == ConsumeResult.DESYNCED) {
                return result;
            }
        }
        return ConsumeResult.DESYNCED;
    }

    private ConsumeResult consumeFromSlotQueue(ServerLevel level, WorkMaterialRequest request, Item item, ArrayDeque<CachedSlotRef> slotRefs) {
        int inspectedSlots = 0;
        int maxSlots = slotRefs.size();
        while (!slotRefs.isEmpty() && inspectedSlots < maxSlots) {
            CachedSlotRef slotRef = slotRefs.peekFirst();
            if (slotRef.remainingCount <= 0) {
                slotRefs.removeFirst();
                continue;
            }
            if (!GenericContainerAccess.isContainer(level, slotRef.containerPos)) {
                inventoryDirty = true;
                return ConsumeResult.DESYNCED;
            }
            ItemStack currentStack = GenericContainerAccess.stackAtSlot(level, slotRef.containerPos, slotRef.slot, slotRef.access, slotRef.side);
            if (currentStack.isEmpty() || currentStack.getItem() != item) {
                inventoryDirty = true;
                return ConsumeResult.DESYNCED;
            }
            if (!request.matches(currentStack)) {
                slotRefs.removeFirst();
                slotRefs.addLast(slotRef);
                inspectedSlots++;
                continue;
            }
            if (!GenericContainerAccess.consumeSingleItemAtSlot(level, slotRef.containerPos, slotRef.slot, slotRef.access, slotRef.side, request::matches)) {
                inventoryDirty = true;
                return ConsumeResult.DESYNCED;
            }
            slotRef.remainingCount--;
            decrementTotal(item);
            if (slotRef.remainingCount <= 0) {
                slotRefs.removeFirst();
            }
            return ConsumeResult.CONSUMED;
        }
        return ConsumeResult.MISSING;
    }

    private List<Item> candidateItems(WorkMaterialRequest request) {
        if (!request.acceptedItems().isEmpty()) {
            return List.copyOf(request.acceptedItems());
        }
        return List.copyOf(cachedItemSlots.keySet());
    }

    private void decrementTotal(Item item) {
        cachedItemTotals.computeIfPresent(item, (ignored, count) -> count <= 1 ? null : count - 1);
    }

    public enum ConsumeResult {
        CONSUMED,
        MISSING,
        DESYNCED
    }

    private static final class CachedSlotRef {
        private final BlockPos containerPos;
        private final int slot;
        private final GenericContainerAccess.SlotAccess access;
        @Nullable
        private final Direction side;
        private int remainingCount;

        private CachedSlotRef(BlockPos containerPos, int slot, GenericContainerAccess.SlotAccess access, @Nullable Direction side, int remainingCount) {
            this.containerPos = containerPos;
            this.slot = slot;
            this.access = access;
            this.side = side;
            this.remainingCount = remainingCount;
        }
    }

    private record ContainerSignature(int totalItems, int nonEmptySlots) {
    }
}
