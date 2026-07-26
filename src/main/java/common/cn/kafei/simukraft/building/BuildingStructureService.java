package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("null")
public final class BuildingStructureService {
    private static final Map<String, String> LEGACY_BLOCK_REMAPS = Map.of(
            "minecraft:grass", "minecraft:short_grass"
    );

    private BuildingStructureService() {
    }

    public static Optional<BuildingStructure> loadStructure(String category, String buildingFileName) {
        return BuildingCatalog.findBuilding(category, buildingFileName).flatMap(BuildingStructureService::loadStructure);
    }

    /** loadStructure: 按建筑任务保存的结构文件优先加载，避免恢复施工或清单材料时读错建筑。 */
    public static Optional<BuildingStructure> loadStructure(BuildingTaskData task) {
        if (task == null) {
            return Optional.empty();
        }
        Optional<BuildingStructure> byStructureFile = BuildingCatalog.findBuildingByStructureFile(task.category(), task.structureFileName())
                .flatMap(BuildingStructureService::loadStructure);
        return byStructureFile.isPresent()
                ? byStructureFile
                : loadStructure(task.category(), task.buildingFileName());
    }

    public static Optional<BuildingStructure> loadStructure(BuildingCatalog.BuildingDefinition definition) {
        if (definition == null) {
            return Optional.empty();
        }
        Optional<BuildingStructureFileLoader.LoadedStructure> loaded = BuildingStructureFileLoader.load(definition);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        List<BuildingBlockData> blocks = parseBlocks(loaded.get().rootTag());
        if (blocks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BuildingStructure(
                definition.category(),
                definition.displayName(),
                stripExtension(definition.metaFileName()),
                definition.amount(),
                definition.structureFileName(),
                definition.author(),
                definition.size(),
                BuildingMetadataReader.parseSize(definition.size()),
                List.copyOf(blocks),
                scanPoiDefinitions(blocks),
                BlockPos.ZERO,
                blocks.size()
        ));
    }

    public static List<BuildingBlockData> resolvePlacedBlocks(BuildingStructure structure, BlockPos origin, int rotationDegrees) {
        List<BuildingBlockData> placed = new ArrayList<>();
        for (BuildingBlockData block : structure.blocks()) {
            BlockPos rotated = BuildingTransform.rotatePosition(block.relativePos(), rotationDegrees);
            BlockState rotatedState = BuildingTransform.rotateState(block.state(), rotationDegrees);
            placed.add(new BuildingBlockData(origin.offset(rotated), rotatedState, block.originalStructurePos()));
        }
        return List.copyOf(placed);
    }

    private static List<BuildingBlockData> parseBlocks(CompoundTag rootTag) {
        List<BuildingBlockData> blocks = new ArrayList<>();
        if (rootTag.contains("Schematic", Tag.TAG_COMPOUND)) {
            return parseBlocks(rootTag.getCompound("Schematic"));
        }
        if (rootTag.contains("blocks", Tag.TAG_LIST) && rootTag.contains("palette", Tag.TAG_LIST)) {
            ListTag palette = rootTag.getList("palette", Tag.TAG_COMPOUND);
            ListTag blockTags = rootTag.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blockTags.size(); i++) {
                CompoundTag blockTag = blockTags.getCompound(i);
                if (!blockTag.contains("pos", Tag.TAG_LIST)) {
                    continue;
                }
                ListTag posList = blockTag.getList("pos", Tag.TAG_INT);
                if (posList.size() < 3) {
                    continue;
                }
                int x = posList.getInt(0);
                int y = posList.getInt(1);
                int z = posList.getInt(2);
                int stateIndex = blockTag.getInt("state");
                if (stateIndex < 0 || stateIndex >= palette.size()) {
                    continue;
                }
                BlockState state = parseState(palette.getCompound(stateIndex));
                if (state == null) {
                    continue;
                }
                BlockPos relative = new BlockPos(x, y, z);
                blocks.add(new BuildingBlockData(relative, state, relative));
            }
        }
        return blocks;
    }

    private static BlockState parseState(CompoundTag stateTag) {
        String name = stateTag.getString("Name");
        if (name == null || name.isBlank()) {
            return null;
        }
        name = LEGACY_BLOCK_REMAPS.getOrDefault(name, name);
        Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(name)).orElse(null);
        if (block == null) {
            SimuKraft.LOGGER.warn("Simukraft: Missing block {} while loading structure", name);
            return null;
        }
        BlockState state = block.defaultBlockState();
        if (stateTag.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag properties = stateTag.getCompound("Properties");
            for (String key : properties.getAllKeys()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                if (property == null) {
                    continue;
                }
                state = applyProperty(state, property, properties.getString(key));
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    /** 扫描 NBT 方块列表中的床头方块，自动生成 POI 定义：红床→RESIDENTIAL，白床→MEDICAL */
    private static List<BuildingPoiDefinition> scanPoiDefinitions(List<BuildingBlockData> blocks) {
        int residentialCount = 0;
        int medicalCount = 0;
        for (BuildingBlockData block : blocks) {
            BlockState state = block.state();
            if (state == null) continue;
            // 只统计床头，避免一张床的头/脚两个方块重复计数
            if (!state.hasProperty(BlockStateProperties.BED_PART) ||
                    state.getValue(BlockStateProperties.BED_PART) != BedPart.HEAD) {
                continue;
            }
            if (state.is(Blocks.RED_BED)) {
                residentialCount++;
            } else if (state.is(Blocks.WHITE_BED)) {
                medicalCount++;
            }
        }
        List<BuildingPoiDefinition> result = new ArrayList<>();
        if (residentialCount > 0) {
            result.add(new BuildingPoiDefinition("residential", CityPoiType.RESIDENTIAL, residentialCount));
        }
        if (medicalCount > 0) {
            result.add(new BuildingPoiDefinition("medical", CityPoiType.MEDICAL, medicalCount));
        }
        return List.copyOf(result);
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }
}
