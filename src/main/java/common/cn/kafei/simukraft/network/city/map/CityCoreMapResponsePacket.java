package common.cn.kafei.simukraft.network.city.map;

import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record CityCoreMapResponsePacket(BlockPos pos, UUID cityId, String cityName, double funds, int cityLevel, int memberCount, CityPermissionLevel permissionLevel, boolean canManageCity, int centerChunkX, int centerChunkZ, List<ChunkEntry> chunks) implements CustomPacketPayload {
    public static final Type<CityCoreMapResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_core_map_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityCoreMapResponsePacket> STREAM_CODEC = StreamCodec.of(CityCoreMapResponsePacket::encode, CityCoreMapResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CityCoreMapResponsePacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeUUID(packet.cityId());
        buffer.writeUtf(packet.cityName(), 64);
        buffer.writeDouble(packet.funds());
        buffer.writeInt(packet.cityLevel());
        buffer.writeInt(packet.memberCount());
        buffer.writeUtf(packet.permissionLevel().name(), 16);
        buffer.writeBoolean(packet.canManageCity());
        buffer.writeInt(packet.centerChunkX());
        buffer.writeInt(packet.centerChunkZ());
        buffer.writeVarInt(packet.chunks().size());
        packet.chunks().forEach(chunk -> {
            buffer.writeInt(chunk.chunkX());
            buffer.writeInt(chunk.chunkZ());
        });
    }

    public static CityCoreMapResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UUID cityId = buffer.readUUID();
        String cityName = buffer.readUtf(64);
        double funds = buffer.readDouble();
        int cityLevel = buffer.readInt();
        int memberCount = buffer.readInt();
        CityPermissionLevel permissionLevel = CityPermissionLevel.fromName(buffer.readUtf(16));
        boolean canManageCity = buffer.readBoolean();
        int centerChunkX = buffer.readInt();
        int centerChunkZ = buffer.readInt();
        int size = buffer.readVarInt();
        List<ChunkEntry> chunks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            chunks.add(new ChunkEntry(buffer.readInt(), buffer.readInt()));
        }
        return new CityCoreMapResponsePacket(pos, cityId, cityName, funds, cityLevel, memberCount, permissionLevel, canManageCity, centerChunkX, centerChunkZ, List.copyOf(chunks));
    }

    public static void handle(CityCoreMapResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleCityCoreMapResponse(packet));
    }

    public record ChunkEntry(int chunkX, int chunkZ) {
    }
}
