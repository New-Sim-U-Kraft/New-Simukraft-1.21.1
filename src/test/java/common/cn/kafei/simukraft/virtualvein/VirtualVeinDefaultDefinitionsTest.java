package common.cn.kafei.simukraft.virtualvein;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class VirtualVeinDefaultDefinitionsTest {
    private static final List<String> DEFINITION_FILES = List.of(
            "lignite.json", "bituminous_coal.json", "hematite.json", "limonite.json", "pyrite.json", "chalcopyrite.json",
            "bornite.json", "malachite.json", "gold.json", "emerald.json", "lapis_lazuli.json", "redstone.json", "diamond.json"
    );

    @Test
    void allDefaultDefinitionsHaveCompleteAndValidJsonShape() throws Exception {
        assertEquals(13, DEFINITION_FILES.size());
        for (String fileName : DEFINITION_FILES) {
            String resourcePath = "/data/simukraft/virtual_veins/" + fileName;
            try (InputStream input = VirtualVeinDefaultDefinitionsTest.class.getResourceAsStream(resourcePath)) {
                assertNotNull(input, resourcePath);
                JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                assertTrue(root.get("id").getAsString().endsWith("_vein"));
                assertTrue(root.get("display_name").getAsString().length() > 0);
                assertTrue(root.get("product").getAsString().startsWith("minecraft:"));

                JsonObject conditions = root.getAsJsonObject("conditions");
                assertEquals(6, conditions.size());
                for (String key : List.of("continentalness", "erosion", "depth", "temperature", "humidity", "weirdness")) {
                    JsonArray range = conditions.getAsJsonArray(key);
                    assertEquals(2, range.size(), fileName + ": " + key);
                    assertTrue(range.get(0).getAsDouble() <= range.get(1).getAsDouble());
                }

                JsonArray yRange = root.getAsJsonArray("y_range");
                assertEquals(2, yRange.size());
                assertTrue(yRange.get(0).getAsInt() <= yRange.get(1).getAsInt());
                JsonObject production = root.getAsJsonObject("production");
                assertTrue(production.get("min_amount").getAsInt() > 0);
                assertTrue(production.get("min_amount").getAsInt() <= production.get("max_amount").getAsInt());
                assertTrue(production.get("period_ticks").getAsInt() > 0);
            }
        }
    }

    @Test
    void loaderRejectsInvalidRangeItemAndProductionIndependently() {
        assertThrows(IllegalArgumentException.class, () -> VirtualVeinDefinitionLoader.parse(
                net.minecraft.resources.ResourceLocation.parse("simukraft:invalid_range"), definitionJson("[0.4, -0.4]", "minecraft:coal", 1, 1, 20)
        ));
        assertThrows(IllegalArgumentException.class, () -> VirtualVeinDefinitionLoader.parse(
                net.minecraft.resources.ResourceLocation.parse("simukraft:invalid_item"), definitionJson("[-1.0, 1.0]", "minecraft:not_a_real_item", 1, 1, 20)
        ));
        assertThrows(IllegalArgumentException.class, () -> VirtualVeinDefinitionLoader.parse(
                net.minecraft.resources.ResourceLocation.parse("simukraft:invalid_production"), definitionJson("[-1.0, 1.0]", "minecraft:coal", 0, 1, 20)
        ));
    }

    @Test
    void defaultDefinitionsMatchNativeOverworldParameterPoints() throws Exception {
        List<VirtualVeinDefinition> definitions = new java.util.ArrayList<>();
        for (String fileName : DEFINITION_FILES) {
            String resourcePath = "/data/simukraft/virtual_veins/" + fileName;
            try (InputStream input = VirtualVeinDefaultDefinitionsTest.class.getResourceAsStream(resourcePath)) {
                assertNotNull(input, resourcePath);
                definitions.add(VirtualVeinDefinitionLoader.parse(
                        ResourceLocation.parse("simukraft:virtual_veins/" + fileName),
                        JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject()
                ));
            }
        }

        List<Climate.ParameterPoint> surfacePoints = MultiNoiseBiomeSourceParameterList.knownPresets()
                .get(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD)
                .values()
                .stream()
                .map(com.mojang.datafixers.util.Pair::getFirst)
                .filter(VirtualVeinService::isSurfaceParameterPoint)
                .toList();

        assertTrue(surfacePoints.size() > 0, "原版主世界必须存在地表参数点");
        for (VirtualVeinDefinition definition : definitions) {
            assertTrue(
                    surfacePoints.stream().anyMatch(definition::matches),
                    () -> definition.id() + " 必须能匹配原版主世界地表参数点"
            );
        }
    }

    @Test
    void cherryGroveHasSurfaceParameterPoint() {
        List<com.mojang.datafixers.util.Pair<Climate.ParameterPoint, net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>>> values =
                MultiNoiseBiomeSourceParameterList.knownPresets()
                        .get(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD)
                        .values();
        long matches = values.stream()
                .filter(pair -> pair.getSecond().location().toString().equals("minecraft:cherry_grove"))
                .filter(pair -> VirtualVeinService.isSurfaceParameterPoint(pair.getFirst()))
                .count();
        assertTrue(matches > 0);
    }

    private static JsonObject definitionJson(String continentalness, String product, int minAmount, int maxAmount, int periodTicks) {
        return JsonParser.parseString("""
                {
                  "id": "test_vein",
                  "display_name": "测试矿脉",
                  "priority": 1,
                  "conditions": {
                    "continentalness": %s,
                    "erosion": [-1.0, 1.0],
                    "depth": [-1.0, 1.0],
                    "temperature": [-1.0, 1.0],
                    "humidity": [-1.0, 1.0],
                    "weirdness": [-1.0, 1.0]
                  },
                  "y_range": [-64, 320],
                  "product": "%s",
                  "production": { "min_amount": %d, "max_amount": %d, "period_ticks": %d }
                }
                """.formatted(continentalness, product, minAmount, maxAmount, periodTicks)).getAsJsonObject();
    }
}
