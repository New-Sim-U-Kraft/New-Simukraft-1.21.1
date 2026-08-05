package common.cn.kafei.simukraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 维持 NPC 工作区块强制加载：任务开始时注册 Ticket，任务结束时释放。
 * 同一区块被多个任务引用时，只在最后一个任务结束后才真正移除 Ticket。
 */

@SuppressWarnings("null")
public final class NpcWorkChunkLoadService {
    private static final int TICKET_DISTANCE = 2;
    private static final ConcurrentMap<String, AtomicInteger> REF_COUNTS = new ConcurrentHashMap<>();

    private NpcWorkChunkLoadService() {
    }

    public static void load(ServerLevel level, BlockPos workPos) {
        if (level == null || workPos == null) return;
        String key = key(level, workPos);
        int count = REF_COUNTS.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        if (count == 1) {
            ChunkPos chunkPos = new ChunkPos(workPos);
            level.getChunkSource().addRegionTicket(TicketType.FORCED, chunkPos, TICKET_DISTANCE, chunkPos);
        }
    }

    public static void release(ServerLevel level, BlockPos workPos) {
        if (level == null || workPos == null) return;
        String key = key(level, workPos);
        AtomicInteger counter = REF_COUNTS.get(key);
        if (counter == null) return;
        if (counter.decrementAndGet() <= 0) {
            REF_COUNTS.remove(key);
            ChunkPos chunkPos = new ChunkPos(workPos);
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunkPos, TICKET_DISTANCE, chunkPos);
        }
    }

    public static void clearServerCaches(MinecraftServer server) {
        String prefix = SaveScopedCacheKey.serverKey(server) + "|";
        REF_COUNTS.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String key(ServerLevel level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        return SaveScopedCacheKey.levelKey(level) + "|" + chunkPos.x + "," + chunkPos.z;
    }
}
