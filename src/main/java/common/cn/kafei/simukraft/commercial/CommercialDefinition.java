package common.cn.kafei.simukraft.commercial;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

@SuppressWarnings("null")
public record CommercialDefinition(String id,
                                   String name,
                                   JobDefinition job,
                                   WorkTime workTime,
                                   Map<String, ContainerDefinition> containers,
                                   List<CommercialOffer> offers,
                                   Path sourcePath) {
    public CommercialDefinition {
        id = id != null && !id.isBlank() ? id.trim() : "commercial";
        name = name != null && !name.isBlank() ? name.trim() : id;
        job = job != null ? job : new JobDefinition("commercial_worker", "商业员工", "");
        workTime = workTime != null ? workTime : WorkTime.always();
        containers = containers != null ? Map.copyOf(containers) : Map.of();
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

    public record ContainerDefinition(String id, String type, List<BlockPos> positions) {
        public ContainerDefinition {
            id = id != null && !id.isBlank() ? id.trim() : "container";
            type = type != null && !type.isBlank() ? type.trim() : "structure_pos";
            positions = positions != null
                    ? positions.stream().filter(pos -> pos != null).map(BlockPos::immutable).distinct().toList()
                    : List.of();
        }
    }

    public record WorkTime(int start, int end) {
        /** always: 创建全天营业时间。 */
        public static WorkTime always() {
            return new WorkTime(0, 0);
        }

        /** openAt: 判断指定 MC 日内时间是否处于营业时间。 */
        public boolean openAt(long dayTime) {
            if (start == end) {
                return true;
            }
            int current = (int) Math.floorMod(dayTime, 24000L);
            int safeStart = Math.floorMod(start, 24000);
            int safeEnd = Math.floorMod(end, 24000);
            if (safeStart < safeEnd) {
                return current >= safeStart && current < safeEnd;
            }
            return current >= safeStart || current < safeEnd;
        }
    }
}
