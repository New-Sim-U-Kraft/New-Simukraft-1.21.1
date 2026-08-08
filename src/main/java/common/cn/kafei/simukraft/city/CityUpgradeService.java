package common.cn.kafei.simukraft.city;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.economy.FinanceLedgerService;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.network.hud.HudSyncService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** CityUpgradeService: 在服务端校验并提交一次城市升级。 */
@SuppressWarnings("null")
public final class CityUpgradeService {
    private CityUpgradeService() {
    }

    /** nextDefinition: 返回城市当前可进入的下一个数据包等级。 */
    public static CityLevelDefinition nextDefinition(CityData city) {
        return city == null ? null : CityLevelDefinitionLoader.INSTANCE.nextLevel(city.cityLevel());
    }

    /** tick: 在服务端主线程完成已到期的城市升级任务并同步解锁结果。 */
    public static void tick(ServerLevel level) {
        if (level == null || SimuSqliteStorage.isDegraded(level)) {
            return;
        }
        long gameTime = level.getGameTime();
        for (CityData city : CityService.allCities(level)) {
            CityUpgradeState pending = city.upgradeState();
            if (!pending.isComplete(gameTime)) {
                continue;
            }
            int previousLevel = city.cityLevel();
            CityUpgradeState completed = city.completeUpgrade(gameTime);
            if (!completed.active()) {
                continue;
            }
            try {
                if (!CityManager.get(level).persistUpgrade(city)) {
                    city.setCityLevel(previousLevel);
                    city.restoreUpgradeState(completed);
                    SimuKraft.LOGGER.error("Rejected completed city upgrade persistence for {}", city.cityId());
                    continue;
                }
            } catch (RuntimeException exception) {
                city.setCityLevel(previousLevel);
                city.restoreUpgradeState(completed);
                SimuKraft.LOGGER.error("Failed to complete city upgrade for {}", city.cityId(), exception);
                continue;
            }
            try {
                CityLevelDefinition definition = CityLevelDefinitionLoader.INSTANCE.definition(completed.targetLevel());
                String displayName = definition == null ? "Lv" + completed.targetLevel() : definition.displayName();
                CityGroupMessageService.successToCity(level, city.cityId(), Component.translatable(
                        "message.simukraft.city_core.upgrade.success", completed.targetLevel(), displayName));
                HudSyncService.syncToCityGroup(level, city.cityId(), true);
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.warn("City upgrade completed but notification failed for {}", city.cityId(), exception);
            }
        }
    }

