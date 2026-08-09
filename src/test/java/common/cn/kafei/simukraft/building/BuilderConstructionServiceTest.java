package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuilderConstructionServiceTest {
    /** rotatedResidentialBedStillBelongsToItsSourceUnit：验证所有朝向下床位均能归属原始户型单元。 */
    @SuppressWarnings("null")
    @Test
    void rotatedResidentialBedStillBelongsToItsSourceUnit() {
        BlockPos origin = new BlockPos(120, 64, -48);
        BlockPos sourceBedPos = new BlockPos(2, 1, 3);
        BuildingUnitDefinition unit = new BuildingUnitDefinition("family_a", sourceBedPos, sourceBedPos);

        for (int rotationDegrees : List.of(0, 90, 180, 270)) {
            BlockPos worldBedPos = origin.offset(BuildingTransform.rotatePosition(sourceBedPos, rotationDegrees));
            BuildingPoiInstance bed = new BuildingPoiInstance(
                    "bed-" + rotationDegrees,
                    CityPoiType.RESIDENTIAL,
                    1,
                    worldBedPos
            );

            List<BuildingUnitInstance> units = BuilderConstructionService.buildUnitInstances(
                    List.of(unit),
                    List.of(bed),
                    origin,
                    rotationDegrees
            );

            assertEquals(1, units.size(), "rotation=" + rotationDegrees);
            assertEquals("family_a", units.getFirst().label(), "rotation=" + rotationDegrees);
            assertEquals(1, units.getFirst().poiIds().size(), "rotation=" + rotationDegrees);
        }
    }
}
