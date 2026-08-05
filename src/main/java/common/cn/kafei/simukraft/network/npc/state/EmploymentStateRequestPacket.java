package common.cn.kafei.simukraft.network.npc.state;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record EmploymentStateRequestPacket(BlockPos sourcePos, String sourceType) implements CustomPacketPayload {
    public static final Type<EmploymentStateRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "employment_state_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EmploymentStateRequestPacket> STREAM_CODEC = StreamCodec.of(EmploymentStateRequestPacket::encode, EmploymentStateRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, EmploymentStateRequestPacket packet) {
        buffer.writeBlockPos(packet.sourcePos());
        buffer.writeUtf(packet.sourceType(), 32);
    }

    public static EmploymentStateRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new EmploymentStateRequestPacket(buffer.readBlockPos(), buffer.readUtf(32));
    }

    public static void handle(EmploymentStateRequestPacket packet, IPayloadContext context) {
        EmploymentStateResponsePacket.handleRequest(packet, context);
    }
}
