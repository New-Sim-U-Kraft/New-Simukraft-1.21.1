package common.cn.kafei.simukraft.network.building.controlbox;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.controlbox.ResidentialControlBoxView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record ResidentialControlBoxViewUpdatePacket(ResidentialControlBoxOpenResponsePacket view) implements CustomPacketPayload {
    public static final Type<ResidentialControlBoxViewUpdatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "residential_control_box_view_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResidentialControlBoxViewUpdatePacket> STREAM_CODEC = StreamCodec.of(ResidentialControlBoxViewUpdatePacket::encode, ResidentialControlBoxViewUpdatePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ResidentialControlBoxViewUpdatePacket from(ResidentialControlBoxView view) {
        return new ResidentialControlBoxViewUpdatePacket(ResidentialControlBoxOpenResponsePacket.from(view));
    }

    public static void encode(RegistryFriendlyByteBuf buffer, ResidentialControlBoxViewUpdatePacket packet) {
        ResidentialControlBoxOpenResponsePacket.encode(buffer, packet.view());
    }

    public static ResidentialControlBoxViewUpdatePacket decode(RegistryFriendlyByteBuf buffer) {
        return new ResidentialControlBoxViewUpdatePacket(ResidentialControlBoxOpenResponsePacket.decode(buffer));
    }

    public static void handle(ResidentialControlBoxViewUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> client.cn.kafei.simukraft.client.controlbox.ResidentialControlBoxScreenOpener.refreshIfOpen(packet.view()));
    }
}
