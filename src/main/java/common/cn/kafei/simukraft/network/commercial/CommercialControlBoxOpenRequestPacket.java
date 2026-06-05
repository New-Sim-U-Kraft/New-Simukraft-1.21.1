package common.cn.kafei.simukraft.network.commercial;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxService;
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

@SuppressWarnings("null")
public record CommercialControlBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<CommercialControlBoxOpenRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "commercial_control_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CommercialControlBoxOpenRequestPacket> STREAM_CODEC = StreamCodec.of(CommercialControlBoxOpenRequestPacket::encode, CommercialControlBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入打开商业控制箱请求。 */
    public static void encode(RegistryFriendlyByteBuf buffer, CommercialControlBoxOpenRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode: 读取打开商业控制箱请求。 */
    public static CommercialControlBoxOpenRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CommercialControlBoxOpenRequestPacket(buffer.readBlockPos());
    }

    /** handle: 处理客户端打开商业控制箱请求。 */
    public static void handle(CommercialControlBoxOpenRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, packet.pos());
        }
    }

    /** openFor: 校验距离和方块后向玩家发送商业控制箱视图。 */
    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 16.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.commercial_control_box.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.COMMERCIAL_CONTROL_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.commercial_control_box.not_found"));
            return;
        }
        PacketDistributor.sendToPlayer(player, CommercialControlBoxOpenResponsePacket.from(CommercialControlBoxService.buildView(level, pos)));
    }
}
