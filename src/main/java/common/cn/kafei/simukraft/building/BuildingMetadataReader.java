package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BuildingMetadataReader {
    private BuildingMetadataReader() {
    }

    public static List<BuildingUnitDefinition> readUnitDefinitions(BuildingCatalog.BuildingDefinition definition) {
        if (definition == null) return List.of();
        return definition.readFileText(definition.metaFileName())
                .map(BuildingMetadataReader::readUnitDefinitionsFromText)
                .orElse(List.of());
    }

    // 支持两种格式：
    // 范围：unit: <label>, <minX>,<minY>,<minZ>~<maxX>,<maxY>,<maxZ>
    // 点列表：unit: <label>, x1,y1,z1|x2,y2,z2|...
    private static List<BuildingUnitDefinition> readUnitDefinitionsFromText(String text) {
        List<BuildingUnitDefinition> result = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!line.regionMatches(true, 0, "unit:", 0, 5)) continue;
            String payload = line.substring(5).trim();
            int commaIdx = payload.indexOf(',');
            if (commaIdx < 0) continue;
            String label = payload.substring(0, commaIdx).trim();
            String coords = payload.substring(commaIdx + 1).trim();

            if (coords.contains("|")) {
                // 点列表模式：x1,y1,z1|x2,y2,z2|...
                List<BlockPos> points = new ArrayList<>();
                for (String segment : coords.split("\\|")) {
                    BlockPos p = parseCoord(segment.trim());
                    if (p != null) points.add(p);
                }
                if (!points.isEmpty()) result.add(new BuildingUnitDefinition(label, points));
            } else if (coords.contains("~")) {
                // 范围模式：minX,minY,minZ~maxX,maxY,maxZ
                String[] halves = coords.split("~");
                if (halves.length != 2) continue;
                BlockPos min = parseCoord(halves[0].trim());
                BlockPos max = parseCoord(halves[1].trim());
                if (min == null || max == null) continue;
                result.add(new BuildingUnitDefinition(label, min, max));
            }
        }
        return List.copyOf(result);
    }

    private static BlockPos parseCoord(String value) {
        String[] parts = value.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BlockPos parseSize(String value) {
        if (value == null || value.isBlank()) {
            return BlockPos.ZERO;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace('×', 'x').replace(' ', 'x');
        String[] parts = normalized.split("x");
        if (parts.length != 3) {
            return BlockPos.ZERO;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException exception) {
            return BlockPos.ZERO;
        }
    }
}
