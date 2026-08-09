package common.cn.kafei.simukraft.mineraldrilling;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

/** MineralDrillingMenuHolder: 持有容器权威状态并处理 LDLib2 服务端交互。 */
@SuppressWarnings("null")
public final class MineralDrillingMenuHolder implements IContainerUIHolder {
    private final MineralDrillingInventory inventory;
    private final MineralDrillingBoxData data;
    private final ServerLevel serverLevel;
    private volatile MineralDrillingMenuSnapshot snapshot;
    private volatile long snapshotGameTime = Long.MIN_VALUE;

    /** 创建菜单持有器，并区分服务端权威库存和客户端镜像库存。 */
    public MineralDrillingMenuHolder(MineralDrillingMenuSnapshot snapshot,
                                     MineralDrillingInventory inventory,
                                     MineralDrillingBoxData data,
                                     ServerLevel serverLevel) {
        this.snapshot = snapshot != null ? snapshot : MineralDrillingMenuSnapshot.empty(BlockPos.ZERO);
        this.inventory = inventory != null ? inventory : new MineralDrillingInventory();
        this.data = data;
        this.serverLevel = serverLevel;
    }

    /** boxPos: 返回当前菜单绑定的控制箱坐标。 */
    public BlockPos boxPos() {
        return currentSnapshot().boxPos();
    }

    /** inventory: 返回菜单使用的两格钻井工具库存。 */
    public MineralDrillingInventory inventory() {
        return inventory;
    }

    /** snapshot: 返回当前不可变界面快照。 */
    public MineralDrillingMenuSnapshot snapshot() {
        return currentSnapshot();
    }

    /** createUI: 两端创建同序元素树，客户端按逻辑分辨率调整布局尺寸。 */
    @Override
    public ModularUI createUI(Player player) {
        if (player != null && player.level().isClientSide()) {
            ModularUI clientUi = MineralDrillingUiBridge.create(this, player);
            if (clientUi != null) {
                return clientUi;
            }
        }
        return MineralDrillingUiLayout.createModularUi(
                this, player, MineralDrillingUiMetrics.maximum(), MineralDrillingUiLayout.ClientActions.NONE);
    }

    /** isStillValid: 持续校验维度、八格距离和目标方块，防止远程操作失效控制箱。 */
    @Override
    public boolean isStillValid(Player player) {
        if (player == null || player.level().isClientSide()) {
            return true;
        }
        return serverLevel != null
                && player.level() == serverLevel
                && MineralDrillingControlBoxService.isControlBox(serverLevel, boxPos())
                && (player.distanceToSqr(boxPos().getCenter()) <= 64.0D
                || player instanceof ServerPlayer serverPlayer && RtsRemoteMenuAccess.hasAccess(serverPlayer, boxPos()));
    }

    Component buildingText() {
        MineralDrillingMenuSnapshot current = currentSnapshot();
        Component value = current.hasBuilding() && !current.buildingName().isBlank()
                ? Component.literal(current.buildingName())
                : Component.translatable("gui.simukraft.mineral_drilling.none");
        return Component.translatable("gui.simukraft.mineral_drilling.building", value);
    }

    Component workerText() {
        MineralDrillingMenuSnapshot current = currentSnapshot();
        Component value = current.hasWorker() && !current.workerName().isBlank()
                ? Component.literal(current.workerName())
                : Component.translatable("gui.simukraft.mineral_drilling.none");
        return Component.translatable("gui.simukraft.mineral_drilling.worker", value);
    }

    Component statusText() {
        MineralDrillingMenuSnapshot current = currentSnapshot();
        Component status = current.statusKey().isBlank()
                ? Component.translatable("gui.simukraft.mineral_drilling.status.idle")
                : Component.translatable(current.statusKey());
        return Component.translatable("gui.simukraft.mineral_drilling.status", status);
    }

    Component integrityText() {
        MineralDrillingMenuSnapshot current = currentSnapshot();
        if (!current.integrityAvailable()) {
            return Component.translatable("gui.simukraft.mineral_drilling.integrity_unavailable");
        }
        return Component.translatable("gui.simukraft.mineral_drilling.integrity",
                Math.round(Math.clamp(current.integrityPercent(), 0.0F, 1.0F) * 100.0F));
    }

    Component mineralText() {
        String name = currentSnapshot().selectedVeinName();
        return Component.translatable("gui.simukraft.mineral_drilling.current_mineral",
                name.isBlank() ? Component.translatable("gui.simukraft.mineral_drilling.none") : Component.literal(name));
    }

    /** productId: 返回服务端同步给客户端的原始产物资源 ID，不在此处解析本地化名称。 */
    String productId() {
        return currentSnapshot().selectedProductId();
    }

    Component depthText() {
        return Component.translatable("gui.simukraft.mineral_drilling.depth", drillDepth());
    }

    Component toggleText() {
        return Component.translatable(currentSnapshot().running()
                ? "gui.simukraft.mineral_drilling.pause"
                : "gui.simukraft.mineral_drilling.start");
    }

    Component boundsText() {
        return Component.translatable("gui.simukraft.mineral_drilling.show_bounds");
    }

    Component markerText(int index) {
        MineralDrillingMenuSnapshot.Marker marker = marker(index);
        if (marker == null) {
            return Component.empty();
        }
        return Component.translatable("gui.simukraft.mineral_drilling.vein_marker",
                marker.displayName(), marker.minY(), marker.maxY(), marker.remainingReserve());
    }

