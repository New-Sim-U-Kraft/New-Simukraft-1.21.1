package common.cn.kafei.simukraft.item;

import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.clientbridge.ClientInteractionBridge;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("null")
public final class ManifestItem extends Item {
    private static final String TAG_MATERIALS = "Materials";
    private static final String TAG_CHECKED = "Checked";
    private static final String TAG_AVAILABLE = "Available";
    private static final String TAG_BUILDING_NAME = "BuildingName";
    private static final String TAG_BUILD_BOX_POS = "BuildBoxPos";
    private static final String TAG_SOURCE_TYPE = "SourceType";
    private static final String TAG_PROGRESS_CURRENT = "ProgressCurrent";
    private static final String TAG_PROGRESS_TOTAL = "ProgressTotal";
    private static final String TAG_PRODUCT_GROUPS = "ProductGroups";
    private static final String TAG_PRODUCTS = "Products";
    private static final String TAG_INDEX = "Index";

    public ManifestItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public @Nonnull InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            ClientInteractionBridge.openManifest(stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public @Nonnull InteractionResult useOn(@Nonnull net.minecraft.world.item.context.UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        BlockPos clickedPos = context.getClickedPos();
        BlockState state = level.getBlockState(context.getClickedPos());
        if (isBuildBox(state)) {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                fillFromBuildBox(context.getItemInHand(), serverLevel, clickedPos, serverPlayer);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (isCommercialControlBox(state)) {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                fillFromCommercialControlBox(context.getItemInHand(), serverLevel, clickedPos, serverPlayer);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (isIndustrialControlBox(state)) {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                fillFromIndustrialControlBox(context.getItemInHand(), serverLevel, clickedPos, serverPlayer);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context, @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag isAdvanced) {
        super.appendHoverText(stack, context, tooltipComponents, isAdvanced);
        if (hasData(stack)) {
            tooltipComponents.add(Component.translatable("tooltip.simukraft.manifest.building", getBuildingName(stack)));
            tooltipComponents.add(Component.translatable("tooltip.simukraft.manifest.progress", getProgressCurrent(stack), getProgressTotal(stack)));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.simukraft.manifest.empty"));
        }
    }

    private static boolean isBuildBox(BlockState state) {
        return state.is(ModBlocks.BUILD_BOX.get());
    }

    /** isCommercialControlBox: 判断方块是否为商业控制箱。 */
    private static boolean isCommercialControlBox(BlockState state) {
        return state.is(ModBlocks.COMMERCIAL_CONTROL_BOX.get());
    }

    /** isIndustrialControlBox: 判断方块是否为工业控制箱。 */
    private static boolean isIndustrialControlBox(BlockState state) {
        return state.is(ModBlocks.INDUSTRIAL_CONTROL_BOX.get());
    }

    private static void fillFromBuildBox(ItemStack stack, ServerLevel level, BlockPos buildBoxPos, ServerPlayer player) {
        UUID buildTaskOwner = findBuildTaskOwner(level, buildBoxPos);
        if (buildTaskOwner == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.manifest.no_task"));
            return;
        }

        BuildingTaskData task = SimuSqliteStorage.loadBuildingTask(level, buildTaskOwner);
        if (task == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.manifest.no_task"));
            return;
        }

        Optional<BuildingStructure> structureOptional = BuildingStructureService.loadStructure(task);
        if (structureOptional.isEmpty()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.manifest.no_building"));
            return;
        }

        BuildingStructure structure = structureOptional.get();
        List<BuildingBlockData> placedBlocks = sortedPlacedBlocks(structure, task);
        int currentIndex = clamp(task.currentBlockIndex(), 0, placedBlocks.size());
        Map<String, Integer> chestMaterials = countAdjacentContainerItems(level, buildBoxPos);
        ManifestMaterialAvailability.Snapshot materialSnapshot = ManifestMaterialAvailability.calculate(placedBlocks, currentIndex, chestMaterials);
        if (materialSnapshot.required().isEmpty()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.manifest.no_building"));
            return;
        }

        CompoundTag tag = customTag(stack);
        tag.putString(TAG_BUILDING_NAME, displayName(task.displayName(), structure.displayName(), task.buildingFileName()));
        tag.putString(TAG_SOURCE_TYPE, "build");
        tag.putLong(TAG_BUILD_BOX_POS, buildBoxPos.asLong());
        tag.putInt(TAG_PROGRESS_CURRENT, currentIndex);
        tag.putInt(TAG_PROGRESS_TOTAL, Math.max(0, task.totalBlocks()));
        writeMaterials(tag, materialSnapshot.required(), materialSnapshot.available());
        tag.remove(TAG_PRODUCT_GROUPS);
        applyCustomTag(stack, tag);
        InfoToastService.success(player, Component.translatable("message.simukraft.manifest.filled"));
    }

    /** fillFromCommercialControlBox: 从商业控制箱填充清单材料。 */
    private static void fillFromCommercialControlBox(ItemStack stack, ServerLevel level, BlockPos controlBoxPos, ServerPlayer player) {
        fillFromControlBoxSnapshot(stack, player, ManifestControlBoxSnapshotService.commercial(level, controlBoxPos));
    }

    /** fillFromIndustrialControlBox: 从工业控制箱填充当前配方清单材料。 */
    private static void fillFromIndustrialControlBox(ItemStack stack, ServerLevel level, BlockPos controlBoxPos, ServerPlayer player) {
        fillFromControlBoxSnapshot(stack, player, ManifestControlBoxSnapshotService.industrial(level, controlBoxPos));
    }

    /** fillFromControlBoxSnapshot: 将控制箱材料快照写入清单 NBT。 */
    private static void fillFromControlBoxSnapshot(ItemStack stack, ServerPlayer player, ManifestControlBoxSnapshotService.FillResult result) {
        if (!result.success()) {
            InfoToastService.warning(player, result.warning());
            return;
        }

        ManifestControlBoxSnapshotService.Snapshot snapshot = result.snapshot();
        CompoundTag tag = customTag(stack);
        tag.putString(TAG_BUILDING_NAME, snapshot.buildingName());
        tag.putString(TAG_SOURCE_TYPE, snapshot.sourceType());
        tag.putLong(TAG_BUILD_BOX_POS, snapshot.sourcePos().asLong());
        tag.putInt(TAG_PROGRESS_CURRENT, snapshot.progressCurrent());
        tag.putInt(TAG_PROGRESS_TOTAL, snapshot.progressTotal());
        writeMaterials(tag, snapshot.materials(), snapshot.availableMaterials());
        writeProductGroups(tag, snapshot.productGroups());
        applyCustomTag(stack, tag);
        InfoToastService.success(player, Component.translatable("message.simukraft.manifest.filled"));
    }

    private static List<BuildingBlockData> sortedPlacedBlocks(BuildingStructure structure, BuildingTaskData task) {
        return BuildingStructureService.resolvePlacedBlocks(structure, task.origin(), task.rotationDegrees()).stream()
                .sorted(Comparator.comparingInt((BuildingBlockData block) -> block.relativePos().getY())
                        .thenComparingInt(block -> block.relativePos().getX())
                        .thenComparingInt(block -> block.relativePos().getZ()))
                .toList();
    }

    /** displayName: 选择清单标题，优先使用任务持久化的建筑名。 */
    private static String displayName(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary;
        }
        return fallback != null ? fallback : "";
    }

    private static Map<String, Integer> countAdjacentContainerItems(ServerLevel level, BlockPos buildBoxPos) {
        return countContainerItems(level, adjacentContainers(level, buildBoxPos));
    }

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
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                items.merge(itemId, stack.getCount(), Integer::sum);
            }
        }
        return items;
    }

