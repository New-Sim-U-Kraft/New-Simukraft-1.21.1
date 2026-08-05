package common.cn.kafei.simukraft.logistics;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("null")
public final class LogisticsControlBoxService {
    private LogisticsControlBoxService() {
    }

    /** buildServerView: 构建物流服务器盒界面快照。 */
    public static ServerView buildServerView(ServerLevel level, BlockPos boxPos) {
        UUID cityId = cityIdFor(level, boxPos);
        CityData city = cityId != null ? CityManager.get(level).getCity(cityId).orElse(null) : null;
        LogisticsWarehouseData warehouse = cityId != null
                ? LogisticsManager.get(level).getOrCreateWarehouse(boxPos, cityId, dimensionId(level), level.getGameTime())
                : null;
        CitizenData worker = findAssignedStorageWorker(level, boxPos);
        List<LogisticsClientData> clients = warehouse != null ? allClients(level, cityId, warehouse.dimensionId()) : List.of();
        List<LogisticsChannelData> channels = warehouse != null ? LogisticsManager.get(level).channels(warehouse.warehouseId()) : List.of();
        List<ClientInventoryEntry> clientInventories = clients.stream()
                .map(client -> new ClientInventoryEntry(client.clientId(), aggregateClientInventory(level, client)))
                .toList();
        return new ServerView(
                boxPos.immutable(),
                city != null,
                cityId,
                city != null ? city.cityName() : "",
                cityId != null ? EconomyService.getCityBalance(level, cityId) : 0.0D,
                warehouse != null ? warehouse.warehouseId() : null,
                worker != null,
                worker != null ? worker.uuid() : null,
                worker != null ? worker.name() : "",
                warehouse != null ? warehouse.containers() : List.of(),
                clients.stream().map(LogisticsControlBoxService::clientEntry).toList(),
                channels.stream().sorted(Comparator.comparing(LogisticsChannelData::updatedAt)).map(LogisticsControlBoxService::channelEntry).toList(),
                warehouse != null ? aggregateInventory(level, boxPos) : List.of(),
                clientInventories);
    }

    /** buildClientView: 构建物流客户端盒界面快照。 */
    public static ClientView buildClientView(ServerLevel level, BlockPos boxPos) {
        UUID cityId = cityIdFor(level, boxPos);
        CityData city = cityId != null ? CityManager.get(level).getCity(cityId).orElse(null) : null;
        LogisticsClientData client = cityId != null
                ? LogisticsManager.get(level).getOrCreateClient(boxPos, cityId, dimensionId(level), level.getGameTime())
                : null;
        List<LogisticsChannelData> channels = client == null ? List.of() : LogisticsManager.get(level).allChannels().stream()
                .filter(channel -> client.clientId().equals(channel.clientId()))
                .toList();
        return new ClientView(
                boxPos.immutable(),
                city != null,
                cityId,
                city != null ? city.cityName() : "",
                client != null ? client.clientId() : null,
                client != null ? client.displayName() : "",
                client != null ? client.ports() : List.of(),
                channels.stream().map(LogisticsControlBoxService::channelEntry).toList());
    }

    /** bindWarehouseAdjacent: 绑定服务器盒相邻实体容器。 */
    public static ActionResult bindWarehouseAdjacent(ServerLevel level, BlockPos boxPos) {
        return LogisticsContainerBindingService.bindWarehouseAdjacent(level, boxPos);
    }

    /** bindWarehouseArea: 通过两点选区批量绑定仓库容器。 */
    public static ActionResult bindWarehouseArea(ServerLevel level, BlockPos boxPos, BlockPos areaMin, BlockPos areaMax) {
        return LogisticsContainerBindingService.bindWarehouseArea(level, boxPos, areaMin, areaMax);
    }

    /** deleteWarehouse: 删除当前服务器盒绑定的仓库与关联路线。 */
    public static ActionResult deleteWarehouse(ServerLevel level, BlockPos boxPos) {
        LogisticsWarehouseData warehouse = LogisticsManager.get(level).warehouseAt(boxPos);
        if (warehouse == null || warehouse.containers().isEmpty()) {
            return ActionResult.NOT_FOUND;
        }
        LogisticsManager.get(level).removeWarehouse(boxPos);
        return ActionResult.SUCCESS;
    }

