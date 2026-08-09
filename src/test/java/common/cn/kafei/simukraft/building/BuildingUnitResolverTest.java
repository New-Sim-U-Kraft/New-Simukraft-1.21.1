package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingUnitResolverTest {
    /** residentialPoiGroupsRespectBuildingRotation：验证按建筑朝向还原坐标后床位仍归属各自单元。 */
    @Test
    void residentialPoiGroupsRespectBuildingRotation() {
        BlockPos origin = new BlockPos(80, 64, -32);
        UUID firstBedId = UUID.randomUUID();
        UUID secondBedId = UUID.randomUUID();
        BuildingUnitDefinition firstUnit = new BuildingUnitDefinition("first", new BlockPos(1, 0, 2), new BlockPos(1, 2, 2));
        BuildingUnitDefinition secondUnit = new BuildingUnitDefinition("second", new BlockPos(-2, 0, 4), new BlockPos(-2, 2, 4));

        for (int rotationDegrees : List.of(0, 90, 180, 270)) {
            @SuppressWarnings("null")
            PlacedBuildingRecord building = building(
                    origin,
                    rotationDegrees,
                    List.of(
                            residentialPoi(firstBedId, origin.offset(BuildingTransform.rotatePosition(new BlockPos(1, 1, 2), rotationDegrees))),
                            residentialPoi(secondBedId, origin.offset(BuildingTransform.rotatePosition(new BlockPos(-2, 1, 4), rotationDegrees)))
                    ),
                    List.of(firstUnit, secondUnit)
            );

            assertEquals(List.of(List.of(firstBedId), List.of(secondBedId)),
                    BuildingUnitResolver.resolveResidentialPoiGroups(building),
                    "rotation=" + rotationDegrees);
        }
    }

    /** missingUnitDefinitionsTreatBuildingAsOneHousehold：验证未定义单元的住宅维持整栋一户。 */
    @Test
    void missingUnitDefinitionsTreatBuildingAsOneHousehold() {
        UUID firstBedId = UUID.randomUUID();
        UUID secondBedId = UUID.randomUUID();
        PlacedBuildingRecord building = building(
                BlockPos.ZERO,
                0,
                List.of(residentialPoi(firstBedId, new BlockPos(1, 1, 1)), residentialPoi(secondBedId, new BlockPos(2, 1, 1))),
                List.of()
        );

        assertEquals(List.of(List.of(firstBedId, secondBedId)),
                BuildingUnitResolver.resolveResidentialPoiGroups(building));
    }

    /** reversedApartmentRangesStillResolveOneHouseholdPerFloor：验证反向范围也能按楼层拆分住户。 */
    @Test
    void reversedApartmentRangesStillResolveOneHouseholdPerFloor() {
        List<BuildingUnitDefinition> units = List.of(
                new BuildingUnitDefinition("101", new BlockPos(15, 1, 1), new BlockPos(5, 3, 8)),
                new BuildingUnitDefinition("201", new BlockPos(15, 5, 1), new BlockPos(5, 7, 8)),
                new BuildingUnitDefinition("301", new BlockPos(15, 9, 1), new BlockPos(5, 11, 8)),
                new BuildingUnitDefinition("401", new BlockPos(15, 13, 1), new BlockPos(5, 15, 8)),
                new BuildingUnitDefinition("501", new BlockPos(15, 17, 1), new BlockPos(5, 19, 8)),
                new BuildingUnitDefinition("601", new BlockPos(15, 21, 1), new BlockPos(5, 23, 8))
        );
        List<UUID> bedIds = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );
        List<BuildingPoiInstance> beds = java.util.stream.IntStream.range(0, bedIds.size())
                .mapToObj(index -> residentialPoi(bedIds.get(index), new BlockPos(5, 2 + index * 4, 3)))
                .toList();

        PlacedBuildingRecord building = building(BlockPos.ZERO, 0, beds, units);

        assertEquals(bedIds.stream().map(List::of).toList(),
                BuildingUnitResolver.resolveResidentialPoiGroups(building));
    }

    private static PlacedBuildingRecord building(BlockPos origin, int rotationDegrees,
                                                  List<BuildingPoiInstance> pois,
                                                  List<BuildingUnitDefinition> units) {
        return new PlacedBuildingRecord(
                UUID.randomUUID(), UUID.randomUUID(), "minecraft:overworld", "residential", "test.sk", "test", "",
                "test.nbt", BuildingTransform.directionFromRotation(rotationDegrees).getSerializedName(), origin,
                BlockPos.ZERO, origin, origin, 0L, List.of(), List.of(), pois, units, List.of()
        );
    }

    private static BuildingPoiInstance residentialPoi(UUID poiId, BlockPos pos) {
        return new BuildingPoiInstance(poiId.toString(), CityPoiType.RESIDENTIAL, 1, pos);
    }
}