    private static Set<BlockPos> adjacentContainers(ServerLevel level, BlockPos buildBoxPos) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = buildBoxPos.relative(direction);
            if (!GenericContainerAccess.isContainer(level, adjacentPos)) {
                continue;
            }
            positions.add(GenericContainerAccess.canonicalContainerPos(level, adjacentPos));
        }
        return positions;
    }

    private static void writeMaterials(@Nonnull CompoundTag tag, @Nonnull Map<String, Integer> materials, @Nonnull Map<String, Integer> availableMaterials) {
        ListTag materialsList = new ListTag();
        ListTag checkedList = new ListTag();
        materials.forEach((itemId, count) -> {
            int available = Math.min(count, Math.max(0, availableMaterials.getOrDefault(itemId, 0)));
            CompoundTag materialTag = new CompoundTag();
            materialTag.putString("Item", itemId);
            materialTag.putInt("Count", count);
            materialTag.putInt(TAG_AVAILABLE, available);
            materialsList.add(materialTag);
            checkedList.add(StringTag.valueOf(Boolean.toString(available >= count)));
        });
        tag.put(TAG_MATERIALS, materialsList);
        tag.put(TAG_CHECKED, checkedList);
    }

    /** writeProductGroups: 写入“材料到商品”的清单分组，保留材料全局勾选索引。 */
    private static void writeProductGroups(@Nonnull CompoundTag tag,
                                           @Nonnull List<ManifestControlBoxSnapshotService.ProductGroup> productGroups) {
        if (productGroups.isEmpty()) {
            tag.remove(TAG_PRODUCT_GROUPS);
            return;
        }
        ListTag groupsList = new ListTag();
        for (ManifestControlBoxSnapshotService.ProductGroup group : productGroups) {
            CompoundTag groupTag = new CompoundTag();
            groupTag.put(TAG_PRODUCTS, materialList(group.products(), Map.of(), -1));
            groupTag.put(TAG_MATERIALS, groupMaterialsList(group.materials(), tag.getList(TAG_MATERIALS, Tag.TAG_COMPOUND)));
            if (!groupTag.getList(TAG_PRODUCTS, Tag.TAG_COMPOUND).isEmpty()
                    && !groupTag.getList(TAG_MATERIALS, Tag.TAG_COMPOUND).isEmpty()) {
                groupsList.add(groupTag);
            }
        }
        if (groupsList.isEmpty()) {
            tag.remove(TAG_PRODUCT_GROUPS);
        } else {
            tag.put(TAG_PRODUCT_GROUPS, groupsList);
        }
    }

    private static ListTag materialList(Map<String, Integer> materials, Map<String, Integer> availableMaterials, int index) {
        ListTag materialsList = new ListTag();
        materials.forEach((itemId, count) -> {
            CompoundTag materialTag = new CompoundTag();
            materialTag.putString("Item", itemId);
            materialTag.putInt("Count", count);
            if (index >= 0) {
                materialTag.putInt(TAG_INDEX, index);
            }
            materialTag.putInt(TAG_AVAILABLE, Math.min(count, Math.max(0, availableMaterials.getOrDefault(itemId, 0))));
            materialsList.add(materialTag);
        });
        return materialsList;
    }

    private static ListTag groupMaterialsList(Map<String, Integer> materials, ListTag flatMaterials) {
        ListTag materialsList = new ListTag();
        materials.forEach((itemId, count) -> {
            int index = findMaterialIndex(flatMaterials, itemId);
            CompoundTag materialTag = new CompoundTag();
            materialTag.putString("Item", itemId);
            materialTag.putInt("Count", count);
            materialTag.putInt(TAG_INDEX, index);
            if (index >= 0 && index < flatMaterials.size()) {
                CompoundTag flatTag = flatMaterials.getCompound(index);
                materialTag.putInt(TAG_AVAILABLE, flatTag.contains(TAG_AVAILABLE) ? flatTag.getInt(TAG_AVAILABLE) : 0);
            }
            materialsList.add(materialTag);
        });
        return materialsList;
    }

    private static int findMaterialIndex(ListTag flatMaterials, String itemId) {
        for (int i = 0; i < flatMaterials.size(); i++) {
            if (flatMaterials.getCompound(i).getString("Item").equals(itemId)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasData(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty();
    }

    @Nonnull
    public static List<MaterialEntry> getMaterials(@Nonnull ItemStack stack) {
        List<MaterialEntry> result = new ArrayList<>();
        CompoundTag tag = customTag(stack);
        if (tag.isEmpty() || !tag.contains(TAG_MATERIALS)) {
            return result;
        }
        ListTag materialsList = tag.getList(TAG_MATERIALS, Tag.TAG_COMPOUND);
        ListTag checkedList = tag.getList(TAG_CHECKED, Tag.TAG_STRING);
        for (int i = 0; i < materialsList.size(); i++) {
            CompoundTag materialTag = materialsList.getCompound(i);
            String itemId = materialTag.getString("Item");
            int count = materialTag.getInt("Count");
            boolean checked = i < checkedList.size() && Boolean.parseBoolean(checkedList.getString(i));
            int available = materialTag.contains(TAG_AVAILABLE) ? materialTag.getInt(TAG_AVAILABLE) : (checked ? count : 0);
            result.add(new MaterialEntry(itemId, count, Math.min(count, Math.max(0, available)), checked, i));
        }
        return result;
    }

    @Nonnull
    public static List<ProductGroup> getProductGroups(@Nonnull ItemStack stack) {
        List<ProductGroup> result = new ArrayList<>();
        CompoundTag tag = customTag(stack);
        if (tag.isEmpty() || !tag.contains(TAG_PRODUCT_GROUPS)) {
            return result;
        }
        ListTag groupsList = tag.getList(TAG_PRODUCT_GROUPS, Tag.TAG_COMPOUND);
        ListTag checkedList = tag.getList(TAG_CHECKED, Tag.TAG_STRING);
        for (int i = 0; i < groupsList.size(); i++) {
            CompoundTag groupTag = groupsList.getCompound(i);
            List<MaterialEntry> products = readEntries(groupTag.getList(TAG_PRODUCTS, Tag.TAG_COMPOUND), checkedList, false);
            List<MaterialEntry> groupMaterials = readEntries(groupTag.getList(TAG_MATERIALS, Tag.TAG_COMPOUND), checkedList, true);
            if (!products.isEmpty() || !groupMaterials.isEmpty()) {
                result.add(new ProductGroup(products, groupMaterials));
            }
        }
        return result;
    }

    private static List<MaterialEntry> readEntries(ListTag entriesList, ListTag checkedList, boolean useStoredIndex) {
        List<MaterialEntry> result = new ArrayList<>();
        for (int i = 0; i < entriesList.size(); i++) {
            CompoundTag entryTag = entriesList.getCompound(i);
            int index = useStoredIndex && entryTag.contains(TAG_INDEX) ? entryTag.getInt(TAG_INDEX) : -1;
            boolean checked = index >= 0 && index < checkedList.size() && Boolean.parseBoolean(checkedList.getString(index));
            int count = entryTag.getInt("Count");
            int available = entryTag.contains(TAG_AVAILABLE) ? entryTag.getInt(TAG_AVAILABLE) : (checked ? count : 0);
            result.add(new MaterialEntry(entryTag.getString("Item"), count, Math.min(count, Math.max(0, available)), checked, index));
        }
        return result;
    }

    @Nonnull
    public static String getBuildingName(@Nonnull ItemStack stack) {
        CompoundTag tag = customTag(stack);
        return tag.contains(TAG_BUILDING_NAME) ? tag.getString(TAG_BUILDING_NAME) : "";
    }

    public static int getProgressCurrent(@Nonnull ItemStack stack) {
        CompoundTag tag = customTag(stack);
        return tag.contains(TAG_PROGRESS_CURRENT) ? Math.max(0, tag.getInt(TAG_PROGRESS_CURRENT)) : 0;
    }

    public static int getProgressTotal(@Nonnull ItemStack stack) {
        CompoundTag tag = customTag(stack);
        return tag.contains(TAG_PROGRESS_TOTAL) ? Math.max(0, tag.getInt(TAG_PROGRESS_TOTAL)) : 0;
    }

    @Nonnull
    public static String getSourceType(@Nonnull ItemStack stack) {
        CompoundTag tag = customTag(stack);
        return tag.contains(TAG_SOURCE_TYPE) ? tag.getString(TAG_SOURCE_TYPE) : "";
    }

    public static int getTotalMaterials(@Nonnull ItemStack stack) {
        return getMaterials(stack).size();
    }

    public static int getCheckedCount(@Nonnull ItemStack stack) {
        int count = 0;
        for (MaterialEntry entry : getMaterials(stack)) {
            if (entry.checked()) {
                count++;
            }
        }
        return count;
    }

    public static void setChecked(@Nonnull ItemStack stack, int index, boolean checked) {
        CompoundTag tag = customTag(stack);
        if (tag.isEmpty() || !tag.contains(TAG_CHECKED)) {
            return;
        }
        ListTag checkedList = tag.getList(TAG_CHECKED, Tag.TAG_STRING);
        if (index < 0 || index >= checkedList.size()) {
            return;
        }
        checkedList.set(index, StringTag.valueOf(Boolean.toString(checked)));
        tag.put(TAG_CHECKED, checkedList);
        applyCustomTag(stack, tag);
    }

    private static UUID findBuildTaskOwner(ServerLevel level, BlockPos buildBoxPos) {
        return CitizenEmploymentService.findAssigned(level, "build_box", "builder", buildBoxPos)
                .map(citizen -> citizen.uuid())
                .orElse(null);
    }
    private static CompoundTag customTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void applyCustomTag(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record MaterialEntry(@Nonnull String itemId, int count, int available, boolean checked, int index) {
    }

    public record ProductGroup(@Nonnull List<MaterialEntry> products, @Nonnull List<MaterialEntry> materials) {
        public ProductGroup {
            products = List.copyOf(products);
            materials = List.copyOf(materials);
        }
    }

}