    /** removeWarehouseContainer: 移除仓库容器绑定。 */
    public static ActionResult removeWarehouseContainer(ServerLevel level, BlockPos boxPos, BlockPos containerPos) {
        LogisticsWarehouseData warehouse = LogisticsManager.get(level).warehouseAt(boxPos);
        if (warehouse == null || containerPos == null) {
            return ActionResult.NOT_FOUND;
        }
        List<BlockPos> containers = warehouse.containers().stream()
                .filter(pos -> !pos.equals(containerPos))
                .toList();
        if (containers.size() == warehouse.containers().size()) {
            return ActionResult.NOT_FOUND;
        }
        LogisticsManager.get(level).updateWarehouse(warehouse.withContainers(containers, level.getGameTime()));
        return ActionResult.SUCCESS;
    }

    /** bindClientAdjacent: 绑定客户端盒相邻实体容器端口。 */
    public static ActionResult bindClientAdjacent(ServerLevel level, BlockPos boxPos) {
        return LogisticsContainerBindingService.bindClientAdjacent(level, boxPos);
    }

    /** bindClientArea: 通过两点选区批量绑定客户端端口容器。 */
    public static ActionResult bindClientArea(ServerLevel level, BlockPos boxPos, BlockPos areaMin, BlockPos areaMax) {
        return LogisticsContainerBindingService.bindClientArea(level, boxPos, areaMin, areaMax);
    }

    /** removeClientPort: 移除客户端端口绑定。 */
    public static ActionResult removeClientPort(ServerLevel level, BlockPos boxPos, String portId) {
        LogisticsClientData client = LogisticsManager.get(level).clientAt(boxPos);
        if (client == null || portId == null || portId.isBlank()) {
            return ActionResult.NOT_FOUND;
        }
        List<LogisticsPortData> ports = client.ports().stream()
                .filter(port -> !port.id().equals(portId))
                .toList();
        if (ports.size() == client.ports().size()) {
            return ActionResult.NOT_FOUND;
        }
        LogisticsManager.get(level).updateClient(client.withPorts(ports, level.getGameTime()));
        return ActionResult.SUCCESS;
    }

    /** renameClient: 修改手动物流客户端显示名。 */
    public static ActionResult renameClient(ServerLevel level, BlockPos boxPos, String name) {
        LogisticsClientData client = LogisticsManager.get(level).clientAt(boxPos);
        if (client == null) {
            return ActionResult.NOT_FOUND;
        }
        LogisticsManager.get(level).updateClient(client.withName(name, level.getGameTime()));
        return ActionResult.SUCCESS;
    }

    /** addChannel: 新增仓库到客户端的物流路线。 */
    public static ActionResult addChannel(ServerLevel level, BlockPos serverBoxPos, UUID clientId, LogisticsDirection direction, String name, String filterItemId) {
        return addChannel(level, serverBoxPos, clientId, direction, name, filterItemId != null && !filterItemId.isBlank() ? List.of(filterItemId) : List.of());
    }

    /** addChannel: 新增带多个物品过滤器的物流路线。 */
    public static ActionResult addChannel(ServerLevel level, BlockPos serverBoxPos, UUID clientId, LogisticsDirection direction, String name, List<String> filterItemIds) {
        LogisticsWarehouseData warehouse = LogisticsManager.get(level).warehouseAt(serverBoxPos);
        LogisticsClientData client = resolveClient(level, clientId);
        if (warehouse == null || client == null) {
            return ActionResult.NOT_FOUND;
        }
        if (!warehouse.dimensionId().equals(client.dimensionId()) || !warehouse.cityId().equals(client.cityId())) {
            return ActionResult.INVALID_TARGET;
        }
        List<LogisticsItemFilter> filters = filterItemIds == null ? List.of() : filterItemIds.stream()
                .filter(itemId -> itemId != null && !itemId.isBlank())
                .distinct()
                .limit(128)
                .map(LogisticsItemFilter::item)
                .toList();
        LogisticsChannelData channel = new LogisticsChannelData(
                UUID.randomUUID(),
                warehouse.warehouseId(),
                client.clientId(),
                direction,
                name != null && !name.isBlank() ? name : client.displayName(),
                true,
                filters,
                level.getGameTime(),
                0,
                0);
        LogisticsManager.get(level).updateChannel(channel);
        return ActionResult.SUCCESS;
    }

