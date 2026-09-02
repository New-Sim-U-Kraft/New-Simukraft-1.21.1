package common.cn.kafei.simukraft.building.controlbox;

import common.cn.kafei.simukraft.building.BuildingUnitInstance;
import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.building.BuildingPoiInstance;
import common.cn.kafei.simukraft.building.BuildingIntegrityService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.building.ResidentialOccupancyService;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.economy.ResidentialRentService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("null")
public final class ResidentialControlBoxService {
    private ResidentialControlBoxService() {
    }

    /** onRemoved：控制箱被移除时清理住户、停用POI并注销建筑记录。 */
    public static void onRemoved(ServerLevel level, BlockPos boxPos) {
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        if (building == null) return;
        CityPoiManager poiManager = CityPoiManager.get(level);
        CitizenManager citizenManager = CitizenManager.get(level);
        // 收集该建筑所有住宅POI的ID
        Set<UUID> residentialPoiIds = building.poiInstances().stream()
                .filter(instance -> instance.poiType() == CityPoiType.RESIDENTIAL)
                .map(instance -> poiManager.getPoiAt(instance.worldPos()))
                .filter(poi -> poi != null)
                .map(CityPoiData::poiId)
                .collect(java.util.stream.Collectors.toSet());
        // 驱逐住户：清除homeId引用
        citizenManager.allCitizens().stream()
                .filter(citizen -> citizen.homeId() != null && residentialPoiIds.contains(citizen.homeId()))
                .forEach(citizen -> {
                    citizen.setHomeId(null);
                    CitizenService.save(level, citizen.uuid());
                });
        // 停用所有住宅POI
        residentialPoiIds.forEach(poiManager::deactivatePoi);
        // 注销建筑记录
        PlacedBuildingService.unregister(level, building.buildingId());
    }

    public static ResidentialControlBoxView buildView(ServerLevel level, BlockPos controlBoxPos) {
        PlacedBuildingRecord building = resolveBuilding(level, controlBoxPos);
        List<CityPoiData> bedPois = resolveBedPois(level, building);
        int capacity = bedPois.size();
        List<ResidentialControlBoxView.ResidentEntry> residents = resolveResidents(level, bedPois);
        List<BlockPos> residentialPoiPositions = bedPois.stream()
                .map(poi -> poi.pos().immutable())
                .distinct()
                .toList();
        String buildingName = building != null && !building.displayName().isBlank()
                ? building.displayName()
                : "gui.residential_control_box.unknown_building";
        BlockPos min = building != null ? building.minPos() : BlockPos.ZERO;
        BlockPos max = building != null ? building.maxPos() : BlockPos.ZERO;
        BuildingIntegrityService.IntegrityPreview integrity = BuildingIntegrityService.preview(level, building);
        List<ResidentialControlBoxView.UnitView> units = buildUnitViews(building, bedPois, level);
        return new ResidentialControlBoxView(
                controlBoxPos.immutable(),
                buildingName,
                "gui.residential_control_box.building_type",
                residents.size(),
                capacity,
                residents,
                building != null,
                min,
                max,
                residentialPoiPositions,
                integrity.available(),
                integrity.percent(),
                integrity.repairableBlocks(),
                integrity.manualRepairBlocks(),
                integrity.repairCost(),
                building == null || ResidentialOccupancyService.isOccupancyAllowed(level, building.buildingId()),
                building != null ? ResidentialRentService.rentForBuilding(building) : 0.0D,
                units
        );
    }

    public static PlacedBuildingRecord findBuilding(ServerLevel level, BlockPos controlBoxPos) {
        return resolveBuilding(level, controlBoxPos);
    }

    /** canManageBuilding: 城市官员或管理员权限才能驱离和改入住开关。 */
    public static boolean canManageBuilding(ServerLevel level, ServerPlayer player, PlacedBuildingRecord building) {
        if (level == null || player == null || building == null || building.cityId() == null) {
            return false;
        }
        return player.hasPermissions(2) || CityService.canManageCity(level, building.cityId(), player.getUUID());
    }

