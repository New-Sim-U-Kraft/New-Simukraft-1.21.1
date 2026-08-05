package common.cn.kafei.simukraft.item;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxService;
import common.cn.kafei.simukraft.commercial.CommercialDefinition;
import common.cn.kafei.simukraft.commercial.CommercialDefinitionLoader;
import common.cn.kafei.simukraft.commercial.CommercialOffer;
import common.cn.kafei.simukraft.commercial.CommercialResource;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialDefinitionLoader;
import common.cn.kafei.simukraft.industrial.IndustrialItemStackSpec;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("null")
final class ManifestControlBoxSnapshotService {
    private static final int COMMERCIAL_MATERIAL_RADIUS_XZ = 5;
    private static final int COMMERCIAL_MATERIAL_RADIUS_Y = 2;

    private ManifestControlBoxSnapshotService() {
    }

    /** commercial: 从商业控制箱构建清单快照。 */
    static FillResult commercial(ServerLevel level, BlockPos controlBoxPos) {
        PlacedBuildingRecord building = CommercialControlBoxService.resolveBuilding(level, controlBoxPos);
        CommercialDefinitionLoader.LoadResult loadResult = CommercialDefinitionLoader.loadForBuilding(building);
        CommercialDefinition definition = loadResult.definition();
        if (building == null) {
            return FillResult.warning(Component.translatable("message.simukraft.commercial_control_box.no_building"));
        }
        if (!loadResult.valid() || definition == null) {
            return FillResult.warning(Component.translatable("message.simukraft.commercial.invalid_definition"));
        }
        return FillResult.success(new Snapshot(
                displayName(definition.name(), building.displayName(), building.buildingFileName()),
                "commercial",
                controlBoxPos,
                0,
                0,
                commercialMaterials(definition),
                countNearbyContainerItems(level, controlBoxPos),
                commercialProductGroups(definition)
        ));
    }

    /** industrial: 从工业控制箱构建当前配方的清单快照。 */
    static FillResult industrial(ServerLevel level, BlockPos controlBoxPos) {
        PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, controlBoxPos);
        IndustrialDefinitionLoader.LoadResult loadResult = IndustrialDefinitionLoader.loadForBuilding(building);
        IndustrialDefinition definition = loadResult.definition();
        if (building == null) {
            return FillResult.warning(Component.translatable("message.simukraft.industrial_control_box.no_building"));
        }
        if (!loadResult.valid() || definition == null) {
            return FillResult.warning(Component.translatable("gui.simukraft.industrial.status.invalid_definition"));
        }

