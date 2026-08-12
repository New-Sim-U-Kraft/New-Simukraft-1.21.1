package common.cn.kafei.simukraft.network.rts;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.rts.RtsBlockPlacementService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** RTS 放置请求：客户端仅提交命中的方块面，服务端使用主手物品完成实际放置。 */
@SuppressWarnings("null")
public record RtsPlaceBlockPacket(BlockPos clickedPos, Direction face) implements CustomPacketPayload {
    public static final Type<RtsPlaceBlockPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rts_place_block"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsPlaceBlockPacket> STREAM_CODEC =
            StreamCodec.of(RtsPlaceBlockPacket::encode, RtsPlaceBlockPacket::decode);

    @Override
    public Type<RtsPlaceBlockPacket> type() {
        return TYPE;
    }

    /** encode: 写入客户端光标命中的方块坐标和面向。 */
    private static void encode(RegistryFriendlyByteBuf buffer, RtsPlaceBlockPacket packet) {
        buffer.writeBlockPos(packet.clickedPos());
        buffer.writeEnum(packet.face());
    }

    /** decode: 读取客户端光标命中的方块坐标和面向。 */
    private static RtsPlaceBlockPacket decode(RegistryFriendlyByteBuf buffer) {
        return new RtsPlaceBlockPacket(buffer.readBlockPos(), buffer.readEnum(Direction.class));
    }

    /** handle: 切换至服务端主线程后执行 RTS 方块放置。 */
    public static void handle(RtsPlaceBlockPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        context.enqueueWork(() -> handleOnServer(level, player, packet));
    }

    /** handleOnServer: 根据服务端放置结果向玩家反馈失败原因。 */
    private static void handleOnServer(ServerLevel level, ServerPlayer player, RtsPlaceBlockPacket packet) {
        RtsBlockPlacementService.PlacementStatus status = RtsBlockPlacementService.place(
                level, player, packet.clickedPos(), packet.face());
        switch (status) {
            case SUCCESS -> {
            }
            case OUTSIDE_CITY -> InfoToastService.warning(
                    player, Component.translatable("message.simukraft.city_placement.outside_city"));
            case INVALID -> InfoToastService.warning(
                    player, Component.translatable("message.simukraft.rts.place_invalid"));
        }
    }
}
