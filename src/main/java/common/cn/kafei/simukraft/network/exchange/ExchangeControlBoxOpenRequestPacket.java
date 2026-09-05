package common.cn.kafei.simukraft.network.exchange;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.economy.CoinDenominations;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.exchange.ExchangeControlBoxService;
import common.cn.kafei.simukraft.exchange.ExchangeMarketClock;
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

import java.util.UUID;

/** ExchangeControlBoxOpenRequestPacket: 打开交易所。 */
public record ExchangeControlBoxOpenRequestPacket(BlockPos pos, String selectedCompanyId) implements CustomPacketPayload {
    public static final Type<ExchangeControlBoxOpenRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "exchange_control_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeControlBoxOpenRequestPacket> STREAM_CODEC =
            StreamCodec.of(ExchangeControlBoxOpenRequestPacket::encode, ExchangeControlBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入坐标和选中公司。 */
    public static void encode(RegistryFriendlyByteBuf buffer, ExchangeControlBoxOpenRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeUtf(packet.selectedCompanyId() != null ? packet.selectedCompanyId() : "", 64);
    }

    /** decode: 读取坐标和选中公司。 */
    public static ExchangeControlBoxOpenRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ExchangeControlBoxOpenRequestPacket(buffer.readBlockPos(), buffer.readUtf(64));
    }

    /** handle: 下发行情快照。 */
    public static void handle(ExchangeControlBoxOpenRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, packet.pos(), packet.selectedCompanyId());
        }
    }

    /** openFor: 校验后发送股市界面。 */
    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        openFor(level, player, pos, "");
    }

    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos, String selectedCompanyId) {
        if (!player.blockPosition().closerThan(pos, 16.0D) && !RtsRemoteMenuAccess.hasAccess(player, pos)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.exchange.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.EXCHANGE_CONTROL_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.exchange.not_found"));
            return;
        }
        PacketDistributor.sendToPlayer(player, snapshot(level, player, pos, selectedCompanyId));
    }

    /** snapshot: 组装股市数据包。 */
    public static ExchangeControlBoxOpenResponsePacket snapshot(ServerLevel level, ServerPlayer player, BlockPos pos, String selectedCompanyId) {
        PlacedBuildingRecord building = ExchangeControlBoxService.resolveBuilding(level, pos);
        CitizenData broker = ExchangeControlBoxService.findAssignedBroker(level, pos);
        UUID cityId = building != null ? building.cityId() : null;
        String statusKey = building == null
                ? "gui.simukraft.exchange.status.no_building"
                : broker == null ? "gui.simukraft.exchange.status.no_broker"
                : ExchangeMarketClock.isOpen(level.getDayTime())
                ? "gui.simukraft.exchange.status.open"
                : "gui.simukraft.exchange.status.closed";
        return new ExchangeControlBoxOpenResponsePacket(
                pos,
                building != null,
                building != null ? building.displayName() : "",
                cityId,
                statusKey,
                broker != null,
                broker != null ? broker.uuid() : null,
                broker != null ? broker.name() : "",
                cityId != null ? EconomyService.getCityBalance(level, cityId) : 0.0D,
                CoinDenominations.countCash(player),
                ExchangeMarketService.marketDay(level),
                ExchangeMarketClock.isOpen(level.getDayTime()),
                ExchangeMarketService.regime(level),
                selectedCompanyId != null ? selectedCompanyId : "",
                ExchangeMarketService.snapshot(level, cityId));
    }
}
