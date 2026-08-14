package common.cn.kafei.simukraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/** NPC 工作区块加载：任务锚点维持实体 tick，工作范围仅维持区块加载。 */
@SuppressWarnings("null")
public final class NpcWorkChunkLoadService {
    private static final int ANCHOR_TICKET_DISTANCE = 2;
    private static final int WORK_AREA_TICKET_DISTANCE = 0;
    private static final TicketType<ChunkPos> WORK_AREA_TICKET = TicketType.create(
            "simukraft_npc_work_area", Comparator.comparingLong(ChunkPos::toLong));

    private static final ConcurrentMap<String, WorkLease> WORK_LEASES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<TicketKey, AtomicInteger> ANCHOR_TICKET_REFS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<TicketKey, AtomicInteger> WORK_AREA_TICKET_REFS = new ConcurrentHashMap<>();

    private NpcWorkChunkLoadService() {
    }

    /** acquire：为一个 NPC 工作任务申请建筑盒锚点的实体 tick ticket。 */
    public static void acquire(ServerLevel level, UUID taskId, BlockPos workPos) {
        if (level == null || taskId == null || workPos == null) {
            return;
        }
        ChunkPos anchor = new ChunkPos(workPos);
        WorkLease previous = WORK_LEASES.putIfAbsent(leaseKey(level, taskId), new WorkLease(anchor));
        if (previous == null) {
            retainAnchorTicket(level, anchor);
        }
    }

    /** loadWorkArea：为任务边界覆盖的区块申请不强制 tick 的加载 ticket。 */
    public static void loadWorkArea(ServerLevel level, UUID taskId, BlockPos minPos, BlockPos maxPos,
            int horizontalPadding) {
        if (level == null || taskId == null || minPos == null || maxPos == null || horizontalPadding < 0) {
            return;
        }
        WorkLease lease = WORK_LEASES.get(leaseKey(level, taskId));
        if (lease == null) {
            return;
        }
        for (long chunkLong : collectWorkAreaChunks(minPos, maxPos, horizontalPadding)) {
            if (chunkLong == lease.anchor().toLong() || !lease.workAreaChunks().add(chunkLong)) {
                continue;
            }
            retainWorkAreaTicket(level, new ChunkPos(chunkLong));
        }
    }

    /** release：释放一个任务持有的锚点与全部工作范围 ticket。 */
    public static void release(ServerLevel level, UUID taskId) {
        if (level == null || taskId == null) {
            return;
        }
        WorkLease lease = WORK_LEASES.remove(leaseKey(level, taskId));
        if (lease == null) {
            return;
        }
        releaseAnchorTicket(level, lease.anchor());
        lease.workAreaChunks().forEach(chunkLong -> releaseWorkAreaTicket(level, new ChunkPos(chunkLong)));
    }

