package common.cn.kafei.simukraft.network.logistics;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.logistics.menu.LogisticsWarehouseGridMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record LogisticsWarehouseGridOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<LogisticsWarehouseGridOpenRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "logistics_warehouse_grid_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsWarehouseGridOpenRequestPacket> STREAM_CODEC = StreamCodec.of(LogisticsWarehouseGridOpenRequestPacket::encode, LogisticsWarehouseGridOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入要打开的物流服务端盒坐标。 */
    public static void encode(RegistryFriendlyByteBuf buffer, LogisticsWarehouseGridOpenRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode: 读取要打开的物流服务端盒坐标。 */
    public static LogisticsWarehouseGridOpenRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new LogisticsWarehouseGridOpenRequestPacket(buffer.readBlockPos());
    }

    /** handle: 服务端校验后打开仓库 Menu。 */
    public static void handle(LogisticsWarehouseGridOpenRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, packet.pos());
        }
    }

    /** openFor: 供方块右键直接打开仓库 Menu。 */
    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (LogisticsWarehouseGridPackets.prepareOpen(level, player, pos)) {
            LogisticsWarehouseGridMenuProvider.open(player, pos);
        }
    }
}
