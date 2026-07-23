package common.cn.kafei.simukraft.path;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.network.path.NpcPathDebugSyncPacket;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("null")
public final class CitizenNavigationService {
    private static final ConcurrentMap<String, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final int STALLED_TELEPORT_TICKS = 300;
    private static final int DEBUG_SYNC_PATH_LIMIT = 96;
    private static final double DEBUG_SYNC_RADIUS = 192.0D;
    private static volatile ExecutorService pathExecutor;
    private static volatile int executorSize;

    private CitizenNavigationService() {
    }

    public static boolean requestMove(ServerLevel level, UUID citizenId, Vec3 target, MovementIntent intent) {
        return requestMove(level, citizenId, target, intent, false);
    }

    public static boolean requestTestMove(ServerLevel level, UUID citizenId, Vec3 target, MovementIntent intent) {
        return requestMove(level, citizenId, target, intent, true);
    }

    private static boolean requestMove(ServerLevel level, UUID citizenId, Vec3 target, MovementIntent intent, boolean bypassAdmissionLimits) {
        if (level == null || citizenId == null || target == null) {
            return false;
        }
        MovementIntent normalizedIntent = intent != null ? intent : MovementIntent.WALK;
        CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, citizenId);
        if (citizen == null) {
            return false;
        }
        if (citizen.isStayInPlace()) {
            return false;
        }
        if (citizen.getFollowPlayerId() != null && normalizedIntent != MovementIntent.FOLLOW) {
            return false;
        }
        if (citizen.isSleeping()) {
            return false;
        }
        Vec3 current = citizen.position();
        double distanceSqr = current.distanceToSqr(target);
        double farDistance = localPathDistanceLimit();
        if (distanceSqr >= farDistance * farDistance || !hasLoadedChunk(level, BlockPos.containing(target.x, target.y, target.z))) {
            if (normalizedIntent == MovementIntent.WANDER) {
                return false;
            }
            return CitizenTeleportService.teleportCitizen(level, citizenId, target);
        }

        LevelRuntime runtime = runtime(level);
        if (normalizedIntent == MovementIntent.SELF_FEEDING) {
            clearLowerPriorityNavigation(level, runtime, citizenId, citizen);
        } else if (normalizedIntent == MovementIntent.WORK && hasSelfFeedingNavigation(runtime, citizenId)) {
            return false;
        }
        ActiveNavigation active = runtime.active.get(citizenId);
        if (active != null && active.sameTarget(target)) {
            return true;
        }
        if (runtime.pending.containsKey(citizenId)) {
            return true;
        }
        PathRequest queued = runtime.latestRequests.get(citizenId);
        if (queued != null && queued.target().distanceToSqr(target) <= 4.0D) {
            return true;
        }
        Long cooldownUntil = runtime.cooldowns.get(citizenId);
        if (cooldownUntil != null && cooldownUntil > level.getGameTime()) {
            return false;
        }
        if (!bypassAdmissionLimits) {
            if (!runtime.active.containsKey(citizenId) && runtime.active.size() >= ServerConfig.pathMaxActiveCitizens()) {
                return false;
            }
            if (countLoadedCitizens(level, runtime) > ServerConfig.pathMaxLoadedCitizenEntities()) {
                return false;
            }
        }

