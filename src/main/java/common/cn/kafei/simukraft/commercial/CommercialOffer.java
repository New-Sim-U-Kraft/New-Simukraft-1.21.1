package common.cn.kafei.simukraft.commercial;

import java.util.List;

public record CommercialOffer(String id,
                              CommercialVisibility visibleTo,
                              List<CommercialResource> cost,
                              List<CommercialResource> result,
                              StockRule stock) {
    public CommercialOffer {
        id = id != null && !id.isBlank() ? id.trim() : "offer";
        visibleTo = visibleTo != null ? visibleTo : CommercialVisibility.PLAYER;
        cost = cost != null ? List.copyOf(cost) : List.of();
        result = result != null ? List.copyOf(result) : List.of();
    }

    /** visibleToPlayer: 判断该报价是否进入玩家交易界面。 */
    public boolean visibleToPlayer() {
        return visibleTo.visibleToPlayer();
    }

    /** visibleToNpc: 判断该报价是否允许 NPC 自动经营。 */
    public boolean visibleToNpc() {
        return visibleTo.visibleToNpc();
    }

    /** valid: 判断报价是否具备可执行的成本与产出。 */
    public boolean valid() {
        return !cost.isEmpty()
                && !result.isEmpty()
                && cost.stream().allMatch(CommercialResource::valid)
                && result.stream().allMatch(CommercialResource::valid);
    }

    /** itemLeavesStock: 判断交易结果是否从商店库存取出物品。 */
    public boolean itemLeavesStock() {
        return stock != null && result.stream()
                .anyMatch(resource -> resource.type() == CommercialResource.Type.ITEM && stock.itemId().equals(resource.itemId()));
    }

    /** itemEntersStock: 判断交易成本是否向商店库存放入物品。 */
    public boolean itemEntersStock() {
        return stock != null && cost.stream()
                .anyMatch(resource -> resource.type() == CommercialResource.Type.ITEM && stock.itemId().equals(resource.itemId()));
    }

    /** stockItemAmount: 计算本次交易影响库存物品的数量。 */
    public int stockItemAmount(int multiplier) {
        if (stock == null) {
            return 0;
        }
        int total = 0;
        List<CommercialResource> resources = itemLeavesStock() ? result : cost;
        for (CommercialResource resource : resources) {
            if (resource.type() == CommercialResource.Type.ITEM && stock.itemId().equals(resource.itemId())) {
                total += resource.countFor(multiplier);
            }
        }
        return total;
    }

    public record StockRule(String itemId, int max, int initial, int restockAmount, long restockInterval) {
        public StockRule {
            itemId = itemId != null ? itemId.trim() : "";
            max = Math.max(0, max);
            initial = Math.clamp(initial, 0, max);
            restockAmount = Math.max(0, restockAmount);
            restockInterval = Math.max(0L, restockInterval);
        }

        /** restockEnabled: 判断该库存是否允许按服务器运行 tick 自动补货。 */
        public boolean restockEnabled() {
            return !itemId.isBlank() && max > 0 && restockAmount > 0 && restockInterval > 0L;
        }
    }
}
