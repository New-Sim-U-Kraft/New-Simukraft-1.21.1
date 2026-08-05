package common.cn.kafei.simukraft.network.city.member;

import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.network.city.CityNetworkViewFactory;
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
public record CityCoreMembersResponsePacket(BlockPos pos, UUID cityId, String cityName, double funds, int cityLevel, List<MemberEntry> members, List<CandidateEntry> onlineCandidates, CityPermissionLevel viewerPermission, boolean canManageCity) implements CustomPacketPayload {
    public static final Type<CityCoreMembersResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_core_members_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityCoreMembersResponsePacket> STREAM_CODEC = StreamCodec.of(CityCoreMembersResponsePacket::encode, CityCoreMembersResponsePacket::decode);

    public CityCoreMembersResponsePacket {
        members = members == null ? List.of() : List.copyOf(members);
        onlineCandidates = onlineCandidates == null ? List.of() : List.copyOf(onlineCandidates);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static CityCoreMembersResponsePacket from(BlockPos pos, CityData city, UUID viewerId) {
        return CityNetworkViewFactory.buildMembersResponse(pos, city, viewerId);
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CityCoreMembersResponsePacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeUUID(packet.cityId());
        buffer.writeUtf(packet.cityName(), 64);
        buffer.writeDouble(packet.funds());
        buffer.writeInt(packet.cityLevel());
        buffer.writeVarInt(packet.members().size());
        packet.members().forEach(member -> {
            buffer.writeUUID(member.playerId());
            buffer.writeUtf(member.playerName(), 64);
            buffer.writeUtf(member.permissionLevel().name(), 16);
        });
        buffer.writeVarInt(packet.onlineCandidates().size());
        packet.onlineCandidates().forEach(candidate -> {
            buffer.writeUUID(candidate.playerId());
            buffer.writeUtf(candidate.playerName(), 64);
        });
        buffer.writeUtf(packet.viewerPermission().name(), 16);
        buffer.writeBoolean(packet.canManageCity());
    }

    public static CityCoreMembersResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UUID cityId = buffer.readUUID();
        String cityName = buffer.readUtf(64);
        double funds = buffer.readDouble();
        int cityLevel = buffer.readInt();
        int size = buffer.readVarInt();
        List<MemberEntry> members = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            members.add(new MemberEntry(buffer.readUUID(), buffer.readUtf(64), CityPermissionLevel.fromName(buffer.readUtf(16))));
        }
        int candidateSize = buffer.readVarInt();
        List<CandidateEntry> candidates = new ArrayList<>(candidateSize);
        for (int i = 0; i < candidateSize; i++) {
            candidates.add(new CandidateEntry(buffer.readUUID(), buffer.readUtf(64)));
        }
        return new CityCoreMembersResponsePacket(pos, cityId, cityName, funds, cityLevel, members, candidates, CityPermissionLevel.fromName(buffer.readUtf(16)), buffer.readBoolean());
    }

    public static void handle(CityCoreMembersResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleCityCoreMembersResponse(packet));
    }

    public record MemberEntry(UUID playerId, String playerName, CityPermissionLevel permissionLevel) {
    }

    public record CandidateEntry(UUID playerId, String playerName) {
    }
}
