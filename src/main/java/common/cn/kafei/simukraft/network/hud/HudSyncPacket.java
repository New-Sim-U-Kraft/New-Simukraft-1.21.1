package common.cn.kafei.simukraft.network.hud;

import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record HudSyncPacket(int currentDay, int worldPopulation, String cityName, double cityFunds, int cityPopulation, CityPermissionLevel permissionLevel, boolean creativeMode) implements CustomPacketPayload {
    public static final Type<HudSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "hud_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HudSyncPacket> STREAM_CODEC = StreamCodec.of(HudSyncPacket::encode, HudSyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, HudSyncPacket packet) {
        buffer.writeInt(packet.currentDay());
        buffer.writeInt(packet.worldPopulation());
        buffer.writeUtf(packet.cityName(), 64);
        buffer.writeDouble(packet.cityFunds());
        buffer.writeInt(packet.cityPopulation());
        buffer.writeUtf(packet.permissionLevel().name(), 16);
        buffer.writeBoolean(packet.creativeMode());
    }

    public static HudSyncPacket decode(RegistryFriendlyByteBuf buffer) {
        return new HudSyncPacket(
                buffer.readInt(),
                buffer.readInt(),
                buffer.readUtf(64),
                buffer.readDouble(),
                buffer.readInt(),
                CityPermissionLevel.fromName(buffer.readUtf(16)),
                buffer.readBoolean()
        );
    }

    public static void handle(HudSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleHudSync(packet));
    }
}