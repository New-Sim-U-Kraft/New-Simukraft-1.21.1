package common.cn.kafei.simukraft.network.rts;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** RTS 建筑边界快照：客户端仅用于渲染，不作为操作权限依据。 */
public record RtsBuildingBoundsSyncPacket(List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 512;
    public static final int MAX_DISPLAY_NAME_LENGTH = 128;
    @SuppressWarnings("null")
    public static final Type<RtsBuildingBoundsSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rts_building_bounds_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsBuildingBoundsSyncPacket> STREAM_CODEC =
            StreamCodec.of(RtsBuildingBoundsSyncPacket::encode, RtsBuildingBoundsSyncPacket::decode);

    public RtsBuildingBoundsSyncPacket {
        entries = entries == null ? List.of() : entries.stream()
                .filter(entry -> entry != null && entry.min() != null && entry.max() != null)
                .limit(MAX_ENTRIES)
                .toList();
    }

    @Override
    public Type<RtsBuildingBoundsSyncPacket> type() {
        return TYPE;
    }

    /** encode: 编码有限建筑边界列表。 */
    @SuppressWarnings("null")
    private static void encode(RegistryFriendlyByteBuf buffer, RtsBuildingBoundsSyncPacket packet) {
        buffer.writeVarInt(packet.entries().size());
        for (Entry entry : packet.entries()) {
            buffer.writeBlockPos(entry.min());
            buffer.writeBlockPos(entry.max());
            buffer.writeUtf(entry.displayName(), MAX_DISPLAY_NAME_LENGTH);
        }
    }

    /** decode: 解码并限制列表大小，避免异常数据造成内存膨胀。 */
    private static RtsBuildingBoundsSyncPacket decode(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(Math.max(0, buffer.readVarInt()), MAX_ENTRIES);
        java.util.ArrayList<Entry> entries = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buffer.readBlockPos(), buffer.readBlockPos(), buffer.readUtf(MAX_DISPLAY_NAME_LENGTH)));
        }
        return new RtsBuildingBoundsSyncPacket(entries);
    }

    /** handle: 切换到客户端线程更新边界缓存。 */
    public static void handle(RtsBuildingBoundsSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleRtsBuildingBoundsSync(packet));
    }

    public record Entry(BlockPos min, BlockPos max, String displayName) {
        public Entry {
            displayName = displayName == null ? "" : displayName.trim();
            if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
                displayName = displayName.substring(0, MAX_DISPLAY_NAME_LENGTH);
            }
        }
    }
}
