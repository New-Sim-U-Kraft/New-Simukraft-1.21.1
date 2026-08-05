package common.cn.kafei.simukraft.commercial;

import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("null")
public final class CommercialTradeSupplyService {
    private static final int MATERIAL_RADIUS_XZ = 5;
    private static final int MATERIAL_RADIUS_Y = 2;

    private CommercialTradeSupplyService() {
    }

    /** validate: 校验商业交易的库存或材料供给是否足够。 */
    public static CommercialTradeService.TradeResult validate(ServerLevel level, BlockPos boxPos, CommercialOffer offer, int times) {
        if (level == null || boxPos == null || offer == null) {
            return CommercialTradeService.TradeResult.fail("message.simukraft.commercial.invalid_trade");
        }
        CommercialTradeService.TradeResult materialValidation = validateMaterials(level, boxPos, offer, times);
        if (!materialValidation.success()) {
            return materialValidation;
        }
        return validateSqliteStock(level, boxPos, offer, times);
    }

    /** apply: 扣减材料并应用 SQLite 库存变化。 */
    public static synchronized boolean apply(ServerLevel level, BlockPos boxPos, CommercialOffer offer, int times) {
        if (level == null || boxPos == null || offer == null) {
            return false;
        }
        if (!consumeMaterials(level, boxPos, offer, times)) {
            return false;
        }
        applySqliteStockDeltas(level, boxPos, stockDeltas(offer, times), offer);
        return true;
    }

    /** availableForOffer: 获取该报价当前可供应次数，供 GUI 或 NPC 选购参考。 */
    public static int availableForOffer(ServerLevel level, BlockPos boxPos, CommercialOffer offer) {
        if (level == null || boxPos == null || offer == null) {
            return 0;
        }
        CommercialOffer.StockRule rule = offer.stock();
        if (usesMaterialSupply(offer)) {
            int available = availableFromMaterials(level, boxPos, rule);
            return rule.max() > 0 ? Math.min(rule.max(), available) : available;
        }
        if (rule != null && rule.sqliteBacked()) {
            CommercialStockData stock = CommercialStockManager.get(level).get(boxPos, rule.itemId());
            return stock != null ? stock.currentStock() : 0;
        }
        return 0;
    }

    /** canSupply: 判断指定次数的交易是否具备供给。 */
    public static boolean canSupply(ServerLevel level, BlockPos boxPos, CommercialOffer offer, int times) {
        return validate(level, boxPos, offer, times).success();
    }

    private static CommercialTradeService.TradeResult validateMaterials(ServerLevel level, BlockPos boxPos, CommercialOffer offer, int times) {
        if (!usesMaterialSupply(offer)) {
            return CommercialTradeService.TradeResult.success("message.simukraft.commercial.ready");
        }
        int multiplier = Math.max(1, times);
        int availableTrades = availableFromMaterials(level, boxPos, offer.stock());
        if (offer.stock().max() > 0) {
            availableTrades = Math.min(offer.stock().max(), availableTrades);
        }
        if (availableTrades < multiplier) {
            return CommercialTradeService.TradeResult.fail("message.simukraft.commercial.insufficient_materials");
        }
        for (CommercialOffer.MaterialRequirement requirement : offer.stock().materials()) {
            if (countMaterial(level, boxPos, requirement.item()) < requirement.countFor(multiplier)) {
                return CommercialTradeService.TradeResult.fail("message.simukraft.commercial.insufficient_materials");
            }
        }
        return CommercialTradeService.TradeResult.success("message.simukraft.commercial.ready");
    }

    private static CommercialTradeService.TradeResult validateSqliteStock(ServerLevel level, BlockPos boxPos, CommercialOffer offer, int times) {
        CommercialStockManager manager = CommercialStockManager.get(level);
        for (Map.Entry<String, Integer> entry : stockDeltas(offer, times).entrySet()) {
            if (isMaterialBackedLeavingDelta(offer, entry.getKey(), entry.getValue())) {
                continue;
            }
            CommercialStockData stock = manager.get(boxPos, entry.getKey());
            if (stock == null) {
                continue;
            }
            long after = stock.currentStock() + (long) entry.getValue();
            if (after < 0L) {
                return CommercialTradeService.TradeResult.fail("message.simukraft.commercial.insufficient_stock");
            }
            if (after > stock.maxStock()) {
                return CommercialTradeService.TradeResult.fail("message.simukraft.commercial.stock_full");
            }
        }
        return CommercialTradeService.TradeResult.success("message.simukraft.commercial.ready");
    }

