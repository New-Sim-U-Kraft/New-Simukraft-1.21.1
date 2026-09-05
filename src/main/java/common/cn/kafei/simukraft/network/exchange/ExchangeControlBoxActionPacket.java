package common.cn.kafei.simukraft.network.exchange;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.exchange.ExchangeControlBoxService;
import common.cn.kafei.simukraft.exchange.ExchangeMarketService;
import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** ExchangeControlBoxActionPacket: 买入或卖出。 */
public record ExchangeControlBoxActionPacket(BlockPos pos, boolean buy, String companyId, int shares)
        implements CustomPacketPayload {
    public static final Type<ExchangeControlBoxActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "exchange_control_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeControlBoxActionPacket> STREAM_CODEC =
            StreamCodec.of(ExchangeControlBoxActionPacket::encode, ExchangeControlBoxActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入买卖请求。 */
    public static void encode(RegistryFriendlyByteBuf buffer, ExchangeControlBoxActionPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeBoolean(packet.buy());
        buffer.writeUtf(packet.companyId() != null ? packet.companyId() : "", 64);
        buffer.writeVarInt(packet.shares());
    }

    /** decode: 读取买卖请求。 */
    public static ExchangeControlBoxActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ExchangeControlBoxActionPacket(buffer.readBlockPos(), buffer.readBoolean(), buffer.readUtf(64), buffer.readVarInt());
    }

    /** handle: 执行买卖并刷新界面。 */
    public static void handle(ExchangeControlBoxActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!player.blockPosition().closerThan(packet.pos(), 16.0D) && !RtsRemoteMenuAccess.hasAccess(player, packet.pos())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.exchange.too_far"));
            return;
        }
        if (!level.getBlockState(packet.pos()).is(ModBlocks.EXCHANGE_CONTROL_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.exchange.not_found"));
            return;
        }
        if (!ExchangeControlBoxService.isOperational(level, packet.pos())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.exchange.not_open"));
            return;
        }
        PlacedBuildingRecord building = ExchangeControlBoxService.resolveBuilding(level, packet.pos());
        if (building == null || building.cityId() == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.exchange.no_city"));
            return;
        }
        ExchangeMarketService.TradeResult result = packet.buy()
                ? ExchangeMarketService.buy(level, player, building.cityId(), packet.companyId(), packet.shares())
                : ExchangeMarketService.sell(level, player, building.cityId(), packet.companyId(), packet.shares());
        if (result.success()) {
            InfoToastService.success(player, Component.translatable(result.messageKey()));
        } else {
            InfoToastService.warning(player, Component.translatable(result.messageKey()));
        }
        PacketDistributor.sendToPlayer(player, ExchangeControlBoxOpenRequestPacket.snapshot(level, player, packet.pos(), packet.companyId()));
    }
}
