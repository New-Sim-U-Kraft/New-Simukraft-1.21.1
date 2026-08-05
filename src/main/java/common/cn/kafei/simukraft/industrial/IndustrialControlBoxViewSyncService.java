package common.cn.kafei.simukraft.industrial;

import common.cn.kafei.simukraft.network.industrial.IndustrialControlBoxViewUpdatePacket;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class IndustrialControlBoxViewSyncService {
    private static final long MIN_SYNC_INTERVAL_TICKS = 5L;
    private static final double SYNC_RADIUS = 64.0D;
    private static final ConcurrentMap<String, SyncState> LAST_SYNC_STATES = new ConcurrentHashMap<>();

    private IndustrialControlBoxViewSyncService() {
    }

    /** syncStatusIfChanged: 工业状态变化时向附近打开控制箱的客户端推送视图。 */
    public static void syncStatusIfChanged(ServerLevel level, IndustrialBoxData data) {
        if (level == null || data == null || data.boxPos() == null) {
            return;
        }
        long gameTime = level.getGameTime();
        String key = key(level, data.boxPos());
        StatusSnapshot snapshot = new StatusSnapshot(data.statusKey(), data.statusText(), data.running(), data.currentStep());
        SyncState previous = LAST_SYNC_STATES.get(key);
        if (previous != null) {
            if (snapshot.equals(previous.snapshot())) {
                return;
            }
            boolean urgent = isUrgent(snapshot)
                    || !Objects.equals(snapshot.statusKey(), previous.snapshot().statusKey())
                    || snapshot.running() != previous.snapshot().running()
                    || snapshot.currentStep() != previous.snapshot().currentStep();
            if (!urgent && gameTime - previous.syncedAt() < MIN_SYNC_INTERVAL_TICKS) {
                return;
            }
        }
        LAST_SYNC_STATES.put(key, new SyncState(snapshot, gameTime));
        BlockPos pos = data.boxPos();
        PacketDistributor.sendToPlayersNear(
                level,
                null,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                SYNC_RADIUS,
                IndustrialControlBoxViewUpdatePacket.from(IndustrialControlBoxService.buildView(level, pos))
        );
    }

    /** isUrgent: 阻塞和缺失类状态立即推送，便于玩家看到故障原因。 */
    private static boolean isUrgent(StatusSnapshot snapshot) {
        String key = snapshot.statusKey();
        return key.contains("blocked")
                || key.contains("invalid")
                || key.contains("missing")
                || key.contains("output_full")
                || key.contains("carry_full");
    }

    /** clearServerCaches: 清理视图同步快照，避免跨存档复用。 */
    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server).toLowerCase(Locale.ROOT);
        LAST_SYNC_STATES.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT) + "|box=" + pos.asLong();
    }

    private record StatusSnapshot(String statusKey, String statusText, boolean running, int currentStep) {
        private StatusSnapshot {
            statusKey = Objects.toString(statusKey, "");
            statusText = Objects.toString(statusText, "");
        }
    }

    private record SyncState(StatusSnapshot snapshot, long syncedAt) {
    }
}