    private static boolean consumeMaterials(ServerLevel level, BlockPos boxPos, CommercialOffer offer, int times) {
        if (!usesMaterialSupply(offer)) {
            return true;
        }
        int multiplier = Math.max(1, times);
        for (CommercialOffer.MaterialRequirement requirement : offer.stock().materials()) {
            if (!consumeMaterial(level, boxPos, requirement.item(), requirement.countFor(multiplier))) {
                return false;
            }
        }
        return true;
    }

    private static int availableFromMaterials(ServerLevel level, BlockPos boxPos, CommercialOffer.StockRule rule) {
        if (rule == null || rule.materials().isEmpty()) {
            return 0;
        }
        int available = Integer.MAX_VALUE;
        for (CommercialOffer.MaterialRequirement requirement : rule.materials()) {
            int required = Math.max(1, requirement.count());
            available = Math.min(available, countMaterial(level, boxPos, requirement.item()) / required);
        }
        return available == Integer.MAX_VALUE ? 0 : available;
    }

    private static int countMaterial(ServerLevel level, BlockPos boxPos, Item item) {
        if (item == null) {
            return 0;
        }
        int total = 0;
        for (BlockPos container : nearbyContainers(level, boxPos)) {
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (!snapshot.stack().isEmpty() && snapshot.stack().getItem() == item) {
                    total += snapshot.stack().getCount();
                }
            }
        }
        return total;
    }

    private static boolean consumeMaterial(ServerLevel level, BlockPos boxPos, Item item, int amount) {
        int remaining = Math.max(0, amount);
        if (remaining <= 0) {
            return true;
        }
        for (BlockPos container : nearbyContainers(level, boxPos)) {
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, container)) {
                while (remaining > 0
                        && !snapshot.stack().isEmpty()
                        && snapshot.stack().getItem() == item
                        && GenericContainerAccess.consumeSingleItemAtSlot(level, container, snapshot.slot(), snapshot.access(), snapshot.side(), item)) {
                    remaining--;
                }
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<BlockPos> nearbyContainers(ServerLevel level, BlockPos centerPos) {
        Set<BlockPos> containers = new LinkedHashSet<>();
        if (level == null || centerPos == null) {
            return containers;
        }
        for (int dx = -MATERIAL_RADIUS_XZ; dx <= MATERIAL_RADIUS_XZ; dx++) {
            for (int dy = -MATERIAL_RADIUS_Y; dy <= MATERIAL_RADIUS_Y; dy++) {
                for (int dz = -MATERIAL_RADIUS_XZ; dz <= MATERIAL_RADIUS_XZ; dz++) {
                    BlockPos candidate = centerPos.offset(dx, dy, dz);
                    if (GenericContainerAccess.isContainer(level, candidate)) {
                        containers.add(GenericContainerAccess.canonicalContainerPos(level, candidate));
                    }
                }
            }
        }
        return containers;
    }

    private static void applySqliteStockDeltas(ServerLevel level, BlockPos boxPos, Map<String, Integer> deltas, CommercialOffer offer) {
        CommercialStockManager manager = CommercialStockManager.get(level);
        for (Map.Entry<String, Integer> entry : deltas.entrySet()) {
            if (isMaterialBackedLeavingDelta(offer, entry.getKey(), entry.getValue())) {
                continue;
            }
            CommercialStockData stock = manager.get(boxPos, entry.getKey());
            int delta = entry.getValue();
            if (stock == null || delta == 0) {
                continue;
            }
            if (delta > 0) {
                stock.add(delta);
            } else {
                stock.remove(-delta);
            }
            manager.persist(stock);
        }
    }

    private static Map<String, Integer> stockDeltas(CommercialOffer offer, int times) {
        Map<String, Integer> deltas = new LinkedHashMap<>();
        int multiplier = Math.max(1, times);
        for (CommercialResource resource : offer.cost()) {
            if (resource.type() == CommercialResource.Type.ITEM) {
                deltas.merge(resource.itemId(), resource.countFor(multiplier), Integer::sum);
            }
        }
        for (CommercialResource resource : offer.result()) {
            if (resource.type() == CommercialResource.Type.ITEM) {
                deltas.merge(resource.itemId(), -resource.countFor(multiplier), Integer::sum);
            }
        }
        deltas.entrySet().removeIf(entry -> entry.getValue() == 0);
        return deltas;
    }

    private static boolean usesMaterialSupply(CommercialOffer offer) {
        return offer.stock() != null && offer.stock().materialBacked() && offer.itemLeavesStock();
    }

    private static boolean isMaterialBackedLeavingDelta(CommercialOffer offer, String itemId, int delta) {
        return delta < 0
                && usesMaterialSupply(offer)
                && offer.stock().itemId().equals(itemId);
    }
}
