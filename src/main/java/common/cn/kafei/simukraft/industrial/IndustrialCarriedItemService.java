package common.cn.kafei.simukraft.industrial;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenInventory;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 工业携带物品服务；新数据直接使用受雇 NPC 的实体 NBT 背包。 */
@SuppressWarnings("null")
public final class IndustrialCarriedItemService {
    private static final String LEGACY_ITEMS_KEY = "items";

    private IndustrialCarriedItemService() {
    }

    /** hasItems：判断 NPC 普通背包或尚未迁移的旧虚拟库存是否有物品。 */
    public static boolean hasItems(ServerLevel level, IndustrialBoxManager manager, IndustrialBoxData data) {
        CitizenInventory inventory = resolveInventory(level, manager, data);
        return inventory != null ? inventory.hasBackpackItems() : data != null && !data.workState().isBlank();
    }

    /** items：返回 NPC 普通背包物品快照。 */
    public static List<ItemStack> items(ServerLevel level, IndustrialBoxManager manager, IndustrialBoxData data) {
        CitizenInventory inventory = resolveInventory(level, manager, data);
        if (inventory == null) {
            return List.of();
        }
        return inventory.backpackSnapshot().stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    /** stackCount：统计 NPC 普通背包已占用的堆栈槽位。 */
    public static int stackCount(ServerLevel level, IndustrialBoxManager manager, IndustrialBoxData data) {
        CitizenInventory inventory = resolveInventory(level, manager, data);
        return inventory != null ? inventory.occupiedBackpackSlots() : 0;
    }

    /** addItems：仅在全部产物都能放入真实背包时原子插入。 */
    public static boolean addItems(ServerLevel level,
                                   IndustrialBoxManager manager,
                                   IndustrialBoxData data,
                                   List<ItemStack> additions) {
        CitizenInventory inventory = resolveInventory(level, manager, data);
        return inventory != null && inventory.insertBackpackAll(additions);
    }

    /** consumeFirstMatching：消耗 NPC 背包中的首个匹配物品。 */
    public static boolean consumeFirstMatching(ServerLevel level,
                                               IndustrialBoxManager manager,
                                               IndustrialBoxData data,
                                               IndustrialItemStackSpec spec) {
        return extractFirstMatching(level, manager, data, spec).isPresent();
    }

    /** extractFirstMatching：从 NPC 背包提取一个匹配物品。 */
    public static Optional<ItemStack> extractFirstMatching(ServerLevel level,
                                                           IndustrialBoxManager manager,
                                                           IndustrialBoxData data,
                                                           IndustrialItemStackSpec spec) {
        if (level == null || spec == null || spec.isEmpty()) {
            return Optional.empty();
        }
        CitizenInventory inventory = resolveInventory(level, manager, data);
        return inventory != null
                ? inventory.extractFirstBackpack(stack -> spec.matches(stack, level.registryAccess()))
                : Optional.empty();
    }

    /** depositToContainers：把 NPC 背包逐槽放入真实容器，未放下的部分保留在原槽。
     *  filter 非空时只处理匹配列表中任意 spec 的槽，其余槽原封不动保留在背包。 */
    public static DepositResult depositToContainers(ServerLevel level,
                                                     IndustrialBoxManager manager,
                                                     IndustrialBoxData data,
                                                     List<BlockPos> containers,
                                                     List<IndustrialItemStackSpec> filter) {
        if (level == null || data == null) {
            return DepositResult.MISSING_CONTAINER;
        }
        CitizenInventory inventory = resolveInventory(level, manager, data);
        if (inventory == null) {
            return depositLegacyToContainers(level, manager, data, containers);
        }
        if (!inventory.hasBackpackItems()) {
            clearLegacy(manager, data);
            return DepositResult.SUCCESS;
        }
        if (containers == null || containers.isEmpty()) {
            return DepositResult.MISSING_CONTAINER;
        }
        HolderLookup.Provider registries = level.registryAccess();
        boolean remainingFound = false;
        List<ItemStack> updated = new ArrayList<>(CitizenInventory.BACKPACK_SIZE);
        synchronized (inventory) {
            for (int slot = 0; slot < CitizenInventory.BACKPACK_SIZE; slot++) {
                ItemStack remaining = inventory.getItem(slot).copy();
                // filter 非空时跳过不在白名单的物品，保留在背包
                final ItemStack stackForFilter = remaining;
                if (!stackForFilter.isEmpty() && !filter.isEmpty() && filter.stream().noneMatch(s -> s.matches(stackForFilter, registries))) {
                    updated.add(remaining);
                    continue;
                }
                for (BlockPos container : containers) {
                    if (remaining.isEmpty()) {
                        break;
                    }
                    remaining = GenericContainerAccess.insert(level, container, remaining);
                }
                updated.add(remaining);
                remainingFound |= !remaining.isEmpty();
            }
            inventory.replaceBackpack(updated);
        }
        return remainingFound ? DepositResult.OUTPUT_FULL : DepositResult.SUCCESS;
    }

    /** depositToContainers：无过滤器版本，保持旧调用点兼容。 */
    public static DepositResult depositToContainers(ServerLevel level,
                                                     IndustrialBoxManager manager,
                                                     IndustrialBoxData data,
                                                     List<BlockPos> containers) {
        return depositToContainers(level, manager, data, containers, List.of());
    }

    /** dropAndClear：在控制箱失效时掉落 NPC 普通背包与旧虚拟库存。 */
    public static void dropAndClear(ServerLevel level,
                                    IndustrialBoxManager manager,
                                    IndustrialBoxData data,
                                    BlockPos fallbackPos) {
        if (level == null || data == null || fallbackPos == null) {
            return;
        }
        CitizenInventory inventory = resolveInventory(level, manager, data);
        if (inventory != null) {
            synchronized (inventory) {
                for (int slot = 0; slot < CitizenInventory.BACKPACK_SIZE; slot++) {
                    ItemStack stack = inventory.removeItemNoUpdate(slot);
                    if (!stack.isEmpty()) {
                        Block.popResource(level, fallbackPos, stack);
                    }
                }
                inventory.setChanged();
            }
        } else {
            for (ItemStack stack : legacyItems(data, level.registryAccess())) {
                if (!stack.isEmpty()) {
                    Block.popResource(level, fallbackPos, stack.copy());
                }
            }
        }
        clearLegacy(manager, data);
    }

    /** clear：兼容旧调用，仅清理已经废弃的虚拟库存字段。 */
    public static void clear(IndustrialBoxManager manager, IndustrialBoxData data) {
        clearLegacy(manager, data);
    }

    private static CitizenInventory resolveInventory(ServerLevel level,
                                                      IndustrialBoxManager manager,
                                                      IndustrialBoxData data) {
        if (level == null || data == null) {
            return null;
        }
        CitizenData worker = IndustrialControlBoxService.findAssignedWorker(level, data.boxPos());
        CitizenEntity entity = worker != null ? CitizenTeleportService.findCitizenEntity(level, worker.uuid()) : null;
        if (entity == null) {
            return null;
        }
        CitizenInventory inventory = entity.getCitizenInventory();
        migrateLegacy(level, manager, data, entity, inventory);
        return inventory;
    }

    private static void migrateLegacy(ServerLevel level,
                                      IndustrialBoxManager manager,
                                      IndustrialBoxData data,
                                      CitizenEntity entity,
                                      CitizenInventory inventory) {
        if (data.workState().isBlank()) {
            return;
        }
        List<ItemStack> legacy = legacyItems(data, level.registryAccess());
        if (!legacy.isEmpty() && !inventory.insertBackpackAll(legacy)) {
            BlockPos fallback = entity != null ? entity.blockPosition() : data.boxPos();
            for (ItemStack stack : legacy) {
                if (!stack.isEmpty()) {
                    Block.popResource(level, fallback, stack.copy());
                }
            }
            SimuKraft.LOGGER.warn("Simukraft: NPC inventory was full while migrating industrial carried items for box {}; dropped {} stacks",
                    data.boxPos(), legacy.size());
        }
        clearLegacy(manager, data);
    }

    private static DepositResult depositLegacyToContainers(ServerLevel level,
                                                            IndustrialBoxManager manager,
                                                            IndustrialBoxData data,
                                                            List<BlockPos> containers) {
        List<ItemStack> legacy = legacyItems(data, level.registryAccess());
        if (legacy.isEmpty()) {
            clearLegacy(manager, data);
            return DepositResult.SUCCESS;
        }
        if (containers == null || containers.isEmpty()) {
            return DepositResult.MISSING_CONTAINER;
        }
        if (!IndustrialInventoryService.hasOutputSpace(level, containers, legacy)) {
            return DepositResult.OUTPUT_FULL;
        }
        if (IndustrialInventoryService.insertItems(level, containers, legacy)) {
            clearLegacy(manager, data);
            return DepositResult.SUCCESS;
        }
        return DepositResult.OUTPUT_FULL;
    }

    private static void clearLegacy(IndustrialBoxManager manager, IndustrialBoxData data) {
        if (data == null || data.workState().isBlank()) {
            return;
        }
        data.setWorkState("");
        if (manager != null) {
            manager.persist(data);
        }
    }

    private static List<ItemStack> legacyItems(IndustrialBoxData data, HolderLookup.Provider registries) {
        if (data == null || data.workState().isBlank() || registries == null) {
            return List.of();
        }
        try {
            JsonObject root = JsonParser.parseString(data.workState()).getAsJsonObject();
            JsonArray array = root.has(LEGACY_ITEMS_KEY) && root.get(LEGACY_ITEMS_KEY).isJsonArray()
                    ? root.getAsJsonArray(LEGACY_ITEMS_KEY)
                    : new JsonArray();
            List<ItemStack> result = new ArrayList<>();
            for (int index = 0; index < array.size(); index++) {
                if (!array.get(index).isJsonObject()) {
                    continue;
                }
                JsonObject object = array.get(index).getAsJsonObject();
                if (!object.has("nbt")) {
                    continue;
                }
                CompoundTag tag = TagParser.parseTag(object.get("nbt").getAsString());
                ItemStack stack = ItemStack.parseOptional(registries, tag);
                if (!stack.isEmpty()) {
                    result.add(stack);
                }
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read legacy industrial carried items for box {}",
                    data.boxPos(), exception);
            return List.of();
        }
    }

    public enum DepositResult {
        SUCCESS,
        MISSING_CONTAINER,
        OUTPUT_FULL
    }
}
