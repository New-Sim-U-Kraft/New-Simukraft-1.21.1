package common.cn.kafei.simukraft.mineraldrilling;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.industrial.IndustrialCoordinateResolver;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** MineralDrillingDefinitionLoader: 读取并缓存钻井平台专用 JSON。 */
public final class MineralDrillingDefinitionLoader {
    private static final int MAX_OUTPUT_POSITIONS = 64;
    private static final int MAX_STRUCTURE_COORDINATE = 2048;
    private static final ConcurrentMap<String, Optional<MineralDrillingDefinition>> CACHE = new ConcurrentHashMap<>();

    private MineralDrillingDefinitionLoader() {
    }

    /** clearCache: 建筑包重载后清理钻井定义缓存。 */
    public static void clearCache() {
        CACHE.clear();
    }

    /** resolveOutputContainers: 解析已放置钻井平台的 JSON 输出容器世界坐标。 */
    public static OutputContainerResolution resolveOutputContainers(PlacedBuildingRecord building) {
        if (building == null) {
            return OutputContainerResolution.legacy();
        }
        BuildingCatalog.BuildingDefinition buildingDefinition = BuildingCatalog
                .findBuilding(building.category(), building.buildingFileName())
                .orElse(null);
        if (buildingDefinition == null || !buildingDefinition.isDrillingPlatform()) {
            return OutputContainerResolution.legacy();
        }
        String fileName = drillingFileName(buildingDefinition);
        if (fileName == null) {
            return OutputContainerResolution.legacy();
        }
        MineralDrillingDefinition definition = load(buildingDefinition, fileName).orElse(null);
        if (definition == null) {
            return OutputContainerResolution.declared(List.of());
        }
        List<BlockPos> positions = new ArrayList<>();
        for (MineralDrillingDefinition.OutputContainerDefinition container : definition.outputContainers()) {
            if (!"structure_pos".equalsIgnoreCase(container.type())) {
                continue;
            }
            positions.addAll(IndustrialCoordinateResolver.resolvePositions(building, container.positions()));
        }
        return OutputContainerResolution.declared(List.copyOf(positions));
    }

    /** parse: 解析专用钻井 JSON，供单元测试和包内加载复用。 */
    static Optional<MineralDrillingDefinition> parse(String text, String fallbackId) {
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (!isDrillingType(root)) {
                return Optional.empty();
            }
            String id = string(root, "id", fallbackId);
            return Optional.of(new MineralDrillingDefinition(id, parseOutputContainers(root.getAsJsonObject("containers"))));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static Optional<MineralDrillingDefinition> load(BuildingCatalog.BuildingDefinition definition, String fileName) {
        if (definition == null || fileName == null || !definition.hasFile(fileName)) {
            return Optional.empty();
        }
        String actualFileName = definition.actualFileName(fileName);
        String cacheKey = definition.packageKey() + ":" + definition.category() + "/" + actualFileName.toLowerCase(Locale.ROOT);
        return CACHE.computeIfAbsent(cacheKey, ignored -> loadUncached(definition, actualFileName));
    }

    private static Optional<MineralDrillingDefinition> loadUncached(BuildingCatalog.BuildingDefinition definition, String fileName) {
        String text = definition.readFileText(fileName).orElse(null);
        Optional<MineralDrillingDefinition> parsed = text != null ? parse(text, stripExtension(fileName)) : Optional.empty();
        if (parsed.isEmpty()) {
            SimuKraft.LOGGER.warn("Simukraft: Invalid drilling definition {} in {}", fileName, definition.packageName());
        }
        return parsed;
    }

    private static List<MineralDrillingDefinition.OutputContainerDefinition> parseOutputContainers(JsonObject containers) {
        if (containers == null) {
            return List.of();
        }
        List<MineralDrillingDefinition.OutputContainerDefinition> output = new ArrayList<>();
        for (var entry : containers.entrySet()) {
            String id = entry.getKey();
            if (id == null || !id.toLowerCase(Locale.ROOT).contains("output") || !entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject container = entry.getValue().getAsJsonObject();
            String type = string(container, "type", "structure_pos");
            List<BlockPos> positions = parsePositions(container.getAsJsonArray("positions"));
            if (positions.isEmpty()) {
                continue;
            }
            output.add(new MineralDrillingDefinition.OutputContainerDefinition(id, type, positions));
        }
        return List.copyOf(output);
    }

    private static List<BlockPos> parsePositions(JsonArray positions) {
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        Set<BlockPos> parsed = new LinkedHashSet<>();
        int limit = Math.min(positions.size(), MAX_OUTPUT_POSITIONS);
        for (int index = 0; index < limit; index++) {
            JsonElement element = positions.get(index);
            if (element == null || !element.isJsonArray()) {
                continue;
            }
            JsonArray coordinate = element.getAsJsonArray();
            if (coordinate.size() != 3 || !isInteger(coordinate.get(0)) || !isInteger(coordinate.get(1)) || !isInteger(coordinate.get(2))) {
                continue;
            }
            int x = coordinate.get(0).getAsInt();
            int y = coordinate.get(1).getAsInt();
            int z = coordinate.get(2).getAsInt();
            if (Math.abs(x) > MAX_STRUCTURE_COORDINATE
                    || Math.abs(y) > MAX_STRUCTURE_COORDINATE
                    || Math.abs(z) > MAX_STRUCTURE_COORDINATE) {
                continue;
            }
            parsed.add(new BlockPos(x, y, z));
        }
        return List.copyOf(parsed);
    }

    private static boolean isDrillingType(JsonObject root) {
        String type = string(root, "type", "");
        return "drilling".equalsIgnoreCase(type) || "simukraft:drilling".equalsIgnoreCase(type);
    }

    private static boolean isInteger(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        double value = element.getAsDouble();
        return Double.isFinite(value) && value == Math.rint(value)
                && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    private static String drillingFileName(BuildingCatalog.BuildingDefinition definition) {
        String text = definition.readFileText(definition.metaFileName()).orElse("");
        for (String rawLine : text.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.regionMatches(true, 0, "drilling:", 0, "drilling:".length())) {
                continue;
            }
            String fileName = line.substring("drilling:".length()).trim();
            return fileName.isBlank() ? null : fileName;
        }
        return null;
    }

    private static String string(JsonObject root, String key, String fallback) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString().trim() : fallback;
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    /** OutputContainerResolution: 区分新 JSON 的显式声明和旧建筑包的兼容回退。 */
    public record OutputContainerResolution(boolean declared, List<BlockPos> positions) {
        public OutputContainerResolution {
            positions = positions != null ? List.copyOf(positions) : List.of();
        }

        private static OutputContainerResolution declared(List<BlockPos> positions) {
            return new OutputContainerResolution(true, positions);
        }

        private static OutputContainerResolution legacy() {
            return new OutputContainerResolution(false, List.of());
        }
    }
}
