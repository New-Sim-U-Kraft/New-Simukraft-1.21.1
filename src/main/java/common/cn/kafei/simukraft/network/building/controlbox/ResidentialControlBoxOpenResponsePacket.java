package common.cn.kafei.simukraft.network.building.controlbox;

import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.controlbox.ResidentialControlBoxView;
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
public record ResidentialControlBoxOpenResponsePacket(BlockPos controlBoxPos,
                                                      String buildingName,
                                                      String buildingTypeKey,
                                                      int residentCount,
                                                      int capacity,
                                                      List<ResidentEntry> residents,
                                                      boolean hasBuildingBounds,
                                                      BlockPos boundsMin,
                                                      BlockPos boundsMax,
                                                      List<BlockPos> residentialPoiPositions,
                                                      boolean integrityAvailable,
                                                      double integrityPercent,
                                                      int integrityRepairableBlocks,
                                                      int integrityManualRepairBlocks,
                                                      double integrityRepairCost,
                                                      List<UnitEntry> units) implements CustomPacketPayload {
    public static final Type<ResidentialControlBoxOpenResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "residential_control_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResidentialControlBoxOpenResponsePacket> STREAM_CODEC = StreamCodec.of(ResidentialControlBoxOpenResponsePacket::encode, ResidentialControlBoxOpenResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ResidentialControlBoxOpenResponsePacket from(ResidentialControlBoxView view) {
        return new ResidentialControlBoxOpenResponsePacket(
                view.controlBoxPos(),
                view.buildingName(),
                view.buildingTypeKey(),
                view.residentCount(),
                view.capacity(),
                view.residents().stream().map(entry -> new ResidentEntry(entry.citizenId(), entry.name())).toList(),
                view.hasBuildingBounds(),
                view.boundsMin(),
                view.boundsMax(),
                view.residentialPoiPositions(),
                view.integrityAvailable(),
                view.integrityPercent(),
                view.integrityRepairableBlocks(),
                view.integrityManualRepairBlocks(),
                view.integrityRepairCost(),
                view.units().stream().map(u -> new UnitEntry(
                        u.unitId(), u.label(), u.bedCount(),
                        u.residents().stream().map(r -> new ResidentEntry(r.citizenId(), r.name())).toList()
                )).toList()
        );
    }

    public static ResidentialControlBoxOpenResponsePacket empty(BlockPos pos) {
        return new ResidentialControlBoxOpenResponsePacket(
                pos,
                "gui.residential_control_box.unknown_building",
                "gui.residential_control_box.building_type",
                0,
                0,
                List.of(),
                false,
                BlockPos.ZERO,
                BlockPos.ZERO,
                List.of(),
                false,
                0.0D,
                0,
                0,
                0.0D,
                List.of()
        );
    }

    public static void encode(RegistryFriendlyByteBuf buffer, ResidentialControlBoxOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.controlBoxPos());
        buffer.writeUtf(packet.buildingName(), 256);
        buffer.writeUtf(packet.buildingTypeKey(), 96);
        buffer.writeVarInt(packet.residentCount());
        buffer.writeVarInt(packet.capacity());
        buffer.writeVarInt(packet.residents().size());
        packet.residents().forEach(resident -> {
            buffer.writeUUID(resident.citizenId());
            buffer.writeUtf(resident.name(), 64);
        });
        buffer.writeBoolean(packet.hasBuildingBounds());
        buffer.writeBlockPos(packet.boundsMin());
        buffer.writeBlockPos(packet.boundsMax());
        buffer.writeVarInt(packet.residentialPoiPositions().size());
        packet.residentialPoiPositions().forEach(buffer::writeBlockPos);
        buffer.writeBoolean(packet.integrityAvailable());
        buffer.writeDouble(packet.integrityPercent());
        buffer.writeVarInt(packet.integrityRepairableBlocks());
        buffer.writeVarInt(packet.integrityManualRepairBlocks());
        buffer.writeDouble(packet.integrityRepairCost());
        buffer.writeVarInt(packet.units().size());
        packet.units().forEach(unit -> {
            buffer.writeUUID(unit.unitId());
            buffer.writeUtf(unit.label(), 64);
            buffer.writeVarInt(unit.bedCount());
            buffer.writeVarInt(unit.residents().size());
            unit.residents().forEach(r -> {
                buffer.writeUUID(r.citizenId());
                buffer.writeUtf(r.name(), 64);
            });
        });
    }

    public static ResidentialControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos controlBoxPos = buffer.readBlockPos();
        String buildingName = buffer.readUtf(256);
        String buildingTypeKey = buffer.readUtf(96);
        int residentCount = buffer.readVarInt();
        int capacity = buffer.readVarInt();
        int residentSize = buffer.readVarInt();
        List<ResidentEntry> residents = new ArrayList<>(residentSize);
        for (int index = 0; index < residentSize; index++) {
            residents.add(new ResidentEntry(buffer.readUUID(), buffer.readUtf(64)));
        }
        boolean hasBuildingBounds = buffer.readBoolean();
        BlockPos boundsMin = buffer.readBlockPos();
        BlockPos boundsMax = buffer.readBlockPos();
        int poiSize = buffer.readVarInt();
        List<BlockPos> residentialPoiPositions = new ArrayList<>(poiSize);
        for (int index = 0; index < poiSize; index++) {
            residentialPoiPositions.add(buffer.readBlockPos());
        }
        boolean integrityAvailable = buffer.readBoolean();
        double integrityPercent = buffer.readDouble();
        int integrityRepairableBlocks = buffer.readVarInt();
        int integrityManualRepairBlocks = buffer.readVarInt();
        double integrityRepairCost = buffer.readDouble();
        int unitSize = buffer.readVarInt();
        List<UnitEntry> units = new ArrayList<>(unitSize);
        for (int i = 0; i < unitSize; i++) {
            UUID unitId = buffer.readUUID();
            String label = buffer.readUtf(64);
            int bedCount = buffer.readVarInt();
            int unitResidentSize = buffer.readVarInt();
            List<ResidentEntry> unitResidents = new ArrayList<>(unitResidentSize);
            for (int j = 0; j < unitResidentSize; j++) {
                unitResidents.add(new ResidentEntry(buffer.readUUID(), buffer.readUtf(64)));
            }
            units.add(new UnitEntry(unitId, label, bedCount, List.copyOf(unitResidents)));
        }
        return new ResidentialControlBoxOpenResponsePacket(
                controlBoxPos,
                buildingName,
                buildingTypeKey,
                residentCount,
                capacity,
                List.copyOf(residents),
                hasBuildingBounds,
                boundsMin,
                boundsMax,
                List.copyOf(residentialPoiPositions),
                integrityAvailable,
                integrityPercent,
                integrityRepairableBlocks,
                integrityManualRepairBlocks,
                integrityRepairCost,
                List.copyOf(units)
        );
    }

    public static void handle(ResidentialControlBoxOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleResidentialControlBoxOpenResponse(packet));
    }

    public record ResidentEntry(UUID citizenId, String name) {
    }

    public record UnitEntry(UUID unitId, String label, int bedCount, List<ResidentEntry> residents) {
    }
}