    /** upgrade: 原子预检资金、人口和背包物品后提交升级。 */
    public static UpgradeResult upgrade(ServerLevel level,
                                        ServerPlayer player,
                                        CityData city,
                                        int expectedCurrentLevel,
                                        int targetLevel) {
        if (level == null || player == null || city == null) {
            return new UpgradeResult(Status.INVALID_CITY, null, null);
        }
        synchronized (city) {
            if (!city.hasPermission(player.getUUID(), CityPermissionLevel.OFFICIAL)) {
                return new UpgradeResult(Status.NO_PERMISSION, null, null);
            }
            if (city.upgradeState().active()) {
                return new UpgradeResult(Status.UPGRADE_IN_PROGRESS, null, null);
            }
            if (city.cityLevel() != expectedCurrentLevel) {
                return new UpgradeResult(Status.STALE_REQUEST, null, null);
            }
            CityLevelDefinition definition = nextDefinition(city);
            if (definition == null) {
                return new UpgradeResult(Status.NO_NEXT_LEVEL, null, null);
            }
            if (definition.level() != targetLevel) {
                return new UpgradeResult(Status.STALE_REQUEST, definition, null);
            }
            if (SimuSqliteStorage.isDegraded(level)) {
                return new UpgradeResult(Status.STORAGE_UNAVAILABLE, definition, null);
            }
            if (city.funds() < definition.requiredFunds()) {
                return new UpgradeResult(Status.NOT_ENOUGH_FUNDS, definition, null);
            }
            int population = CityPopulationStats.snapshot(level, city.cityId()).population();
            if (population < definition.requiredPopulation()) {
                return new UpgradeResult(Status.NOT_ENOUGH_POPULATION, definition, null);
            }
            for (CityLevelDefinition.ItemRequirement requirement : definition.items()) {
                if (countPlayerItems(player, requirement) < requirement.count()) {
                    ResourceLocation missingId = requirement.isTag() ? requirement.itemTag() : requirement.itemId();
                    return new UpgradeResult(Status.NOT_ENOUGH_ITEMS, definition, missingId);
                }
            }

            List<ConsumedItem> consumedItems = planPlayerItems(player, definition.items());
            if (consumedItems == null) {
                return new UpgradeResult(Status.NOT_ENOUGH_ITEMS, definition, null);
            }
            double previousFunds = city.funds();
            CityUpgradeState previousUpgradeState = city.upgradeState();
            if (definition.requiredFunds() > 0.0D && !city.withdrawFunds(definition.requiredFunds())) {
                return new UpgradeResult(Status.NOT_ENOUGH_FUNDS, definition, null);
            }
            applyConsumedItems(player, consumedItems);
            city.beginUpgrade(definition.level(), level.getGameTime(), definition.durationTicks());
            FinanceTransactionData financeTransaction = null;
            List<FinanceTransactionData> evictedFinanceTransactions = List.of();
            if (definition.requiredFunds() > 0.0D) {
                financeTransaction = new FinanceTransactionData(
                        level.getGameTime(),
                        player.getUUID(),
                        player.getGameProfile().getName(),
                        -definition.requiredFunds(),
                        city.funds(),
                        FinanceTransactionData.Type.EXPENSE,
                        "city_upgrade"
                );
                evictedFinanceTransactions = city.addFinanceTransactionTracked(
                        financeTransaction, FinanceLedgerService.MAX_RECORDS_PER_CITY);
            }
            boolean persisted;
            try {
                persisted = CityManager.get(level).persistUpgrade(city);
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.error("Failed to persist started city upgrade for {}", city.cityId(), exception);
                persisted = false;
            }
            if (!persisted) {
                SimuKraft.LOGGER.error("Rejected city upgrade persistence for {} after authoritative validation", city.cityId());
                city.setFunds(previousFunds);
                city.restoreUpgradeState(previousUpgradeState);
                city.rollbackFinanceTransaction(financeTransaction, evictedFinanceTransactions);
                restorePlayerItems(player, consumedItems);
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                return new UpgradeResult(Status.STORAGE_UNAVAILABLE, definition, null);
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return new UpgradeResult(Status.STARTED, definition, null);
        }
    }

    /** countPlayerItems: 按精确物品或物品标签统计玩家背包数量。 */
    private static int countPlayerItems(ServerPlayer player, CityLevelDefinition.ItemRequirement requirement) {
        long count = 0L;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (requirement.matches(stack)) {
                count += stack.getCount();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    /** planPlayerItems: 为多条可能重叠的物品/标签条件预留背包槽位。 */
    private static List<ConsumedItem> planPlayerItems(ServerPlayer player,
                                                      List<CityLevelDefinition.ItemRequirement> requirements) {
        int[] available = new int[player.getInventory().getContainerSize()];
        for (int slot = 0; slot < available.length; slot++) {
            available[slot] = player.getInventory().getItem(slot).getCount();
        }
        List<ConsumedItem> consumedItems = new ArrayList<>();
        for (CityLevelDefinition.ItemRequirement requirement : requirements) {
            int remaining = requirement.count();
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (available[slot] <= 0 || !requirement.matches(stack)) {
                    continue;
                }
                int consumed = Math.min(remaining, available[slot]);
                consumedItems.add(new ConsumedItem(slot, stack.copyWithCount(consumed)));
                available[slot] -= consumed;
                remaining -= consumed;
            }
            if (remaining > 0) {
                return null;
            }
        }
        return List.copyOf(consumedItems);
    }

    /** applyConsumedItems: 将已验证的扣除计划应用到玩家库存。 */
    private static void applyConsumedItems(ServerPlayer player, List<ConsumedItem> consumedItems) {
        for (ConsumedItem consumed : consumedItems) {
            player.getInventory().getItem(consumed.slot()).shrink(consumed.stack().getCount());
        }
    }

    private static void restorePlayerItems(ServerPlayer player, List<ConsumedItem> consumedItems) {
        for (int index = consumedItems.size() - 1; index >= 0; index--) {
            ConsumedItem consumed = consumedItems.get(index);
            ItemStack current = player.getInventory().getItem(consumed.slot());
            ItemStack restored = consumed.stack().copy();
            if (current.isEmpty()) {
                player.getInventory().setItem(consumed.slot(), restored);
            } else if (ItemStack.isSameItemSameComponents(current, restored)
                    && current.getCount() + restored.getCount() <= current.getMaxStackSize()) {
                current.grow(restored.getCount());
            } else {
                player.getInventory().placeItemBackInInventory(restored);
            }
        }
    }

    private record ConsumedItem(int slot, ItemStack stack) {
    }

    public enum Status {
        STARTED,
        INVALID_CITY,
        NO_PERMISSION,
        NO_NEXT_LEVEL,
        NOT_ENOUGH_FUNDS,
        NOT_ENOUGH_POPULATION,
        NOT_ENOUGH_ITEMS,
        STALE_REQUEST,
        STORAGE_UNAVAILABLE,
        UPGRADE_IN_PROGRESS
    }

    /** UpgradeResult: 返回升级状态以及客户端提示所需的目标定义。 */
    public record UpgradeResult(Status status, CityLevelDefinition definition, ResourceLocation missingItemId) {
        public boolean success() {
            return status == Status.STARTED;
        }

        /** started: 判断本次请求是否成功创建了升级任务。 */
        public boolean started() {
            return status == Status.STARTED;
        }
    }
}
