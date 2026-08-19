package common.cn.kafei.simukraft.logistics;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.commercial.CommercialDefinition;
import common.cn.kafei.simukraft.commercial.CommercialDefinitionLoader;
import common.cn.kafei.simukraft.industrial.IndustrialCoordinateResolver;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialDefinitionLoader;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class LogisticsAutoClientService {
    private static final long CACHE_TTL_TICKS = 100L;
    private static final ConcurrentMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private LogisticsAutoClientService() {
    }

    /** clientsForCity: 获取指定城市和维度的建筑自动物流客户端。 */
    public static List<LogisticsClientData> clientsForCity(ServerLevel level, UUID cityId, String dimensionId) {
        if (level == null || cityId == null) {
            return List.of();
        }
        String safeDimensionId = dimensionId != null ? dimensionId : level.dimension().location().toString();
        return allClients(level).stream()
                .filter(client -> cityId.equals(client.cityId()) && safeDimensionId.equals(client.dimensionId()))
                .toList();
    }

    /** findClient: 按稳定 UUID 查找自动物流客户端。 */
    public static LogisticsClientData findClient(ServerLevel level, UUID clientId) {
        if (level == null || clientId == null) {
            return null;
        }
        for (LogisticsClientData client : allClients(level)) {
            if (clientId.equals(client.clientId())) return client;
        }
        return null;
    }

    /** allClients: 返回当前维度的自动客户端快照。 */
    public static List<LogisticsClientData> allClients(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        String key = SaveScopedCacheKey.levelKey(level);
        long gameTime = level.getGameTime();
        CacheEntry cached = CACHE.get(key);
        if (cached != null && gameTime - cached.loadedAt() <= CACHE_TTL_TICKS) {
            return cached.clients();
        }
        List<LogisticsClientData> clients = buildClients(level, gameTime);
        CACHE.put(key, new CacheEntry(gameTime, clients));
        return clients;
    }

    /** clearServerCaches: 清理服务器维度缓存，防止切档复用旧建筑端口。 */
    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server);
        CACHE.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    private static List<LogisticsClientData> buildClients(ServerLevel level, long gameTime) {
        List<LogisticsClientData> clients = new ArrayList<>();
        String dimensionId = level.dimension().location().toString();
        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            if (building == null || building.cityId() == null) {
                continue;
            }
            String category = building.category() != null ? building.category().toLowerCase(Locale.ROOT) : "";
            try {
                if ("industrial".equals(category) || "industry".equals(category)) {
                    appendIndustrialClient(level, building, dimensionId, gameTime, clients);
                } else if ("commercial".equals(category) || "commerce".equals(category)) {
                    appendCommercialClient(level, building, dimensionId, gameTime, clients);
                }
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.warn("Simukraft: Failed to build logistics auto client for {}", building.buildingId(), exception);
            }
        }
        return List.copyOf(clients);
    }

    private static void appendIndustrialClient(ServerLevel level, PlacedBuildingRecord building, String dimensionId, long gameTime, List<LogisticsClientData> output) {
        if (isDrillingPlatform(building)) {
            return;
        }
        IndustrialDefinition definition = IndustrialDefinitionLoader.loadForBuilding(building).definition();
        if (definition == null || definition.containers().isEmpty()) {
            return;
        }
        List<LogisticsPortData> ports = new ArrayList<>();
        for (IndustrialDefinition.ContainerDefinition container : definition.containers().values()) {
            appendPorts(building, container.id(), container.type(), container.positions(), ports);
        }
        appendClient(building, dimensionId, gameTime, LogisticsConstants.AUTO_INDUSTRIAL_SOURCE_TYPE, ports, output);
    }

    /** isDrillingPlatform: 钻井平台木桶由玩家手动建立物流接口，不注册自动客户端。 */
    private static boolean isDrillingPlatform(PlacedBuildingRecord building) {
        return building != null
                && BuildingCatalog.findBuilding(building.category(), building.buildingFileName())
                .map(BuildingCatalog.BuildingDefinition::isDrillingPlatform)
                .orElse(false);
    }

    private static void appendCommercialClient(ServerLevel level, PlacedBuildingRecord building, String dimensionId, long gameTime, List<LogisticsClientData> output) {
        CommercialDefinition definition = CommercialDefinitionLoader.loadForBuilding(building).definition();
        if (definition == null || definition.containers().isEmpty()) {
            return;
        }
        List<LogisticsPortData> ports = new ArrayList<>();
        for (CommercialDefinition.ContainerDefinition container : definition.containers().values()) {
            appendPorts(building, container.id(), container.type(), container.positions(), ports);
        }
        appendClient(building, dimensionId, gameTime, LogisticsConstants.AUTO_COMMERCIAL_SOURCE_TYPE, ports, output);
    }

    private static void appendClient(PlacedBuildingRecord building,
                                     String dimensionId,
                                     long gameTime,
                                     String sourceType,
                                     List<LogisticsPortData> ports,
                                     List<LogisticsClientData> output) {
        if (ports.isEmpty()) {
            return;
        }
        String sourceId = building.buildingId().toString();
        output.add(new LogisticsClientData(
                stableClientId(sourceType, dimensionId, sourceId),
                building.worldOrigin(),
                building.cityId(),
                dimensionId,
                building.displayName(),
                true,
                sourceType,
                sourceId,
                ports,
                gameTime));
    }

    private static void appendPorts(PlacedBuildingRecord building, String id, String type, List<BlockPos> structurePositions, List<LogisticsPortData> output) {
        if (!"structure_pos".equalsIgnoreCase(type)) {
            return;
        }
        List<BlockPos> positions = IndustrialCoordinateResolver.resolvePositions(building, structurePositions);
        for (int i = 0; i < positions.size(); i++) {
            String portId = id + "_" + i;
            output.add(new LogisticsPortData(portId, id, portKind(id), positions.get(i)));
        }
    }

    private static String portKind(String id) {
        String normalized = id != null ? id.toLowerCase(Locale.ROOT) : "";
        if (normalized.contains("input")) {
            return "input";
        }
        if (normalized.contains("output")) {
            return "output";
        }
        return normalized.isBlank() ? "manual" : normalized;
    }

    private static UUID stableClientId(String sourceType, String dimensionId, String sourceId) {
        String key = sourceType + ":" + dimensionId + ":" + sourceId;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private record CacheEntry(long loadedAt, List<LogisticsClientData> clients) {
    }
}