    /** toggleOccupancy: 切换该住宅是否允许被分配系统入住。 */
    public static boolean toggleOccupancy(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null) {
            return false;
        }
        boolean next = !ResidentialOccupancyService.isOccupancyAllowed(level, building.buildingId());
        ResidentialOccupancyService.setOccupancyAllowed(level, building.buildingId(), next);
        return next;
    }

    /** evictResidents: 按该建筑租金扣费后清除本楼全部住户。 */
    public static EvictResult evictResidents(ServerLevel level, ServerPlayer player, PlacedBuildingRecord building) {
        if (level == null || building == null || building.cityId() == null) {
            return new EvictResult(EvictResult.Status.NO_BUILDING, 0, 0.0D);
        }
        List<ResidentialControlBoxView.ResidentEntry> residents = resolveResidents(level, resolveBedPois(level, building));
        if (residents.isEmpty()) {
            return new EvictResult(EvictResult.Status.NO_RESIDENTS, 0, 0.0D);
        }
        double cost = EconomyService.normalizeAmount(ResidentialRentService.rentForBuilding(building));
        if (cost > 0.0D && !EconomyService.canAfford(level, building.cityId(), cost)) {
            return new EvictResult(EvictResult.Status.NOT_ENOUGH_FUNDS, 0, cost);
        }
        if (cost > 0.0D && !EconomyService.withdrawCityFunds(level, building.cityId(), player, cost, "residential_eviction")) {
            return new EvictResult(EvictResult.Status.NOT_ENOUGH_FUNDS, 0, cost);
        }
        for (ResidentialControlBoxView.ResidentEntry resident : residents) {
            CitizenService.setHome(level, resident.citizenId(), null);
        }
        return EvictResult.success(residents.size(), cost);
    }

    /** EvictResult: 驱离结果，供网络层选择提示。 */
    public record EvictResult(Status status, int evictedCount, double cost) {
        public static EvictResult success(int evictedCount, double cost) {
            return new EvictResult(Status.SUCCESS, evictedCount, cost);
        }

        public enum Status {
            SUCCESS,
            NO_RESIDENTS,
            NOT_ENOUGH_FUNDS,
            NO_BUILDING
        }
    }

    private static PlacedBuildingRecord resolveBuilding(ServerLevel level, BlockPos controlBoxPos) {
        PlacedBuildingRecord byControlBoxPoi = PlacedBuildingService.findByPoiPos(level, controlBoxPos);
        if (byControlBoxPoi != null) {
            return byControlBoxPoi;
        }
        PlacedBuildingRecord byRecordedControlBox = findByRecordedControlBox(level, controlBoxPos);
        if (byRecordedControlBox != null) {
            return byRecordedControlBox;
        }
        return PlacedBuildingService.findByContainedPos(level, controlBoxPos);
    }

    private static PlacedBuildingRecord findByRecordedControlBox(ServerLevel level, BlockPos controlBoxPos) {
        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            boolean containsControlBoxBlock = building.blocks().stream()
                    .anyMatch(block -> controlBoxPos.equals(block.relativePos()) && block.state().is(ModBlocks.RESIDENTIAL_CONTROL_BOX.get()));
            if (containsControlBoxBlock) {
                return building;
            }
        }
        return null;
    }

    private static List<ResidentialControlBoxView.UnitView> buildUnitViews(
            PlacedBuildingRecord building, List<CityPoiData> bedPois, ServerLevel level) {
        if (building == null || building.unitInstances().isEmpty()) return List.of();
        java.util.Map<UUID, String> poiToUnit = new java.util.HashMap<>();
        java.util.Map<UUID, String> unitLabels = new java.util.LinkedHashMap<>();
        for (BuildingUnitInstance unit : building.unitInstances()) {
            unitLabels.put(unit.unitId(), unit.label());
            for (UUID poiId : unit.poiIds()) {
                poiToUnit.put(poiId, unit.label());
            }
        }
        java.util.Map<UUID, List<ResidentialControlBoxView.ResidentEntry>> residentsByUnit = new java.util.LinkedHashMap<>();
        java.util.Map<UUID, java.util.concurrent.atomic.AtomicInteger> bedCountByUnit = new java.util.LinkedHashMap<>();
        for (UUID unitId : unitLabels.keySet()) {
            residentsByUnit.put(unitId, new java.util.ArrayList<>());
            bedCountByUnit.put(unitId, new java.util.concurrent.atomic.AtomicInteger(0));
        }
        // 计算每户的床位数和入住居民
        java.util.Set<UUID> unitPoiIds = new java.util.HashSet<>();
        for (BuildingUnitInstance unit : building.unitInstances()) {
            unit.poiIds().forEach(unitPoiIds::add);
            bedCountByUnit.get(unit.unitId()).addAndGet(unit.poiIds().size());
        }
        for (CityPoiData poi : bedPois) {
            UUID unitId = null;
            for (var unit : building.unitInstances()) {
                if (unit.poiIds().contains(poi.poiId())) {
                    unitId = unit.unitId();
                    break;
                }
            }
            if (unitId == null) continue;
            final UUID unitIdFinal = unitId;
            CitizenManager.get(level).allCitizens().stream()
                    .filter(c -> !c.dead() && poi.poiId().equals(c.homeId()))
                    .forEach(c -> residentsByUnit.get(unitIdFinal).add(
                            new ResidentialControlBoxView.ResidentEntry(c.uuid(), c.name())));
        }
        return unitLabels.entrySet().stream()
                .map(e -> new ResidentialControlBoxView.UnitView(
                        e.getKey(), e.getValue(),
                        bedCountByUnit.get(e.getKey()).get(),
                        residentsByUnit.get(e.getKey())))
                .toList();
    }

    private static List<CityPoiData> resolveBedPois(ServerLevel level, PlacedBuildingRecord building) {
        if (building == null) {
            return List.of();
        }
        CityPoiManager manager = CityPoiManager.get(level);
        List<CityPoiData> registeredPois = building.poiInstances().stream()
                .filter(instance -> instance.poiType() == CityPoiType.RESIDENTIAL)
                .map(instance -> manager.getPoiAt(instance.worldPos()))
                .filter(registeredPoi -> registeredPoi != null && registeredPoi.active() && registeredPoi.type() == CityPoiType.RESIDENTIAL && isRedBedHead(level.getBlockState(registeredPoi.pos())))
                .toList();
        if (!registeredPois.isEmpty()) {
            return registeredPois;
        }
        return repairMissingBedPois(level, building, manager);
    }

    private static List<CityPoiData> repairMissingBedPois(ServerLevel level, PlacedBuildingRecord building, CityPoiManager manager) {
        if (building.cityId() == null) {
            return List.of();
        }
        List<BuildingPoiInstance> bedPoiInstances = BuilderConstructionService.resolveResidentialBedPois(building);
        if (bedPoiInstances.isEmpty()) {
            return List.of();
        }
        List<CityPoiData> repaired = bedPoiInstances.stream()
                .map(instance -> manager.registerPoi(stablePoiId(instance, building.dimensionId()), building.cityId(), instance.worldPos(), CityPoiType.RESIDENTIAL, instance.capacity()))
                .toList();
        PlacedBuildingService.register(level, new PlacedBuildingRecord(
                building.buildingId(),
                building.cityId(),
                building.dimensionId(),
                building.category(),
                building.buildingFileName(),
                building.displayName(),
                building.amount(),
                building.structureFileName(),
                building.facing(),
                building.worldOrigin(),
                building.structureAnchor(),
                building.minPos(),
                building.maxPos(),
                building.completedAt(),
                building.blocks(),
                building.poiDefinitions(),
                mergePoiInstances(building.poiInstances(), bedPoiInstances),
                building.unitDefinitions(),
                building.unitInstances()
        ));
        return repaired;
    }

    private static List<BuildingPoiInstance> mergePoiInstances(List<BuildingPoiInstance> existing, List<BuildingPoiInstance> additions) {
        java.util.LinkedHashMap<String, BuildingPoiInstance> merged = new java.util.LinkedHashMap<>();
        existing.forEach(instance -> merged.put(instance.key(), instance));
        additions.forEach(instance -> merged.putIfAbsent(instance.key(), instance));
        return List.copyOf(merged.values());
    }

    private static UUID stablePoiId(BuildingPoiInstance instance, String dimensionId) {
        try {
            return UUID.fromString(instance.key());
        } catch (IllegalArgumentException exception) {
            String scope = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
            return UUID.nameUUIDFromBytes((scope + ":" + instance.poiType().name() + "@" + instance.worldPos().toShortString()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static List<ResidentialControlBoxView.ResidentEntry> resolveResidents(ServerLevel level, List<CityPoiData> bedPois) {
        Set<UUID> homePoiIds = bedPois.stream().map(CityPoiData::poiId).collect(java.util.stream.Collectors.toSet());
        if (homePoiIds.isEmpty()) {
            return List.of();
        }
        return CitizenManager.get(level).allCitizens().stream()
                .filter(citizen -> !citizen.dead())
                .filter(citizen -> homePoiIds.contains(citizen.homeId()))
                .sorted(Comparator.comparing(citizen -> safeName(citizen.name())))
                .map(citizen -> new ResidentialControlBoxView.ResidentEntry(citizen.uuid(), safeName(citizen.name())))
                .toList();
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "entity.simukraft.citizen" : name;
    }

    private static boolean isRedBedHead(BlockState state) {
        return state.is(Blocks.RED_BED)
                && (!state.hasProperty(BlockStateProperties.BED_PART)
                || state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD);
    }
}
