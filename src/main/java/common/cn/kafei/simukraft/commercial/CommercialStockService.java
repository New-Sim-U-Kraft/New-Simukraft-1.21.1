package common.cn.kafei.simukraft.commercial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CommercialStockService {
    private CommercialStockService() {
    }

    /** ensureStock: 确保商业定义中的库存条目已经初始化。 */
    public static void ensureStock(ServerLevel level, BlockPos boxPos, CommercialDefinition definition) {
        if (level == null || boxPos == null || definition == null) {
            return;
        }
        CommercialStockManager manager = CommercialStockManager.get(level);
        long gameTime = level.getGameTime();
        for (CommercialOffer.StockRule rule : uniqueStockRules(definition).values()) {
            CommercialStockData stock = manager.getOrCreate(boxPos, rule, gameTime);
            boolean changed = false;
            if (stock.maxStock() != rule.max()) {
                stock.setMaxStock(rule.max());
                changed = true;
            }
            if (stock.lastRestockGameTime() <= 0L) {
                stock.setLastRestockGameTime(gameTime);
                changed = true;
            }
            if (changed) {
                manager.persist(stock);
            }
        }
    }

    /** restock: 按服务器运行 tick 间隔补货。 */
    public static void restock(ServerLevel level, BlockPos boxPos, CommercialDefinition definition) {
        if (level == null || boxPos == null || definition == null) {
            return;
        }
        ensureStock(level, boxPos, definition);
        CommercialStockManager manager = CommercialStockManager.get(level);
        long gameTime = level.getGameTime();
        for (CommercialOffer.StockRule rule : uniqueStockRules(definition).values()) {
            if (!rule.restockEnabled()) {
                continue;
            }
            CommercialStockData stock = manager.get(boxPos, rule.itemId());
            if (stock == null) {
                continue;
            }
            long lastRestock = stock.lastRestockGameTime();
            if (gameTime < lastRestock) {
                stock.setLastRestockGameTime(gameTime);
                manager.persist(stock);
                continue;
            }
            long elapsed = gameTime - lastRestock;
            long passed = elapsed / rule.restockInterval();
            if (passed <= 0L) {
                continue;
            }
            int added = stock.add(safeRestockAmount(passed, rule.restockAmount()));
            stock.setLastRestockGameTime(lastRestock + passed * rule.restockInterval());
            if (added > 0 || passed > 0) {
                manager.persist(stock);
            }
        }
    }

    /** snapshot: 获取当前商业箱库存快照。 */
    public static Map<String, CommercialStockData> snapshot(ServerLevel level, BlockPos boxPos) {
        return level == null || boxPos == null ? Map.of() : CommercialStockManager.get(level).allAt(boxPos);
    }

    /** removeBox: 删除商业箱全部库存。 */
    public static void removeBox(ServerLevel level, BlockPos boxPos) {
        if (level != null && boxPos != null) {
            CommercialStockManager.get(level).removeBox(boxPos);
        }
    }

    private static Map<String, CommercialOffer.StockRule> uniqueStockRules(CommercialDefinition definition) {
        Map<String, CommercialOffer.StockRule> rules = new LinkedHashMap<>();
        for (CommercialOffer offer : definition.offers()) {
            CommercialOffer.StockRule rule = offer.stock();
            if (rule != null && !rule.itemId().isBlank() && rule.max() > 0) {
                rules.putIfAbsent(rule.itemId(), rule);
            }
        }
        return rules;
    }

    private static int safeRestockAmount(long passed, int amount) {
        long total = passed * (long) amount;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }
}
