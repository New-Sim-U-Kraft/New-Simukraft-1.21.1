package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingMetadataReaderTest {
    @Test
    void householdCountDefaultsToOneWithoutUnits() {
        assertEquals(1, BuildingMetadataReader.householdCount(List.of()));
    }

    @Test
    void householdCountMatchesUnitDefinitions() {
        List<BuildingUnitDefinition> units = List.of(
                new BuildingUnitDefinition("unit_a", BlockPos.ZERO, new BlockPos(4, 4, 4)),
                new BuildingUnitDefinition("unit_b", new BlockPos(5, 0, 0), new BlockPos(9, 4, 4))
        );

        assertEquals(2, BuildingMetadataReader.householdCount(units));
    }
}
