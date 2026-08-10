package common.cn.kafei.simukraft.network.rts;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingMoveService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** RTS 移动请求：客户端提交源、目标位置、高度微调和预览旋转，服务端重新校验最终落点。 */
@SuppressWarnings("null")
public record RtsMovePacket(BlockPos source, BlockPos destination, int manualVerticalOffset,
                            int rotationDegrees, int focusChunkX, int focusChunkZ) implements CustomPacketPayload {
    public static final Type<RtsMovePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rts_move"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsMovePacket> STREAM_CODEC =
            StreamCodec.of(RtsMovePacket::encode, RtsMovePacket::decode);

    @Override
    public Type<RtsMovePacket> type() {
        return TYPE;
    }

    /** encode: 编码移动源、目标位置、高度微调和旋转角度。 */
    private static void encode(RegistryFriendlyByteBuf buffer, RtsMovePacket packet) {
        buffer.writeBlockPos(packet.source());
        buffer.writeBlockPos(packet.destination());
        buffer.writeVarInt(packet.manualVerticalOffset());
        buffer.writeVarInt(packet.rotationDegrees());
        buffer.writeInt(packet.focusChunkX());
        buffer.writeInt(packet.focusChunkZ());
    }

    /** decode: 解码移动源、目标位置、高度微调和旋转角度。 */
    private static RtsMovePacket decode(RegistryFriendlyByteBuf buffer) {
        return new RtsMovePacket(buffer.readBlockPos(), buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readInt(), buffer.readInt());
    }

    /** handle: 在服务端主线程执行移动并反馈结果。 */
    public static void handle(RtsMovePacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        context.enqueueWork(() -> {
            // 与移动请求同帧刷新视窗，避免独立焦点包尚未处理时误判为玩家本体距离。
            RtsChunkViewService.activate(player, new ChunkPos(packet.focusChunkX(), packet.focusChunkZ()));
            PlacedBuildingMoveService.MoveStatus status = PlacedBuildingMoveService.move(
                    level, player, packet.source(), packet.destination(), packet.manualVerticalOffset(), packet.rotationDegrees());
            switch (status) {
                case SUCCESS_BLOCK -> InfoToastService.success(player, Component.translatable("message.simukraft.rts.block_moved"));
                case SUCCESS_BUILDING -> {
                    InfoToastService.success(player, Component.translatable("message.simukraft.rts.building_moved"));
                    RtsBuildingBoundsRequestPacket.sendNearbyBounds(player, level);
                }
                case TOO_FAR -> InfoToastService.warning(player, Component.translatable("message.simukraft.rts.too_far"));
                case NO_PERMISSION -> InfoToastService.warning(player, Component.translatable("message.simukraft.no_permission"));
                case OUTSIDE_CITY -> InfoToastService.warning(player, Component.translatable("message.simukraft.construction.outside_city"));
                case INVALID -> InfoToastService.warning(player, Component.translatable("message.simukraft.rts.move_invalid"));
            }
        });
    }
}
