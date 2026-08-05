package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.planner.PlannerWorkService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CityJobType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class CitizenJobVisualService {
    public static final CitizenJobVisualAction SWING_WHEN_BUILDING = BuilderConstructionService::hasActiveBuildTask;
    public static final CitizenJobVisualAction SWING_WHEN_PLANNING = PlannerWorkService::hasActiveTask;

    private static final CitizenJobVisualRule EMPTY_RULE = new CitizenJobVisualRule(ItemStack.EMPTY, ItemStack.EMPTY, CitizenJobVisualAction.NONE);
    private static final ConcurrentMap<CityJobType, CitizenJobVisualRule> RULES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, ItemStack> MAIN_HAND_OVERRIDES = new ConcurrentHashMap<>();

    static {
        define(CityJobType.BUILDER, new CitizenJobVisualRule(new ItemStack(Items.COBBLESTONE), ItemStack.EMPTY, SWING_WHEN_BUILDING));
        define(CityJobType.PLANNER, new CitizenJobVisualRule(new ItemStack(Items.IRON_SHOVEL), ItemStack.EMPTY, SWING_WHEN_PLANNING));
        define(CityJobType.STORAGE_WORKER, new CitizenJobVisualRule(new ItemStack(Items.CHEST), ItemStack.EMPTY, CitizenJobVisualAction.NONE));
    }

    private CitizenJobVisualService() {
    }

    // define：注册职业手持物和动作，供后续职业扩展复用。
    public static void define(CityJobType jobType, CitizenJobVisualRule rule) {
        if (jobType == null || rule == null || jobType == CityJobType.UNEMPLOYED) {
            return;
        }
        RULES.put(jobType, rule);
    }

    public static void sync(ServerLevel level, CitizenEntity entity, CitizenData data) {
        if (level == null || entity == null || data == null || data.dead()) {
            return;
        }
        CitizenInventory inventory = entity.getCitizenInventory();
        applySlot(entity, EquipmentSlot.HEAD, inventory.getItem(CitizenInventory.HEAD_SLOT), false);
        applySlot(entity, EquipmentSlot.CHEST, inventory.getItem(CitizenInventory.CHEST_SLOT), false);
        applySlot(entity, EquipmentSlot.LEGS, inventory.getItem(CitizenInventory.LEGS_SLOT), false);
        applySlot(entity, EquipmentSlot.FEET, inventory.getItem(CitizenInventory.FEET_SLOT), false);
        CitizenJobVisualRule rule = RULES.getOrDefault(data.jobType(), EMPTY_RULE);
        ItemStack rightHand = MAIN_HAND_OVERRIDES.get(data.uuid());
        boolean rightHandOverride = rightHand != null && !rightHand.isEmpty();
        if (!rightHandOverride && !rule.rightHand().isEmpty()) {
            rightHand = rule.rightHand();
            rightHandOverride = true;
        } else if (!rightHandOverride) {
            rightHand = inventory.getItem(CitizenInventory.MAIN_HAND_SLOT);
        }
        boolean leftHandOverride = !rule.leftHand().isEmpty();
        ItemStack leftHand = leftHandOverride ? rule.leftHand() : inventory.getItem(CitizenInventory.OFF_HAND_SLOT);
        applySlot(entity, EquipmentSlot.MAINHAND, rightHand, rightHandOverride);
        applySlot(entity, EquipmentSlot.OFFHAND, leftHand, leftHandOverride);
        applyAction(level, entity, data, rule.action());
    }

    public static void setMainHandOverride(UUID citizenId, ItemStack stack) {
        if (citizenId == null) {
            return;
        }
        ItemStack normalized = normalizeVisualStack(stack);
        if (normalized.isEmpty()) {
            MAIN_HAND_OVERRIDES.remove(citizenId);
            return;
        }
        MAIN_HAND_OVERRIDES.put(citizenId, normalized);
    }

    public static void clearMainHandOverride(UUID citizenId) {
        if (citizenId != null) {
            MAIN_HAND_OVERRIDES.remove(citizenId);
        }
    }

    /** applySlot：把真实背包装备或职业临时外观写入原版装备槽；真实装备保留同一物品栈引用。 */
    private static void applySlot(CitizenEntity entity, EquipmentSlot slot, ItemStack desired, boolean copyForVisualOverride) {
        ItemStack normalized = desired != null ? desired : ItemStack.EMPTY;
        ItemStack current = entity.getItemBySlot(slot);
        boolean sameStack = ItemStack.isSameItemSameComponents(current, normalized) && current.getCount() == normalized.getCount();
        if (sameStack && (copyForVisualOverride || current == normalized)) {
            return;
        }
        entity.setItemSlot(slot, copyForVisualOverride ? normalized.copy() : normalized);
        entity.setDropChance(slot, 0.0F);
    }

    private static void applyAction(ServerLevel level, CitizenEntity entity, CitizenData data, CitizenJobVisualAction action) {
        entity.setHasActiveVisualTask(action.isActive(level, data.uuid()));
    }

    private static ItemStack normalizeVisualStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return stack.copyWithCount(Math.max(1, stack.getCount()));
    }

    public record CitizenJobVisualRule(ItemStack rightHand, ItemStack leftHand, CitizenJobVisualAction action) {
        public CitizenJobVisualRule {
            rightHand = normalizeVisualStack(rightHand);
            leftHand = normalizeVisualStack(leftHand);
            action = action != null ? action : CitizenJobVisualAction.NONE;
        }
    }

    @FunctionalInterface
    public interface CitizenJobVisualAction {
        boolean isActive(ServerLevel level, UUID citizenId);

        CitizenJobVisualAction NONE = (level, citizenId) -> false;
    }
}
