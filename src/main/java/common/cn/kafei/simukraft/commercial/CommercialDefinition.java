package common.cn.kafei.simukraft.commercial;

import java.nio.file.Path;
import java.util.List;

public record CommercialDefinition(String id,
                                   String name,
                                   JobDefinition job,
                                   List<CommercialOffer> offers,
                                   Path sourcePath) {
    public CommercialDefinition {
        id = id != null && !id.isBlank() ? id.trim() : "commercial";
        name = name != null && !name.isBlank() ? name.trim() : id;
        job = job != null ? job : new JobDefinition("commercial_worker", "商业员工", "");
        offers = offers != null ? List.copyOf(offers) : List.of();
    }

    /** offerById: 按报价 ID 查找交易项。 */
    public CommercialOffer offerById(String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return null;
        }
        for (CommercialOffer offer : offers) {
            if (offer.id().equals(offerId)) {
                return offer;
            }
        }
        return null;
    }

    /** playerOffers: 获取玩家可见的交易项。 */
    public List<CommercialOffer> playerOffers() {
        return offers.stream().filter(CommercialOffer::visibleToPlayer).toList();
    }

    /** npcOffers: 获取 NPC 可处理的交易项。 */
    public List<CommercialOffer> npcOffers() {
        return offers.stream().filter(CommercialOffer::visibleToNpc).toList();
    }

    public record JobDefinition(String id, String name, String heldItem) {
        public JobDefinition {
            id = id != null && !id.isBlank() ? id.trim() : "commercial_worker";
            name = name != null && !name.isBlank() ? name.trim() : id;
            heldItem = heldItem != null ? heldItem.trim() : "";
        }
    }
}
