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
public record LogisticsClientBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<LogisticsClientBoxOpenRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "logistics_client_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsClientBoxOpenRequestPacket> STREAM_CODEC = StreamCodec.of(LogisticsClientBoxOpenRequestPacket::encode, LogisticsClientBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, LogisticsClientBoxOpenRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    public static LogisticsClientBoxOpenRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new LogisticsClientBoxOpenRequestPacket(buffer.readBlockPos());
    }

    public static void handle(LogisticsClientBoxOpenRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, packet.pos());
        }
    }

    /** openFor: 校验物流客户端盒并发送界面快照。 */
    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 16.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.LOGISTICS_CLIENT_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.client_not_found"));
            return;
        }
        PacketDistributor.sendToPlayer(player, LogisticsClientBoxOpenResponsePacket.from(LogisticsControlBoxService.buildClientView(level, pos)));
    }
}
