package common.cn.kafei.simukraft.network.medical;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.medical.MedicalControlBoxView;
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

/** 医疗控制箱服务端视图响应。 */
@SuppressWarnings("null")
public record MedicalControlBoxOpenResponsePacket(BlockPos boxPos,
                                                  boolean hasBuilding,
                                                  String buildingName,
                                                  boolean definitionValid,
                                                  String definitionName,
                                                  int serviceRangeRings,
                                                  int coveredChunkCount,
                                                  String statusKey,
                                                  boolean hasDoctor,
                                                  UUID doctorId,
                                                  String doctorName,
                                                  int bedCount,
                                                  int occupiedBedCount,
                                                  List<MedicalControlBoxView.PatientEntry> patients) implements CustomPacketPayload {
    public static final Type<MedicalControlBoxOpenResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "medical_control_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MedicalControlBoxOpenResponsePacket> STREAM_CODEC = StreamCodec.of(MedicalControlBoxOpenResponsePacket::encode, MedicalControlBoxOpenResponsePacket::decode);

    public MedicalControlBoxOpenResponsePacket {
        patients = patients != null ? List.copyOf(patients) : List.of();
    }

    /** from：把服务端视图转换为网络响应。 */
    public static MedicalControlBoxOpenResponsePacket from(MedicalControlBoxView view) {
        return new MedicalControlBoxOpenResponsePacket(view.boxPos(), view.hasBuilding(), view.buildingName(),
                view.definitionValid(), view.definitionName(), view.serviceRangeRings(), view.coveredChunkCount(),
                view.statusKey(), view.hasDoctor(), view.doctorId(), view.doctorName(), view.bedCount(),
                view.occupiedBedCount(), view.patients());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode：写入医疗控制箱视图。 */
    public static void encode(RegistryFriendlyByteBuf buffer, MedicalControlBoxOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.boxPos());
        buffer.writeBoolean(packet.hasBuilding());
        buffer.writeUtf(packet.buildingName(), 256);
        buffer.writeBoolean(packet.definitionValid());
        buffer.writeUtf(packet.definitionName(), 256);
        buffer.writeVarInt(packet.serviceRangeRings());
        buffer.writeVarInt(packet.coveredChunkCount());
        buffer.writeUtf(packet.statusKey(), 256);
        boolean hasDoctor = packet.hasDoctor() && packet.doctorId() != null;
        buffer.writeBoolean(hasDoctor);
        if (hasDoctor) {
            buffer.writeUUID(packet.doctorId());
        }
        buffer.writeUtf(packet.doctorName(), 256);
        buffer.writeVarInt(packet.bedCount());
        buffer.writeVarInt(packet.occupiedBedCount());
        int patientCount = Math.min(256, packet.patients().size());
        buffer.writeVarInt(patientCount);
        for (MedicalControlBoxView.PatientEntry patient : packet.patients().subList(0, patientCount)) {
            buffer.writeUUID(patient.citizenId());
            buffer.writeUtf(patient.name(), 256);
            buffer.writeUtf(patient.conditionKey(), 256);
            buffer.writeDouble(patient.health());
        }
    }

    /** decode：读取医疗控制箱视图。 */
    public static MedicalControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos boxPos = buffer.readBlockPos();
        boolean hasBuilding = buffer.readBoolean();
        String buildingName = buffer.readUtf(256);
        boolean definitionValid = buffer.readBoolean();
        String definitionName = buffer.readUtf(256);
        int rings = buffer.readVarInt();
        int chunks = buffer.readVarInt();
        String statusKey = buffer.readUtf(256);
        boolean hasDoctor = buffer.readBoolean();
        UUID doctorId = hasDoctor ? buffer.readUUID() : null;
        String doctorName = buffer.readUtf(256);
        int bedCount = buffer.readVarInt();
        int occupied = buffer.readVarInt();
        int size = Math.min(256, Math.max(0, buffer.readVarInt()));
        List<MedicalControlBoxView.PatientEntry> patients = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            patients.add(new MedicalControlBoxView.PatientEntry(buffer.readUUID(), buffer.readUtf(256), buffer.readUtf(256), buffer.readDouble()));
        }
        return new MedicalControlBoxOpenResponsePacket(boxPos, hasBuilding, buildingName, definitionValid,
                definitionName, rings, chunks, statusKey, hasDoctor, doctorId, doctorName, bedCount, occupied, patients);
    }

    /** handle：分发视图到客户端 LDLib 界面。 */
    public static void handle(MedicalControlBoxOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleMedicalControlBoxOpenResponse(packet));
    }
}
