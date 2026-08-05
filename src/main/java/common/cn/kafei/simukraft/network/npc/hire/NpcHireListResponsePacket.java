package common.cn.kafei.simukraft.network.npc.hire;

import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.kafei.simukraft.SimuKraft;
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
public record NpcHireListResponsePacket(BlockPos sourcePos, String sourceType, String role, UUID assignedCitizenId, List<HireCandidate> candidates) implements CustomPacketPayload {
    public static final Type<NpcHireListResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "npc_hire_list_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcHireListResponsePacket> STREAM_CODEC = StreamCodec.of(NpcHireListResponsePacket::encode, NpcHireListResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, NpcHireListResponsePacket packet) {
        buffer.writeBlockPos(packet.sourcePos());
        buffer.writeUtf(packet.sourceType(), 32);
        buffer.writeUtf(packet.role(), 32);
        buffer.writeBoolean(packet.assignedCitizenId() != null);
        if (packet.assignedCitizenId() != null) {
            buffer.writeUUID(packet.assignedCitizenId());
        }
        buffer.writeVarInt(packet.candidates().size());
        for (HireCandidate candidate : packet.candidates()) {
            HireCandidate.STREAM_CODEC.encode(buffer, candidate);
        }
    }

    public static NpcHireListResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos sourcePos = buffer.readBlockPos();
        String sourceType = buffer.readUtf(32);
        String role = buffer.readUtf(32);
        UUID assignedCitizenId = buffer.readBoolean() ? buffer.readUUID() : null;
        int size = buffer.readVarInt();
        List<HireCandidate> candidates = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            candidates.add(HireCandidate.STREAM_CODEC.decode(buffer));
        }
        return new NpcHireListResponsePacket(sourcePos, sourceType, role, assignedCitizenId, candidates);
    }

    public static void handle(NpcHireListResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleNpcHireListResponse(packet));
    }

    public record HireCandidate(UUID citizenId, String name, String gender, int age, double health, double hunger,
                                String skinPath, String currentJob, String workStatus, int skillLevel, int skillXp,
                                int skillMaxLevel) {
        public static final StreamCodec<RegistryFriendlyByteBuf, HireCandidate> STREAM_CODEC = StreamCodec.of(HireCandidate::encode, HireCandidate::decode);

        private static void encode(RegistryFriendlyByteBuf buffer, HireCandidate candidate) {
            buffer.writeUUID(candidate.citizenId());
            buffer.writeUtf(candidate.name(), 64);
            buffer.writeUtf(candidate.gender(), 16);
            buffer.writeVarInt(candidate.age());
            buffer.writeDouble(candidate.health());
            buffer.writeDouble(candidate.hunger());
            buffer.writeUtf(candidate.skinPath(), 256);
            buffer.writeUtf(candidate.currentJob(), 32);
            buffer.writeUtf(candidate.workStatus(), 32);
            buffer.writeVarInt(candidate.skillLevel());
            buffer.writeVarInt(candidate.skillXp());
            buffer.writeVarInt(candidate.skillMaxLevel());
        }

        private static HireCandidate decode(RegistryFriendlyByteBuf buffer) {
            return new HireCandidate(
                    buffer.readUUID(),
                    buffer.readUtf(64),
                    buffer.readUtf(16),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readUtf(256),
                    buffer.readUtf(32),
                    buffer.readUtf(32),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            );
        }
    }
}
