package common.cn.kafei.simukraft.network.logistics;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record LogisticsWarehouseGridRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<LogisticsWarehouseGridRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "logistics_warehouse_grid_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsWarehouseGridRequestPacket> STREAM_CODEC = StreamCodec.of(LogisticsWarehouseGridRequestPacket::encode, LogisticsWarehouseGridRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入仓库快照请求坐标。 */
    public static void encode(RegistryFriendlyByteBuf buffer, LogisticsWarehouseGridRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode: 读取仓库快照请求坐标。 */
    public static LogisticsWarehouseGridRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new LogisticsWarehouseGridRequestPacket(buffer.readBlockPos());
    }

    /** handle: 校验权限后直接返回仓库物品快照，不打开容器 Menu。 */
    public static void handle(LogisticsWarehouseGridRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (LogisticsWarehouseGridPackets.prepareOpen(level, player, packet.pos())) {
            LogisticsWarehouseGridPackets.sendSnapshot(level, player, packet.pos());
        }
    }
}
