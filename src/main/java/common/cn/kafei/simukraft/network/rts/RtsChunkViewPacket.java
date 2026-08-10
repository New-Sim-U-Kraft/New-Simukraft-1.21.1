package common.cn.kafei.simukraft.network.rts;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** RTS 摄像机区块视窗请求：仅在焦点跨区块或退出 RTS 时从客户端发起。 */
public record RtsChunkViewPacket(boolean active, int chunkX, int chunkZ) implements CustomPacketPayload {
    public static final Type<RtsChunkViewPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rts_chunk_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsChunkViewPacket> STREAM_CODEC =
            StreamCodec.of(RtsChunkViewPacket::encode, RtsChunkViewPacket::decode);

    @Override
    public Type<RtsChunkViewPacket> type() {
        return TYPE;
    }

    /** encode: 写入 RTS 启用状态和摄像机焦点区块。 */
    private static void encode(RegistryFriendlyByteBuf buffer, RtsChunkViewPacket packet) {
        buffer.writeBoolean(packet.active());
        buffer.writeInt(packet.chunkX());
        buffer.writeInt(packet.chunkZ());
    }

    /** decode: 读取 RTS 启用状态和摄像机焦点区块。 */
    private static RtsChunkViewPacket decode(RegistryFriendlyByteBuf buffer) {
        return new RtsChunkViewPacket(buffer.readBoolean(), buffer.readInt(), buffer.readInt());
    }

    /** handle: 在服务端主线程更新当前玩家的 RTS 区块视窗。 */
    public static void handle(RtsChunkViewPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (packet.active()) {
                RtsChunkViewService.activate(player, new ChunkPos(packet.chunkX(), packet.chunkZ()));
                RtsBuildingBoundsRequestPacket.sendNearbyBounds(player, level);
            } else {
                RtsChunkViewService.deactivate(player);
            }
        });
    }
}