    float integrityProgress() {
        MineralDrillingMenuSnapshot current = currentSnapshot();
        return current.integrityAvailable()
                ? Math.clamp(current.integrityPercent(), 0.0F, 1.0F)
                : 0.0F;
    }

    float drillDepthValue() {
        return drillDepth();
    }

    float markerDepthValue(int index) {
        MineralDrillingMenuSnapshot.Marker marker = marker(index);
        if (marker == null) {
            return minDepth();
        }
        return marker.minY() + (marker.maxY() - marker.minY()) * 0.5F;
    }

    /** markerMinDepthValue: 返回矿脉范围较深一端的 Y，供右侧长条底部定位。 */
    float markerMinDepthValue(int index) {
        MineralDrillingMenuSnapshot.Marker marker = marker(index);
        return marker == null ? minDepth() : marker.minY();
    }

    /** markerMaxDepthValue: 返回矿脉范围较浅一端的 Y，供右侧长条顶部定位。 */
    float markerMaxDepthValue(int index) {
        MineralDrillingMenuSnapshot.Marker marker = marker(index);
        return marker == null ? minDepth() : marker.maxY();
    }

    int minDepth() {
        MineralDrillingMenuSnapshot current = currentSnapshot();
        return Math.min(current.minDepth(), current.maxDepth());
    }

    int maxDepth() {
        MineralDrillingMenuSnapshot current = currentSnapshot();
        return Math.max(current.minDepth(), current.maxDepth());
    }

    boolean hasMarker(int index) {
        return marker(index) != null;
    }

    /** setDrillDepth: 接收菜单提交的最终深度并保留控制箱服务端校验。 */
    void setDrillDepth(Player player, int requestedDepth) {
        runServerAction(player, "set depth", level -> MineralDrillingControlBoxService.setDrillDepth(
                level, boxPos(), Math.clamp(requestedDepth, minDepth(), maxDepth())), false);
    }

    void toggleRunning(Player player) {
        runServerAction(player, "toggle running",
                level -> MineralDrillingControlBoxService.toggleRunning(level, boxPos()), false);
    }

    void fireWorker(Player player) {
        MineralDrillingMenuSnapshot current = currentSnapshot();
        UUID expectedWorkerId = current.workerId();
        if (!current.hasWorker() || expectedWorkerId == null) {
            return;
        }
        runServerAction(player, "fire worker",
                level -> MineralDrillingControlBoxService.fireWorker(
                        level, (ServerPlayer) player, boxPos(), expectedWorkerId), false);
    }

    void demolish(Player player) {
        if (!currentSnapshot().hasBuilding()) {
            return;
        }
        runServerAction(player, "demolish",
                level -> MineralDrillingControlBoxService.demolish(level, (ServerPlayer) player, boxPos()), true);
    }

    private int drillDepth() {
        return data != null ? data.drillDepth() : currentSnapshot().drillDepth();
    }

    private MineralDrillingMenuSnapshot.Marker marker(int index) {
        List<MineralDrillingMenuSnapshot.Marker> markers = currentSnapshot().markers();
        return index >= 0 && markers != null && index < markers.size() ? markers.get(index) : null;
    }

    private void runServerAction(Player player,
                                 String actionName,
                                 ServerAction action,
                                 boolean closeOnSuccess) {
        if (!(player instanceof ServerPlayer serverPlayer) || !isStillValid(player) || serverLevel == null) {
            return;
        }
        try {
            completeServerAction(action.run(serverLevel), closeOnSuccess,
                    this::refreshSnapshot, serverPlayer::closeContainer);
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.error("Failed to {} mineral drilling control box at {}", actionName, boxPos(), exception);
        }
    }

    /** completeServerAction: 成功拆除时关闭容器，其他结果才刷新快照，避免重建已删除状态。 */
    static void completeServerAction(boolean succeeded,
                                     boolean closeOnSuccess,
                                     Runnable refreshAction,
                                     Runnable closeAction) {
        if (succeeded && closeOnSuccess) {
            closeAction.run();
            return;
        }
        refreshAction.run();
    }

    @FunctionalInterface
    private interface ServerAction {
        /** run: 在服务端线程执行一次经过菜单校验的业务操作。 */
        boolean run(ServerLevel level);
    }

    private void refreshSnapshot() {
        if (serverLevel == null) {
            return;
        }
        synchronized (this) {
            refreshSnapshotLocked();
        }
    }

    /** currentSnapshot: 按世界 tick 刷新一次服务端视图，让多个绑定共享同一快照。 */
    private MineralDrillingMenuSnapshot currentSnapshot() {
        if (serverLevel == null) {
            return snapshot;
        }
        long gameTime = serverLevel.getGameTime();
        if (gameTime == snapshotGameTime) {
            return snapshot;
        }
        try {
            synchronized (this) {
                if (gameTime != snapshotGameTime) {
                    refreshSnapshotLocked();
                }
            }
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.error("Failed to refresh mineral drilling menu snapshot at {}", snapshot.boxPos(), exception);
        }
        return snapshot;
    }

    private void refreshSnapshotLocked() {
        BlockPos position = snapshot.boxPos();
        snapshot = MineralDrillingMenuSnapshot.fromView(
                MineralDrillingControlBoxService.buildView(serverLevel, position));
        snapshotGameTime = serverLevel.getGameTime();
    }
}
