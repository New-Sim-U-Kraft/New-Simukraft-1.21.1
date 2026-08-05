package common.cn.kafei.simukraft.material;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class WorkMaterialPolicy {
    private static final ConcurrentMap<String, WorkMaterialRequest> REQUEST_CACHE = new ConcurrentHashMap<>();
    private static volatile ConfigSnapshot cachedSnapshot = null;
    private static volatile ParsedRules cachedRules = ParsedRules.EMPTY;

    private WorkMaterialPolicy() {
    }

    public static boolean requiresMaterial(BlockState state) {
        if (state == null || isAirBlock(state.getBlock()) || ServerConfig.materialsCreativeMode()) {
            return false;
        }
        String blockId = blockId(state.getBlock());
        if (blockId.isBlank()) {
            return false;
        }
        ParsedRules rules = rules();
        if (ServerConfig.materialsExpertMode()) {
            return !rules.expertSkipList().contains(blockId);
        }
        if (rules.basicMaterials().contains(blockId)) {
            return true;
        }
        return ServerConfig.materialCategoryMatchingEnabled() && rules.findGroup(blockId) != null;
    }

    public static WorkMaterialRequest requestForBlock(BlockState state) {
        if (!requiresMaterial(state)) {
            return WorkMaterialRequest.EMPTY;
        }
        String blockId = blockId(state.getBlock());
        if (blockId.isBlank()) {
            return WorkMaterialRequest.EMPTY;
        }
        ConfigSnapshot snapshot = snapshot();
        return REQUEST_CACHE.computeIfAbsent(snapshot.cacheKey() + "|" + blockId, ignored -> buildRequest(state, blockId, snapshot));
    }

    public static void clearCache() {
        synchronized (WorkMaterialPolicy.class) {
            cachedSnapshot = null;
            cachedRules = ParsedRules.EMPTY;
            REQUEST_CACHE.clear();
        }
    }

    private static WorkMaterialRequest buildRequest(BlockState state, String blockId, ConfigSnapshot snapshot) {
        ParsedRules rules = rules(snapshot);
        LinkedHashSet<Item> acceptedItems = new LinkedHashSet<>();
        if (snapshot.expertMode()) {
            addAcceptedMaterialId(acceptedItems, blockId);
        } else if (snapshot.categoryMatching()) {
            MaterialGroupInfo group = rules.findGroup(blockId);
            if (group != null) {
                if (!group.headers().isEmpty()) {
                    if (!group.isHeader(blockId)) {
                        addAcceptedMaterialId(acceptedItems, blockId);
                    }
                    group.headers().forEach(materialId -> addAcceptedMaterialId(acceptedItems, materialId));
                } else {
                    group.members().forEach(materialId -> addAcceptedMaterialId(acceptedItems, materialId));
                }
            }
        }
        if (acceptedItems.isEmpty()) {
            addAcceptedMaterialId(acceptedItems, blockId);
        }

        List<Item> acceptedList = List.copyOf(acceptedItems);
        Item displayItem = state.getBlock().asItem();
        ItemStack displayStack = displayItem == Items.AIR && !acceptedList.isEmpty()
                ? new ItemStack(acceptedList.getFirst())
                : new ItemStack(displayItem);
        return WorkMaterialRequest.matching(displayStack, acceptedList, stack -> acceptedItems.contains(stack.getItem()));
    }

    private static ParsedRules rules() {
        return rules(snapshot());
    }

    private static ParsedRules rules(ConfigSnapshot snapshot) {
        ParsedRules rules = cachedRules;
        if (snapshot.equals(cachedSnapshot)) {
            return rules;
        }
        synchronized (WorkMaterialPolicy.class) {
            if (!snapshot.equals(cachedSnapshot)) {
                cachedRules = parseRules(snapshot);
                cachedSnapshot = snapshot;
                REQUEST_CACHE.clear();
            }
            return cachedRules;
        }
    }

    private static ParsedRules parseRules(ConfigSnapshot snapshot) {
        LinkedHashSet<String> basicMaterials = normalizeEntries(snapshot.basicMaterials());
        LinkedHashSet<String> expertSkipList = normalizeEntries(snapshot.expertSkipList());
        Map<String, MaterialGroupInfo> groups = parseMaterialCategoryGroups(snapshot.categoryGroups());
        return new ParsedRules(basicMaterials, expertSkipList, groups);
    }

    private static Map<String, MaterialGroupInfo> parseMaterialCategoryGroups(List<String> entries) {
        LinkedHashMap<String, MaterialGroupInfo> groups = new LinkedHashMap<>();
        for (String rawEntry : entries) {
            if (rawEntry == null || rawEntry.isBlank()) {
                continue;
            }
            MaterialGroupInfo groupInfo = parseGroup(rawEntry.trim());
            if (groupInfo != null) {
                groups.put(groupInfo.groupName(), groupInfo);
            }
        }
        return Map.copyOf(groups);
    }

    private static MaterialGroupInfo parseGroup(String entry) {
        if (entry.contains("|")) {
            String[] parts = entry.split("\\|", 3);
            if (parts.length >= 2) {
                String groupName = parts[0].trim();
                List<String> headers = splitMaterialIds(parts[1]);
                List<String> members = parts.length >= 3 ? splitMaterialIds(parts[2]) : List.of();
                if (!groupName.isBlank() && (!headers.isEmpty() || !members.isEmpty())) {
                    return new MaterialGroupInfo(groupName, headers, members);
                }
            }
            return null;
        }
        String[] parts = entry.split(":", 2);
        if (parts.length == 2 && !parts[0].isBlank()) {
            List<String> headers = splitMaterialIds(parts[1]);
            if (!headers.isEmpty()) {
                return new MaterialGroupInfo(parts[0].trim(), headers, List.of());
            }
        }
        return null;
    }

    private static List<String> splitMaterialIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String rawId : value.split(",")) {
            String id = normalizeMaterialId(rawId);
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private static LinkedHashSet<String> normalizeEntries(List<String> entries) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String entry : entries) {
            String normalized = normalizeMaterialId(entry);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static String normalizeMaterialId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static void addAcceptedMaterialId(Set<Item> acceptedItems, String materialId) {
        resolveItem(materialId).ifPresent(acceptedItems::add);
        for (String variantId : variantItemIds(materialId)) {
            resolveItem(variantId).ifPresent(acceptedItems::add);
        }
    }

    private static java.util.Optional<Item> resolveItem(String materialId) {
        ResourceLocation id = ResourceLocation.tryParse(materialId);
        if (id == null) {
            return java.util.Optional.empty();
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item != Items.AIR) {
            return java.util.Optional.of(item);
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        Item blockItem = block.asItem();
        if (blockItem != Items.AIR) {
            return java.util.Optional.of(blockItem);
        }
        SimuKraft.LOGGER.debug("Simukraft: Unknown material id in config: {}", materialId);
        return java.util.Optional.empty();
    }

    private static List<String> variantItemIds(String blockId) {
        String namespace = "minecraft";
        String path = blockId;
        int separatorIndex = blockId.indexOf(':');
        if (separatorIndex >= 0) {
            namespace = blockId.substring(0, separatorIndex);
            path = blockId.substring(separatorIndex + 1);
        }
        return switch (path) {
            case "wall_torch" -> List.of(namespace + ":torch");
            case "soul_wall_torch" -> List.of(namespace + ":soul_torch");
            case "redstone_wall_torch" -> List.of(namespace + ":redstone_torch");
            case "wall_lantern" -> List.of(namespace + ":lantern");
            case "soul_wall_lantern" -> List.of(namespace + ":soul_lantern");
            default -> List.of();
        };
    }

    private static ConfigSnapshot snapshot() {
        return new ConfigSnapshot(
                ServerConfig.materialsCreativeMode(),
                ServerConfig.materialsExpertMode(),
                ServerConfig.materialCategoryMatchingEnabled(),
                ServerConfig.basicMaterials(),
                ServerConfig.materialCategoryGroups(),
                ServerConfig.expertModeSkipList()
        );
    }

    private static String blockId(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean isAirBlock(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }

    private record ConfigSnapshot(
            boolean creativeMode,
            boolean expertMode,
            boolean categoryMatching,
            List<String> basicMaterials,
            List<String> categoryGroups,
            List<String> expertSkipList
    ) {
        private ConfigSnapshot {
            basicMaterials = List.copyOf(basicMaterials);
            categoryGroups = List.copyOf(categoryGroups);
            expertSkipList = List.copyOf(expertSkipList);
        }

        private String cacheKey() {
            return creativeMode + "|" + expertMode + "|" + categoryMatching + "|" + basicMaterials + "|" + categoryGroups + "|" + expertSkipList;
        }
    }

    private record ParsedRules(Set<String> basicMaterials, Set<String> expertSkipList, Map<String, MaterialGroupInfo> groups) {
        private static final ParsedRules EMPTY = new ParsedRules(Set.of(), Set.of(), Map.of());

        private MaterialGroupInfo findGroup(String materialId) {
            for (MaterialGroupInfo group : groups.values()) {
                if (group.contains(materialId)) {
                    return group;
                }
            }
            return null;
        }
    }

    private record MaterialGroupInfo(String groupName, List<String> headers, List<String> members) {
        private MaterialGroupInfo {
            headers = List.copyOf(headers);
            members = List.copyOf(members);
        }

        private boolean isHeader(String materialId) {
            return headers.contains(materialId);
        }

        private boolean contains(String materialId) {
            return headers.contains(materialId) || members.contains(materialId);
        }
    }
}
