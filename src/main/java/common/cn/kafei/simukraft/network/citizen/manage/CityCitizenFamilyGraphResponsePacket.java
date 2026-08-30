package common.cn.kafei.simukraft.network.citizen.manage;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.family.CitizenFamilyGraphSnapshot;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 五代直系关系图快照（服务端 -> 客户端）。 */
public record CityCitizenFamilyGraphResponsePacket(BlockPos pos, CitizenFamilyGraphSnapshot snapshot)
        implements CustomPacketPayload {
    public static final Type<CityCitizenFamilyGraphResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_citizen_family_graph_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityCitizenFamilyGraphResponsePacket> STREAM_CODEC =
            StreamCodec.of(CityCitizenFamilyGraphResponsePacket::encode, CityCitizenFamilyGraphResponsePacket::decode);

    public CityCitizenFamilyGraphResponsePacket {
        snapshot = snapshot != null ? snapshot : CitizenFamilyGraphSnapshot.empty();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CityCitizenFamilyGraphResponsePacket packet) {
        buffer.writeBlockPos(packet.pos());
        CitizenFamilyGraphSnapshot snapshot = packet.snapshot();
        buffer.writeUUID(snapshot.focusId());
        buffer.writeUtf(snapshot.focusName(), 64);
        buffer.writeVarInt(snapshot.nodes().size());
        for (CitizenFamilyGraphSnapshot.Node node : snapshot.nodes()) {
            buffer.writeUUID(node.citizenId());
            buffer.writeUtf(node.name(), 64);
            buffer.writeUtf(node.gender(), 16);
            buffer.writeVarInt(node.age());
            buffer.writeBoolean(node.dead());
            buffer.writeBoolean(node.focus());
            buffer.writeUtf(node.skinPath(), 256);
            buffer.writeUtf(node.jobKey(), 64);
            buffer.writeUtf(node.relationKey(), 64);
            buffer.writeVarInt(node.generation());
            buffer.writeBoolean(node.spouseId() != null);
            if (node.spouseId() != null) {
                buffer.writeUUID(node.spouseId());
            }
        }
        buffer.writeVarInt(snapshot.links().size());
        for (CitizenFamilyGraphSnapshot.Link link : snapshot.links()) {
            buffer.writeUUID(link.parentId());
            buffer.writeUUID(link.childId());
        }
    }

    public static CityCitizenFamilyGraphResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UUID focusId = buffer.readUUID();
        String focusName = buffer.readUtf(64);
        int nodeCount = buffer.readVarInt();
        List<CitizenFamilyGraphSnapshot.Node> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            UUID id = buffer.readUUID();
            String name = buffer.readUtf(64);
            String gender = buffer.readUtf(16);
            int age = buffer.readVarInt();
            boolean dead = buffer.readBoolean();
            boolean focus = buffer.readBoolean();
            String skinPath = buffer.readUtf(256);
            String jobKey = buffer.readUtf(64);
            String relationKey = buffer.readUtf(64);
            int generation = buffer.readVarInt();
            UUID spouseId = buffer.readBoolean() ? buffer.readUUID() : null;
            nodes.add(new CitizenFamilyGraphSnapshot.Node(
                    id, name, gender, age, dead, focus, skinPath, jobKey, relationKey, generation, spouseId));
        }
        int linkCount = buffer.readVarInt();
        List<CitizenFamilyGraphSnapshot.Link> links = new ArrayList<>(linkCount);
        for (int i = 0; i < linkCount; i++) {
            links.add(new CitizenFamilyGraphSnapshot.Link(buffer.readUUID(), buffer.readUUID()));
        }
        return new CityCitizenFamilyGraphResponsePacket(pos, new CitizenFamilyGraphSnapshot(focusId, focusName, nodes, links));
    }

    public static void handle(CityCitizenFamilyGraphResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleCityCitizenFamilyGraphResponse(packet));
    }
}