    /** clearServerCaches：关服时释放残留 ticket，避免静态运行时状态跨存档保留。 */
    public static void clearServerCaches(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            clearLevelTickets(level);
        }
    }

    /** collectWorkAreaChunks：将方块边界和水平缓冲区转换为需要加载的区块集合。 */
    static Set<Long> collectWorkAreaChunks(BlockPos firstPos, BlockPos secondPos, int horizontalPadding) {
        int minBlockX = Math.min(firstPos.getX(), secondPos.getX()) - horizontalPadding;
        int minBlockZ = Math.min(firstPos.getZ(), secondPos.getZ()) - horizontalPadding;
        int maxBlockX = Math.max(firstPos.getX(), secondPos.getX()) + horizontalPadding;
        int maxBlockZ = Math.max(firstPos.getZ(), secondPos.getZ()) + horizontalPadding;
        int minChunkX = minBlockX >> 4;
        int minChunkZ = minBlockZ >> 4;
        int maxChunkX = maxBlockX >> 4;
        int maxChunkZ = maxBlockZ >> 4;
        Set<Long> chunks = ConcurrentHashMap.newKeySet();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.add(ChunkPos.asLong(chunkX, chunkZ));
            }
        }
        return Set.copyOf(chunks);
    }

    /** retainAnchorTicket：增加建筑盒锚点的实体 tick ticket 引用。 */
    private static void retainAnchorTicket(ServerLevel level, ChunkPos chunkPos) {
        TicketKey ticketKey = ticketKey(level, chunkPos);
        ANCHOR_TICKET_REFS.compute(ticketKey, (ignored, counter) -> {
            if (counter == null) {
                level.getChunkSource().addRegionTicket(
                        TicketType.FORCED, chunkPos, ANCHOR_TICKET_DISTANCE, chunkPos);
                return new AtomicInteger(1);
            }
            counter.incrementAndGet();
            return counter;
        });
    }

    /** releaseAnchorTicket：减少建筑盒锚点的实体 tick ticket 引用。 */
    private static void releaseAnchorTicket(ServerLevel level, ChunkPos chunkPos) {
        TicketKey ticketKey = ticketKey(level, chunkPos);
        ANCHOR_TICKET_REFS.computeIfPresent(ticketKey, (ignored, counter) -> {
            if (counter.decrementAndGet() > 0) {
                return counter;
            }
            level.getChunkSource().removeRegionTicket(
                    TicketType.FORCED, chunkPos, ANCHOR_TICKET_DISTANCE, chunkPos);
            return null;
        });
    }

    /** retainWorkAreaTicket：增加任务区块的非 tick 加载 ticket 引用。 */
    private static void retainWorkAreaTicket(ServerLevel level, ChunkPos chunkPos) {
        TicketKey ticketKey = ticketKey(level, chunkPos);
        WORK_AREA_TICKET_REFS.compute(ticketKey, (ignored, counter) -> {
            if (counter == null) {
                level.getChunkSource().addRegionTicket(
                        WORK_AREA_TICKET, chunkPos, WORK_AREA_TICKET_DISTANCE, chunkPos, false);
                return new AtomicInteger(1);
            }
            counter.incrementAndGet();
            return counter;
        });
    }

    /** releaseWorkAreaTicket：减少任务区块的非 tick 加载 ticket 引用。 */
    private static void releaseWorkAreaTicket(ServerLevel level, ChunkPos chunkPos) {
        TicketKey ticketKey = ticketKey(level, chunkPos);
        WORK_AREA_TICKET_REFS.computeIfPresent(ticketKey, (ignored, counter) -> {
            if (counter.decrementAndGet() > 0) {
                return counter;
            }
            level.getChunkSource().removeRegionTicket(
                    WORK_AREA_TICKET, chunkPos, WORK_AREA_TICKET_DISTANCE, chunkPos, false);
            return null;
        });
    }

    /** clearLevelTickets：释放一个维度中全部未归还的 NPC 工作 ticket。 */
    private static void clearLevelTickets(ServerLevel level) {
        String levelKey = SaveScopedCacheKey.levelKey(level);
        WORK_LEASES.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(levelKey + "|"))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(key -> release(level, UUID.fromString(key.substring(key.lastIndexOf('|') + 1))));
        removeRemainingTickets(level, ANCHOR_TICKET_REFS, true);
        removeRemainingTickets(level, WORK_AREA_TICKET_REFS, false);
    }

    /** removeRemainingTickets：清除 lease 账目之外的异常残留 ticket。 */
    private static void removeRemainingTickets(ServerLevel level,
            ConcurrentMap<TicketKey, AtomicInteger> ticketRefs, boolean forceTicks) {
        String levelKey = SaveScopedCacheKey.levelKey(level);
        ticketRefs.keySet().stream()
                .filter(ticketKey -> ticketKey.levelKey().equals(levelKey))
                .toList()
                .forEach(ticketKey -> {
                    if (ticketRefs.remove(ticketKey) == null) {
                        return;
                    }
                    ChunkPos chunkPos = new ChunkPos(ticketKey.chunkLong());
                    if (forceTicks) {
                        level.getChunkSource().removeRegionTicket(
                                TicketType.FORCED, chunkPos, ANCHOR_TICKET_DISTANCE, chunkPos);
                    } else {
                        level.getChunkSource().removeRegionTicket(
                                WORK_AREA_TICKET, chunkPos, WORK_AREA_TICKET_DISTANCE, chunkPos, false);
                    }
                });
    }

    /** leaseKey：生成任务在指定存档维度内的唯一 lease 键。 */
    private static String leaseKey(ServerLevel level, UUID taskId) {
        return SaveScopedCacheKey.levelKey(level) + "|" + taskId;
    }

    /** ticketKey：生成指定维度区块的 ticket 引用键。 */
    private static TicketKey ticketKey(ServerLevel level, ChunkPos chunkPos) {
        return new TicketKey(SaveScopedCacheKey.levelKey(level), chunkPos.toLong());
    }

    private record TicketKey(String levelKey, long chunkLong) {
    }

    private record WorkLease(ChunkPos anchor, Set<Long> workAreaChunks) {
        private WorkLease(ChunkPos anchor) {
            this(anchor, ConcurrentHashMap.newKeySet());
        }
    }
}
