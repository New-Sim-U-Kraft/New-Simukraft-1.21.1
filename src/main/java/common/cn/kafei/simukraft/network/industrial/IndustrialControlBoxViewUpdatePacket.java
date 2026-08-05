package common.cn.kafei.simukraft.network.industrial;

import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record IndustrialControlBoxViewUpdatePacket(IndustrialControlBoxOpenResponsePacket view) implements CustomPacketPayload {
    public static final Type<IndustrialControlBoxViewUpdatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "industrial_control_box_view_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IndustrialControlBoxViewUpdatePacket> STREAM_CODEC = StreamCodec.of(IndustrialControlBoxViewUpdatePacket::encode, IndustrialControlBoxViewUpdatePacket::decode);

    public static IndustrialControlBoxViewUpdatePacket from(IndustrialControlBoxView view) {
        return new IndustrialControlBoxViewUpdatePacket(IndustrialControlBoxOpenResponsePacket.from(view));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, IndustrialControlBoxViewUpdatePacket packet) {
        IndustrialControlBoxOpenResponsePacket.encode(buffer, packet.view());
    }

    public static IndustrialControlBoxViewUpdatePacket decode(RegistryFriendlyByteBuf buffer) {
        return new IndustrialControlBoxViewUpdatePacket(IndustrialControlBoxOpenResponsePacket.decode(buffer));
    }

    public static void handle(IndustrialControlBoxViewUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleIndustrialControlBoxViewUpdate(packet));
    }
}
