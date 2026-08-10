package common.cn.kafei.simukraft.network.rts;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Comparator;

/** RTS 建筑边界请求：仅请求玩家附近的已登记建筑，不修改服务端状态。 */
public record RtsBuildingBoundsRequestPacket() implements CustomPacketPayload {
    private static final double MAX_DISTANCE_SQR = 192.0D * 192.0D;
    @SuppressWarnings("null")
    public static final Type<RtsBuildingBoundsRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rts_building_bounds_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsBuildingBoundsRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new RtsBuildingBoundsRequestPacket());

    @Override
    public Type<RtsBuildingBoundsRequestPacket> type() {
        return TYPE;
    }

    /** handle: 在服务端线程读取附近建筑并返回有限快照。 */
    public static void handle(RtsBuildingBoundsRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        context.enqueueWork(() -> sendNearbyBounds(player, level));
    }

    /** sendNearbyBounds: 在服务端主线程生成受限边界快照。 */
    @SuppressWarnings("null")
    public static void sendNearbyBounds(ServerPlayer player, ServerLevel level) {
        List<RtsBuildingBoundsSyncPacket.Entry> entries = PlacedBuildingService.getBuildings(level).stream()
                .filter(record -> isNear(player, record, MAX_DISTANCE_SQR))
                .sorted(Comparator.comparingDouble(record -> distanceToBoundsSqr(player, record)))
                .limit(RtsBuildingBoundsSyncPacket.MAX_ENTRIES)
                .map(record -> new RtsBuildingBoundsSyncPacket.Entry(record.minPos(), record.maxPos(), record.displayName()))
                .toList();
        PacketDistributor.sendToPlayer(player, new RtsBuildingBoundsSyncPacket(entries));
    }

    /** refreshNearbyPlayers: 新建筑登记后立即刷新附近玩家的 RTS 建筑边界。 */
    public static void refreshNearbyPlayers(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (isNear(player, building, MAX_DISTANCE_SQR)) {
                sendNearbyBounds(player, level);
            }
        }
    }

    private static boolean isNear(ServerPlayer player, PlacedBuildingRecord record, double maxDistanceSqr) {
        if (record.minPos() == null || record.maxPos() == null) {
            return false;
        }
        return distanceToBoundsSqr(player, record) <= maxDistanceSqr;
    }

    /** distanceToBoundsSqr: 计算玩家到建筑边界最近点的平方距离。 */
    private static double distanceToBoundsSqr(ServerPlayer player, PlacedBuildingRecord record) {
        double minX = Math.min(record.minPos().getX(), record.maxPos().getX());
        double minY = Math.min(record.minPos().getY(), record.maxPos().getY());
        double minZ = Math.min(record.minPos().getZ(), record.maxPos().getZ());
        double maxX = Math.max(record.minPos().getX(), record.maxPos().getX()) + 1.0D;
        double maxY = Math.max(record.minPos().getY(), record.maxPos().getY()) + 1.0D;
        double maxZ = Math.max(record.minPos().getZ(), record.maxPos().getZ()) + 1.0D;
        net.minecraft.world.level.ChunkPos center = RtsChunkViewService.viewCenter(player.serverLevel(), player);
        double centerX = center.getMiddleBlockX();
        double centerZ = center.getMiddleBlockZ();
        double deltaX = centerX - Math.clamp(centerX, minX, maxX);
        double deltaY = player.getY() - Math.clamp(player.getY(), minY, maxY);
        double deltaZ = centerZ - Math.clamp(centerZ, minZ, maxZ);
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }
}
