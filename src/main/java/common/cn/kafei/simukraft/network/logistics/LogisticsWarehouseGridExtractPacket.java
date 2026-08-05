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
public record LogisticsWarehouseGridExtractPacket(BlockPos pos, ItemStack target, int count) implements CustomPacketPayload {
    public static final Type<LogisticsWarehouseGridExtractPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "logistics_warehouse_grid_extract"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsWarehouseGridExtractPacket> STREAM_CODEC = StreamCodec.of(LogisticsWarehouseGridExtractPacket::encode, LogisticsWarehouseGridExtractPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入要从仓库取出的物品原型和数量。 */
    public static void encode(RegistryFriendlyByteBuf buffer, LogisticsWarehouseGridExtractPacket packet) {
        buffer.writeBlockPos(packet.pos());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.target());
        buffer.writeVarInt(Math.max(1, packet.count()));
    }

    /** decode: 读取要从仓库取出的物品原型和数量。 */
    public static LogisticsWarehouseGridExtractPacket decode(RegistryFriendlyByteBuf buffer) {
        return new LogisticsWarehouseGridExtractPacket(buffer.readBlockPos(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarInt());
    }

    /** handle: 服务端从仓库取出一组物品到鼠标手上。 */
    public static void handle(LogisticsWarehouseGridExtractPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!LogisticsWarehouseGridPackets.prepareOpen(level, player, packet.pos()) || !player.containerMenu.getCarried().isEmpty()) {
            return;
        }
        ItemStack extracted = LogisticsWarehouseInventoryService.extract(level, packet.pos(), packet.target(), packet.count());
        if (extracted.isEmpty()) {
            return;
        }
        player.containerMenu.setCarried(extracted);
        player.containerMenu.broadcastChanges();
        LogisticsWarehouseGridPackets.sendSnapshot(level, player, packet.pos());
    }
}
