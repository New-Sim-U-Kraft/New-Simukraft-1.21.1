package common.cn.kafei.simukraft.network.commercial;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.commercial.CommercialTradeView;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record CommercialTradeOpenResponsePacket(BlockPos boxPos,
                                                UUID workerId,
                                                String shopName,
                                                String workerName,
                                                double cityBalance,
                                                boolean running,
                                                List<OfferEntry> offers) implements CustomPacketPayload {
    public static final Type<CommercialTradeOpenResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "commercial_trade_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CommercialTradeOpenResponsePacket> STREAM_CODEC = StreamCodec.of(CommercialTradeOpenResponsePacket::encode, CommercialTradeOpenResponsePacket::decode);

    public static CommercialTradeOpenResponsePacket from(CommercialTradeView view) {
        return new CommercialTradeOpenResponsePacket(
                view.boxPos(),
                view.workerId(),
                view.shopName(),
                view.workerName(),
                view.cityBalance(),
                view.running(),
                view.offers().stream()
                        .map(offer -> new OfferEntry(
                                offer.id(),
                                resourceEntries(offer.cost()),
                                resourceEntries(offer.result()),
                                offer.stockItem(),
                                offer.currentStock(),
                                offer.maxStock(),
                                offer.restockInterval(),
                                offer.restockAmount()))
                        .toList()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入 NPC 商业交易视图响应。 */
    public static void encode(RegistryFriendlyByteBuf buffer, CommercialTradeOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.boxPos());
        buffer.writeBoolean(packet.workerId() != null);
        if (packet.workerId() != null) {
            buffer.writeUUID(packet.workerId());
        }
        buffer.writeUtf(packet.shopName(), 128);
        buffer.writeUtf(packet.workerName(), 128);
        buffer.writeDouble(packet.cityBalance());
        buffer.writeBoolean(packet.running());
        buffer.writeVarInt(packet.offers().size());
        for (OfferEntry offer : packet.offers()) {
            offer.encode(buffer);
        }
    }

    /** decode: 读取 NPC 商业交易视图响应。 */
    public static CommercialTradeOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos boxPos = buffer.readBlockPos();
        UUID workerId = buffer.readBoolean() ? buffer.readUUID() : null;
        String shopName = buffer.readUtf(128);
        String workerName = buffer.readUtf(128);
        double cityBalance = buffer.readDouble();
        boolean running = buffer.readBoolean();
        int offerCount = buffer.readVarInt();
        List<OfferEntry> offers = new ArrayList<>();
        for (int i = 0; i < offerCount; i++) {
            offers.add(OfferEntry.decode(buffer));
        }
        return new CommercialTradeOpenResponsePacket(boxPos, workerId, shopName, workerName, cityBalance, running, List.copyOf(offers));
    }

    /** handle: 分发 NPC 商业交易视图到客户端 UI。 */
    public static void handle(CommercialTradeOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleCommercialTradeOpenResponse(packet));
    }

    private static List<ResourceEntry> resourceEntries(List<CommercialTradeView.ResourceEntry> entries) {
        return entries.stream()
                .map(entry -> new ResourceEntry(entry.type(), entry.itemId(), entry.count(), entry.money()))
                .toList();
    }

    public record OfferEntry(String id,
                             List<ResourceEntry> cost,
                             List<ResourceEntry> result,
                             String stockItem,
                             int currentStock,
                             int maxStock,
                             long restockInterval,
                             int restockAmount) {
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(id, 128);
            buffer.writeVarInt(cost.size());
            for (ResourceEntry resource : cost) {
                resource.encode(buffer);
            }
            buffer.writeVarInt(result.size());
            for (ResourceEntry resource : result) {
                resource.encode(buffer);
            }
            buffer.writeUtf(stockItem, 128);
            buffer.writeVarInt(currentStock);
            buffer.writeVarInt(maxStock);
            buffer.writeVarLong(restockInterval);
            buffer.writeVarInt(restockAmount);
        }

        private static OfferEntry decode(RegistryFriendlyByteBuf buffer) {
            String id = buffer.readUtf(128);
            int costCount = buffer.readVarInt();
            List<ResourceEntry> cost = new ArrayList<>();
            for (int i = 0; i < costCount; i++) {
                cost.add(ResourceEntry.decode(buffer));
            }
            int resultCount = buffer.readVarInt();
            List<ResourceEntry> result = new ArrayList<>();
            for (int i = 0; i < resultCount; i++) {
                result.add(ResourceEntry.decode(buffer));
            }
            return new OfferEntry(id, List.copyOf(cost), List.copyOf(result), buffer.readUtf(128), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(), buffer.readVarInt());
        }
    }

    public record ResourceEntry(String type, String itemId, int count, double money) {
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(type, 16);
            buffer.writeUtf(itemId, 128);
            buffer.writeVarInt(count);
            buffer.writeDouble(money);
        }

        private static ResourceEntry decode(RegistryFriendlyByteBuf buffer) {
            return new ResourceEntry(buffer.readUtf(16), buffer.readUtf(128), buffer.readVarInt(), buffer.readDouble());
        }
    }
}
