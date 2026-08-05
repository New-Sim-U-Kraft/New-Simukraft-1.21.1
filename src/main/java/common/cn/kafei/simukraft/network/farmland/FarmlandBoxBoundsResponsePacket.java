package common.cn.kafei.simukraft.network.farmland;

import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FarmlandBoxBoundsResponsePacket(BlockPos pos, boolean hasPlot, BlockPos min, BlockPos max) implements CustomPacketPayload {
    public static final Type<FarmlandBoxBoundsResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "farmland_box_bounds_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FarmlandBoxBoundsResponsePacket> STREAM_CODEC = StreamCodec.of(FarmlandBoxBoundsResponsePacket::encode, FarmlandBoxBoundsResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, FarmlandBoxBoundsResponsePacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeBoolean(packet.hasPlot());
        buffer.writeBlockPos(packet.min());
        buffer.writeBlockPos(packet.max());
    }

    public static FarmlandBoxBoundsResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        return new FarmlandBoxBoundsResponsePacket(buffer.readBlockPos(), buffer.readBoolean(), buffer.readBlockPos(), buffer.readBlockPos());
    }

    public static void handle(FarmlandBoxBoundsResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleFarmlandBoxBoundsResponse(packet));
    }
}
