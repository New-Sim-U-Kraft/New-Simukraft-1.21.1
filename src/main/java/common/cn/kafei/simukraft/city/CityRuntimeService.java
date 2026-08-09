package common.cn.kafei.simukraft.city;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.citizen.CitizenWorkplaceMoveService;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 城市激活状态和离线居民按需恢复。 */
@SuppressWarnings("null")
public final class CityRuntimeService {
    private static final long RECOVERY_PROCESS_INTERVAL_TICKS = 20L;
    private static final long ACTIVE_GRACE_TICKS = 20L * 15L;
    private static final long RECOVERY_CHUNK_TIMEOUT_TICKS = 20L * 3L;
    private static final long RECOVERY_RETRY_DELAY_TICKS = 20L * 60L;
    private static final int MAX_PENDING_RECOVERIES_PER_CITY = 8;
    private static final int MAX_LEGACY_RECOVERY_CITY_CHUNKS = 32;
    private static final int RECOVERY_TICKET_DISTANCE = 2;
    private static final TicketType<ChunkPos> CITIZEN_RECOVERY_TICKET = TicketType.create(
            "simukraft_citizen_recovery", Comparator.comparingLong(ChunkPos::toLong));
    private static final ConcurrentMap<String, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, AtomicInteger> RECOVERY_TICKET_REFS = new ConcurrentHashMap<>();

    private CityRuntimeService() {
    }

