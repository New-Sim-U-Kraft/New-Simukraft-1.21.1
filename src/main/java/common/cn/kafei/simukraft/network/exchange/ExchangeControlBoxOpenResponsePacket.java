package common.cn.kafei.simukraft.network.exchange;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.exchange.ExchangeCandle;
import common.cn.kafei.simukraft.exchange.ExchangeMarketRegime;
import common.cn.kafei.simukraft.exchange.ExchangeMarketService;
import common.cn.kafei.simukraft.exchange.ExchangeQuote;
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

/** ExchangeControlBoxOpenResponsePacket: 股市界面快照。 */
public record ExchangeControlBoxOpenResponsePacket(BlockPos boxPos,
                                                   boolean hasBuilding,
                                                   String buildingName,
                                                   UUID cityId,
                                                   String statusKey,
                                                   boolean hasBroker,
                                                   UUID brokerId,
                                                   String brokerName,
                                                   double cityFunds,
                                                   double playerCash,
                                                   long marketDay,
                                                   boolean marketOpen,
                                                   ExchangeMarketRegime regime,
                                                   String selectedCompanyId,
                                                   List<ExchangeQuote> quotes) implements CustomPacketPayload {
    public static final Type<ExchangeControlBoxOpenResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "exchange_control_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeControlBoxOpenResponsePacket> STREAM_CODEC =
            StreamCodec.of(ExchangeControlBoxOpenResponsePacket::encode, ExchangeControlBoxOpenResponsePacket::decode);

    public ExchangeControlBoxOpenResponsePacket {
        quotes = quotes != null ? List.copyOf(quotes) : List.of();
        buildingName = buildingName != null ? buildingName : "";
        statusKey = statusKey != null ? statusKey : "";
        brokerName = brokerName != null ? brokerName : "";
        selectedCompanyId = selectedCompanyId != null ? selectedCompanyId : "";
        regime = regime != null ? regime : ExchangeMarketRegime.MIXED;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入股市快照。 */
    public static void encode(RegistryFriendlyByteBuf buffer, ExchangeControlBoxOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.boxPos());
        buffer.writeBoolean(packet.hasBuilding());
        buffer.writeUtf(packet.buildingName(), 256);
        buffer.writeBoolean(packet.cityId() != null);
        if (packet.cityId() != null) {
            buffer.writeUUID(packet.cityId());
        }
        buffer.writeUtf(packet.statusKey(), 256);
        boolean hasBroker = packet.hasBroker() && packet.brokerId() != null;
        buffer.writeBoolean(hasBroker);
        if (hasBroker) {
            buffer.writeUUID(packet.brokerId());
        }
        buffer.writeUtf(packet.brokerName(), 256);
        buffer.writeDouble(packet.cityFunds());
        buffer.writeDouble(packet.playerCash());
        buffer.writeVarLong(packet.marketDay());
        buffer.writeBoolean(packet.marketOpen());
        buffer.writeEnum(packet.regime());
        buffer.writeUtf(packet.selectedCompanyId(), 64);
        buffer.writeVarInt(packet.quotes().size());
        for (ExchangeQuote quote : packet.quotes()) {
            buffer.writeUtf(quote.id(), 64);
            buffer.writeUtf(quote.displayName(), 64);
            buffer.writeDouble(quote.price());
            buffer.writeDouble(quote.previousClose());
            buffer.writeVarInt(quote.volume());
            buffer.writeVarInt(quote.sharesHeld());
            buffer.writeDouble(quote.costBasis());
            buffer.writeVarInt(quote.candles().size());
            for (ExchangeCandle candle : quote.candles()) {
                buffer.writeVarLong(candle.marketDay());
                buffer.writeVarInt(candle.hourIndex());
                buffer.writeDouble(candle.open());
                buffer.writeDouble(candle.high());
                buffer.writeDouble(candle.low());
                buffer.writeDouble(candle.close());
                buffer.writeVarInt(candle.volume());
            }
        }
    }

    /** decode: 读取股市快照。 */
    public static ExchangeControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos boxPos = buffer.readBlockPos();
        boolean hasBuilding = buffer.readBoolean();
        String buildingName = buffer.readUtf(256);
        UUID cityId = buffer.readBoolean() ? buffer.readUUID() : null;
        String statusKey = buffer.readUtf(256);
        boolean hasBroker = buffer.readBoolean();
        UUID brokerId = hasBroker ? buffer.readUUID() : null;
        String brokerName = buffer.readUtf(256);
        double cityFunds = buffer.readDouble();
        double playerCash = buffer.readDouble();
        long marketDay = buffer.readVarLong();
        boolean marketOpen = buffer.readBoolean();
        ExchangeMarketRegime regime = buffer.readEnum(ExchangeMarketRegime.class);
        String selected = buffer.readUtf(64);
        int quoteCount = Math.min(32, Math.max(0, buffer.readVarInt()));
        List<ExchangeQuote> quotes = new ArrayList<>(quoteCount);
        for (int i = 0; i < quoteCount; i++) {
            String id = buffer.readUtf(64);
            String name = buffer.readUtf(64);
            double price = buffer.readDouble();
            double previous = buffer.readDouble();
            int volume = buffer.readVarInt();
            int shares = buffer.readVarInt();
            double basis = buffer.readDouble();
            int candleCount = Math.min(ExchangeMarketService.HISTORY_CANDLES, Math.max(0, buffer.readVarInt()));
            List<ExchangeCandle> candles = new ArrayList<>(candleCount);
            for (int c = 0; c < candleCount; c++) {
                candles.add(new ExchangeCandle(buffer.readVarLong(), buffer.readVarInt(), buffer.readDouble(),
                        buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readVarInt()));
            }
            quotes.add(new ExchangeQuote(id, name, price, previous, volume, shares, basis, candles));
        }
        return new ExchangeControlBoxOpenResponsePacket(boxPos, hasBuilding, buildingName, cityId, statusKey,
                hasBroker, brokerId, brokerName, cityFunds, playerCash, marketDay, marketOpen, regime, selected, quotes);
    }

    /** handle: 打开客户端股市界面。 */
    public static void handle(ExchangeControlBoxOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleExchangeControlBoxOpenResponse(packet));
    }
}
