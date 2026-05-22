package common.cn.kafei.simukraft.network.building.controlbox;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record ResidentialControlBoxBoundsUpdatePacket(BlockPos controlBoxPos,
                                                      boolean hasBuildingBounds,
                                                      BlockPos boundsMin,
                                                      BlockPos boundsMax,
                                                      List<BlockPos> residentialPoiPositions) implements CustomPacketPayload {
    public static final Type<ResidentialControlBoxBoundsUpdatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "residential_control_box_bounds_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResidentialControlBoxBoundsUpdatePacket> STREAM_CODEC = StreamCodec.of(ResidentialControlBoxBoundsUpdatePacket::encode, ResidentialControlBoxBoundsUpdatePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, ResidentialControlBoxBoundsUpdatePacket packet) {
        buffer.writeBlockPos(packet.controlBoxPos());
        buffer.writeBoolean(packet.hasBuildingBounds());
        buffer.writeBlockPos(packet.boundsMin());
        buffer.writeBlockPos(packet.boundsMax());
        buffer.writeVarInt(packet.residentialPoiPositions().size());
        packet.residentialPoiPositions().forEach(buffer::writeBlockPos);
    }

    public static ResidentialControlBoxBoundsUpdatePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos controlBoxPos = buffer.readBlockPos();
        boolean hasBuildingBounds = buffer.readBoolean();
        BlockPos boundsMin = buffer.readBlockPos();
        BlockPos boundsMax = buffer.readBlockPos();
        int poiSize = buffer.readVarInt();
        List<BlockPos> residentialPoiPositions = new ArrayList<>(poiSize);
        for (int index = 0; index < poiSize; index++) {
            residentialPoiPositions.add(buffer.readBlockPos());
        }
        return new ResidentialControlBoxBoundsUpdatePacket(controlBoxPos, hasBuildingBounds, boundsMin, boundsMax, List.copyOf(residentialPoiPositions));
    }

    public static void handle(ResidentialControlBoxBoundsUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer.updateDisplayedBuildingBounds(
                packet.controlBoxPos(),
                packet.hasBuildingBounds(),
                packet.boundsMin(),
                packet.boundsMax(),
                packet.residentialPoiPositions()
        ));
    }
}
