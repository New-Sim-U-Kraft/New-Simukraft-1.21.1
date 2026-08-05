package common.cn.kafei.simukraft.network.logistics;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseInventoryService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record LogisticsWarehouseGridInsertPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<LogisticsWarehouseGridInsertPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "logistics_warehouse_grid_insert"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsWarehouseGridInsertPacket> STREAM_CODEC = StreamCodec.of(LogisticsWarehouseGridInsertPacket::encode, LogisticsWarehouseGridInsertPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入仓库存入目标坐标。 */
    public static void encode(RegistryFriendlyByteBuf buffer, LogisticsWarehouseGridInsertPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode: 读取仓库存入目标坐标。 */
    public static LogisticsWarehouseGridInsertPacket decode(RegistryFriendlyByteBuf buffer) {
        return new LogisticsWarehouseGridInsertPacket(buffer.readBlockPos());
    }

    /** handle: 服务端把鼠标手上的物品存入仓库。 */
    public static void handle(LogisticsWarehouseGridInsertPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!LogisticsWarehouseGridPackets.prepareOpen(level, player, packet.pos())) {
            return;
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        ItemStack remaining = LogisticsWarehouseInventoryService.insert(level, packet.pos(), carried.copy());
        int inserted = carried.getCount() - remaining.getCount();
        if (inserted <= 0) {
            return;
        }
        carried.shrink(inserted);
        if (carried.isEmpty()) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        player.containerMenu.broadcastChanges();
        LogisticsWarehouseGridPackets.sendSnapshot(level, player, packet.pos());
    }
}
