package common.cn.kafei.simukraft.path;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded LRU cache of successful path results, keyed by request signature. */
final class PathResultCache {
    private static final int MAX_ENTRIES = 512;
    private final LinkedHashMap<PathCacheKey, CacheEntry> entries =
            new LinkedHashMap<>(16, 0.75f, true);

    synchronized PathResult get(PathCacheKey key, long gameTime) {
        CacheEntry entry = entries.get(key);
        if (entry == null || entry.expiresAt < gameTime) {
            if (entry != null) {
                entries.remove(key);
            }
            return null;
        }
        return entry.result;
    }

    synchronized void put(PathCacheKey key, PathResult result, long gameTime, int ttlTicks) {
        if (key == null || result == null || !result.success() || ttlTicks <= 0) {
            return;
        }
        if (entries.size() >= MAX_ENTRIES) {
            trim(gameTime);
        }
        entries.put(key, new CacheEntry(result, gameTime + ttlTicks));
    }

    synchronized void clear() {
        entries.clear();
    }

    synchronized void cleanup(long gameTime) {
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt < gameTime);
    }

    // entries iterates in access order (least-recently-used first), so evicting from the
    // front discards the coldest entries rather than an arbitrary hash-order slice.
    private void trim(long gameTime) {
        cleanup(gameTime);
        for (Iterator<Map.Entry<PathCacheKey, CacheEntry>> iterator = entries.entrySet().iterator();
                iterator.hasNext() && entries.size() >= MAX_ENTRIES;) {
            iterator.next();
            iterator.remove();
        }
    }

    private record CacheEntry(PathResult result, long expiresAt) {
    }
}
