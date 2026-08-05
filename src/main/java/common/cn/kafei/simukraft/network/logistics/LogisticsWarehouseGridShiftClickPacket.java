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
public record LogisticsWarehouseGridShiftClickPacket(BlockPos pos, ItemStack target) implements CustomPacketPayload {
    public static final Type<LogisticsWarehouseGridShiftClickPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "logistics_warehouse_grid_shift_click"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsWarehouseGridShiftClickPacket> STREAM_CODEC = StreamCodec.of(LogisticsWarehouseGridShiftClickPacket::encode, LogisticsWarehouseGridShiftClickPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入快速取出的目标物品原型。 */
    public static void encode(RegistryFriendlyByteBuf buffer, LogisticsWarehouseGridShiftClickPacket packet) {
        buffer.writeBlockPos(packet.pos());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.target());
    }

    /** decode: 读取快速取出的目标物品原型。 */
    public static LogisticsWarehouseGridShiftClickPacket decode(RegistryFriendlyByteBuf buffer) {
        return new LogisticsWarehouseGridShiftClickPacket(buffer.readBlockPos(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
    }

    /** handle: 服务端把仓库物品直接转移到玩家背包。 */
    public static void handle(LogisticsWarehouseGridShiftClickPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!LogisticsWarehouseGridPackets.prepareOpen(level, player, packet.pos()) || packet.target().isEmpty()) {
            return;
        }
        ItemStack extracted = LogisticsWarehouseInventoryService.extract(level, packet.pos(), packet.target(),
                Math.min(64, packet.target().getMaxStackSize()));
        if (extracted.isEmpty()) {
            return;
        }
        ItemStack remaining = LogisticsWarehouseInventoryService.insertIntoPlayerInventory(player.getInventory(), extracted);
        if (!remaining.isEmpty()) {
            ItemStack returned = LogisticsWarehouseInventoryService.insert(level, packet.pos(), remaining);
            if (!returned.isEmpty()) {
                player.drop(returned, false);
            }
        }
        player.containerMenu.broadcastChanges();
        LogisticsWarehouseGridPackets.sendSnapshot(level, player, packet.pos());
    }
}
