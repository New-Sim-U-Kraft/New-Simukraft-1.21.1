package common.cn.kafei.simukraft.industrial;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class IndustrialDefinitionLoader {
    private static final int MAX_POSITIONS = 64;
    private static final int MAX_RECIPES = 64;
    private static final int MAX_STEPS = 256;
    private static final java.util.concurrent.ConcurrentHashMap<String, LoadResult> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private IndustrialDefinitionLoader() {
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static LoadResult loadForBuilding(PlacedBuildingRecord building) {
        if (building == null) {
            return LoadResult.missing("missing_building");
        }
        String cacheKey = building.category() + "/" + building.buildingFileName();
        LoadResult cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        LoadResult result = loadForBuildingInternal(building);
        CACHE.put(cacheKey, result);
        return result;
    }

    private static LoadResult loadForBuildingInternal(PlacedBuildingRecord building) {
        Optional<BuildingCatalog.BuildingDefinition> definition = BuildingCatalog.findBuilding(building.category(), building.buildingFileName());
        if (definition.isEmpty()) {
            return LoadResult.missing("missing_building_definition");
        }
        String industrialFile = resolveIndustrialFileName(definition.get());
        if (industrialFile != null) {
            return load(definition.get(), industrialFile);
        }
        return LoadResult.missing("missing_industrial_json");
    }

    public static LoadResult load(Path path) {
        try {
            return loadText(Files.readString(path, StandardCharsets.UTF_8), stripExtension(path.getFileName().toString()), path);
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to load industrial definition {}", path, exception);
            return LoadResult.missing("invalid_industrial_json");
        }
    }

    private static LoadResult loadText(String text, String fallbackId, Path path) {
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        List<String> errors = new ArrayList<>();
        String id = string(root, "id", fallbackId);
        String name = string(root, "name", id);
        String jobType = stringAny(root, "industrial_worker", "jobType", "JobType", "job_type");
        String jobName = stringAny(root, name, "jobName", "JobName", "job_name");
        String heldItem = string(root, "heldItem", "");
        Map<String, IndustrialDefinition.PointDefinition> points = parsePoints(root.getAsJsonObject("points"), errors);
        Map<String, IndustrialDefinition.ContainerDefinition> containers = parseContainers(root.getAsJsonObject("containers"), errors);
        IndustrialDefinition.WorkAreaDefinition workArea = parseWorkArea(root.getAsJsonObject("workArea"));
        List<IndustrialDefinition.RecipeDefinition> recipes = parseRecipes(root.getAsJsonArray("recipes"), errors);
        IndustrialDefinition.SpawnEntityDefinition spawnEntity = parseSpawnEntity(root.getAsJsonObject("spawnEntity"));
        if (recipes.isEmpty()) {
            errors.add("missing_recipes");
        }
        IndustrialDefinition definition = new IndustrialDefinition(id, name, jobType, jobName, heldItem, Map.copyOf(points), Map.copyOf(containers), workArea, List.copyOf(recipes), spawnEntity, path);
        return new LoadResult(definition, List.copyOf(errors), path);
    }

    private static LoadResult load(BuildingCatalog.BuildingDefinition definition, String fileName) {
        String key = "package:" + definition.packageKey() + ":" + definition.category() + "/" + fileName.toLowerCase(Locale.ROOT);
        LoadResult cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            String text = definition.readFileText(fileName).orElse(null);
            if (text == null) {
                return LoadResult.missing("missing_industrial_json");
            }
            LoadResult result = loadText(text, stripExtension(fileName), definition.packagePath());
            CACHE.put(key, result);
            return result;
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to load industrial definition {} from {}", fileName, definition.packageName(), exception);
            return LoadResult.missing("invalid_industrial_json");
        }
    }

    private static String resolveIndustrialFileName(BuildingCatalog.BuildingDefinition definition) {
        if (hasDedicatedDrillingDeclaration(definition)) {
            return null;
        }
        String explicit = explicitIndustrialFileName(definition);
        if (explicit != null) {
            return explicit;
        }
        String sibling = stripExtension(definition.metaFileName()) + ".json";
        return definition.hasFile(sibling) ? definition.actualFileName(sibling) : null;
    }

    /** hasDedicatedDrillingDeclaration: 检查建筑元数据是否声明专用钻井定义。 */
    private static boolean hasDedicatedDrillingDeclaration(BuildingCatalog.BuildingDefinition definition) {
        if (definition == null || !"industry".equalsIgnoreCase(definition.category())) {
            return false;
        }
        try {
            String text = definition.readFileText(definition.metaFileName()).orElse("");
            for (String rawLine : text.split("\\R")) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (!line.regionMatches(true, 0, "drilling:", 0, "drilling:".length())) {
                    continue;
                }
                return !line.substring("drilling:".length()).trim().isBlank();
            }
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read drilling entry from {}", definition.metaFileName(), exception);
        }
        return false;
    }

    private static String explicitIndustrialFileName(BuildingCatalog.BuildingDefinition definition) {
        if (definition == null) {
            return null;
        }
        try {
            String text = definition.readFileText(definition.metaFileName()).orElse("");
            for (String rawLine : text.split("\\R")) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (!line.regionMatches(true, 0, "industrial:", 0, "industrial:".length())) {
                    continue;
                }
                String fileName = line.substring("industrial:".length()).trim();
                if (!fileName.isBlank()) {
                    return definition.hasFile(fileName) ? definition.actualFileName(fileName) : null;
                }
            }
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to read industrial entry from {} in {}", definition.metaFileName(), definition.packageName(), exception);
        }
        return null;
    }

    private static Map<String, IndustrialDefinition.PointDefinition> parsePoints(JsonObject object, List<String> errors) {
        Map<String, IndustrialDefinition.PointDefinition> points = new LinkedHashMap<>();
        if (object == null) {
            return points;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String id = entry.getKey();
            JsonObject pointObject = asObject(entry.getValue());
            if (pointObject == null) {
                errors.add("invalid_point:" + id);
                continue;
            }
            List<BlockPos> positions = parsePositions(pointObject, errors, "point:" + id);
            if (positions.isEmpty()) {
                continue;
            }
            points.put(id, new IndustrialDefinition.PointDefinition(id, string(pointObject, "type", "structure_pos"), positions,
                    IndustrialDefinition.SelectionMode.fromName(string(pointObject, "select", "nearest"))));
        }
        return points;
    }

    private static Map<String, IndustrialDefinition.ContainerDefinition> parseContainers(JsonObject object, List<String> errors) {
        Map<String, IndustrialDefinition.ContainerDefinition> containers = new LinkedHashMap<>();
        if (object == null) {
            return containers;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String id = entry.getKey();
            JsonObject containerObject = asObject(entry.getValue());
            if (containerObject == null) {
                errors.add("invalid_container:" + id);
                continue;
            }
            List<BlockPos> positions = parsePositions(containerObject, errors, "container:" + id);
            if (positions.isEmpty()) {
                continue;
            }
            containers.put(id, new IndustrialDefinition.ContainerDefinition(id, string(containerObject, "type", "structure_pos"), positions));
        }
        return containers;
    }

    private static List<IndustrialDefinition.RecipeDefinition> parseRecipes(JsonArray array, List<String> errors) {
        List<IndustrialDefinition.RecipeDefinition> recipes = new ArrayList<>();
        if (array == null) {
            return recipes;
        }
        int limit = Math.min(array.size(), MAX_RECIPES);
        for (int i = 0; i < limit; i++) {
            JsonObject object = asObject(array.get(i));
            if (object == null) {
                errors.add("invalid_recipe:" + i);
                continue;
            }
            String id = string(object, "id", "recipe_" + i);
            recipes.add(new IndustrialDefinition.RecipeDefinition(
                    id,
                    string(object, "name", id),
                    string(object, "heldItem", ""),
                    parseInputs(object.getAsJsonArray("inputs"), errors, "recipe:" + id + ":inputs"),
                    parseOutputs(object.getAsJsonArray("outputs"), errors, "recipe:" + id + ":outputs"),
                    parseSteps(object.getAsJsonArray("steps"), errors, id)
            ));
        }
        return recipes;
    }

    private static IndustrialDefinition.WorkAreaDefinition parseWorkArea(JsonObject object) {
        if (object == null) {
            return IndustrialDefinition.WorkAreaDefinition.none();
        }
        return new IndustrialDefinition.WorkAreaDefinition(
                string(object, "type", "building_outer_rect"),
                Math.max(0, integer(object, "radius", 0)),
                Math.max(0, integer(object, "startOffset", integer(object, "start_offset", 0))),
                integer(object, "minYOffset", integer(object, "min_y_offset", -8)),
                integer(object, "maxYOffset", integer(object, "max_y_offset", 24)),
                bool(object, "excludeBuilding", bool(object, "exclude_building", true)),
                Math.max(1, integer(object, "scanColumnsPerTick", integer(object, "scan_columns_per_tick", 64)))
        );
    }

    private static List<IndustrialDefinition.InputRequirement> parseInputs(JsonArray array, List<String> errors, String context) {
        List<IndustrialDefinition.InputRequirement> inputs = new ArrayList<>();
        if (array == null) {
            return inputs;
        }
        for (int i = 0; i < array.size(); i++) {
            IndustrialDefinition.InputRequirement requirement = parseInputRequirement(array.get(i), errors, context + ":" + i);
            if (requirement != null) {
                inputs.add(requirement);
            }
        }
        return List.copyOf(inputs);
    }

    private static IndustrialDefinition.InputRequirement parseInputRequirement(JsonElement element, List<String> errors, String context) {
        JsonObject object = asObject(element);
        if (object == null) {
            return null;
        }
        JsonArray anyInputs = arrayAny(object, "or", "anyOf", "any", "或");
        if (anyInputs != null) {
            return inputGroup(IndustrialDefinition.InputLogic.ANY, anyInputs, errors, context + ":or");
        }
        JsonArray allInputs = arrayAny(object, "and", "allOf", "all", "与");
        if (allInputs != null) {
            return inputGroup(IndustrialDefinition.InputLogic.ALL, allInputs, errors, context + ":and");
        }
        int count = Math.max(1, integer(object, "count", integer(object, "amount", 1)));
        boolean consume = bool(object, "consume", true);
        IndustrialItemStackSpec spec = IndustrialItemSpecJsonParser.parse(object, errors, context);
        return spec.isEmpty() ? null : new IndustrialDefinition.ItemRequirement(spec, count, consume);
    }

    private static IndustrialDefinition.InputRequirement inputGroup(IndustrialDefinition.InputLogic logic, JsonArray array, List<String> errors, String context) {
        List<IndustrialDefinition.InputRequirement> children = parseInputs(array, errors, context);
        return children.isEmpty() ? null : new IndustrialDefinition.InputRequirementGroup(logic, children);
    }

    private static List<IndustrialDefinition.ProductOutput> parseOutputs(JsonArray array, List<String> errors, String context) {
        List<IndustrialDefinition.ProductOutput> outputs = new ArrayList<>();
        if (array == null) {
            return outputs;
        }
        for (int i = 0; i < array.size(); i++) {
            JsonObject object = asObject(array.get(i));
            if (object == null) {
                continue;
            }
            int baseAmount = Math.max(1, integer(object, "baseAmount", integer(object, "count", 1)));
            int randomRange = Math.max(0, integer(object, "randomRange", 0));
            double probability = Math.max(0.0D, Math.min(1.0D, decimal(object, "probability", 1.0D)));
            boolean ignoreMultiplier = bool(object, "ignoreMultiplier", false);
            IndustrialItemStackSpec spec = IndustrialItemSpecJsonParser.parse(object, errors, context + ":" + i);
            if (!spec.isEmpty()) {
                outputs.add(new IndustrialDefinition.ProductOutput(spec, baseAmount, randomRange, probability, ignoreMultiplier));
            }
        }
        return List.copyOf(outputs);
    }

    private static List<IndustrialDefinition.StepDefinition> parseSteps(JsonArray array, List<String> errors, String recipeId) {
        List<IndustrialDefinition.StepDefinition> steps = new ArrayList<>();
        if (array == null) {
            return steps;
        }
        int limit = Math.min(array.size(), MAX_STEPS);
        for (int i = 0; i < limit; i++) {
            JsonObject object = asObject(array.get(i));
            if (object == null) {
                errors.add("invalid_step:" + recipeId + ":" + i);
                continue;
            }
            appendStepDefinitions(steps, object, errors, recipeId, i);
            if (steps.size() >= MAX_STEPS) {
                errors.add("steps_truncated:" + recipeId);
                break;
            }
        }
        return List.copyOf(steps);
    }

    private static void appendStepDefinitions(List<IndustrialDefinition.StepDefinition> steps,
                                              JsonObject object,
                                              List<String> errors,
                                              String recipeId,
                                              int index) {
        String type = string(object, "type", "");
        if ("repeat".equalsIgnoreCase(type) || "loop".equalsIgnoreCase(type)) {
            appendRepeatedSteps(steps, object, errors, recipeId, index);
            return;
        }
        steps.add(parseStepDefinition(object, errors, recipeId + ":" + index));
    }

    private static void appendRepeatedSteps(List<IndustrialDefinition.StepDefinition> steps,
                                            JsonObject object,
                                            List<String> errors,
                                            String recipeId,
                                            int index) {
        JsonArray nestedSteps = object.getAsJsonArray("steps");
        if (nestedSteps == null || nestedSteps.isEmpty()) {
            errors.add("missing_repeat_steps:" + recipeId + ":" + index);
            return;
        }
        List<BlockPos> positions = parseOptionalPositions(object);
        int repetitions = !positions.isEmpty() ? positions.size() : Math.max(0, integer(object, "count", 0));
        if (repetitions <= 0) {
            errors.add("missing_repeat_count:" + recipeId + ":" + index);
            return;
        }
        for (int repeatIndex = 0; repeatIndex < repetitions && steps.size() < MAX_STEPS; repeatIndex++) {
            BlockPos position = positions.isEmpty() ? null : positions.get(repeatIndex);
            for (int nestedIndex = 0; nestedIndex < nestedSteps.size() && steps.size() < MAX_STEPS; nestedIndex++) {
                JsonObject nestedObject = asObject(nestedSteps.get(nestedIndex));
                if (nestedObject == null) {
                    errors.add("invalid_repeat_step:" + recipeId + ":" + index + ":" + nestedIndex);
                    continue;
                }
                JsonObject expanded = nestedObject.deepCopy();
                if (position != null && !expanded.has("pos") && !expanded.has("positions")) {
                    expanded.add("pos", positionArray(position));
                }
                appendStepDefinitions(steps, expanded, errors, recipeId, nestedIndex);
            }
        }
    }

    private static IndustrialDefinition.StepDefinition parseStepDefinition(JsonObject object, List<String> errors, String context) {
        return new IndustrialDefinition.StepDefinition(
                string(object, "type", ""),
                string(object, "point", ""),
                string(object, "container", ""),
                string(object, "input", ""),
                string(object, "output", ""),
                string(object, "item", ""),
                IndustrialItemSpecJsonParser.parse(object, errors, context + ":item"),
                IndustrialItemSpecJsonParser.parseList(arrayAny(object, "items", "itemSpecs"), errors, context + ":items"),
                Math.max(1, integer(object, "ticks", 1)),
                bool(object, "swing", false),
                Math.max(0.1D, decimal(object, "range", 1.5D)),
                string(object, "statusKey", ""),
                string(object, "statusText", ""),
                stringAny(object, "", "entityType", "entity"),
                Math.max(0, integer(object, "count", 0)),
                Math.max(0.5D, decimal(object, "radius", 6.0D)),
                bool(object, "requireFood", bool(object, "requiresFood", true)),
                parseOptionalPositions(object),
                string(object, "block", ""),
                stringAny(object, "", "fluid", "liquid"),
                bool(object, "consume", false),
                bool(object, "replace", false),
                bool(object, "dropItems", bool(object, "drop", false)),
                stringAny(object, "extract_to_output", "outputPolicy", "output_policy"),
                Math.max(1, integer(object, "timeoutTicks", integer(object, "timeout", 12000))),
                Math.max(1, integer(object, "pollTicks", integer(object, "poll", 20))),
                bool(object, "skipOnTimeout", bool(object, "skip_on_timeout", false)),
                integer(object, "slot", -1),
                Math.max(0, integer(object, "targetCount", integer(object, "target", integer(object, "fillTo", 0)))),
                integer(object, "thresholdCount", integer(object, "threshold", -1)),
                stringAny(object, "", "targetBlockTag", "target_block_tag", "blockTag", "block_tag"),
                stringAny(object, "", "attachedBlockTag", "attached_block_tag"),
                stringAny(object, "", "supportBlockTag", "support_block_tag"),
                stringAny(object, "", "plantItemTag", "plant_item_tag"),
                Math.max(0, integer(object, "minAttachedBlocks", integer(object, "min_attached_blocks", 0))),
                Math.max(1, integer(object, "maxClusterBlocks", integer(object, "max_cluster_blocks", 96))),
                Math.max(1, integer(object, "maxBlocksPerTick", integer(object, "max_blocks_per_tick", 16))),
                Math.max(1, integer(object, "maxCarryStacks", integer(object, "max_carry_stacks", 18))),
                bool(object, "untilAreaEmpty", bool(object, "until_area_empty", false)),
                object.has("inputs"),
                object.has("outputs"),
                parseInputs(object.getAsJsonArray("inputs"), errors, context + ":inputs"),
                parseOutputs(object.getAsJsonArray("outputs"), errors, context + ":outputs")
        );
    }

    private static JsonArray positionArray(BlockPos pos) {
        JsonArray array = new JsonArray();
        array.add(pos.getX());
        array.add(pos.getY());
        array.add(pos.getZ());
        return array;
    }

    private static IndustrialDefinition.SpawnEntityDefinition parseSpawnEntity(JsonObject object) {
        if (object == null) {
            return new IndustrialDefinition.SpawnEntityDefinition(false, "", 0);
        }
        boolean enabled = bool(object, "enabled", false);
        String type = string(object, "type", "");
        int count = Math.max(0, integer(object, "count", 0));
        return new IndustrialDefinition.SpawnEntityDefinition(enabled, type, count);
    }

    private static List<BlockPos> parsePositions(JsonObject object, List<String> errors, String context) {
        List<BlockPos> positions = new ArrayList<>();
        if (object.has("pos")) {
            BlockPos pos = parsePositionArray(object.get("pos"));
            if (pos != null) {
                positions.add(pos);
            }
        }
        if (object.has("positions") && object.get("positions").isJsonArray()) {
            JsonArray array = object.getAsJsonArray("positions");
            int limit = Math.min(array.size(), MAX_POSITIONS);
            for (int i = 0; i < limit; i++) {
                BlockPos pos = parsePositionArray(array.get(i));
                if (pos != null) {
                    positions.add(pos);
                }
            }
        }
        if (positions.isEmpty()) {
            errors.add("missing_positions:" + context);
        }
        return List.copyOf(positions);
    }

    private static List<BlockPos> parseOptionalPositions(JsonObject object) {
        if (object == null) {
            return List.of();
        }
        List<BlockPos> positions = new ArrayList<>();
        if (object.has("pos")) {
            BlockPos pos = parsePositionArray(object.get("pos"));
            if (pos != null) {
                positions.add(pos);
            }
        }
        if (object.has("positions") && object.get("positions").isJsonArray()) {
            JsonArray array = object.getAsJsonArray("positions");
            int limit = Math.min(array.size(), MAX_POSITIONS);
            for (int i = 0; i < limit; i++) {
                BlockPos pos = parsePositionArray(array.get(i));
                if (pos != null) {
                    positions.add(pos);
                }
            }
        }
        return List.copyOf(positions);
    }

    private static BlockPos parsePositionArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() < 3) {
            return null;
        }
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static String stringAny(JsonObject object, String fallback, String... keys) {
        for (String key : keys) {
            String value = string(object, key, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private static JsonArray arrayAny(JsonObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonArray()) {
                return object.getAsJsonArray(key);
            }
        }
        return null;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static double decimal(JsonObject object, String key, double fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsDouble();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName.toLowerCase(Locale.ROOT);
    }

    public record LoadResult(IndustrialDefinition definition, List<String> errors, Path path) {
        public static LoadResult missing(String error) {
            return new LoadResult(null, List.of(error), null);
        }

        public boolean valid() {
            return definition != null && errors.isEmpty();
        }
    }
}
