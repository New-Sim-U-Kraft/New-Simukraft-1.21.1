package common.cn.kafei.simukraft.network.farmland;

import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.farmland.FarmlandBoxView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FarmlandBoxOpenResponsePacket(BlockPos boxPos,
                                            boolean hasCity,
                                            String cropId,
                                            boolean hasPlot,
                                            BlockPos plotMin,
                                            BlockPos plotMax,
                                            boolean hasChest,
                                            BlockPos chestPos,
                                            boolean running,
                                            boolean hasFarmer,
                                            String farmerName) implements CustomPacketPayload {
    public static final Type<FarmlandBoxOpenResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "farmland_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FarmlandBoxOpenResponsePacket> STREAM_CODEC = StreamCodec.of(FarmlandBoxOpenResponsePacket::encode, FarmlandBoxOpenResponsePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static FarmlandBoxOpenResponsePacket from(FarmlandBoxView view) {
        return new FarmlandBoxOpenResponsePacket(
                view.boxPos(),
                view.hasCity(),
                view.cropId(),
                view.hasPlot(),
                view.plotMin(),
                view.plotMax(),
                view.hasChest(),
                view.chestPos(),
                view.running(),
                view.hasFarmer(),
                view.farmerName());
    }

    public static FarmlandBoxOpenResponsePacket empty(BlockPos pos) {
        return new FarmlandBoxOpenResponsePacket(pos, false, "", false, BlockPos.ZERO, BlockPos.ZERO, false, BlockPos.ZERO, false, false, "");
    }

    public static void encode(RegistryFriendlyByteBuf buffer, FarmlandBoxOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.boxPos());
        buffer.writeBoolean(packet.hasCity());
        buffer.writeUtf(packet.cropId(), 32);
        buffer.writeBoolean(packet.hasPlot());
        buffer.writeBlockPos(packet.plotMin());
        buffer.writeBlockPos(packet.plotMax());
        buffer.writeBoolean(packet.hasChest());
        buffer.writeBlockPos(packet.chestPos());
        buffer.writeBoolean(packet.running());
        buffer.writeBoolean(packet.hasFarmer());
        buffer.writeUtf(packet.farmerName(), 64);
    }

    public static FarmlandBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        return new FarmlandBoxOpenResponsePacket(
                buffer.readBlockPos(),
                buffer.readBoolean(),
                buffer.readUtf(32),
                buffer.readBoolean(),
                buffer.readBlockPos(),
                buffer.readBlockPos(),
                buffer.readBoolean(),
                buffer.readBlockPos(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(64));
    }

    public static void handle(FarmlandBoxOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleFarmlandBoxOpenResponse(packet));
    }
}