    /** toggleChannel: 启用或暂停物流路线。 */
    public static ActionResult toggleChannel(ServerLevel level, UUID channelId) {
        LogisticsChannelData channel = LogisticsManager.get(level).channel(channelId);
        if (channel == null) {
            return ActionResult.NOT_FOUND;
        }
        LogisticsManager.get(level).updateChannel(channel.withEnabled(!channel.enabled(), level.getGameTime()));
        return ActionResult.SUCCESS;
    }

    /** setChannelKeepQuantities: 设置路线发送端/接收端保有量，0 表示该端不限制。 */
    public static ActionResult setChannelKeepQuantities(ServerLevel level, UUID channelId, int sourceQuantity, int targetQuantity) {
        LogisticsChannelData channel = LogisticsManager.get(level).channel(channelId);
        if (channel == null) {
            return ActionResult.NOT_FOUND;
        }
        LogisticsManager.get(level).updateChannel(channel.withKeepQuantities(sourceQuantity, targetQuantity, level.getGameTime()));
        return ActionResult.SUCCESS;
    }

    /** removeChannel: 删除物流路线。 */
    public static ActionResult removeChannel(ServerLevel level, UUID channelId) {
        if (LogisticsManager.get(level).channel(channelId) == null) {
            return ActionResult.NOT_FOUND;
        }
        LogisticsManager.get(level).removeChannel(channelId);
        return ActionResult.SUCCESS;
    }

