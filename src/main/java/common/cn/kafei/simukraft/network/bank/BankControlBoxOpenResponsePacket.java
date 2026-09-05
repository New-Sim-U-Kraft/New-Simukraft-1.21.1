package common.cn.kafei.simukraft.network.bank;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.bank.BankControlBoxView;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** BankControlBoxOpenResponsePacket: 银行控制箱视图。 */
public record BankControlBoxOpenResponsePacket(BlockPos boxPos,
                                               boolean hasBuilding,
                                               String buildingName,
                                               UUID cityId,
                                               String statusKey,
                                               boolean hasTeller,
                                               UUID tellerId,
                                               String tellerName,
                                               double cityFunds,
                                               double playerCash) implements CustomPacketPayload {
    public static final Type<BankControlBoxOpenResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "bank_control_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BankControlBoxOpenResponsePacket> STREAM_CODEC =
            StreamCodec.of(BankControlBoxOpenResponsePacket::encode, BankControlBoxOpenResponsePacket::decode);

    /** from: 视图转网络包。 */
    public static BankControlBoxOpenResponsePacket from(BankControlBoxView view) {
        return new BankControlBoxOpenResponsePacket(view.boxPos(), view.hasBuilding(), view.buildingName(),
                view.cityId(), view.statusKey(), view.hasTeller(), view.tellerId(), view.tellerName(),
                view.cityFunds(), view.playerCash());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入银行视图。 */
    public static void encode(RegistryFriendlyByteBuf buffer, BankControlBoxOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.boxPos());
        buffer.writeBoolean(packet.hasBuilding());
        buffer.writeUtf(packet.buildingName() != null ? packet.buildingName() : "", 256);
        buffer.writeBoolean(packet.cityId() != null);
        if (packet.cityId() != null) {
            buffer.writeUUID(packet.cityId());
        }
        buffer.writeUtf(packet.statusKey() != null ? packet.statusKey() : "", 256);
        boolean hasTeller = packet.hasTeller() && packet.tellerId() != null;
        buffer.writeBoolean(hasTeller);
        if (hasTeller) {
            buffer.writeUUID(packet.tellerId());
        }
        buffer.writeUtf(packet.tellerName() != null ? packet.tellerName() : "", 256);
        buffer.writeDouble(packet.cityFunds());
        buffer.writeDouble(packet.playerCash());
    }

    /** decode: 读取银行视图。 */
    public static BankControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos boxPos = buffer.readBlockPos();
        boolean hasBuilding = buffer.readBoolean();
        String buildingName = buffer.readUtf(256);
        UUID cityId = buffer.readBoolean() ? buffer.readUUID() : null;
        String statusKey = buffer.readUtf(256);
        boolean hasTeller = buffer.readBoolean();
        UUID tellerId = hasTeller ? buffer.readUUID() : null;
        String tellerName = buffer.readUtf(256);
        double cityFunds = buffer.readDouble();
        double playerCash = buffer.readDouble();
        return new BankControlBoxOpenResponsePacket(boxPos, hasBuilding, buildingName, cityId, statusKey,
                hasTeller, tellerId, tellerName, cityFunds, playerCash);
    }

    /** handle: 分发到客户端界面。 */
    public static void handle(BankControlBoxOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleBankControlBoxOpenResponse(packet));
    }
}
