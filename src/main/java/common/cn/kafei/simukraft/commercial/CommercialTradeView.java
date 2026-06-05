package common.cn.kafei.simukraft.commercial;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public record CommercialTradeView(BlockPos boxPos,
                                  UUID workerId,
                                  String shopName,
                                  String workerName,
                                  double cityBalance,
                                  boolean running,
                                  List<OfferEntry> offers) {
    public record OfferEntry(String id,
                             List<ResourceEntry> cost,
                             List<ResourceEntry> result,
                             String stockItem,
                             int currentStock,
                             int maxStock,
                             long restockInterval,
                             int restockAmount) {
    }

    public record ResourceEntry(String type, String itemId, int count, double money) {
    }
}
