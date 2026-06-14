package common.cn.kafei.simukraft.building;

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
        return BY_DIMENSION.computeIfAbsent(cacheKey, ignored -> load(level, dimensionId));
    }

    public static void register(ServerLevel level, PlacedBuildingRecord record) {
        if (level == null || record == null) {
            return;
        }
        String cacheKey = SaveScopedCacheKey.levelKey(level);
        BuildingStructureRepository repository = new BuildingStructureRepository(BuildingStructureSqliteDatabase.open(level.getServer()));
        repository.upsert(record);
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
        BuildingStructureRepository repository = new BuildingStructureRepository(BuildingStructureSqliteDatabase.open(level.getServer()));
        repository.delete(buildingId);
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
                            poiInstances
                    ));
                }
            }
            for (BuildingPoiInstance poi : poiInstances) {
                manager.registerPoi(stablePoiId(poi), record.cityId(), poi.worldPos(), poi.poiType(), poi.capacity());
            }
        }
        repairedCities.forEach(cityId -> CitizenHousingService.fillVacantHomes(level, cityId));
    }

    private static List<PlacedBuildingRecord> load(ServerLevel level, String dimensionId) {
        BuildingStructureRepository repository = new BuildingStructureRepository(BuildingStructureSqliteDatabase.open(level.getServer()));
        return repository.loadByDimension(dimensionId);
    }

    // 清理指定存档的建筑实例缓存，防止单人切换世界后复用旧存档数据。
    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server);
        BY_DIMENSION.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
        POI_REPAIRED_DIMENSIONS.removeIf(key -> key.startsWith(serverKey + "|"));
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

    private static UUID stablePoiId(BuildingPoiInstance poi) {
        // 优先使用建筑记录中的稳定 UUID，非 UUID key 再退回到类型和坐标生成。
        try {
            return UUID.fromString(poi.key());
        } catch (IllegalArgumentException exception) {
            return UUID.nameUUIDFromBytes((poi.poiType().name() + "@" + poi.worldPos().toShortString()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
