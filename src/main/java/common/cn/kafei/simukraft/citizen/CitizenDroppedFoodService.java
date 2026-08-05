package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;

@SuppressWarnings("null")
public final class CitizenDroppedFoodService {
    private static final double EAT_RADIUS = 2.0D;
    private static final double EAT_DISTANCE_SQR = EAT_RADIUS * EAT_RADIUS;
    private static final long SCAN_INTERVAL_TICKS = 5L;

    private CitizenDroppedFoodService() {
    }

    /** tryEatNearbyFood: 让未吃饱的已加载 NPC 消耗附近一份地面食物。 */
    public static void tryEatNearbyFood(ServerLevel level, CitizenEntity entity, CitizenData data) {
        if (level == null || level.isClientSide() || entity == null || data == null || data.dead()) {
            return;
        }
        CitizenFoodConsumptionService.clearExpiredVisual(level, entity, data);
        if (entity.getHungerValue() >= CitizenFoodConsumptionService.FULL_HUNGER || !shouldScan(entity, level.getGameTime())) {
            return;
        }
        ItemEntity foodDrop = nearestFoodDrop(level, entity, data);
        if (foodDrop == null) {
            return;
        }
        FoodProperties properties = CitizenFoodConsumptionService.foodProperties(entity, foodDrop.getItem());
        if (properties == null || properties.nutrition() <= 0) {
            return;
        }
        consumeDrop(level, entity, data, foodDrop, properties);
    }

    /** clearServerCaches: 清理指定服务器存档下的投喂视觉缓存。 */
    public static void clearServerCaches(MinecraftServer server) {
        CitizenFoodConsumptionService.clearServerCaches(server);
    }

    /** shouldScan: 按 UUID 分散扫描 tick，避免大量 NPC 同时查找掉落物。 */
    private static boolean shouldScan(CitizenEntity entity, long gameTime) {
        return Math.floorMod(entity.getUUID().getLeastSignificantBits(), SCAN_INTERVAL_TICKS) == gameTime % SCAN_INTERVAL_TICKS;
    }

    /** isProtectingWorkProduct: 工作中只保护无主掉落物，玩家丢出的食物仍视为投喂。 */
    private static boolean isProtectingWorkProduct(CitizenData data, ItemEntity drop) {
        return data.workStatusType() == CitizenWorkStatus.WORKING && !isPlayerThrownDrop(drop);
    }

    /** isPlayerThrownDrop: 判断掉落物是否来自玩家主动丢弃。 */
    private static boolean isPlayerThrownDrop(ItemEntity drop) {
        Entity owner = drop != null ? drop.getOwner() : null;
        return owner instanceof Player;
    }

    /** nearestFoodDrop: 查找 NPC 身边最近的可食用掉落物。 */
    private static ItemEntity nearestFoodDrop(ServerLevel level, CitizenEntity entity, CitizenData data) {
        return level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(EAT_RADIUS), drop -> canEatDrop(entity, data, drop))
                .stream()
                .min(Comparator.comparingDouble(drop -> drop.distanceToSqr(entity)))
                .orElse(null);
    }

    /** canEatDrop: 过滤已移除、过远或不可食用的掉落物。 */
    private static boolean canEatDrop(CitizenEntity entity, CitizenData data, ItemEntity drop) {
        if (drop == null || drop.isRemoved() || !drop.isAlive() || drop.distanceToSqr(entity) > EAT_DISTANCE_SQR) {
            return false;
        }
        if (isProtectingWorkProduct(data, drop)) {
            return false;
        }
        return CitizenFoodConsumptionService.canEatStack(entity, drop.getItem());
    }

    /** consumeDrop: 消耗一份食物并同步 NPC 饥饿值和吃饭表现。 */
    private static void consumeDrop(ServerLevel level, CitizenEntity entity, CitizenData data, ItemEntity drop, FoodProperties properties) {
        ItemStack source = drop.getItem();
        if (source.isEmpty()) {
            return;
        }
        ItemStack visualStack = source.copyWithCount(1);
        if (!CitizenFoodConsumptionService.applyFood(level, entity, data, visualStack, properties)) {
            return;
        }
        ItemStack remaining = source.copy();
        remaining.shrink(1);
        if (remaining.isEmpty()) {
            drop.discard();
        } else {
            drop.setItem(remaining);
        }
    }
}
