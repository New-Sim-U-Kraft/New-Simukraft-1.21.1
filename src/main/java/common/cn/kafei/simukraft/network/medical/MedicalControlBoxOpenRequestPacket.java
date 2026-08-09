package common.cn.kafei.simukraft.network.medical;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.medical.MedicalControlBoxService;
import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端请求打开医疗控制箱。 */
@SuppressWarnings("null")
public record MedicalControlBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<MedicalControlBoxOpenRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "medical_control_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MedicalControlBoxOpenRequestPacket> STREAM_CODEC = StreamCodec.of(MedicalControlBoxOpenRequestPacket::encode, MedicalControlBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode：写入控制箱坐标。 */
    public static void encode(RegistryFriendlyByteBuf buffer, MedicalControlBoxOpenRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode：读取控制箱坐标。 */
    public static MedicalControlBoxOpenRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new MedicalControlBoxOpenRequestPacket(buffer.readBlockPos());
    }

    /** handle：在服务端校验并打开医疗控制箱。 */
    public static void handle(MedicalControlBoxOpenRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, packet.pos());
        }
    }

    /** openFor：校验距离和方块后发送只读视图。 */
    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 16.0D) && !RtsRemoteMenuAccess.hasAccess(player, pos)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.medical_control_box.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.MEDICAL_CONTROL_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.medical_control_box.not_found"));
            return;
        }
        PacketDistributor.sendToPlayer(player, MedicalControlBoxOpenResponsePacket.from(MedicalControlBoxService.buildView(level, pos)));
    }
}
