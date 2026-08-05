package common.cn.kafei.simukraft.logistics;

import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("null")
final class LogisticsContainerBindingService {
    private static final long MAX_SELECTION_SCAN_BLOCKS = 65536L;

    private LogisticsContainerBindingService() {
    }

    /** bindWarehouseAdjacent: 绑定服务器盒相邻实体容器。 */
    static LogisticsControlBoxService.ActionResult bindWarehouseAdjacent(ServerLevel level, BlockPos boxPos) {
        LogisticsWarehouseData warehouse = LogisticsManager.get(level).warehouseAt(boxPos);
        if (warehouse == null) {
            return LogisticsControlBoxService.ActionResult.NOT_FOUND;
        }
        List<BlockPos> containers = new ArrayList<>(warehouse.containers());
        for (Direction direction : Direction.values()) {
            BlockPos candidate = GenericContainerAccess.canonicalContainerPos(level, boxPos.relative(direction));
            if (GenericContainerAccess.isContainer(level, candidate) && !containers.contains(candidate)) {
                containers.add(candidate.immutable());
            }
        }
        if (containers.size() > ServerConfig.logisticsMaxWarehouseContainers()) {
            return LogisticsControlBoxService.ActionResult.LIMIT_REACHED;
        }
        if (containers.equals(warehouse.containers())) {
            return LogisticsControlBoxService.ActionResult.NO_CONTAINER;
        }
        LogisticsManager.get(level).updateWarehouse(warehouse.withContainers(containers, level.getGameTime()));
        return LogisticsControlBoxService.ActionResult.SUCCESS;
    }

    /** bindWarehouseArea: 通过两点选区批量绑定仓库容器。 */
    static LogisticsControlBoxService.ActionResult bindWarehouseArea(ServerLevel level, BlockPos boxPos, BlockPos areaMin, BlockPos areaMax) {
        LogisticsWarehouseData warehouse = LogisticsManager.get(level).warehouseAt(boxPos);
        if (warehouse == null) {
            return LogisticsControlBoxService.ActionResult.NOT_FOUND;
        }
        if (exceedsSelectionLimit(areaMin, areaMax)) {
            return LogisticsControlBoxService.ActionResult.AREA_TOO_LARGE;
        }
        List<BlockPos> selected = scanContainersInArea(level, areaMin, areaMax);
        if (selected.isEmpty()) {
            return LogisticsControlBoxService.ActionResult.NO_CONTAINER;
        }
        Set<BlockPos> merged = new LinkedHashSet<>(warehouse.containers());
        selected.forEach(pos -> merged.add(pos.immutable()));
        if (merged.size() > ServerConfig.logisticsMaxWarehouseContainers()) {
            return LogisticsControlBoxService.ActionResult.LIMIT_REACHED;
        }
        if (merged.size() == warehouse.containers().size()) {
            return LogisticsControlBoxService.ActionResult.NO_CONTAINER;
        }
        LogisticsManager.get(level).updateWarehouse(warehouse.withContainers(List.copyOf(merged), level.getGameTime()));
        return LogisticsControlBoxService.ActionResult.SUCCESS;
    }

    /** bindClientAdjacent: 绑定客户端盒相邻实体容器端口。 */
    static LogisticsControlBoxService.ActionResult bindClientAdjacent(ServerLevel level, BlockPos boxPos) {
        LogisticsClientData client = LogisticsManager.get(level).clientAt(boxPos);
        if (client == null) {
            return LogisticsControlBoxService.ActionResult.NOT_FOUND;
        }
        List<LogisticsPortData> ports = new ArrayList<>(client.ports());
        for (Direction direction : Direction.values()) {
            BlockPos candidate = GenericContainerAccess.canonicalContainerPos(level, boxPos.relative(direction));
            if (!GenericContainerAccess.isContainer(level, candidate) || ports.stream().anyMatch(port -> port.pos().equals(candidate))) {
                continue;
            }
            ports.add(new LogisticsPortData(manualPortId(candidate), "manual", "manual", candidate));
        }
        if (ports.size() > ServerConfig.logisticsMaxClientPorts()) {
            return LogisticsControlBoxService.ActionResult.LIMIT_REACHED;
        }
        if (ports.equals(client.ports())) {
            return LogisticsControlBoxService.ActionResult.NO_CONTAINER;
        }
        LogisticsManager.get(level).updateClient(client.withPorts(ports, level.getGameTime()));
        return LogisticsControlBoxService.ActionResult.SUCCESS;
    }

    /** bindClientArea: 通过两点选区批量绑定客户端端口容器。 */
    static LogisticsControlBoxService.ActionResult bindClientArea(ServerLevel level, BlockPos boxPos, BlockPos areaMin, BlockPos areaMax) {
        LogisticsClientData client = LogisticsManager.get(level).clientAt(boxPos);
        if (client == null) {
            return LogisticsControlBoxService.ActionResult.NOT_FOUND;
        }
        if (exceedsSelectionLimit(areaMin, areaMax)) {
            return LogisticsControlBoxService.ActionResult.AREA_TOO_LARGE;
        }
        List<BlockPos> selected = scanContainersInArea(level, areaMin, areaMax);
        if (selected.isEmpty()) {
            return LogisticsControlBoxService.ActionResult.NO_CONTAINER;
        }
        Set<BlockPos> existingPositions = new LinkedHashSet<>();
        client.ports().forEach(port -> existingPositions.add(port.pos()));
        List<LogisticsPortData> ports = new ArrayList<>(client.ports());
        for (BlockPos pos : selected) {
            if (existingPositions.add(pos.immutable())) {
                ports.add(new LogisticsPortData(manualPortId(pos), "manual", "manual", pos));
            }
        }
        if (ports.size() > ServerConfig.logisticsMaxClientPorts()) {
            return LogisticsControlBoxService.ActionResult.LIMIT_REACHED;
        }
        if (ports.size() == client.ports().size()) {
            return LogisticsControlBoxService.ActionResult.NO_CONTAINER;
        }
        LogisticsManager.get(level).updateClient(client.withPorts(ports, level.getGameTime()));
        return LogisticsControlBoxService.ActionResult.SUCCESS;
    }

    /** scanContainersInArea: 扫描两点选区内的实体容器并做双箱归一化。 */
    private static List<BlockPos> scanContainersInArea(ServerLevel level, BlockPos first, BlockPos second) {
        if (level == null || first == null || second == null || exceedsSelectionLimit(first, second)) {
            return List.of();
        }
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        Set<BlockPos> result = new LinkedHashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos candidate = GenericContainerAccess.canonicalContainerPos(level, new BlockPos(x, y, z));
                    if (GenericContainerAccess.isContainer(level, candidate)) {
                        result.add(candidate.immutable());
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    /** exceedsSelectionLimit: 防止异常大选区造成单 tick 卡顿。 */
    private static boolean exceedsSelectionLimit(BlockPos first, BlockPos second) {
        if (first == null || second == null) {
            return true;
        }
        long x = Math.abs((long) first.getX() - second.getX()) + 1L;
        long y = Math.abs((long) first.getY() - second.getY()) + 1L;
        long z = Math.abs((long) first.getZ() - second.getZ()) + 1L;
        return x > MAX_SELECTION_SCAN_BLOCKS
                || y > MAX_SELECTION_SCAN_BLOCKS / x
                || z > MAX_SELECTION_SCAN_BLOCKS / (x * y);
    }

    /** manualPortId: 按容器坐标生成稳定手动端口 ID。 */
    private static String manualPortId(BlockPos pos) {
        return "manual_" + Long.toUnsignedString(pos.asLong(), 36);
    }
}
