package common.cn.kafei.simukraft.network.logistics;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record LogisticsWarehouseGridResponsePacket(BlockPos pos,
                                                   List<ItemStack> items,
                                                   List<BlockPos> containerPositions,
                                                   List<Integer> actualCounts) implements CustomPacketPayload {
    private static final int MAX_ITEMS = 4096;
    public static final Type<LogisticsWarehouseGridResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "logistics_warehouse_grid_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsWarehouseGridResponsePacket> STREAM_CODEC = StreamCodec.of(LogisticsWarehouseGridResponsePacket::encode, LogisticsWarehouseGridResponsePacket::decode);

    public LogisticsWarehouseGridResponsePacket {
        items = items != null ? List.copyOf(items) : List.of();
        containerPositions = containerPositions != null ? List.copyOf(containerPositions) : List.of();
        actualCounts = actualCounts != null ? List.copyOf(actualCounts) : List.of();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入仓库物品、容器位置和真实数量。 */
    public static void encode(RegistryFriendlyByteBuf buffer, LogisticsWarehouseGridResponsePacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeVarInt(packet.items().size());
        for (ItemStack stack : packet.items()) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
        }
        buffer.writeVarInt(packet.containerPositions().size());
        for (BlockPos pos : packet.containerPositions()) {
            buffer.writeBlockPos(pos);
        }
        buffer.writeVarInt(packet.actualCounts().size());
        for (int count : packet.actualCounts()) {
            buffer.writeVarInt(count);
        }
    }

    /** decode: 读取仓库物品、容器位置和真实数量。 */
    public static LogisticsWarehouseGridResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int itemCount = Math.max(0, buffer.readVarInt());
        List<ItemStack> items = new ArrayList<>(Math.min(itemCount, MAX_ITEMS));
        for (int i = 0; i < itemCount; i++) {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            if (i < MAX_ITEMS) {
                items.add(stack);
            }
        }
        int positionCount = Math.max(0, buffer.readVarInt());
        List<BlockPos> positions = new ArrayList<>(Math.min(positionCount, MAX_ITEMS));
        for (int i = 0; i < positionCount; i++) {
            BlockPos containerPos = buffer.readBlockPos();
            if (i < MAX_ITEMS) {
                positions.add(containerPos);
            }
        }
        int countSize = Math.max(0, buffer.readVarInt());
        List<Integer> counts = new ArrayList<>(Math.min(countSize, MAX_ITEMS));
        for (int i = 0; i < countSize; i++) {
            int count = buffer.readVarInt();
            if (i < MAX_ITEMS) {
                counts.add(count);
            }
        }
        return new LogisticsWarehouseGridResponsePacket(pos, items, positions, counts);
    }

    /** handle: 客户端把仓库快照交给当前仓库屏幕。 */
    public static void handle(LogisticsWarehouseGridResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleLogisticsWarehouseGridResponse(packet));
    }
}
