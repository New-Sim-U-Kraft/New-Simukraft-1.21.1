package common.cn.kafei.simukraft.industrial;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustrialDefinitionLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsWorkAreaAndHarvestStepFields() throws Exception {
        IndustrialDefinition definition = load("""
                {
                  "id": "simukraft:test_lumber",
                  "name": "Test Lumber",
                  "jobType": "lumberjack",
                  "containers": {
                    "input": { "type": "structure_pos", "positions": [[1, 0, 1]] },
                    "output": { "type": "structure_pos", "positions": [[2, 0, 1]] }
                  },
                  "workArea": {
                    "type": "building_outer_rect",
                    "radius": 32,
                    "startOffset": 1,
                    "minYOffset": -4,
                    "maxYOffset": 32,
                    "excludeBuilding": true,
                    "scanColumnsPerTick": 64
                  },
                  "recipes": [
                    {
                      "id": "harvest_natural_trees",
                      "inputs": [{ "tag": "minecraft:saplings", "count": 1 }],
                      "outputs": [{ "item": "minecraft:oak_log", "baseAmount": 1 }],
                      "steps": [
                        {
                          "type": "harvest_block_clusters",
                          "targetBlockTag": "minecraft:logs",
                          "attachedBlockTag": "minecraft:leaves",
                          "supportBlockTag": "minecraft:dirt",
                          "plantItemTag": "minecraft:saplings",
                          "minAttachedBlocks": 4,
                          "maxClusterBlocks": 160,
                          "maxBlocksPerTick": 12,
                          "maxCarryStacks": 18,
                          "timeoutTicks": 200,
                          "skipOnTimeout": true,
                          "untilAreaEmpty": true
                        }
                      ]
                    }
                  ]
                }
                """);

        assertEquals(32, definition.workArea().radius());
        assertEquals(1, definition.workArea().startOffset());
        assertEquals(-4, definition.workArea().minYOffset());
        assertEquals(32, definition.workArea().maxYOffset());
        assertTrue(definition.workArea().excludeBuilding());
        assertEquals(64, definition.workArea().scanColumnsPerTick());

        IndustrialDefinition.StepDefinition step = definition.recipes().getFirst().steps().getFirst();
        assertEquals("minecraft:logs", step.targetBlockTag());
        assertEquals("minecraft:leaves", step.attachedBlockTag());
        assertEquals("minecraft:dirt", step.supportBlockTag());
        assertEquals("minecraft:saplings", step.plantItemTag());
        assertEquals(4, step.minAttachedBlocks());
        assertEquals(160, step.maxClusterBlocks());
        assertEquals(12, step.maxBlocksPerTick());
        assertEquals(18, step.maxCarryStacks());
        assertEquals(200, step.timeoutTicks());
        assertTrue(step.skipOnTimeout());
        assertTrue(step.untilAreaEmpty());
        assertEquals("minecraft:saplings", ((IndustrialDefinition.ItemRequirement) definition.recipes().getFirst().inputs().getFirst()).spec().itemTag());
    }

    @Test
    void bundledIndustrialJsonFilesLoad() throws Exception {
        Path packageFile = copyOfficialPackage();
        try (ZipFile zipFile = new ZipFile(packageFile.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith("buildings/industry/") || !entry.getName().endsWith(".json")) {
                    continue;
                }
                Path file = tempDir.resolve(Path.of(entry.getName()).getFileName().toString());
                byte[] jsonBytes;
                try (var input = zipFile.getInputStream(entry)) {
                    jsonBytes = input.readAllBytes();
                }
                var json = JsonParser.parseString(new String(jsonBytes, StandardCharsets.UTF_8)).getAsJsonObject();
                if ((json.has("buildingType")
                        && "drilling_platform".equalsIgnoreCase(json.get("buildingType").getAsString()))
                        || (json.has("type")
                        && ("drilling".equalsIgnoreCase(json.get("type").getAsString())
                        || "simukraft:drilling".equalsIgnoreCase(json.get("type").getAsString())))) {
                    continue;
                }
                Files.write(file, jsonBytes);
                IndustrialDefinitionLoader.LoadResult result = IndustrialDefinitionLoader.load(file);

                assertTrue(result.valid(), () -> file.getFileName() + " errors: " + result.errors());
                assertNotNull(result.definition(), file.getFileName().toString());
                assertFalse(result.definition().recipes().isEmpty(), file.getFileName().toString());
                assertTrue(result.definition().recipes().stream().allMatch(recipe -> !recipe.steps().isEmpty()), file.getFileName().toString());
            }
        }
    }

    private Path copyOfficialPackage() throws Exception {
        Path packageFile = tempDir.resolve("official_building_" + System.nanoTime() + ".zip");
        try (var input = IndustrialDefinitionLoaderTest.class.getResourceAsStream("/assets/simukraft/building/official_building.zip")) {
            assertNotNull(input);
            Files.copy(input, packageFile);
        }
        return packageFile;
    }

    @Test
    void workAreaBoundsExpandOutsideBuilding() {
        var building = new common.cn.kafei.simukraft.building.PlacedBuildingRecord(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                "minecraft:overworld",
                "industry",
                "test",
                "Test",
                "",
                "test.nbt",
                "north",
                BlockPos.ZERO,
                BlockPos.ZERO,
                new BlockPos(10, 64, 20),
                new BlockPos(21, 71, 31),
                0L,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        );
        IndustrialDefinition.WorkAreaDefinition workArea = new IndustrialDefinition.WorkAreaDefinition(
                "building_outer_rect", 32, 1, -4, 32, true, 64);

        var bounds = IndustrialWorkAreaService.workAreaBounds(building, workArea);

        assertEquals(-22.0D, bounds.minX);
        assertEquals(54.0D, bounds.maxX);
        assertEquals(60.0D, bounds.minY);
        assertEquals(104.0D, bounds.maxY);
        assertEquals(-12.0D, bounds.minZ);
        assertEquals(64.0D, bounds.maxZ);
    }

    private IndustrialDefinition load(String json) throws Exception {
        Path file = tempDir.resolve("industrial_" + System.nanoTime() + ".json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        IndustrialDefinitionLoader.LoadResult result = IndustrialDefinitionLoader.load(file);
        assertTrue(result.valid(), () -> "Loader errors: " + result.errors());
        return result.definition();
    }
}
