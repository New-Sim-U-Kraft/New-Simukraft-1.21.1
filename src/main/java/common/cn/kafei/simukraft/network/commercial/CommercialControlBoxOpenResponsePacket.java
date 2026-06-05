package common.cn.kafei.simukraft.network.commercial;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxView;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

@SuppressWarnings("null")
public record CommercialControlBoxOpenResponsePacket(BlockPos boxPos,
                                                     boolean hasBuilding,
                                                     String buildingName,
                                                     boolean definitionValid,
                                                     String definitionName,
                                                     String statusKey,
                                                     String statusText,
                                                     boolean running,
                                                     boolean hasWorker,
                                                     UUID workerId,
                                                     String workerName,
                                                     double cityBalance) implements CustomPacketPayload {
    public static final Type<CommercialControlBoxOpenResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "commercial_control_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CommercialControlBoxOpenResponsePacket> STREAM_CODEC = StreamCodec.of(CommercialControlBoxOpenResponsePacket::encode, CommercialControlBoxOpenResponsePacket::decode);

    public static CommercialControlBoxOpenResponsePacket from(CommercialControlBoxView view) {
        return new CommercialControlBoxOpenResponsePacket(
                view.boxPos(),
                view.hasBuilding(),
                view.buildingName(),
                view.definitionValid(),
                view.definitionName(),
                view.statusKey(),
                view.statusText(),
                view.running(),
                view.hasWorker(),
                view.workerId(),
                view.workerName(),
                view.cityBalance()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入商业控制箱视图响应。 */
    public static void encode(RegistryFriendlyByteBuf buffer, CommercialControlBoxOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.boxPos());
        buffer.writeBoolean(packet.hasBuilding());
        buffer.writeUtf(packet.buildingName(), 128);
        buffer.writeBoolean(packet.definitionValid());
        buffer.writeUtf(packet.definitionName(), 128);
        buffer.writeUtf(packet.statusKey(), 128);
        buffer.writeUtf(packet.statusText(), 256);
        buffer.writeBoolean(packet.running());
        buffer.writeBoolean(packet.hasWorker());
        if (packet.hasWorker() && packet.workerId() != null) {
            buffer.writeUUID(packet.workerId());
        }
        buffer.writeUtf(packet.workerName(), 128);
        buffer.writeDouble(packet.cityBalance());
    }

    /** decode: 读取商业控制箱视图响应。 */
    public static CommercialControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos boxPos = buffer.readBlockPos();
        boolean hasBuilding = buffer.readBoolean();
        String buildingName = buffer.readUtf(128);
        boolean definitionValid = buffer.readBoolean();
        String definitionName = buffer.readUtf(128);
        String statusKey = buffer.readUtf(128);
        String statusText = buffer.readUtf(256);
        boolean running = buffer.readBoolean();
        boolean hasWorker = buffer.readBoolean();
        UUID workerId = hasWorker ? buffer.readUUID() : null;
        String workerName = buffer.readUtf(128);
        double cityBalance = buffer.readDouble();
        return new CommercialControlBoxOpenResponsePacket(boxPos, hasBuilding, buildingName, definitionValid, definitionName, statusKey, statusText, running, hasWorker, workerId, workerName, cityBalance);
    }

    /** handle: 分发商业控制箱视图到客户端 UI。 */
    public static void handle(CommercialControlBoxOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleCommercialControlBoxOpenResponse(packet));
    }

}
