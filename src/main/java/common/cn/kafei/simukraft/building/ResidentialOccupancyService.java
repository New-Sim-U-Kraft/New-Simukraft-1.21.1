package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** ResidentialOccupancyService: 住宅是否允许被分配系统入住；默认允许。 */
@SuppressWarnings("null")
public final class ResidentialOccupancyService {
    private static final Set<String> CLOSED = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOADED_SERVERS = ConcurrentHashMap.newKeySet();

    private ResidentialOccupancyService() {
    }

    /** isOccupancyAllowed: 未记录时默认允许入住。 */
    public static boolean isOccupancyAllowed(ServerLevel level, UUID buildingId) {
        if (level == null || buildingId == null) {
            return true;
        }
        ensureLoaded(level);
        return !CLOSED.contains(key(level, buildingId));
    }

    /** setOccupancyAllowed: 更新内存并写入 SQLite。 */
    public static void setOccupancyAllowed(ServerLevel level, UUID buildingId, boolean allowed) {
        if (level == null || buildingId == null) {
            return;
        }
        ensureLoaded(level);
        String cacheKey = key(level, buildingId);
        if (allowed) {
            CLOSED.remove(cacheKey);
        } else {
            CLOSED.add(cacheKey);
        }
        SimuSqliteStorage.saveResidentialOccupancy(level, buildingId, allowed);
    }

    /** forget: 建筑拆除后清掉入住开关。 */
    public static void forget(ServerLevel level, UUID buildingId) {
        if (level == null || buildingId == null) {
            return;
        }
        CLOSED.remove(key(level, buildingId));
        SimuSqliteStorage.deleteResidentialOccupancy(level, buildingId);
    }

    /** clearCache: 关服时释放该存档的入住开关缓存。 */
    public static void clearCache(MinecraftServer server) {
        if (server == null) {
            return;
        }
        String serverKey = SaveScopedCacheKey.serverKey(server);
        CLOSED.removeIf(cacheKey -> cacheKey.startsWith(serverKey + "|"));
        LOADED_SERVERS.removeIf(loadedKey -> loadedKey.startsWith(serverKey));
    }

    private static void ensureLoaded(ServerLevel level) {
        String serverKey = SaveScopedCacheKey.serverKey(level.getServer());
        if (!LOADED_SERVERS.add(serverKey)) {
            return;
        }
        for (UUID buildingId : SimuSqliteStorage.loadClosedResidentialOccupancy(level)) {
            CLOSED.add(serverKey + "|" + buildingId);
        }
    }

    private static String key(ServerLevel level, UUID buildingId) {
        return SaveScopedCacheKey.serverKey(level.getServer()) + "|" + buildingId;
    }
}