    /** depositPlayerInventory: 将玩家背包可放入的物品存入仓库容器。 */
    public static ActionResult depositPlayerInventory(ServerLevel level, BlockPos boxPos, ServerPlayer player) {
        LogisticsWarehouseData warehouse = LogisticsManager.get(level).warehouseAt(boxPos);
        if (warehouse == null || warehouse.containers().isEmpty() || player == null) {
            return ActionResult.NOT_FOUND;
        }
        Inventory inventory = player.getInventory();
        boolean moved = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remaining = LogisticsWarehouseInventoryService.insert(level, boxPos, stack.copy());
            int movedCount = stack.getCount() - remaining.getCount();
            if (movedCount > 0) {
                inventory.setItem(slot, remaining);
                moved = true;
            }
        }
        if (moved) {
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
            return ActionResult.SUCCESS;
        }
        return ActionResult.NO_SPACE;
    }

    /** extractWarehouseItem: 按物品 ID 从仓库取出一组到玩家背包。 */
    public static ActionResult extractWarehouseItem(ServerLevel level, BlockPos boxPos, ServerPlayer player, String itemId) {
        LogisticsWarehouseData warehouse = LogisticsManager.get(level).warehouseAt(boxPos);
        if (warehouse == null || warehouse.containers().isEmpty() || player == null || itemId == null || itemId.isBlank()) {
            return ActionResult.NOT_FOUND;
        }
        long variants = LogisticsWarehouseInventoryService.aggregate(level, boxPos).stream()
                .filter(item -> itemId.equals(itemId(item.displayStack())))
                .count();
        if (variants > 1L) {
            return ActionResult.INVALID_TARGET;
        }
        for (BlockPos container : warehouse.containers()) {
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, container)) {
                ItemStack stack = snapshot.stack();
                if (!itemId.equals(itemId(stack))) {
                    continue;
                }
                int amount = Math.min(stack.getCount(), Math.max(1, countInsertablePlayer(player, stack)));
                if (amount <= 0) {
                    return ActionResult.NO_SPACE;
                }
                ItemStack extracted = GenericContainerAccess.extractFromSlot(level, container, snapshot.slot(), snapshot.access(), snapshot.side(), amount,
                        current -> itemId.equals(itemId(current)) && ItemStack.isSameItemSameComponents(current, stack));
                if (extracted.isEmpty()) {
                    continue;
                }
                ItemStack remaining = extracted.copy();
                player.getInventory().add(remaining);
                if (!remaining.isEmpty()) {
                    GenericContainerAccess.insert(level, container, remaining);
                }
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                return remaining.getCount() < extracted.getCount() ? ActionResult.SUCCESS : ActionResult.NO_SPACE;
            }
        }
        return ActionResult.NOT_FOUND;
    }

    /** onServerRemoved: 服务器盒被破坏时清理仓库、路线和仓储管理员岗位。 */
    public static void onServerRemoved(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return;
        }
        CitizenEmploymentService.fireAssigned(level,
                CitizenEmploymentService.workplaceId(LogisticsConstants.SERVER_SOURCE_TYPE, LogisticsConstants.STORAGE_ROLE, boxPos),
                LogisticsConstants.SERVER_SOURCE_TYPE,
                LogisticsConstants.STORAGE_ROLE,
                boxPos,
                "logistics_server_removed");
        LogisticsManager.get(level).removeWarehouse(boxPos);
    }

    /** onClientRemoved: 客户端盒被破坏时清理客户端和相关路线。 */
    public static void onClientRemoved(ServerLevel level, BlockPos boxPos) {
        if (level != null && boxPos != null) {
            LogisticsManager.get(level).removeClient(boxPos);
        }
    }

    /** findAssignedStorageWorker: 查找仓储管理员。 */
    public static CitizenData findAssignedStorageWorker(ServerLevel level, BlockPos boxPos) {
        return CitizenEmploymentService.findAssigned(level, LogisticsConstants.SERVER_SOURCE_TYPE, LogisticsConstants.STORAGE_ROLE, boxPos).orElse(null);
    }

    /** cityIdFor: 按物流盒所在区块解析城市归属。 */
    public static UUID cityIdFor(ServerLevel level, BlockPos pos) {
        return level != null && pos != null ? CityChunkManager.get(level).getChunkOwner(new ChunkPos(pos).toLong()) : null;
    }

    /** canManage: 校验玩家是否可以管理物流盒所在城市。 */
    public static boolean canManage(ServerLevel level, BlockPos pos, ServerPlayer player) {
        UUID cityId = cityIdFor(level, pos);
        return cityId != null && player != null && CityService.canManageCity(level, cityId, player.getUUID());
    }

    /** resolveClient: 同时查找手动客户端和建筑自动客户端。 */
    public static LogisticsClientData resolveClient(ServerLevel level, UUID clientId) {
        LogisticsClientData manual = LogisticsManager.get(level).manualClient(clientId);
        return manual != null ? manual : LogisticsAutoClientService.findClient(level, clientId);
    }

    public static String dimensionId(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static List<LogisticsClientData> allClients(ServerLevel level, UUID cityId, String dimensionId) {
        List<LogisticsClientData> clients = new ArrayList<>();
        clients.addAll(LogisticsManager.get(level).manualClients(cityId));
        clients.addAll(LogisticsAutoClientService.clientsForCity(level, cityId, dimensionId));
        return clients.stream()
                .sorted(Comparator.comparing(LogisticsClientData::displayName))
                .toList();
    }

    private static List<LogisticsInventoryEntry> aggregateInventory(ServerLevel level, BlockPos boxPos) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LogisticsWarehouseInventoryService.WarehouseItem item : LogisticsWarehouseInventoryService.aggregate(level, boxPos)) {
            String itemId = itemId(item.displayStack());
            if (!itemId.isBlank()) {
                counts.merge(itemId, (long) item.count(), Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new LogisticsInventoryEntry(entry.getKey(), entry.getKey(),
                        entry.getValue() > Integer.MAX_VALUE ? Integer.MAX_VALUE : entry.getValue().intValue()))
                .toList();
    }

    private static List<LogisticsInventoryEntry> aggregateClientInventory(ServerLevel level, LogisticsClientData client) {
        if (level == null || client == null || client.ports().isEmpty()) {
            return List.of();
        }
        Set<BlockPos> containers = new LinkedHashSet<>();
        for (BlockPos pos : clientSourcePortPositions(client)) {
            containers.add(GenericContainerAccess.canonicalContainerPos(level, pos));
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (BlockPos container : containers) {
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, container)) {
                String itemId = itemId(snapshot.stack());
                if (!itemId.isBlank()) {
                    counts.merge(itemId, (long) snapshot.stack().getCount(), Long::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new LogisticsInventoryEntry(entry.getKey(), entry.getKey(),
                        entry.getValue() > Integer.MAX_VALUE ? Integer.MAX_VALUE : entry.getValue().intValue()))
                .toList();
    }

    private static List<BlockPos> clientSourcePortPositions(LogisticsClientData client) {
        if (client == null || client.ports().isEmpty()) {
            return List.of();
        }
        List<BlockPos> outputPorts = new ArrayList<>();
        for (LogisticsPortData port : client.ports()) {
            if ("output".equalsIgnoreCase(port.kind())) {
                outputPorts.add(port.pos());
            }
        }
        return outputPorts.isEmpty()
                ? client.ports().stream().map(LogisticsPortData::pos).toList()
                : List.copyOf(outputPorts);
    }

    private static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "" : stack.getItemHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse("");
    }

    private static ClientEntry clientEntry(LogisticsClientData client) {
        return new ClientEntry(client.clientId(), client.boxPos(), client.displayName(), client.automatic(), client.sourceType(), client.ports().size());
    }

    private static ChannelEntry channelEntry(LogisticsChannelData channel) {
        return new ChannelEntry(channel.channelId(), channel.clientId(), channel.direction(), channel.name(), channel.enabled(),
                channel.filters().stream().map(LogisticsItemFilter::itemId).toList(), channel.keepSourceQuantity(), channel.keepTargetQuantity());
    }

    public enum ActionResult {
        SUCCESS,
        NOT_FOUND,
        NO_CONTAINER,
        NO_SPACE,
        LIMIT_REACHED,
        AREA_TOO_LARGE,
        INVALID_TARGET,
        NO_PERMISSION
    }

    public record ServerView(BlockPos boxPos,
                             boolean hasCity,
                             UUID cityId,
                             String cityName,
                             double cityBalance,
                             UUID warehouseId,
                             boolean hasWorker,
                             UUID workerId,
                             String workerName,
                             List<BlockPos> containers,
                             List<ClientEntry> clients,
                             List<ChannelEntry> channels,
                             List<LogisticsInventoryEntry> inventory,
                             List<ClientInventoryEntry> clientInventories) {
    }

    public record ClientView(BlockPos boxPos,
                             boolean hasCity,
                             UUID cityId,
                             String cityName,
                             UUID clientId,
                             String name,
                             List<LogisticsPortData> ports,
                             List<ChannelEntry> channels) {
    }

    public record ClientEntry(UUID clientId, BlockPos boxPos, String name, boolean automatic, String sourceType, int portCount) {
    }

    public record ClientInventoryEntry(UUID clientId, List<LogisticsInventoryEntry> inventory) {
    }

    public record ChannelEntry(UUID channelId, UUID clientId, LogisticsDirection direction, String name, boolean enabled, List<String> filters, int keepSourceQuantity, int keepTargetQuantity) {
        public String directionName() {
            return direction != null ? direction.name().toLowerCase(Locale.ROOT) : "";
        }
    }

    private static int countInsertablePlayer(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int remaining = stack.getCount();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) {
                continue;
            }
            int max = Math.min(inventory.getMaxStackSize(), existing.getMaxStackSize());
            remaining -= Math.max(0, Math.min(remaining, max - existing.getCount()));
        }
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            if (!inventory.getItem(slot).isEmpty()) {
                continue;
            }
            remaining -= Math.max(0, Math.min(remaining, Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize())));
        }
        return stack.getCount() - remaining;
    }
}
