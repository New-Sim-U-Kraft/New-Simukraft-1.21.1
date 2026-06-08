package common.cn.kafei.simukraft.protection;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class NpcBlockProtectionPolicy {
    private static final long SKIP_LOG_INTERVAL_TICKS = 200L;
    private static final long LOG_CACHE_TTL_TICKS = 1200L;
    private static final int LOG_CACHE_CLEANUP_THRESHOLD = 4096;
    private static final ConcurrentMap<String, Long> LAST_SKIP_LOG_TICKS = new ConcurrentHashMap<>();
    private static volatile ConfigSnapshot cachedSnapshot = null;
    private static volatile Set<String> cachedBlacklist = Set.of();

    private NpcBlockProtectionPolicy() {
    }

    /** isProtected: 判断建筑师和规划师是否必须跳过该方块。 */
    public static boolean isProtected(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        ConfigSnapshot snapshot = snapshot();
        return snapshot.enabled() && blacklist(snapshot).contains(blockId(state.getBlock()));
    }

    /** logSkipped: 按配置记录被黑名单保护跳过的方块。 */
    public static void logSkipped(String workerType, ServerLevel level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null || !ServerConfig.logBlacklistSkippedBlocks()) {
            return;
        }
        long gameTime = level.getGameTime();
        String key = level.dimension().location() + "|" + normalize(workerType) + "|" + pos.asLong();
        Long previous = LAST_SKIP_LOG_TICKS.put(key, gameTime);
        if (previous != null && gameTime - previous < SKIP_LOG_INTERVAL_TICKS) {
            return;
        }
        if (LAST_SKIP_LOG_TICKS.size() > LOG_CACHE_CLEANUP_THRESHOLD) {
            cleanupLogCache(gameTime);
        }
        SimuKraft.LOGGER.info("Simukraft: {} skipped protected block {} at {}", workerType, blockId(state.getBlock()), pos.toShortString());
    }

    /** clearCache: 服务端配置变动或关服时清理解析缓存。 */
    public static void clearCache() {
        synchronized (NpcBlockProtectionPolicy.class) {
            cachedSnapshot = null;
            cachedBlacklist = Set.of();
            LAST_SKIP_LOG_TICKS.clear();
        }
    }

    private static Set<String> blacklist(ConfigSnapshot snapshot) {
        Set<String> blacklist = cachedBlacklist;
        if (snapshot.equals(cachedSnapshot)) {
            return blacklist;
        }
        synchronized (NpcBlockProtectionPolicy.class) {
            if (!snapshot.equals(cachedSnapshot)) {
                cachedBlacklist = parseBlacklist(snapshot.blacklist());
                cachedSnapshot = snapshot;
            }
            return cachedBlacklist;
        }
    }

    private static Set<String> parseBlacklist(List<String> entries) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String entry : entries) {
            String id = normalize(entry);
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return Set.copyOf(ids);
    }

    private static ConfigSnapshot snapshot() {
        return new ConfigSnapshot(ServerConfig.blacklistProtectionEnabled(), ServerConfig.allModeBlockBlacklist());
    }

    private static String blockId(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? "" : normalize(id.toString());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void cleanupLogCache(long gameTime) {
        LAST_SKIP_LOG_TICKS.entrySet().removeIf(entry -> gameTime - entry.getValue() > LOG_CACHE_TTL_TICKS);
    }

    private record ConfigSnapshot(boolean enabled, List<String> blacklist) {
        private ConfigSnapshot {
            blacklist = List.copyOf(blacklist);
        }
    }
}