        PathRequest request = new PathRequest(citizenId, level.dimension().location(), citizen.blockPosition(), target, normalizedIntent, level.getGameTime());
        runtime.latestRequests.put(citizenId, request);
        if (runtime.queuedCitizenIds.add(citizenId)) {
            runtime.queue.offer(citizenId);
        }
        return true;
    }

    /** hasSelfFeedingNavigation: 判断买饭导航是否正在占用该 NPC，避免普通工作移动抢占。 */
    private static boolean hasSelfFeedingNavigation(LevelRuntime runtime, UUID citizenId) {
        ActiveNavigation active = runtime.active.get(citizenId);
        if (active != null && active.intent == MovementIntent.SELF_FEEDING) {
            return true;
        }
        RunningRequest running = runtime.pending.get(citizenId);
        if (running != null && running.cacheKey().intent() == MovementIntent.SELF_FEEDING) {
            return true;
        }
        PathRequest queued = runtime.latestRequests.get(citizenId);
        return queued != null && queued.intent() == MovementIntent.SELF_FEEDING;
    }

    /** clearLowerPriorityNavigation: 买饭开始时清掉旧的普通工作导航，防止两套状态轮流改目标。 */
    private static void clearLowerPriorityNavigation(ServerLevel level, LevelRuntime runtime, UUID citizenId, CitizenEntity citizen) {
        ActiveNavigation active = runtime.active.get(citizenId);
        if (active != null && active.intent != MovementIntent.SELF_FEEDING) {
            runtime.active.remove(citizenId);
            citizen.getNavigation().stop();
            PathCrowdCoordinator.clear(level, citizenId);
        }
        RunningRequest running = runtime.pending.get(citizenId);
        if (running != null && running.cacheKey().intent() != MovementIntent.SELF_FEEDING) {
            runtime.pending.remove(citizenId);
        }
        PathRequest queued = runtime.latestRequests.get(citizenId);
        if (queued != null && queued.intent() != MovementIntent.SELF_FEEDING) {
            runtime.latestRequests.remove(citizenId);
            runtime.queuedCitizenIds.remove(citizenId);
        }
    }

    public static void stop(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        runtime.latestRequests.remove(citizenId);
        runtime.blockedSince.remove(citizenId);
        runtime.pending.remove(citizenId);
        runtime.queuedCitizenIds.remove(citizenId);
        runtime.active.remove(citizenId);
        CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, citizenId);
        if (citizen != null) {
            citizen.getNavigation().stop();
            citizen.getMoveControl().setWantedPosition(citizen.getX(), citizen.getY(), citizen.getZ(), 0.0D);
        }
        PathCrowdCoordinator.clear(level, citizenId);
    }

    public static boolean isNavigating(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return false;
        }
        LevelRuntime runtime = runtime(level);
        return runtime.active.containsKey(citizenId) || runtime.pending.containsKey(citizenId) || runtime.latestRequests.containsKey(citizenId);
    }

    public static boolean debugPathTo(ServerPlayer player, Vec3 target) {
        if (player == null || target == null) {
            return false;
        }
        CitizenEntity citizen = CitizenPathDebugService.findNearestLoadedCitizen(player.serverLevel(), player.position(), ServerConfig.pathLocalRadiusBlocks());
        if (citizen == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.path_debug.no_citizen"));
            PacketDistributor.sendToPlayer(player, NpcPathDebugSyncPacket.clear());
            return false;
        }
        return debugPathTo(player, citizen, target);
    }

    public static boolean debugPathTo(ServerPlayer player, CitizenEntity citizen, Vec3 target) {
        if (player == null || citizen == null || target == null || !(citizen.level() instanceof ServerLevel level)) {
            return false;
        }
        if (level != player.serverLevel()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.path_debug.failed", "citizen_dimension_mismatch"));
            return false;
        }
        BlockPos targetPos = BlockPos.containing(target.x, target.y, target.z);
        if (!hasLoadedChunk(level, targetPos)) {
            CitizenPathDebugService.sendDebugFailure(player, citizen.getUUID(), "target_chunk_not_loaded");
            return false;
        }
        double radius = ServerConfig.pathLocalRadiusBlocks();
        if (citizen.position().distanceToSqr(target) > radius * radius) {
            CitizenPathDebugService.sendDebugFailure(player, citizen.getUUID(), "target_outside_local_radius");
            return false;
        }

        PathRequest request = new PathRequest(citizen.getUUID(), level.dimension().location(), citizen.blockPosition(), target, MovementIntent.RUN, level.getGameTime());
        PathSnapshot snapshot = PathSnapshotBuilder.build(level, request.startPos(), request.targetBlockPos(), ServerConfig.pathLocalRadiusBlocks());
        InfoToastService.send(player, Component.translatable("message.simukraft.path_debug.started", citizen.getName().getString(), CitizenPathDebugService.formatTarget(target)));
        CompletableFuture<PathResult> future = CompletableFuture.supplyAsync(() -> HybridPathfinder.find(request, snapshot), executor());
        future.whenComplete((result, throwable) -> level.getServer().execute(() -> applyDebugPath(level, player, citizen.getUUID(), result, throwable)));
        return true;
    }

    public static void clearDebugPath(ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, NpcPathDebugSyncPacket.clear());
            InfoToastService.send(player, Component.translatable("message.simukraft.path_debug.cleared"));
        }
    }

    public static boolean sendStatus(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        LevelRuntime runtime = runtime(level);
        runtime.cooldowns.entrySet().removeIf(entry -> entry.getValue() <= level.getGameTime());
        InfoToastService.send(player, Component.translatable(
                "message.simukraft.path_status.summary",
                runtime.queuedCitizenIds.size(),
                runtime.pending.size(),
                runtime.active.size(),
                runtime.cooldowns.size()));
        CitizenPathDebugService.PathRuntimeIssue issue = CitizenPathDebugService.nearestIssue(level, runtime.active, player.position());
        if (issue != null) {
            InfoToastService.warning(player, Component.translatable(
                    "message.simukraft.path_status.issue",
                    CitizenPathDebugService.shortId(issue.citizenId()),
                    issue.status(),
                    String.format(Locale.ROOT, "%.1f", Math.sqrt(issue.distanceToTargetSqr())),
                    issue.waypointIndex(),
                    issue.waypointCount()));
        } else {
            InfoToastService.success(player, Component.translatable("message.simukraft.path_status.no_issue"));
        }
        syncDebugPaths(level, player);
        return true;
    }

    public static void syncDebugPaths(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        PacketDistributor.sendToPlayer(player, NpcPathDebugSyncPacket.clear());
        double maxDistanceSqr = DEBUG_SYNC_RADIUS * DEBUG_SYNC_RADIUS;
        List<CitizenPathDebugService.DebugPathEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, ActiveNavigation> entry : runtime.active.entrySet()) {
            CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, entry.getKey());
            if (citizen == null) {
                continue;
            }
            double distanceSqr = citizen.position().distanceToSqr(player.position());
            if (distanceSqr <= maxDistanceSqr) {
                entries.add(new CitizenPathDebugService.DebugPathEntry(entry.getKey(), entry.getValue(), distanceSqr));
            }
        }
        entries.sort(Comparator.comparingDouble(CitizenPathDebugService.DebugPathEntry::distanceSqr));
        int sent = 0;
        for (CitizenPathDebugService.DebugPathEntry entry : entries) {
            if (sent >= DEBUG_SYNC_PATH_LIMIT) {
                break;
            }
            PacketDistributor.sendToPlayer(player, NpcPathDebugSyncPacket.fromWaypoints(entry.citizenId(), entry.navigation().waypoints, entry.navigation().debugStatus()));
            sent++;
        }
        if (sent > 0) {
            InfoToastService.send(player, Component.translatable("message.simukraft.path_debug.synced", sent, runtime.active.size()));
        }
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        applyCompletedPaths(level, runtime);
        tickActivePaths(level, runtime);
        CitizenDoorService.processOpenedDoors(level, runtime.openedDoors, runtime.active.keySet());
        processQueuedRequests(level, runtime);
        if (level.getGameTime() % 200L == 0L) {
            runtime.pathCache.cleanup(level.getGameTime());
            runtime.cooldowns.entrySet().removeIf(entry -> entry.getValue() <= level.getGameTime());
            PathCrowdCoordinator.cleanup(level);
        }
    }

    public static void invalidate(ServerLevel level, BlockPos changedPos) {
        if (level == null || changedPos == null) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        runtime.pathCache.clear();
        runtime.snapshotCache.clear();
    }

    public static void clearServerCaches(MinecraftServer server) {
        if (server != null) {
            String prefix = SaveKey.serverKey(server) + "|";
            RUNTIMES.keySet().removeIf(key -> key.startsWith(prefix));
        } else {
            RUNTIMES.clear();
        }
        PathCrowdCoordinator.clearServerCaches(server);
        ExecutorService executor = pathExecutor;
        if (executor != null) {
            executor.shutdownNow();
        }
        pathExecutor = null;
        executorSize = 0;
    }

    private static void processQueuedRequests(ServerLevel level, LevelRuntime runtime) {
        int processed = 0;
        int budget = Math.max(0, ServerConfig.pathMaxNewRequestsPerTick());
        while (processed < budget) {
            if (runtime.active.size() + runtime.pending.size() >= ServerConfig.pathMaxActiveCitizens()) {
                return;
            }
            UUID citizenId = runtime.queue.poll();
            if (citizenId == null) {
                return;
            }
            runtime.queuedCitizenIds.remove(citizenId);
            PathRequest request = runtime.latestRequests.remove(citizenId);
            if (request == null || runtime.pending.containsKey(citizenId)) {
                continue;
            }
            CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, citizenId);
            if (citizen == null) {
                continue;
            }
            if (citizen.isSleeping()) {
                clearRuntimeNavigation(level, runtime, citizenId, citizen, true);
                continue;
            }
            if (!level.isPositionEntityTicking(citizen.blockPosition())) {
                continue;
            }
            PathRequest currentRequest = new PathRequest(citizenId, request.dimensionId(), citizen.blockPosition(), request.target(), request.intent(), level.getGameTime());
            PathCacheKey cacheKey = new PathCacheKey(currentRequest.dimensionId(), currentRequest.startPos(), currentRequest.targetBlockPos(), currentRequest.intent());
            PathResult cached = runtime.pathCache.get(cacheKey, level.getGameTime());
            if (cached != null) {
                activate(level, runtime, cached);
                processed++;
                continue;
            }
            PathSnapshotBuilder.ChunkDataCapture capture = runtime.snapshotCache.acquire(level, currentRequest.startPos(), currentRequest.targetBlockPos(), ServerConfig.pathLocalRadiusBlocks());
            BlockPos reqStart = currentRequest.startPos();
            BlockPos reqTarget = currentRequest.targetBlockPos();
            CompletableFuture<PathResult> future = CompletableFuture.supplyAsync(
                    () -> HybridPathfinder.find(currentRequest, PathSnapshotBuilder.buildFromCapture(capture, reqStart, reqTarget)),
                    executor());
            runtime.pending.put(citizenId, new RunningRequest(future, cacheKey));
            processed++;
        }
    }

    private static void applyCompletedPaths(ServerLevel level, LevelRuntime runtime) {
        for (Iterator<Map.Entry<UUID, RunningRequest>> iterator = runtime.pending.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, RunningRequest> entry = iterator.next();
            RunningRequest running = entry.getValue();
            if (!running.future().isDone()) {
                continue;
            }
            iterator.remove();
            PathResult result;
            try {
                result = running.future().get();
            } catch (Exception e) {
                result = null;
            }
            if (result != null && result.success()) {
                runtime.pathCache.put(running.cacheKey(), result, level.getGameTime(), ServerConfig.pathCacheTtlTicks());
                activate(level, runtime, result);
            } else {
                UUID citizenId = entry.getKey();
                runtime.cooldowns.remove(citizenId);
                runtime.blockedSince.remove(citizenId);
                if (result != null) {
                    if (running.cacheKey().intent() != MovementIntent.WANDER) {
                        CitizenTeleportService.teleportCitizen(level, citizenId, result.target());
                        if (ServerConfig.pathDebugEnabled()) {
                            SimuKraft.LOGGER.info("Simukraft: NPC path failed for {}, teleporting to target: {}", citizenId, result.reason());
                        }
                    }
                }
            }
        }
    }

    private static void activate(ServerLevel level, LevelRuntime runtime, PathResult result) {
        if (!result.success() || result.waypoints().isEmpty()) {
            return;
        }
        CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, result.citizenId());
        if (citizen == null) {
            return;
        }
        if (citizen.isSleeping()) {
            clearRuntimeNavigation(level, runtime, result.citizenId(), citizen, true);
            return;
        }
        runtime.active.put(result.citizenId(), new ActiveNavigation(result));
    }

    private static void tickActivePaths(ServerLevel level, LevelRuntime runtime) {
        for (Iterator<Map.Entry<UUID, ActiveNavigation>> iterator = runtime.active.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, ActiveNavigation> entry = iterator.next();
            CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, entry.getKey());
            if (citizen == null) {
                iterator.remove();
                continue;
            }
            if (citizen.isSleeping()) {
                clearRuntimeNavigation(level, runtime, entry.getKey(), citizen, false);
                iterator.remove();
                continue;
            }
            if (!level.isPositionEntityTicking(citizen.blockPosition())) {
                continue;
            }
            ActiveTickResult result = entry.getValue().tick(level, citizen, runtime.openedDoors);
            if (result == ActiveTickResult.RUNNING) {
                continue;
            }
            citizen.getNavigation().stop();
            citizen.getMoveControl().setWantedPosition(citizen.getX(), citizen.getY(), citizen.getZ(), 0.0);
            PathCrowdCoordinator.clear(level, entry.getKey());
            iterator.remove();
            if (result == ActiveTickResult.REPATH) {
                ActiveNavigation active = entry.getValue();
                long blockedSince = runtime.blockedSince.computeIfAbsent(entry.getKey(), id -> level.getGameTime());
                if (level.getGameTime() - blockedSince >= STALLED_TELEPORT_TICKS) {
                    if (active.intent != MovementIntent.WANDER) {
                        CitizenTeleportService.teleportCitizen(level, entry.getKey(), active.target);
                    }
                    runtime.latestRequests.remove(entry.getKey());
                    runtime.cooldowns.remove(entry.getKey());
                    runtime.blockedSince.remove(entry.getKey());
                    continue;
                }
                runtime.cooldowns.put(entry.getKey(), level.getGameTime() + 20L);
                PathRequest request = new PathRequest(entry.getKey(), level.dimension().location(), citizen.blockPosition(), active.target, active.intent, level.getGameTime());
                runtime.latestRequests.put(entry.getKey(), request);
                if (runtime.queuedCitizenIds.add(entry.getKey())) {
                    runtime.queue.offer(entry.getKey());
                }
            } else {
                runtime.blockedSince.remove(entry.getKey());
            }
        }
    }

    private static void clearRuntimeNavigation(ServerLevel level, LevelRuntime runtime, UUID citizenId, CitizenEntity citizen, boolean removeActive) {
        if (removeActive) {
            runtime.active.remove(citizenId);
        }
        runtime.latestRequests.remove(citizenId);
        runtime.pending.remove(citizenId);
        runtime.queuedCitizenIds.remove(citizenId);
        runtime.blockedSince.remove(citizenId);
        runtime.cooldowns.remove(citizenId);
        if (citizen != null) {
            citizen.getNavigation().stop();
            citizen.getMoveControl().setWantedPosition(citizen.getX(), citizen.getY(), citizen.getZ(), 0.0D);
        }
        PathCrowdCoordinator.clear(level, citizenId);
    }

    private static LevelRuntime runtime(ServerLevel level) {
        return RUNTIMES.computeIfAbsent(runtimeKey(level), key -> new LevelRuntime());
    }

    private static String runtimeKey(ServerLevel level) {
        return SaveKey.serverKey(level.getServer()) + "|" + level.dimension().location();
    }

    /**
     * Counts the citizen entities loaded in the level, memoizing the result for the current tick so
     * the many move requests issued per tick share a single full entity scan.
     */
    private static int countLoadedCitizens(ServerLevel level, LevelRuntime runtime) {
        long gameTime = level.getGameTime();
        if (runtime.loadedCitizenCountTick == gameTime) {
            return runtime.loadedCitizenCount;
        }
        int count = 0;
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof CitizenEntity) {
                count++;
            }
        }
        runtime.loadedCitizenCountTick = gameTime;
        runtime.loadedCitizenCount = count;
        return count;
    }

    private static boolean hasLoadedChunk(ServerLevel level, BlockPos pos) {
        return level != null
                && pos != null
                && level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private static void applyDebugPath(ServerLevel level, ServerPlayer player, UUID citizenId, PathResult result, Throwable throwable) {
        if (throwable != null) {
            SimuKraft.LOGGER.warn("Simukraft: NPC debug path calculation failed for {}", citizenId, throwable);
            CitizenPathDebugService.sendDebugFailure(player, citizenId, "path_calculation_failed");
            return;
        }
        if (result == null) {
            CitizenPathDebugService.sendDebugFailure(player, citizenId, "path_result_missing");
            return;
        }
        PacketDistributor.sendToPlayer(player, NpcPathDebugSyncPacket.fromResult(result));
        if (result.success()) {
            LevelRuntime runtime = runtime(level);
            runtime.latestRequests.remove(result.citizenId());
            runtime.pending.remove(result.citizenId());
            runtime.cooldowns.remove(result.citizenId());
            activate(level, runtime, result);
            InfoToastService.success(player, Component.translatable("message.simukraft.path_debug.success", result.waypoints().size()));
        } else {
            InfoToastService.warning(player, Component.translatable("message.simukraft.path_debug.failed", result.reason()));
        }
    }

    private static int localPathDistanceLimit() {
        return Math.min(ServerConfig.pathFarMovementTeleportDistance(), ServerConfig.pathLocalRadiusBlocks());
    }

    private static ExecutorService executor() {
        int requestedSize = Math.max(1, ServerConfig.pathWorkerThreads());
        ExecutorService existing = pathExecutor;
        if (existing != null && !existing.isShutdown() && executorSize == requestedSize) {
            return existing;
        }
        synchronized (CitizenNavigationService.class) {
            existing = pathExecutor;
            if (existing != null && !existing.isShutdown() && executorSize == requestedSize) {
                return existing;
            }
            if (existing != null) {
                existing.shutdownNow();
            }
            executorSize = requestedSize;
            pathExecutor = Executors.newFixedThreadPool(requestedSize, new PathThreadFactory());
            return pathExecutor;
        }
    }

    private static final class LevelRuntime {
        private final ConcurrentLinkedQueue<UUID> queue = new ConcurrentLinkedQueue<>();
        private final java.util.Set<UUID> queuedCitizenIds = ConcurrentHashMap.newKeySet();
        private final Map<UUID, PathRequest> latestRequests = new java.util.HashMap<>();
        private final Map<UUID, RunningRequest> pending = new java.util.HashMap<>();
        private final Map<UUID, ActiveNavigation> active = new java.util.HashMap<>();
        private final Map<UUID, Long> cooldowns = new java.util.HashMap<>();
        private final Map<UUID, Long> blockedSince = new java.util.HashMap<>();
        private final Map<Long, CitizenDoorService.OpenedDoor> openedDoors = new java.util.HashMap<>();
        private final PathResultCache pathCache = new PathResultCache();
        private final PathSnapshotCache snapshotCache = new PathSnapshotCache();
        private long loadedCitizenCountTick = Long.MIN_VALUE;
        private int loadedCitizenCount;
    }

    private record RunningRequest(CompletableFuture<PathResult> future, PathCacheKey cacheKey) {
    }

    private static final class PathThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "simukraft-path-worker-" + THREAD_ID.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            return thread;
        }
    }

    private static final class SaveKey {
        private static final java.util.WeakHashMap<net.minecraft.server.MinecraftServer, String> CACHE = new java.util.WeakHashMap<>();
        private static String serverKey(net.minecraft.server.MinecraftServer server) {
            if (server == null) return "unknown";
            return CACHE.computeIfAbsent(server, s ->
                    s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT));
        }
    }
}