    /** tick：每 tick 刷新玩家触发的城市状态，每秒处理居民恢复 ticket。 */
    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        LevelRuntime runtime = RUNTIMES.computeIfAbsent(SaveScopedCacheKey.levelKey(level), ignored -> new LevelRuntime());
        long gameTime = level.getGameTime();
        CityManager cityManager = CityManager.get(level);
        for (ServerPlayer player : level.players()) {
            cityManager.getPlayerCity(player.getUUID())
                    .filter(city -> CityService.belongsToLevel(level, city))
                    .filter(city -> city.hasPermission(player.getUUID(), CityPermissionLevel.OFFICIAL))
                    .ifPresent(city -> activate(runtime, city.cityId(), gameTime));
        }
        if (gameTime % RECOVERY_PROCESS_INTERVAL_TICKS != 0L) {
            return;
        }
        runtime.cities.forEach((cityId, cityRuntime) -> {
            cityRuntime.state = gameTime - cityRuntime.lastActiveTick <= ACTIVE_GRACE_TICKS
                    ? CityRuntimeState.ACTIVE
                    : CityRuntimeState.SLEEPING;
            cityRuntime.nextRecoveryAttemptTick.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
            if (cityRuntime.state == CityRuntimeState.ACTIVE) {
                processRecoveries(level, cityRuntime, gameTime);
            } else {
                releaseAllRecoveries(level, cityRuntime);
            }
        });
    }

    /** isCityActive：判断城市是否允许执行实体级操作。 */
    public static boolean isCityActive(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return false;
        }
        LevelRuntime runtime = RUNTIMES.get(SaveScopedCacheKey.levelKey(level));
        CityRuntime cityRuntime = runtime != null ? runtime.cities.get(cityId) : null;
        return cityRuntime != null && cityRuntime.state == CityRuntimeState.ACTIVE;
    }

    /** isCitizenActive：判断居民所属城市是否允许执行实体级操作。 */
    public static boolean isCitizenActive(ServerLevel level, CitizenData citizen) {
        return citizen != null && !citizen.dead()
                && level != null
                && level.dimension().location().toString().equals(citizen.dimensionId())
                && isCityActive(level, citizen.cityId());
    }

    /** requestCitizenRecovery：为活跃城市中未加载的居民申请短时源区块加载。 */
    public static void requestCitizenRecovery(ServerLevel level, CitizenData citizen) {
        if (!isCitizenActive(level, citizen) || CitizenTeleportService.findCitizenEntity(level, citizen.uuid()) != null) {
            return;
        }
        LevelRuntime levelRuntime = RUNTIMES.get(SaveScopedCacheKey.levelKey(level));
        CityRuntime cityRuntime = levelRuntime != null ? levelRuntime.cities.get(citizen.cityId()) : null;
        if (cityRuntime == null || cityRuntime.state != CityRuntimeState.ACTIVE
                || cityRuntime.pendingRecoveries.size() >= MAX_PENDING_RECOVERIES_PER_CITY
                || cityRuntime.pendingRecoveries.containsKey(citizen.uuid())
                || cityRuntime.nextRecoveryAttemptTick.getOrDefault(citizen.uuid(), Long.MIN_VALUE) > level.getGameTime()) {
            return;
        }
        List<ChunkPos> sourceChunks = resolveRecoveryChunks(level, citizen);
        if (sourceChunks.isEmpty()) {
            return;
        }
        PendingRecovery pending = PendingRecovery.start(sourceChunks, level.getGameTime());
        if (cityRuntime.pendingRecoveries.putIfAbsent(citizen.uuid(), pending) == null) {
            acquireRecoveryTicket(level, new ChunkPos(pending.chunkLong()));
            common.cn.kafei.simukraft.SimuKraft.LOGGER.debug(
                    "Simukraft: recovery started for citizen {} at chunk {} with {} candidates",
                    citizen.uuid(), new ChunkPos(pending.chunkLong()), pending.candidates().size());
        }
    }

    /** clearServerCaches：服务器停止时移除尚未释放的恢复 ticket。 */
    public static void clearServerCaches(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            LevelRuntime runtime = RUNTIMES.remove(SaveScopedCacheKey.levelKey(level));
            if (runtime != null) {
                runtime.cities.values().forEach(cityRuntime -> releaseAllRecoveries(level, cityRuntime));
            }
        }
        String prefix = SaveScopedCacheKey.serverKey(server) + "|";
        RUNTIMES.keySet().removeIf(key -> key.startsWith(prefix));
        RECOVERY_TICKET_REFS.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static void activate(LevelRuntime runtime, UUID cityId, long gameTime) {
        CityRuntime cityRuntime = runtime.cities.computeIfAbsent(cityId, ignored -> new CityRuntime());
        cityRuntime.lastActiveTick = gameTime;
        cityRuntime.state = CityRuntimeState.ACTIVE;
    }

    private static void processRecoveries(ServerLevel level, CityRuntime cityRuntime, long gameTime) {
        cityRuntime.pendingRecoveries.forEach((citizenId, pending) -> {
            CitizenData citizen = CitizenManager.get(level).getCitizen(citizenId).orElse(null);
            if (citizen == null || !isCitizenActive(level, citizen)) {
                releaseRecovery(level, cityRuntime, citizenId, pending);
                return;
            }
            CitizenEntity citizenEntity = CitizenTeleportService.findCitizenEntity(level, citizenId);
            if (citizenEntity != null) {
                CitizenTeleportService.refreshClientTracking(level, citizenEntity);
                common.cn.kafei.simukraft.SimuKraft.LOGGER.debug(
                        "Simukraft: recovery loaded citizen {} from chunk {}", citizenId, new ChunkPos(pending.chunkLong()));
                if (citizen.workStatusType() == CitizenWorkStatus.WORKING && citizen.workplaceId() != null) {
                    if (!CitizenWorkplaceMoveService.recoverToWorkplace(level, citizen)) {
                        return;
                    }
                }
                cityRuntime.nextRecoveryAttemptTick.remove(citizenId);
                releaseRecovery(level, cityRuntime, citizenId, pending);
                return;
            }
            if (gameTime - pending.requestedAt() >= RECOVERY_CHUNK_TIMEOUT_TICKS) {
                advanceRecovery(level, cityRuntime, citizenId, pending, gameTime);
            }
        });
    }

    /** resolveRecoveryChunks：优先精确位置，并为旧存档按城市领地顺序补充候选区块。 */
    private static List<ChunkPos> resolveRecoveryChunks(ServerLevel level, CitizenData citizen) {
        LinkedHashSet<Long> chunkLongs = new LinkedHashSet<>();
        citizen.lastKnownChunk().ifPresent(chunkPos -> chunkLongs.add(chunkPos.toLong()));
        CityPoiManager poiManager = CityPoiManager.get(level);
        if (citizen.homeId() != null) {
            CityPoiData home = poiManager.getPoi(citizen.homeId());
            if (home != null) {
                chunkLongs.add(new ChunkPos(home.pos()).toLong());
            }
        }
        if (citizen.workplacePos() != null) {
            chunkLongs.add(new ChunkPos(citizen.workplacePos()).toLong());
        }
        CityManager.get(level).getCity(citizen.cityId())
                .ifPresent(city -> chunkLongs.add(new ChunkPos(city.cityCorePos()).toLong()));
        CityChunkManager.get(level).getCityChunks(citizen.cityId()).stream()
                .sorted()
                .limit(MAX_LEGACY_RECOVERY_CITY_CHUNKS)
                .forEach(chunkLongs::add);
        ArrayList<ChunkPos> chunks = new ArrayList<>(chunkLongs.size());
        chunkLongs.forEach(chunkLong -> chunks.add(new ChunkPos(chunkLong)));
        return List.copyOf(chunks);
    }

    /** advanceRecovery：当前候选未恢复实体时切换到下一个短时强加载区块。 */
    private static void advanceRecovery(ServerLevel level, CityRuntime cityRuntime, UUID citizenId,
            PendingRecovery pending, long gameTime) {
        PendingRecovery next = pending.nextCandidate(gameTime);
        if (next != null) {
            if (cityRuntime.pendingRecoveries.replace(citizenId, pending, next)) {
                releaseRecoveryTicket(level, new ChunkPos(pending.chunkLong()));
                acquireRecoveryTicket(level, new ChunkPos(next.chunkLong()));
                common.cn.kafei.simukraft.SimuKraft.LOGGER.debug(
                        "Simukraft: recovery switched citizen {} from chunk {} to {}",
                        citizenId, new ChunkPos(pending.chunkLong()), new ChunkPos(next.chunkLong()));
            }
            return;
        }
        if (pending.equals(cityRuntime.pendingRecoveries.get(citizenId))) {
            cityRuntime.nextRecoveryAttemptTick.put(citizenId, gameTime + RECOVERY_RETRY_DELAY_TICKS);
            releaseRecovery(level, cityRuntime, citizenId, pending);
            common.cn.kafei.simukraft.SimuKraft.LOGGER.warn(
                    "Simukraft: recovery could not find citizen {} in {} candidate chunks; retrying later",
                    citizenId, pending.candidates().size());
        }
    }

    private static void releaseAllRecoveries(ServerLevel level, CityRuntime cityRuntime) {
        cityRuntime.pendingRecoveries.forEach((citizenId, pending) -> releaseRecovery(level, cityRuntime, citizenId, pending));
        cityRuntime.nextRecoveryAttemptTick.clear();
    }

    private static void releaseRecovery(ServerLevel level, CityRuntime cityRuntime, UUID citizenId, PendingRecovery pending) {
        if (cityRuntime.pendingRecoveries.remove(citizenId, pending)) {
            releaseRecoveryTicket(level, new ChunkPos(pending.chunkLong()));
        }
    }

    private static void acquireRecoveryTicket(ServerLevel level, ChunkPos chunkPos) {
        String key = recoveryTicketKey(level, chunkPos);
        int count = RECOVERY_TICKET_REFS.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        if (count == 1) {
            level.getChunkSource().addRegionTicket(CITIZEN_RECOVERY_TICKET, chunkPos,
                    RECOVERY_TICKET_DISTANCE, chunkPos, true);
        }
    }

    private static void releaseRecoveryTicket(ServerLevel level, ChunkPos chunkPos) {
        String key = recoveryTicketKey(level, chunkPos);
        AtomicInteger counter = RECOVERY_TICKET_REFS.get(key);
        if (counter == null || counter.decrementAndGet() > 0) {
            return;
        }
        if (RECOVERY_TICKET_REFS.remove(key, counter)) {
            level.getChunkSource().removeRegionTicket(CITIZEN_RECOVERY_TICKET, chunkPos,
                    RECOVERY_TICKET_DISTANCE, chunkPos, true);
        }
    }

    private static String recoveryTicketKey(ServerLevel level, ChunkPos chunkPos) {
        return SaveScopedCacheKey.levelKey(level) + "|" + chunkPos.toLong();
    }

    private static final class LevelRuntime {
        private final ConcurrentMap<UUID, CityRuntime> cities = new ConcurrentHashMap<>();
    }

    private static final class CityRuntime {
        private final ConcurrentMap<UUID, PendingRecovery> pendingRecoveries = new ConcurrentHashMap<>();
        private final ConcurrentMap<UUID, Long> nextRecoveryAttemptTick = new ConcurrentHashMap<>();
        private volatile long lastActiveTick = Long.MIN_VALUE;
        private volatile CityRuntimeState state = CityRuntimeState.SLEEPING;
    }

    private record PendingRecovery(List<Long> candidates, int candidateIndex, long requestedAt) {
        private static PendingRecovery start(List<ChunkPos> candidates, long requestedAt) {
            List<Long> chunkLongs = candidates.stream().map(ChunkPos::toLong).toList();
            return new PendingRecovery(chunkLongs, 0, requestedAt);
        }

        private long chunkLong() {
            return candidates.get(candidateIndex);
        }

        private PendingRecovery nextCandidate(long gameTime) {
            int nextIndex = candidateIndex + 1;
            return nextIndex < candidates.size()
                    ? new PendingRecovery(candidates, nextIndex, gameTime)
                    : null;
        }
    }
}
