package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** BuildingUnitResolver：按建筑元数据和世界坐标确定住宅户的床位归属。 */
public final class BuildingUnitResolver {
    private BuildingUnitResolver() {
    }

    /** resolveResidentialPoiGroups：返回建筑内每户的住宅 POI ID；无单元定义时整栋为一户。 */
    public static List<List<UUID>> resolveResidentialPoiGroups(PlacedBuildingRecord building) {
        if (building == null) {
            return List.of();
        }
        List<BuildingPoiInstance> residentialPois = residentialPois(building);
        if (residentialPois.isEmpty()) {
            return List.of();
        }

        List<BuildingUnitDefinition> definitions = resolveUnitDefinitions(building);
        if (!definitions.isEmpty()) {
            List<List<UUID>> groups = new ArrayList<>();
            LinkedHashSet<UUID> assigned = new LinkedHashSet<>();
            for (List<UUID> unitPoiIds : unitPoiIdsByDefinition(building, definitions)) {
                if (!unitPoiIds.isEmpty()) {
                    groups.add(unitPoiIds);
                    assigned.addAll(unitPoiIds);
                }
            }
            List<UUID> unassigned = residentialPois.stream()
                    .map(poi -> stablePoiId(building, poi))
                    .filter(poiId -> !assigned.contains(poiId))
                    .toList();
            if (!unassigned.isEmpty()) {
                groups.add(unassigned);
            }
            return List.copyOf(groups);
        }

        return List.copyOf(groupsFromInstances(building, residentialPois));
    }

    /** resolveUnitInstances：按元数据重建单元实例，并尽量保留已持久化的单元 ID。 */
    public static List<BuildingUnitInstance> resolveUnitInstances(PlacedBuildingRecord building,
                                                                    CityPoiManager poiManager) {
        List<BuildingUnitDefinition> definitions = resolveUnitDefinitions(building);
        if (definitions.isEmpty()) {
            return List.of();
        }

        List<List<UUID>> poiIdsByUnit = unitPoiIdsByDefinition(building, definitions);
        List<BuildingUnitInstance> units = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            List<UUID> poiIds = poiIdsByUnit.get(index);
            if (poiIds.isEmpty()) {
                continue;
            }
            BuildingUnitDefinition definition = definitions.get(index);
            units.add(new BuildingUnitInstance(
                    resolveUnitId(building, poiManager, poiIds, index, definition.label()),
                    definition.label(),
                    List.copyOf(poiIds)
            ));
        }
        return List.copyOf(units);
    }

    /** resolveUnitDefinitions：优先使用记录中的定义，旧存档则从当前建筑包补读。 */
    public static List<BuildingUnitDefinition> resolveUnitDefinitions(PlacedBuildingRecord building) {
        if (building == null) {
            return List.of();
        }
        if (!building.unitDefinitions().isEmpty()) {
            return building.unitDefinitions();
        }
        BuildingCatalog.BuildingDefinition definition = BuildingCatalog
                .findBuilding(building.category(), building.buildingFileName())
                .orElse(null);
        return definition != null ? BuildingMetadataReader.readUnitDefinitions(definition) : List.of();
    }

    private static List<List<UUID>> unitPoiIdsByDefinition(PlacedBuildingRecord building,
                                                             List<BuildingUnitDefinition> definitions) {
        List<List<UUID>> poiIdsByUnit = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            poiIdsByUnit.add(new ArrayList<>());
        }
        int rotationDegrees = BuildingTransform.rotationDegreesFromFacing(building.facing());
        for (BuildingPoiInstance poi : residentialPois(building)) {
            BlockPos sourceRelativePos = BuildingTransform.inverseRotatePosition(
                    poi.worldPos().subtract(building.worldOrigin()),
                    rotationDegrees
            );
            for (int index = 0; index < definitions.size(); index++) {
                if (definitions.get(index).contains(sourceRelativePos)) {
                    poiIdsByUnit.get(index).add(stablePoiId(building, poi));
                    break;
                }
            }
        }
        return poiIdsByUnit.stream().map(List::copyOf).toList();
    }

    private static List<List<UUID>> groupsFromInstances(PlacedBuildingRecord building,
                                                          List<BuildingPoiInstance> residentialPois) {
        LinkedHashSet<UUID> knownPoiIds = residentialPois.stream()
                .map(poi -> stablePoiId(building, poi))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<UUID> assignedPoiIds = new LinkedHashSet<>();
        List<List<UUID>> groups = new ArrayList<>();
        for (BuildingUnitInstance unit : building.unitInstances()) {
            List<UUID> poiIds = unit.poiIds().stream().filter(knownPoiIds::contains).toList();
            if (!poiIds.isEmpty()) {
                groups.add(poiIds);
                assignedPoiIds.addAll(poiIds);
            }
        }
        List<UUID> unassigned = knownPoiIds.stream().filter(poiId -> !assignedPoiIds.contains(poiId)).toList();
        if (!unassigned.isEmpty()) {
            groups.add(unassigned);
        }
        return groups;
    }

    private static UUID resolveUnitId(PlacedBuildingRecord building, CityPoiManager poiManager,
                                      List<UUID> poiIds, int index, String label) {
        UUID persistedId = null;
        for (UUID poiId : poiIds) {
            var poi = poiManager != null ? poiManager.getPoi(poiId) : null;
            UUID unitId = poi != null ? poi.unitId() : null;
            if (unitId == null) {
                persistedId = null;
                break;
            }
            if (persistedId == null) {
                persistedId = unitId;
            } else if (!persistedId.equals(unitId)) {
                persistedId = null;
                break;
            }
        }
        if (persistedId != null) {
            return persistedId;
        }
        String key = building.buildingId() + ":unit:" + index + ":" + label;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static List<BuildingPoiInstance> residentialPois(PlacedBuildingRecord building) {
        return building.poiInstances().stream()
                .filter(poi -> poi.poiType() == CityPoiType.RESIDENTIAL)
                .toList();
    }

    private static UUID stablePoiId(PlacedBuildingRecord building, BuildingPoiInstance poi) {
        try {
            return UUID.fromString(poi.key());
        } catch (IllegalArgumentException exception) {
            String dimensionId = building.dimensionId() == null || building.dimensionId().isBlank()
                    ? "minecraft:overworld" : building.dimensionId();
            String key = dimensionId + ":" + poi.poiType().name() + "@" + poi.worldPos().toShortString();
            return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        }
    }
}
