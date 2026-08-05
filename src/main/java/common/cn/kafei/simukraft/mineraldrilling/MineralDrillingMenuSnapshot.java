package common.cn.kafei.simukraft.mineraldrilling;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingControlBoxView.VeinMarker;

import java.util.List;
import java.util.UUID;

/** MineralDrillingMenuSnapshot: 限长编码控制箱打开时的客户端初始视图。 */
@SuppressWarnings("null")
public record MineralDrillingMenuSnapshot(BlockPos boxPos,
                                         boolean hasBuilding,
                                         String buildingName,
                                         boolean integrityAvailable,
                                         float integrityPercent,
                                         boolean hasWorker,
                                         UUID workerId,
                                         String workerName,
                                         boolean running,
                                         int drillDepth,
                                         int minDepth,
                                         int maxDepth,
                                         String statusKey,
                                         String statusText,
                                         String selectedVeinName,
                                         String selectedProductId,
                                         boolean hasBounds,
                                         BlockPos boundsMin,
                                         BlockPos boundsMax,
                                         List<Marker> markers) {
    private static final int MAX_TEXT_LENGTH = 256;
    private static final int MAX_STATUS_TEXT_LENGTH = 1024;
    private static final int MAX_MARKERS = 2;

    /** fromView: 将服务端权威视图转换成有限大小的菜单快照。 */
    public static MineralDrillingMenuSnapshot fromView(MineralDrillingControlBoxView view) {
        if (view == null) {
            return empty(BlockPos.ZERO);
        }
        List<Marker> markers = prioritizedMarkers(view.veinMarkers());
        return new MineralDrillingMenuSnapshot(
                safePos(view.boxPos()), view.hasBuilding(), limit(view.buildingName(), MAX_TEXT_LENGTH),
                view.integrityAvailable(), Math.clamp((float) (view.integrityPercent() / 100.0D), 0.0F, 1.0F),
                view.hasWorker(), view.workerId(), limit(view.workerName(), MAX_TEXT_LENGTH), view.running(),
                view.drillDepth(), view.minDepth(), view.maxDepth(), limit(view.statusKey(), MAX_TEXT_LENGTH),
                limit(view.statusText(), MAX_STATUS_TEXT_LENGTH), limit(view.selectedVeinName(), MAX_TEXT_LENGTH),
                limit(view.selectedProductId(), MAX_TEXT_LENGTH), view.hasBounds(), safePos(view.boundsMin()),
                safePos(view.boundsMax()), List.copyOf(markers));
    }

    /** empty: 为异常或缺少打开数据时生成安全的空快照。 */
    public static MineralDrillingMenuSnapshot empty(BlockPos boxPos) {
        BlockPos safe = safePos(boxPos);
        return new MineralDrillingMenuSnapshot(safe, false, "", false, 0.0F, false, null, "", false,
                safe.getY(), -64, 320, "gui.simukraft.mineral_drilling.status.no_building", "", "", "",
                false, BlockPos.ZERO, BlockPos.ZERO, List.of());
    }

    /** encode: 将快照写入菜单打开缓冲区并限制列表长度。 */
    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(safePos(boxPos));
        buffer.writeBoolean(hasBuilding);
        buffer.writeUtf(limit(buildingName, MAX_TEXT_LENGTH), MAX_TEXT_LENGTH);
        buffer.writeBoolean(integrityAvailable);
        buffer.writeFloat(Math.clamp(integrityPercent, 0.0F, 1.0F));
        buffer.writeBoolean(hasWorker);
        buffer.writeBoolean(workerId != null);
        if (workerId != null) {
            buffer.writeUUID(workerId);
        }
        buffer.writeUtf(limit(workerName, MAX_TEXT_LENGTH), MAX_TEXT_LENGTH);
        buffer.writeBoolean(running);
        buffer.writeInt(drillDepth);
        buffer.writeInt(minDepth);
        buffer.writeInt(maxDepth);
        buffer.writeUtf(limit(statusKey, MAX_TEXT_LENGTH), MAX_TEXT_LENGTH);
        buffer.writeUtf(limit(statusText, MAX_STATUS_TEXT_LENGTH), MAX_STATUS_TEXT_LENGTH);
        buffer.writeUtf(limit(selectedVeinName, MAX_TEXT_LENGTH), MAX_TEXT_LENGTH);
        buffer.writeUtf(limit(selectedProductId, MAX_TEXT_LENGTH), MAX_TEXT_LENGTH);
        buffer.writeBoolean(hasBounds);
        buffer.writeBlockPos(safePos(boundsMin));
        buffer.writeBlockPos(safePos(boundsMax));
        int count = Math.min(MAX_MARKERS, markers == null ? 0 : markers.size());
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            markers.get(index).encode(buffer);
        }
    }

    /** decode: 从菜单打开缓冲区读取并限长校验客户端快照。 */
    public static MineralDrillingMenuSnapshot decode(RegistryFriendlyByteBuf buffer) {
        if (buffer == null) {
            return empty(BlockPos.ZERO);
        }
        BlockPos boxPos = buffer.readBlockPos();
        boolean hasBuilding = buffer.readBoolean();
        String buildingName = buffer.readUtf(MAX_TEXT_LENGTH);
        boolean integrityAvailable = buffer.readBoolean();
        float integrityPercent = Math.clamp(buffer.readFloat(), 0.0F, 1.0F);
        boolean hasWorker = buffer.readBoolean();
        UUID workerId = buffer.readBoolean() ? buffer.readUUID() : null;
        String workerName = buffer.readUtf(MAX_TEXT_LENGTH);
        boolean running = buffer.readBoolean();
        int drillDepth = buffer.readInt();
        int minDepth = buffer.readInt();
        int maxDepth = buffer.readInt();
        String statusKey = buffer.readUtf(MAX_TEXT_LENGTH);
        String statusText = buffer.readUtf(MAX_STATUS_TEXT_LENGTH);
        String selectedVeinName = buffer.readUtf(MAX_TEXT_LENGTH);
        String selectedProductId = buffer.readUtf(MAX_TEXT_LENGTH);
        boolean hasBounds = buffer.readBoolean();
        BlockPos boundsMin = buffer.readBlockPos();
        BlockPos boundsMax = buffer.readBlockPos();
        int markerCount = buffer.readVarInt();
        if (markerCount < 0 || markerCount > MAX_MARKERS) {
            throw new IllegalArgumentException("Invalid mineral drilling marker count: " + markerCount);
        }
        java.util.ArrayList<Marker> markers = new java.util.ArrayList<>(markerCount);
        for (int index = 0; index < markerCount; index++) {
            markers.add(Marker.decode(buffer));
        }
        return new MineralDrillingMenuSnapshot(boxPos, hasBuilding, buildingName, integrityAvailable,
                integrityPercent, hasWorker, workerId, workerName, running, drillDepth, minDepth, maxDepth,
                statusKey, statusText, selectedVeinName, selectedProductId, hasBounds, boundsMin, boundsMax,
                List.copyOf(markers));
    }

    private static Marker marker(VeinMarker marker) {
        return new Marker(marker.veinId(), marker.displayName(), marker.productId(), marker.minY(), marker.maxY(),
                Math.max(0L, marker.remainingReserve()), marker.selected());
    }

    private static List<Marker> prioritizedMarkers(List<VeinMarker> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<Marker> result = new java.util.ArrayList<>(MAX_MARKERS);
        for (VeinMarker marker : source) {
            if (marker != null && marker.selected()) {
                result.add(marker(marker));
                break;
            }
        }
        for (VeinMarker marker : source) {
            if (result.size() >= MAX_MARKERS) {
                break;
            }
            if (marker != null && !marker.selected()) {
                result.add(marker(marker));
            }
        }
        return List.copyOf(result);
    }

    private static BlockPos safePos(BlockPos pos) {
        return pos == null ? BlockPos.ZERO : pos.immutable();
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** Marker: 客户端显示的单条矿脉深度标记。 */
    public record Marker(String veinId, String displayName, String productId, int minY, int maxY,
                         long remainingReserve, boolean selected) {
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(limit(veinId, MAX_TEXT_LENGTH), MAX_TEXT_LENGTH);
            buffer.writeUtf(limit(displayName, MAX_TEXT_LENGTH), MAX_TEXT_LENGTH);
            buffer.writeUtf(limit(productId, MAX_TEXT_LENGTH), MAX_TEXT_LENGTH);
            buffer.writeInt(minY);
            buffer.writeInt(maxY);
            buffer.writeLong(Math.max(0L, remainingReserve));
            buffer.writeBoolean(selected);
        }

        private static Marker decode(RegistryFriendlyByteBuf buffer) {
            return new Marker(buffer.readUtf(MAX_TEXT_LENGTH), buffer.readUtf(MAX_TEXT_LENGTH),
                    buffer.readUtf(MAX_TEXT_LENGTH), buffer.readInt(), buffer.readInt(),
                    Math.max(0L, buffer.readLong()), buffer.readBoolean());
        }
    }
}
