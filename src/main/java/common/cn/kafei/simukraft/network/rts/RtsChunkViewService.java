package common.cn.kafei.simukraft.network.rts;

import common.cn.kafei.simukraft.mixin.MixinChunkMapAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** RTS 摄像机区块视窗：维护焦点票据，并让原版区块和实体追踪以焦点为中心。 */
@SuppressWarnings("null")
public final class RtsChunkViewService {
    private static final long FOCUS_UPDATE_INTERVAL_TICKS = 2L;
    private static final TicketType<UUID> RTS_VIEW_TICKET = TicketType.create("simukraft_rts_view",
            Comparator.comparingLong(UUID::getMostSignificantBits)
                    .thenComparingLong(UUID::getLeastSignificantBits));
    private static final ConcurrentMap<UUID, RtsView> VIEWS = new ConcurrentHashMap<>();
    private static final ThreadLocal<ServerPlayer> TRACKING_PLAYER = new ThreadLocal<>();

    private RtsChunkViewService() {
    }

    /** activate: 登记摄像机焦点并为焦点周围申请与实际视距一致的非 tick 区块票据。 */
    public static void activate(ServerPlayer player, ChunkPos focus) {
        if (!(player != null && player.level() instanceof ServerLevel level) || focus == null
                || !level.getWorldBorder().isWithinBounds(focus)) {
            return;
        }
        MixinChunkMapAccessor chunkMap = chunkMap(level);
        int viewDistance = chunkMap != null
                ? chunkMap.simukraft$getPlayerViewDistance(player)
                : level.getServer().getPlayerList().getViewDistance();
        long gameTime = level.getGameTime();
        RtsView nextView = new RtsView(level.getServer(), level.dimension(), focus, viewDistance, gameTime);
        RtsView previousView = VIEWS.get(player.getUUID());
        if (sameView(previousView, nextView)) {
            return;
        }
        if (previousView != null && previousView.server() == level.getServer()
                && previousView.dimension().equals(level.dimension())
                && gameTime - previousView.updatedAt() < FOCUS_UPDATE_INTERVAL_TICKS) {
            return;
        }
        VIEWS.put(player.getUUID(), nextView);
        releaseTicket(player.getUUID(), previousView);
        level.getChunkSource().addRegionTicket(RTS_VIEW_TICKET, focus, viewDistance, player.getUUID());
        if (chunkMap != null) {
            refreshTracking(chunkMap, player);
        }
    }

    /** deactivate: 移除摄像机票据并立即让客户端区块缓存中心回到玩家本体。 */
    public static void deactivate(ServerPlayer player) {
        if (player == null) {
            return;
        }
        RtsView previousView = VIEWS.remove(player.getUUID());
        releaseTicket(player.getUUID(), previousView);
        if (player.level() instanceof ServerLevel level) {
            MixinChunkMapAccessor chunkMap = chunkMap(level);
            if (chunkMap != null) {
                refreshTracking(chunkMap, player);
            }
        }
    }

    /** clear: 在断线时只释放票据；原版玩家移除流程会负责发送区块卸载包。 */
    public static void clear(ServerPlayer player) {
        if (player != null) {
            releaseTicket(player.getUUID(), VIEWS.remove(player.getUUID()));
        }
    }

    /** clearServer: 服务器停止时清理静态视窗状态，避免下次集成服启动残留引用。 */
    public static void clearServer(MinecraftServer server) {
        if (server == null) {
            return;
        }
        VIEWS.forEach((playerId, view) -> {
            if (view.server() == server && VIEWS.remove(playerId, view)) {
                releaseTicket(playerId, view);
            }
        });
        TRACKING_PLAYER.remove();
    }

    /** enterTrackingUpdate: 记录本次原版 ChunkMap 刷新所属玩家，供 Mixin 替换追踪中心。 */
    public static void enterTrackingUpdate(ServerPlayer player) {
        if (player != null) {
            TRACKING_PLAYER.set(player);
        }
    }

    /** leaveTrackingUpdate: 清除线程局部玩家，防止后续原版刷新误用 RTS 焦点。 */
    public static void leaveTrackingUpdate() {
        TRACKING_PLAYER.remove();
    }

    /** trackingView: RTS 激活时返回焦点视窗，否则保持原版以玩家本体为中心的视窗。 */
    public static ChunkTrackingView trackingView(ChunkPos vanillaCenter, int viewDistance) {
        ServerPlayer player = TRACKING_PLAYER.get();
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return ChunkTrackingView.of(vanillaCenter, viewDistance);
        }
        RtsView view = currentView(level, player);
        return view == null ? ChunkTrackingView.of(vanillaCenter, viewDistance)
                : ChunkTrackingView.of(view.focus(), viewDistance);
    }

    /** isTargetReachable: RTS 激活时限制操作在当前已发送视窗内；普通状态沿用原本距离限制。 */
    public static boolean isTargetReachable(ServerLevel level, ServerPlayer player, BlockPos target,
                                            double vanillaDistance) {
        if (level == null || player == null || target == null) {
            return false;
        }
        RtsView view = currentView(level, player);
        if (view == null) {
            return player.blockPosition().closerThan(target, vanillaDistance);
        }
        return ChunkTrackingView.isWithinDistance(view.focus().x, view.focus().z, view.viewDistance(),
                target.getX() >> 4, target.getZ() >> 4, false);
    }

    /** viewCenter: 返回当前 RTS 焦点用于边界快照；非 RTS 状态回退到玩家所在区块。 */
    public static ChunkPos viewCenter(ServerLevel level, ServerPlayer player) {
        RtsView view = currentView(level, player);
        return view == null ? player.chunkPosition() : view.focus();
    }

    private static RtsView currentView(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) {
            return null;
        }
        RtsView view = VIEWS.get(player.getUUID());
        return view != null && view.server() == level.getServer() && view.dimension().equals(level.dimension())
                ? view : null;
    }

    private static MixinChunkMapAccessor chunkMap(ServerLevel level) {
        if (level == null || !(level.getChunkSource().chunkMap instanceof MixinChunkMapAccessor chunkMap)) {
            return null;
        }
        return chunkMap;
    }

    private static void releaseTicket(UUID playerId, RtsView view) {
        if (playerId == null || view == null || view.server() == null) {
            return;
        }
        ServerLevel level = view.server().getLevel(view.dimension());
        if (level != null) {
            level.getChunkSource().removeRegionTicket(RTS_VIEW_TICKET, view.focus(), view.viewDistance(), playerId);
        }
    }

    /** refreshTracking: 焦点从本体区块出发时清除原版短路条件，再执行原版增量同步。 */
    private static void refreshTracking(MixinChunkMapAccessor chunkMap, ServerPlayer player) {
        if (chunkMap == null || player == null) {
            return;
        }
        ChunkTrackingView currentView = player.getChunkTrackingView();
        if (currentView instanceof ChunkTrackingView.Positioned positioned
                && positioned.center().equals(player.chunkPosition())) {
            player.setChunkTrackingView(ChunkTrackingView.EMPTY);
        }
        chunkMap.simukraft$refreshRtsTracking(player);
    }

    private static boolean sameView(RtsView first, RtsView second) {
        return first != null && second != null && first.server() == second.server()
                && first.dimension().equals(second.dimension()) && first.focus().equals(second.focus())
                && first.viewDistance() == second.viewDistance();
    }

    private record RtsView(MinecraftServer server, net.minecraft.resources.ResourceKey<Level> dimension,
                           ChunkPos focus, int viewDistance, long updatedAt) {
    }
}
