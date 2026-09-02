package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.storage.BuildingStructureRepository;
import common.cn.kafei.simukraft.storage.BuildingStructureSqliteDatabase;
import common.cn.kafei.simukraft.citizen.CitizenHousingService;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class PlacedBuildingService {
    // 已完成建筑属于存档数据，缓存键必须包含存档和维度。
    private static final ConcurrentMap<String, List<PlacedBuildingRecord>> BY_DIMENSION = new ConcurrentHashMap<>();
    // 每个存档维度只做一次 POI 自修复，避免每 tick 扫描所有建筑。
    private static final java.util.Set<String> POI_REPAIRED_DIMENSIONS = ConcurrentHashMap.newKeySet();

    private PlacedBuildingService() {
    }

    public static List<PlacedBuildingRecord> getBuildings(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        String dimensionId = level.dimension().location().toString();
        String cacheKey = SaveScopedCacheKey.levelKey(level);
        List<PlacedBuildingRecord> records = BY_DIMENSION.computeIfAbsent(cacheKey, ignored -> load(level, dimensionId));
        // 加载失败返回 null 时不落缓存（computeIfAbsent 不存 null），本次按空列表兜底，下次访问重试；
        // 不能把失败的空结果缓存整个会话，否则一次读取故障就让全维度建筑"消失"到重启。
        return records != null ? records : List.of();
    }

    public static void register(ServerLevel level, PlacedBuildingRecord record) {
        if (level == null || record == null) {
            return;
        }
        String cacheKey = SaveScopedCacheKey.levelKey(level);
        BuildingStructureRepository repository = repository(level);
        BuildingStructureRepository.WriteOutcome outcome = repository != null
                ? repository.upsert(record)
                : BuildingStructureRepository.WriteOutcome.STORAGE_UNAVAILABLE;
        if (outcome == BuildingStructureRepository.WriteOutcome.FAILED) {
            // 单条写入失败就不写内存缓存：宁可建筑立即表现为未登记，也不让内存与磁盘静默分叉、重启后消失。
            SimuKraft.LOGGER.error("Placed building {} was not registered because its structure could not be persisted.", record.buildingId());
            return;
        }
        if (outcome == BuildingStructureRepository.WriteOutcome.STORAGE_UNAVAILABLE) {
            /*
             * 整库降级/已关闭是整会话状态。此时照样登记进内存：降级的语义是"磁盘冻结、内存权威"，
             * 若也当成失败作废，降级之后每一座建成的建筑都会彻底不生效（无 POI、无住房、无产线绑定），
             * 玩家侧只能看到"造完什么都没发生"。代价是这些建筑重启后会丢，日志里必须写清楚。
             */
            SimuKraft.LOGGER.error("Placed building {} is registered in memory only: building structure storage is unavailable. It will be lost on restart.", record.buildingId());
        }
        BY_DIMENSION.compute(cacheKey, (ignored, records) -> {
            List<PlacedBuildingRecord> mutable = new ArrayList<>(records != null ? records : List.of());
            mutable.removeIf(existing -> existing.buildingId().equals(record.buildingId()));
            mutable.add(record);
            return List.copyOf(mutable);
        });
    }

    public static void unregister(ServerLevel level, UUID buildingId) {
        if (level == null || buildingId == null) {
            return;
        }
        String cacheKey = SaveScopedCacheKey.levelKey(level);
        BuildingStructureRepository repository = repository(level);
        BuildingStructureRepository.WriteOutcome outcome = repository != null
                ? repository.delete(buildingId)
                : BuildingStructureRepository.WriteOutcome.STORAGE_UNAVAILABLE;
        if (outcome == BuildingStructureRepository.WriteOutcome.FAILED) {
            // 单条删除失败则保留内存登记，与库内状态保持一致（重进档后建筑仍在），并留下可追踪日志。
            SimuKraft.LOGGER.error("Placed building {} could not be removed from storage; keeping it registered to stay consistent with the database.", buildingId);
            return;
        }
        if (outcome == BuildingStructureRepository.WriteOutcome.STORAGE_UNAVAILABLE) {
            // 降级时磁盘冻结、内存跟随游戏世界：方块已经被拆了，内存里再留着登记会让 POI/住房指向不存在的建筑。
            SimuKraft.LOGGER.error("Placed building {} is unregistered in memory only: building structure storage is unavailable. It will come back on restart.", buildingId);
        }
        // 建筑没了，废弃度记录也要清掉，否则同一 buildingId 的旧废弃度会一直留在库里。
        BuildingAbandonmentService.forget(level, buildingId);
        ResidentialOccupancyService.forget(level, buildingId);
        BY_DIMENSION.computeIfPresent(cacheKey, (ignored, records) -> {
            List<PlacedBuildingRecord> mutable = new ArrayList<>(records);
            mutable.removeIf(existing -> existing.buildingId().equals(buildingId));
            return List.copyOf(mutable);
        });
    }

    public static boolean intersects(ServerLevel level, BlockPos worldPos) {
        for (PlacedBuildingRecord building : getBuildings(level)) {
            if (isInside(worldPos, building.minPos(), building.maxPos())) {
                return true;
            }
        }
        return false;
    }

    public static PlacedBuildingRecord findByPoi(ServerLevel level, UUID poiId) {
        if (level == null || poiId == null) {
            return null;
        }
        for (PlacedBuildingRecord record : getBuildings(level)) {
            for (BuildingPoiInstance poi : record.poiInstances()) {
                if (poiId.toString().equalsIgnoreCase(poi.key())) {
                    return record;
                }
            }
        }
        return null;
    }

    public static PlacedBuildingRecord findByPoiPos(ServerLevel level, BlockPos poiPos) {
        if (level == null || poiPos == null) {
            return null;
        }
        BlockPos immutablePos = poiPos.immutable();
        for (PlacedBuildingRecord record : getBuildings(level)) {
            for (BuildingPoiInstance poi : record.poiInstances()) {
                if (immutablePos.equals(poi.worldPos())) {
                    return record;
                }
            }
        }
        return null;
    }

    public static PlacedBuildingRecord findByContainedPos(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        for (PlacedBuildingRecord record : getBuildings(level)) {
            if (isInside(pos, record.minPos(), record.maxPos())) {
                return record;
            }
        }
        return null;
    }

    public static PlacedBuildingRecord findByContainedPosAndCategory(ServerLevel level, BlockPos pos, String... categories) {
        if (level == null || pos == null || categories == null || categories.length == 0) {
            return null;
        }
        for (PlacedBuildingRecord record : getBuildings(level)) {
            if (!isInside(pos, record.minPos(), record.maxPos())) {
                continue;
            }
            String cat = record.category() != null ? record.category().toLowerCase(Locale.ROOT) : "";
            for (String expected : categories) {
                if (expected != null && expected.toLowerCase(Locale.ROOT).equals(cat)) {
                    return record;
                }
            }
        }
        return null;
    }

    public static boolean isOccupiedByOtherBuilding(ServerLevel level, UUID ignoredBuildingId, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        for (PlacedBuildingRecord record : getBuildings(level)) {
            if (ignoredBuildingId != null && ignoredBuildingId.equals(record.buildingId())) {
                continue;
            }
            if (containsRecordedBlock(record, pos)) {
                return true;
            }
        }
        return false;
    }

    public static void ensureCityPoisRegistered(ServerLevel level) {
        if (level == null) {
            return;
        }
        String cacheKey = SaveScopedCacheKey.levelKey(level);
        if (!POI_REPAIRED_DIMENSIONS.add(cacheKey)) {
            return;
        }
        CityPoiManager manager = CityPoiManager.get(level);
        java.util.Set<UUID> repairedCities = new java.util.HashSet<>();
        // 从已持久化建筑反推 POI，解决旧存档或异常退出后 POI 丢失的问题。
        for (PlacedBuildingRecord record : getBuildings(level)) {
            if (record.cityId() == null) {
                continue;
            }
            repairedCities.add(record.cityId());
            List<BuildingPoiInstance> poiInstances = record.poiInstances();
            if (poiInstances.stream().noneMatch(instance -> instance.poiType() == common.cn.kafei.simukraft.city.poi.CityPoiType.RESIDENTIAL)) {
                // 旧记录没有住宅床位时，用已保存的方块数据重新生成床位 POI。
                List<BuildingPoiInstance> repaired = BuilderConstructionService.resolveResidentialBedPois(record);
                if (!repaired.isEmpty()) {
                    poiInstances = mergePoiInstances(poiInstances, repaired);
                    register(level, new PlacedBuildingRecord(
                            record.buildingId(),
                            record.cityId(),
                            record.dimensionId(),
                            record.category(),
                            record.buildingFileName(),
                            record.displayName(),
                            record.amount(),
                            record.structureFileName(),
                            record.facing(),
                            record.worldOrigin(),
                            record.structureAnchor(),
                            record.minPos(),
                            record.maxPos(),
                            record.completedAt(),
                            record.blocks(),
                            record.poiDefinitions(),
                            poiInstances,
                            record.unitDefinitions(),
                            record.unitInstances()
                    ));
                }
            }
            if (poiInstances.stream().noneMatch(instance -> instance.poiType() == common.cn.kafei.simukraft.city.poi.CityPoiType.MEDICAL)) {
                List<BuildingPoiInstance> repaired = BuilderConstructionService.resolveMedicalBedPois(record);
                if (!repaired.isEmpty()) {
                    poiInstances = mergePoiInstances(poiInstances, repaired);
                    register(level, new PlacedBuildingRecord(
                            record.buildingId(), record.cityId(), record.dimensionId(), record.category(),
                            record.buildingFileName(), record.displayName(), record.amount(), record.structureFileName(),
                            record.facing(), record.worldOrigin(), record.structureAnchor(), record.minPos(), record.maxPos(),
                            record.completedAt(), record.blocks(), record.poiDefinitions(), poiInstances,
                            record.unitDefinitions(), record.unitInstances()));
                }
            }
            for (BuildingPoiInstance poi : poiInstances) {
                manager.registerPoi(stablePoiId(poi, record.dimensionId()), record.cityId(), poi.worldPos(), poi.poiType(), poi.capacity());
            }
        }
        // 服务器重启后按建筑元数据重建单元，兼容旧存档缺少 unitId 的情况。
        rebuildUnitInstancesIfNeeded(level, manager);
        repairedCities.forEach(cityId -> CitizenHousingService.fillVacantHomes(level, cityId));
    }

    // 通过已记录或建筑包中的 unit: 定义重建运行时单元，并同步 POI 的 unitId。
    private static void rebuildUnitInstancesIfNeeded(ServerLevel level, CityPoiManager poiManager) {
        String cacheKey = SaveScopedCacheKey.levelKey(level);
        List<PlacedBuildingRecord> current = BY_DIMENSION.getOrDefault(cacheKey, List.of());
        List<PlacedBuildingRecord> updated = new ArrayList<>(current.size());
        boolean anyChanged = false;
        for (PlacedBuildingRecord record : current) {
            if (!record.unitInstances().isEmpty()) {
                updated.add(record);
                continue;
            }
            List<BuildingUnitDefinition> unitDefs = BuildingUnitResolver.resolveUnitDefinitions(record);
            List<BuildingUnitInstance> rebuilt = BuildingUnitResolver.resolveUnitInstances(record, poiManager);
            if (rebuilt.isEmpty()) {
                updated.add(record);
                continue;
            }
            for (BuildingUnitInstance unit : rebuilt) {
                unit.poiIds().forEach(poiId -> poiManager.updatePoiUnitId(poiId, unit.unitId()));
            }
            updated.add(new PlacedBuildingRecord(
                    record.buildingId(), record.cityId(), record.dimensionId(),
                    record.category(), record.buildingFileName(), record.displayName(),
                    record.amount(), record.structureFileName(), record.facing(),
                    record.worldOrigin(), record.structureAnchor(), record.minPos(),
                    record.maxPos(), record.completedAt(), record.blocks(),
                    record.poiDefinitions(), record.poiInstances(), unitDefs, rebuilt));
            anyChanged = true;
        }
        if (anyChanged) {
            BY_DIMENSION.put(cacheKey, List.copyOf(updated));
        }
    }

    private static List<PlacedBuildingRecord> load(ServerLevel level, String dimensionId) {
        BuildingStructureRepository repository = repository(level);
        return repository != null ? repository.loadByDimension(dimensionId) : List.of();
    }

    /**
     * repository：BuildingStructureSqliteDatabase.open 按存档缓存实例，这里不再每次调用都新建数据库对象。
     * <p>关服之后 open 返回 null（不再重建僵尸实例），此时返回 null，调用方按"存储不可用"处理。
     */
    private static BuildingStructureRepository repository(ServerLevel level) {
        BuildingStructureSqliteDatabase database = BuildingStructureSqliteDatabase.open(level.getServer());
        return database != null ? new BuildingStructureRepository(database) : null;
    }

    // 清理指定存档的建筑实例缓存，防止单人切换世界后复用旧存档数据。
    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server);
        BY_DIMENSION.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
        POI_REPAIRED_DIMENSIONS.removeIf(key -> key.startsWith(serverKey + "|"));
        BuildingAbandonmentService.clearCache(server);
        ResidentialOccupancyService.clearCache(server);
        BuildingStructureSqliteDatabase.closeFor(server);
    }

    private static boolean isInside(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= Math.min(min.getX(), max.getX()) && pos.getX() <= Math.max(min.getX(), max.getX())
                && pos.getY() >= Math.min(min.getY(), max.getY()) && pos.getY() <= Math.max(min.getY(), max.getY())
                && pos.getZ() >= Math.min(min.getZ(), max.getZ()) && pos.getZ() <= Math.max(min.getZ(), max.getZ());
    }

    private static boolean containsRecordedBlock(PlacedBuildingRecord record, BlockPos worldPos) {
        if (!isInside(worldPos, record.minPos(), record.maxPos())) {
            return false;
        }
        return record.blocks().stream().anyMatch(block -> worldPos.equals(resolveWorldPos(record, block.relativePos())));
    }

    private static BlockPos resolveWorldPos(PlacedBuildingRecord record, BlockPos storedPos) {
        if (isInside(storedPos, record.minPos(), record.maxPos())) {
            return storedPos;
        }
        return record.worldOrigin().offset(storedPos);
    }

    private static List<BuildingPoiInstance> mergePoiInstances(List<BuildingPoiInstance> existing, List<BuildingPoiInstance> additions) {
        java.util.LinkedHashMap<String, BuildingPoiInstance> merged = new java.util.LinkedHashMap<>();
        existing.forEach(instance -> merged.put(instance.key(), instance));
        additions.forEach(instance -> merged.putIfAbsent(instance.key(), instance));
        return List.copyOf(merged.values());
    }

    private static UUID stablePoiId(BuildingPoiInstance poi, String dimensionId) {
        // 优先使用建筑记录中的稳定 UUID，非 UUID key 再退回到类型和坐标生成。
        try {
            return UUID.fromString(poi.key());
        } catch (IllegalArgumentException exception) {
            String scope = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
            return UUID.nameUUIDFromBytes((scope + ":" + poi.poiType().name() + "@" + poi.worldPos().toShortString()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
