package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class CitizenDroppedFoodService {
    private static final double FULL_HUNGER = 20.0D;
    private static final double EAT_RADIUS = 2.0D;
    private static final double EAT_DISTANCE_SQR = EAT_RADIUS * EAT_RADIUS;
    private static final long SCAN_INTERVAL_TICKS = 5L;
    private static final long EAT_VISUAL_TICKS = 40L;
    private static final ConcurrentMap<String, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();
    private static final Set<Item> FAILED_FOOD_LOOKUPS = ConcurrentHashMap.newKeySet();

    private CitizenDroppedFoodService() {
    }

    /** tryEatNearbyFood: 让未吃饱的已加载 NPC 消耗附近一份地面食物。 */
    public static void tryEatNearbyFood(ServerLevel level, CitizenEntity entity, CitizenData data) {
        if (level == null || level.isClientSide() || entity == null || data == null || data.dead()) {
            return;
        }
        clearExpiredVisual(level, entity, data);
        if (entity.getHungerValue() >= FULL_HUNGER || !shouldScan(entity, level.getGameTime())) {
            return;
        }
        ItemEntity foodDrop = nearestFoodDrop(level, entity);
        if (foodDrop == null) {
            return;
        }
        FoodProperties properties = foodProperties(entity, foodDrop.getItem());
        if (properties == null || properties.nutrition() <= 0) {
            return;
        }
        consumeDrop(level, entity, data, foodDrop, properties);
    }

    /** clearServerCaches: 清理指定服务器存档下的投喂视觉缓存。 */
    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server).toLowerCase(Locale.ROOT);
        RUNTIMES.forEach((key, runtime) -> {
            if (key.startsWith(serverKey + "|")) {
                runtime.visualExpiries.keySet().forEach(CitizenJobVisualService::clearMainHandOverride);
            }
        });
        RUNTIMES.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    /** shouldScan: 按 UUID 分散扫描 tick，避免大量 NPC 同时查找掉落物。 */
    private static boolean shouldScan(CitizenEntity entity, long gameTime) {
        return Math.floorMod(entity.getUUID().getLeastSignificantBits(), SCAN_INTERVAL_TICKS) == gameTime % SCAN_INTERVAL_TICKS;
    }

    /** nearestFoodDrop: 查找 NPC 身边最近的可食用掉落物。 */
    private static ItemEntity nearestFoodDrop(ServerLevel level, CitizenEntity entity) {
        return level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(EAT_RADIUS), drop -> canEatDrop(entity, drop))
                .stream()
                .min(Comparator.comparingDouble(drop -> drop.distanceToSqr(entity)))
                .orElse(null);
    }

    /** canEatDrop: 过滤已移除、过远或不可食用的掉落物。 */
    private static boolean canEatDrop(CitizenEntity entity, ItemEntity drop) {
        if (drop == null || drop.isRemoved() || !drop.isAlive() || drop.distanceToSqr(entity) > EAT_DISTANCE_SQR) {
            return false;
        }
        FoodProperties properties = foodProperties(entity, drop.getItem());
        return properties != null && properties.nutrition() > 0;
    }

    /** foodProperties: 安全读取食物属性，兼容其他模组的异常物品实现。 */
    private static FoodProperties foodProperties(CitizenEntity entity, ItemStack stack) {
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

    /** consumeDrop: 消耗一份食物并同步 NPC 饥饿值和吃饭表现。 */
    private static void consumeDrop(ServerLevel level, CitizenEntity entity, CitizenData data, ItemEntity drop, FoodProperties properties) {
        ItemStack source = drop.getItem();
        if (source.isEmpty()) {
            return;
        }
        double currentHunger = entity.getHungerValue();
        double nextHunger = Math.min(FULL_HUNGER, currentHunger + properties.nutrition());
        if (nextHunger <= currentHunger) {
            return;
        }
        ItemStack visualStack = source.copyWithCount(1);
        ItemStack remaining = source.copy();
        remaining.shrink(1);
        if (remaining.isEmpty()) {
            drop.discard();
        } else {
            drop.setItem(remaining);
        }

        CitizenManager manager = CitizenManager.get(level);
        entity.setHunger(nextHunger);
        CitizenJobVisualService.setMainHandOverride(data.uuid(), visualStack);
        runtime(level).visualExpiries.put(data.uuid(), level.getGameTime() + EAT_VISUAL_TICKS);
        manager.syncEntity(entity);
        level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F, 1.0F);
        entity.triggerWorkSwing(InteractionHand.MAIN_HAND);
    }

    /** clearExpiredVisual: 到期后恢复 NPC 原本的职业手持物。 */
    private static void clearExpiredVisual(ServerLevel level, CitizenEntity entity, CitizenData data) {
        Long expiresAt = runtime(level).visualExpiries.get(data.uuid());
        if (expiresAt == null || level.getGameTime() < expiresAt) {
            return;
        }
        if (runtime(level).visualExpiries.remove(data.uuid(), expiresAt)) {
            CitizenJobVisualService.clearMainHandOverride(data.uuid());
            CitizenManager.get(level).syncEntity(entity);
        }
    }

    /** runtime: 按存档和维度隔离运行时缓存，避免跨世界串数据。 */
    private static LevelRuntime runtime(ServerLevel level) {
        return RUNTIMES.computeIfAbsent(SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT), ignored -> new LevelRuntime());
    }

    private static final class LevelRuntime {
        private final ConcurrentMap<UUID, Long> visualExpiries = new ConcurrentHashMap<>();
    }
}
