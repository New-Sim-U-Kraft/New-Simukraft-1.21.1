package common.cn.kafei.simukraft.virtualvein;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class VirtualVeinFieldResolverTest {
    @Test
    void resolutionIsStableAndSeedDependent() {
        VirtualVeinFieldKey resolved = VirtualVeinFieldResolver.resolve(123_456_789L, 76_543, -12_345, "minecraft:forest");

        assertEquals(resolved, VirtualVeinFieldResolver.resolve(123_456_789L, 76_543, -12_345, "minecraft:forest"));
        assertEquals(resolved, VirtualVeinFieldResolver.resolve(123_456_789L, 76_544, -12_344, "minecraft:forest"));
        assertNotEquals(resolved, VirtualVeinFieldResolver.resolve(123_456_789L, 76_543, -12_345, "minecraft:taiga"));

        boolean hasDifferentBoundary = false;
        for (long seed = 123_456_790L; seed < 123_456_854L; seed++) {
            if (!resolved.equals(VirtualVeinFieldResolver.resolve(seed, 76_543, -12_345, "minecraft:forest"))) {
                hasDifferentBoundary = true;
                break;
            }
        }
        assertTrue(hasDifferentBoundary);
    }

    @Test
    void fieldCountUsesConfiguredProbabilityBranches() {
        int[] counts = new int[3];
        long seed = 9_876_543_210L;
        for (int index = 0; index < 10_000; index++) {
            VirtualVeinFieldKey key = VirtualVeinFieldResolver.resolve(seed, index * 4_096, 0, "minecraft:plains");
            counts[VirtualVeinService.targetCount(seed, key)]++;
        }

        assertTrue(counts[0] > 2_700 && counts[0] < 3_300);
        assertTrue(counts[1] > 3_700 && counts[1] < 4_300);
        assertTrue(counts[2] > 2_700 && counts[2] < 3_300);
    }

    @Test
    void candidatesFollowPriorityThenIdAndRemainCappedAtTwo() {
        Climate.ParameterPoint climate = Climate.parameters(0, 0, 0, 0, 0, 0, 0);
        List<VirtualVeinDefinition> definitions = List.of(
                definition("zeta", 20),
                definition("alpha", 20),
                definition("lower", 10)
        );

        List<String> selectedIds = VirtualVeinService.selectCandidates(definitions, climate).stream()
                .map(VirtualVeinDefinition::id)
                .toList();

        assertEquals(List.of("alpha", "zeta"), selectedIds);
    }

    @Test
    void surfaceParameterFilterExcludesCaveAndDeepDarkPoints() {
        assertTrue(VirtualVeinService.isSurfaceParameterPoint(parameterPointWithDepth(0.0F)));
        assertTrue(VirtualVeinService.isSurfaceParameterPoint(parameterPointWithDepth(1.0F)));
        assertFalse(VirtualVeinService.isSurfaceParameterPoint(parameterPointWithDepth(0.5F)));
        assertFalse(VirtualVeinService.isSurfaceParameterPoint(parameterPointWithDepth(1.1F)));
    }

    @Test
    void definitionRejectsInvalidProductionAndYAxisRange() {
        assertFalse(new VirtualVeinRange(-0.2D, 0.2D).overlaps(0.3D, 0.5D));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> definition("invalid", 1, 0, 1, 20));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> definition("invalid-y", 1, 1, 1, 321));
    }

    private static Climate.ParameterPoint parameterPointWithDepth(float depth) {
        return Climate.parameters(0.0F, 0.0F, 0.0F, 0.0F, depth, 0.0F, 0.0F);
    }

    private static VirtualVeinDefinition definition(String id, int priority) {
        return definition(id, priority, 1, 1, 20);
    }

    private static VirtualVeinDefinition definition(String id, int priority, int minAmount, int maxAmount, int maxY) {
        VirtualVeinRange range = new VirtualVeinRange(-1, 1);
        return new VirtualVeinDefinition(
                id, id, priority, range, range, range, range, range, range,
                -64, maxY, ResourceLocation.parse("minecraft:coal"), minAmount, maxAmount, 20
        );
    }
}
