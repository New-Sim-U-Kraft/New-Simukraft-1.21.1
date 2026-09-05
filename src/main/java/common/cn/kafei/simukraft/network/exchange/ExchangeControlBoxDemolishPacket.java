package common.cn.kafei.simukraft.network.exchange;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingDemolitionService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.exchange.ExchangeControlBoxService;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** ExchangeControlBoxDemolishPacket: 拆除交易所建筑。 */
public record ExchangeControlBoxDemolishPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ExchangeControlBoxDemolishPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "exchange_control_box_demolish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeControlBoxDemolishPacket> STREAM_CODEC =
            StreamCodec.of(ExchangeControlBoxDemolishPacket::encode, ExchangeControlBoxDemolishPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入坐标。 */
    public static void encode(RegistryFriendlyByteBuf buffer, ExchangeControlBoxDemolishPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode: 读取坐标。 */
    public static ExchangeControlBoxDemolishPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ExchangeControlBoxDemolishPacket(buffer.readBlockPos());
    }

    /** handle: 拆除交易所。 */
    public static void handle(ExchangeControlBoxDemolishPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!player.blockPosition().closerThan(packet.pos(), 16.0D) && !RtsRemoteMenuAccess.hasAccess(player, packet.pos())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.exchange.too_far"));
            return;
        }
        if (!level.getBlockState(packet.pos()).is(ModBlocks.EXCHANGE_CONTROL_BOX.get())) {
            return;
        }
        PlacedBuildingRecord building = ExchangeControlBoxService.resolveBuilding(level, packet.pos());
        if (building == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.exchange.no_building"));
            return;
        }
        if (PlacedBuildingDemolitionService.demolish(level, building)) {
            InfoToastService.success(player, Component.translatable("message.simukraft.exchange.demolished"));
        }
    }
}