        List<IndustrialDefinition.RecipeDefinition> recipes = definition.recipes().stream()
                .filter(ManifestControlBoxSnapshotService::hasMaterialSupplyRecipe)
                .toList();
        List<BlockPos> inputContainers = industrialInputContainers(building, definition);
        return FillResult.success(new Snapshot(
                displayName(definition.name(), building.displayName(), building.buildingFileName()),
                "industrial",
                controlBoxPos,
                0,
                recipes.size(),
                industrialMaterials(recipes),
                industrialAvailableMaterials(level, inputContainers, recipes),
                industrialProductGroups(recipes)
        ));
    }

    /** commercialMaterials: 汇总材料供货型商业报价的需求。 */
    private static Map<String, Integer> commercialMaterials(CommercialDefinition definition) {
        Map<String, Integer> materials = new LinkedHashMap<>();
        for (CommercialOffer offer : definition.offers()) {
            CommercialOffer.StockRule stock = offer.stock();
            if (stock == null || !stock.materialBacked() || !offer.itemLeavesStock()) {
                continue;
            }
            for (CommercialOffer.MaterialRequirement requirement : stock.materials()) {
                addMaterial(materials, requirement.itemId(), requirement.count());
            }
        }
        return materials;
    }

    /** hasMaterialSupplyRecipe: 判断工业配方是否需要供应材料且存在产物。 */
    private static boolean hasMaterialSupplyRecipe(IndustrialDefinition.RecipeDefinition recipe) {
        return recipe != null
                && !industrialInputMaterials(recipe.inputs()).isEmpty()
                && !industrialProducts(recipe.outputs()).isEmpty();
    }

    /** industrialMaterials: 汇总工业清单页涉及的全部配方输入需求。 */
    private static Map<String, Integer> industrialMaterials(List<IndustrialDefinition.RecipeDefinition> recipes) {
        Map<String, Integer> materials = new LinkedHashMap<>();
        if (recipes == null) {
            return materials;
        }
        for (IndustrialDefinition.RecipeDefinition recipe : recipes) {
            industrialInputMaterials(recipe.inputs()).forEach((itemId, count) -> addMaterial(materials, itemId, count));
        }
        return materials;
    }

    /** industrialInputMaterials: 汇总单个工业配方的输入需求。 */
    private static Map<String, Integer> industrialInputMaterials(List<IndustrialDefinition.InputRequirement> requirements) {
        Map<String, Integer> materials = new LinkedHashMap<>();
        if (requirements == null) {
            return materials;
        }
        for (IndustrialDefinition.InputRequirement requirement : requirements) {
            appendIndustrialRequirement(materials, requirement);
        }
        return materials;
    }

    /** appendIndustrialRequirement: 按工业输入表达式写入材料需求。 */
    private static void appendIndustrialRequirement(Map<String, Integer> materials, IndustrialDefinition.InputRequirement requirement) {
        if (requirement instanceof IndustrialDefinition.ItemRequirement itemRequirement) {
            addMaterial(materials, materialItemId(itemRequirement.spec()), itemRequirement.count());
            return;
        }
        if (requirement instanceof IndustrialDefinition.InputRequirementGroup group) {
            List<IndustrialDefinition.InputRequirement> children = group.children();
            if (children.isEmpty()) {
                return;
            }
            if (group.logic() == IndustrialDefinition.InputLogic.ANY) {
                appendIndustrialRequirement(materials, children.getFirst());
                return;
            }
            for (IndustrialDefinition.InputRequirement child : children) {
                appendIndustrialRequirement(materials, child);
            }
        }
    }

    /** commercialProductGroups: 按售出商品组织商业清单分组。 */
    private static List<ProductGroup> commercialProductGroups(CommercialDefinition definition) {
        List<ProductGroup> groups = new ArrayList<>();
        for (CommercialOffer offer : definition.offers()) {
            CommercialOffer.StockRule stock = offer.stock();
            if (stock == null || !stock.materialBacked() || !offer.itemLeavesStock()) {
                continue;
            }
            Map<String, Integer> materials = new LinkedHashMap<>();
            for (CommercialOffer.MaterialRequirement requirement : stock.materials()) {
                addMaterial(materials, requirement.itemId(), requirement.count());
            }
            Map<String, Integer> products = commercialProducts(offer);
            if (!materials.isEmpty() && !products.isEmpty()) {
                groups.add(new ProductGroup(products, materials));
            }
        }
        return List.copyOf(groups);
    }

    /** commercialProducts: 解析玩家实际获得的商业商品。 */
    private static Map<String, Integer> commercialProducts(CommercialOffer offer) {
        Map<String, Integer> products = new LinkedHashMap<>();
        for (CommercialResource resource : offer.result()) {
            if (resource.type() == CommercialResource.Type.ITEM) {
                addMaterial(products, resource.itemId(), resource.count());
            }
        }
        return products;
    }

    /** industrialProductGroups: 按配方组织工业清单分组，每页显示一个配方。 */
    private static List<ProductGroup> industrialProductGroups(List<IndustrialDefinition.RecipeDefinition> recipes) {
        List<ProductGroup> groups = new ArrayList<>();
        if (recipes == null) {
            return groups;
        }
        for (IndustrialDefinition.RecipeDefinition recipe : recipes) {
            ProductGroup group = industrialProductGroup(recipe);
            if (group != null) {
                groups.add(group);
            }
        }
        return List.copyOf(groups);
    }

    /** industrialProductGroup: 转换单个工业配方的材料和产物。 */
    private static ProductGroup industrialProductGroup(IndustrialDefinition.RecipeDefinition recipe) {
        Map<String, Integer> materials = industrialInputMaterials(recipe.inputs());
        Map<String, Integer> products = industrialProducts(recipe.outputs());
        return materials.isEmpty() || products.isEmpty() ? null : new ProductGroup(products, materials);
    }

    /** industrialProducts: 汇总单个工业配方的产物。 */
    private static Map<String, Integer> industrialProducts(List<IndustrialDefinition.ProductOutput> outputs) {
        Map<String, Integer> products = new LinkedHashMap<>();
        if (outputs == null) {
            return products;
        }
        for (IndustrialDefinition.ProductOutput output : outputs) {
            addMaterial(products, materialItemId(output.spec()), output.baseAmount());
        }
        return products;
    }

    /** industrialAvailableMaterials: 统计工业输入箱内满足全部清单配方约束的可用材料。 */
    private static Map<String, Integer> industrialAvailableMaterials(ServerLevel level,
                                                                     List<BlockPos> inputContainers,
                                                                     List<IndustrialDefinition.RecipeDefinition> recipes) {
        if (recipes == null || recipes.isEmpty() || inputContainers.isEmpty()) {
            return Map.of();
        }
        Set<IndustrialItemStackSpec> specs = new LinkedHashSet<>();
        for (IndustrialDefinition.RecipeDefinition recipe : recipes) {
            collectIndustrialInputSpecs(specs, recipe.inputs());
        }
        Map<String, Integer> availableMaterials = new LinkedHashMap<>();
        specs.forEach(spec -> addMaterial(availableMaterials, materialItemId(spec), countIndustrialInput(level, inputContainers, spec)));
        return availableMaterials;
    }

    /** collectIndustrialInputSpecs: 收集需要精确匹配组件的工业输入物品。 */
    private static void collectIndustrialInputSpecs(Set<IndustrialItemStackSpec> specs,
                                                    List<IndustrialDefinition.InputRequirement> requirements) {
        if (requirements == null) {
            return;
        }
        for (IndustrialDefinition.InputRequirement requirement : requirements) {
            collectIndustrialInputSpec(specs, requirement);
        }
    }

    /** collectIndustrialInputSpec: 按工业输入表达式收集物品匹配规则。 */
    private static void collectIndustrialInputSpec(Set<IndustrialItemStackSpec> specs,
                                                   IndustrialDefinition.InputRequirement requirement) {
        if (requirement instanceof IndustrialDefinition.ItemRequirement itemRequirement) {
            specs.add(itemRequirement.spec());
            return;
        }
        if (requirement instanceof IndustrialDefinition.InputRequirementGroup group) {
            List<IndustrialDefinition.InputRequirement> children = group.children();
            if (children.isEmpty()) {
                return;
            }
            if (group.logic() == IndustrialDefinition.InputLogic.ANY) {
                collectIndustrialInputSpec(specs, children.getFirst());
                return;
            }
            for (IndustrialDefinition.InputRequirement child : children) {
                collectIndustrialInputSpec(specs, child);
            }
        }
    }

    /** industrialInputContainers: 按定义解析工业输入箱坐标。 */
    private static List<BlockPos> industrialInputContainers(PlacedBuildingRecord building, IndustrialDefinition definition) {
        Set<BlockPos> containers = new LinkedHashSet<>(IndustrialControlBoxService.resolveContainerPositions(building, definition, "input"));
        if (!containers.isEmpty()) {
            return List.copyOf(containers);
        }
        definition.containers().keySet().stream()
                .filter(id -> id != null && id.toLowerCase(java.util.Locale.ROOT).contains("input"))
                .forEach(id -> containers.addAll(IndustrialControlBoxService.resolveContainerPositions(building, definition, id)));
        if (!containers.isEmpty()) {
            return List.copyOf(containers);
        }
        definition.containers().keySet().forEach(id ->
                containers.addAll(IndustrialControlBoxService.resolveContainerPositions(building, definition, id)));
        return List.copyOf(containers);
    }

    /** countNearbyContainerItems: 统计商业控制箱附近供货材料。 */
    private static Map<String, Integer> countNearbyContainerItems(ServerLevel level, BlockPos centerPos) {
        return countContainerItems(level, nearbyContainers(level, centerPos));
    }

    /** countContainerItems: 按物品 ID 统计一组容器内物品数量。 */
    private static Map<String, Integer> countContainerItems(ServerLevel level, Iterable<BlockPos> containerPositions) {
        Map<String, Integer> items = new LinkedHashMap<>();
        if (containerPositions == null) {
            return items;
        }
        for (BlockPos containerPos : containerPositions) {
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, containerPos)) {
                ItemStack stack = snapshot.stack();
                if (stack.isEmpty() || stack.getItem() == Items.AIR) {
                    continue;
                }
                addMaterial(items, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
            }
        }
        return items;
    }

    /** nearbyContainers: 查找商业材料供给半径内的容器。 */
    private static Set<BlockPos> nearbyContainers(ServerLevel level, BlockPos centerPos) {
        Set<BlockPos> containers = new LinkedHashSet<>();
        if (level == null || centerPos == null) {
            return containers;
        }
        for (int dx = -COMMERCIAL_MATERIAL_RADIUS_XZ; dx <= COMMERCIAL_MATERIAL_RADIUS_XZ; dx++) {
            for (int dy = -COMMERCIAL_MATERIAL_RADIUS_Y; dy <= COMMERCIAL_MATERIAL_RADIUS_Y; dy++) {
                for (int dz = -COMMERCIAL_MATERIAL_RADIUS_XZ; dz <= COMMERCIAL_MATERIAL_RADIUS_XZ; dz++) {
                    BlockPos candidate = centerPos.offset(dx, dy, dz);
                    if (GenericContainerAccess.isContainer(level, candidate)) {
                        containers.add(GenericContainerAccess.canonicalContainerPos(level, candidate));
                    }
                }
            }
        }
        return containers;
    }

    /** countIndustrialInput: 统计满足工业物品组件约束的输入数量。 */
    private static int countIndustrialInput(ServerLevel level, List<BlockPos> containers, IndustrialItemStackSpec spec) {
        int total = 0;
        Set<BlockPos> visited = new LinkedHashSet<>();
        for (BlockPos container : containers) {
            if (!GenericContainerAccess.isContainer(level, container)) {
                continue;
            }
            BlockPos canonical = GenericContainerAccess.canonicalContainerPos(level, container);
            if (!visited.add(canonical)) {
                continue;
            }
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, canonical)) {
                if (spec.matches(snapshot.stack(), level.registryAccess())) {
                    total = safeAdd(total, snapshot.stack().getCount());
                }
            }
        }
        return total;
    }

    /** materialItemId: 获取清单可展示的物品 ID。 */
    private static String materialItemId(IndustrialItemStackSpec spec) {
        return spec != null ? spec.displayItemId() : "";
    }

    /** addMaterial: 合并材料数量并防止整数溢出。 */
    private static void addMaterial(Map<String, Integer> materials, String itemId, int count) {
        if (itemId == null || itemId.isBlank() || count <= 0) {
            return;
        }
        materials.merge(itemId, count, ManifestControlBoxSnapshotService::safeAdd);
    }

    /** displayName: 选择清单标题使用的建筑名。 */
    private static String displayName(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary;
        }
        return fallback != null ? fallback : "";
    }

    /** safeAdd: 做饱和加法避免计数溢出。 */
    private static int safeAdd(int first, int second) {
        long result = (long) Math.max(0, first) + Math.max(0, second);
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    record Snapshot(@Nonnull String buildingName,
                    @Nonnull String sourceType,
                    @Nonnull BlockPos sourcePos,
                    int progressCurrent,
                    int progressTotal,
                    @Nonnull Map<String, Integer> materials,
                    @Nonnull Map<String, Integer> availableMaterials,
                    @Nonnull List<ProductGroup> productGroups) {
        Snapshot {
            sourcePos = sourcePos.immutable();
            materials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
            availableMaterials = Collections.unmodifiableMap(new LinkedHashMap<>(availableMaterials));
            productGroups = List.copyOf(productGroups);
        }
    }

    record ProductGroup(@Nonnull Map<String, Integer> products,
                        @Nonnull Map<String, Integer> materials) {
        ProductGroup {
            products = Collections.unmodifiableMap(new LinkedHashMap<>(products));
            materials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
        }
    }

    record FillResult(Snapshot snapshot, Component warning) {
        private static FillResult success(Snapshot snapshot) {
            return new FillResult(snapshot, null);
        }

        private static FillResult warning(Component warning) {
            return new FillResult(null, warning);
        }

        boolean success() {
            return snapshot != null;
        }
    }
}
