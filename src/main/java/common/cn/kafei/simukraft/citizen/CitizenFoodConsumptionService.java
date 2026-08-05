package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.medical.DiseaseType;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class CitizenFoodConsumptionService {
    static final double FULL_HUNGER = CitizenEntity.DEFAULT_HUNGER;
    private static final long EAT_VISUAL_TICKS = 40L;
    private static final ConcurrentMap<String, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();
    private static final Set<Item> FAILED_FOOD_LOOKUPS = ConcurrentHashMap.newKeySet();

    private CitizenFoodConsumptionService() {
    }

    /** canEatStack: 判断指定物品是否能给未吃饱的 NPC 增加饥饿值。 */
    public static boolean canEatStack(CitizenEntity entity, ItemStack stack) {
        if (entity == null || entity.getHungerValue() >= FULL_HUNGER) {
            return false;
        }
        FoodProperties properties = foodProperties(entity, stack);
        return properties != null && properties.nutrition() > 0;
    }

    /** isFoodStack：判断物品是否为可供 NPC 食用的有效食物，不受当前饱食度影响。 */
    public static boolean isFoodStack(CitizenEntity entity, ItemStack stack) {
        FoodProperties properties = foodProperties(entity, stack);
        return properties != null && properties.nutrition() > 0;
    }

    /** tryEatBackpackFood：让未吃饱的 NPC 从真实背包取出并食用一份食物。 */
    public static boolean tryEatBackpackFood(ServerLevel level, CitizenEntity entity, CitizenData data) {
        if (level == null || entity == null || data == null || data.dead() || entity.getHungerValue() >= FULL_HUNGER) {
            return false;
        }
        var extracted = entity.getCitizenInventory().extractFirstBackpack(stack -> isFoodStack(entity, stack));
        if (extracted.isEmpty()) {
            return false;
        }
        ItemStack meal = extracted.get();
        FoodProperties properties = foodProperties(entity, meal);
        if (applyFood(level, entity, data, meal, properties)) {
            return true;
        }
        entity.getCitizenInventory().insertBackpackAll(java.util.List.of(meal));
        return false;
    }

    /** foodProperties: 兼容读取原版和模组食物属性，异常只记录一次。 */
    static FoodProperties foodProperties(CitizenEntity entity, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            return stack.getFoodProperties(entity);
        } catch (RuntimeException exception) {
            Item item = stack.getItem();
            if (FAILED_FOOD_LOOKUPS.add(item)) {
                SimuKraft.LOGGER.warn("Simukraft: Failed to read food properties from {}", BuiltInRegistries.ITEM.getKey(item), exception);
            }
            return null;
        }
    }

    /** applyFood: 应用一次食物效果并同步实体 NBT 饱食度和吃饭表现。 */
    static boolean applyFood(ServerLevel level, CitizenEntity entity, CitizenData data, ItemStack visualStack, FoodProperties properties) {
        if (level == null || entity == null || data == null || data.dead() || properties == null) {
            return false;
        }
        double currentHunger = entity.getHungerValue();
        double nextHunger = Math.min(FULL_HUNGER, currentHunger + properties.nutrition());
        if (nextHunger <= currentHunger) {
            return false;
        }

        entity.setHunger(nextHunger);
        if (visualStack != null && !visualStack.isEmpty()) {
            CitizenJobVisualService.setMainHandOverride(data.uuid(), visualStack.copyWithCount(1));
            runtime(level).visualExpiries.put(data.uuid(), level.getGameTime() + EAT_VISUAL_TICKS);
        }
        // 食用蜘蛛眼或腐肉触发食物中毒
        if (!data.disease().isActive() && isFoodPoisoningItem(visualStack)) {
            long currentDay = level.getDayTime() / 24_000L;
            data.setDisease(DiseaseType.FOOD_POISONING, currentDay);
            CitizenService.save(level, data.uuid());
        }
        CitizenManager.get(level).syncEntity(entity);
        level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F, 1.0F);
        entity.triggerWorkSwing(InteractionHand.MAIN_HAND);
        return true;
    }

    /** clearExpiredVisual: 到期后恢复 NPC 原本的职业手持物。 */
    public static void clearExpiredVisual(ServerLevel level, CitizenEntity entity, CitizenData data) {
        if (level == null || entity == null || data == null) {
            return;
        }
        Long expiresAt = runtime(level).visualExpiries.get(data.uuid());
        if (expiresAt == null || level.getGameTime() < expiresAt) {
            return;
        }
        if (runtime(level).visualExpiries.remove(data.uuid(), expiresAt)) {
            CitizenJobVisualService.clearMainHandOverride(data.uuid());
            CitizenManager.get(level).syncEntity(entity);
        }
    }

    /** clearServerCaches: 清理指定服务器存档下的吃饭表现缓存。 */
    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server).toLowerCase(Locale.ROOT);
        RUNTIMES.forEach((key, runtime) -> {
            if (key.startsWith(serverKey + "|")) {
                runtime.visualExpiries.keySet().forEach(CitizenJobVisualService::clearMainHandOverride);
            }
        });
        RUNTIMES.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    /** runtime: 按存档和维度隔离运行时缓存，避免跨世界串数据。 */
    private static LevelRuntime runtime(ServerLevel level) {
        return RUNTIMES.computeIfAbsent(SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT), ignored -> new LevelRuntime());
    }

    private static boolean isFoodPoisoningItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.SPIDER_EYE || item == Items.ROTTEN_FLESH || item == Items.PUFFERFISH;
    }

    private static final class LevelRuntime {
        private final ConcurrentMap<UUID, Long> visualExpiries = new ConcurrentHashMap<>();
    }
}
