package common.cn.kafei.simukraft.network.industrial;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
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
public record IndustrialControlBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<IndustrialControlBoxOpenRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "industrial_control_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IndustrialControlBoxOpenRequestPacket> STREAM_CODEC = StreamCodec.of(IndustrialControlBoxOpenRequestPacket::encode, IndustrialControlBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, IndustrialControlBoxOpenRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    public static IndustrialControlBoxOpenRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new IndustrialControlBoxOpenRequestPacket(buffer.readBlockPos());
    }

    public static void handle(IndustrialControlBoxOpenRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, packet.pos());
        }
    }

    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 16.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.build_box.too_far"));
            return;
        }
        PacketDistributor.sendToPlayer(player, IndustrialControlBoxOpenResponsePacket.from(IndustrialControlBoxService.buildView(level, pos)));
    }
}
