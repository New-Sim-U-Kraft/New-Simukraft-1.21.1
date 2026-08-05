package common.cn.kafei.simukraft.network.building;

import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record BuildingCacheReloadPacket() implements CustomPacketPayload {
    public static final Type<BuildingCacheReloadPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("simukraft", "building_cache_reload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildingCacheReloadPacket> STREAM_CODEC = StreamCodec.unit(new BuildingCacheReloadPacket());

    @Override
    public Type<BuildingCacheReloadPacket> type() {
        return TYPE;
    }

    public static void handle(BuildingCacheReloadPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleBuildingCacheReload(packet));
    }
}
