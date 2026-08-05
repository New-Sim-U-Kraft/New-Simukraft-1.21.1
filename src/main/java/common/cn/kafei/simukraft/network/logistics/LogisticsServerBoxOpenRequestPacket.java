package common.cn.kafei.simukraft.network.logistics;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
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

@SuppressWarnings("null")
public record LogisticsServerBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<LogisticsServerBoxOpenRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "logistics_server_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsServerBoxOpenRequestPacket> STREAM_CODEC = StreamCodec.of(LogisticsServerBoxOpenRequestPacket::encode, LogisticsServerBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, LogisticsServerBoxOpenRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    public static LogisticsServerBoxOpenRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new LogisticsServerBoxOpenRequestPacket(buffer.readBlockPos());
    }

    public static void handle(LogisticsServerBoxOpenRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, packet.pos());
        }
    }

    /** openFor: 校验物流服务器盒并发送界面快照。 */
    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 16.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.LOGISTICS_SERVER_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.server_not_found"));
            return;
        }
        PacketDistributor.sendToPlayer(player, LogisticsServerBoxOpenResponsePacket.from(LogisticsControlBoxService.buildServerView(level, pos)));
    }
}
