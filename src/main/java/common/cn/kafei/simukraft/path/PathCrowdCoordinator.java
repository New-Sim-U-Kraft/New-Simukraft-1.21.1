package common.cn.kafei.simukraft.path;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 记录 NPC 当前计划移动方向，用于主线程执行阶段做轻量避让。
 */

@SuppressWarnings("null")
final class PathCrowdCoordinator {
    private static final double SEARCH_RADIUS = 2.75D;
    private static final double FRONT_DISTANCE = 2.2D;
    private static final double LANE_RADIUS = 1.05D;
    private static final double OPPOSING_DOT = -0.25D;
    private static final double SAME_DOT = 0.35D;
    private static final long STATE_TTL_TICKS = 40L;
    private static final long YIELD_CACHE_TICKS = 2L;
    private static final double YIELD_CACHE_DIRECTION_DOT = 0.98D;
    private static final double GRID_CELL_SIZE = 4.0D;
    private static final ConcurrentMap<String, ConcurrentMap<UUID, CrowdMoveState>> STATES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CrowdSpatialIndex> SPATIAL_INDEXES = new ConcurrentHashMap<>();

    private PathCrowdCoordinator() {
    }

    static void record(ServerLevel level, UUID citizenId, Vec3 position, Vec3 commandTarget) {
        if (level == null || citizenId == null || position == null || commandTarget == null) {
            return;
        }
        Direction direction = Direction.from(position, commandTarget);
        if (direction == null) {
            clear(level, citizenId);
            return;
        }
        ConcurrentMap<UUID, CrowdMoveState> states = states(level);
        CrowdMoveState previous = states.get(citizenId);
        long lastYieldCheckTick = Long.MIN_VALUE;
        boolean lastShouldYield = false;
        if (hasSameDirection(direction, previous)) {
            lastYieldCheckTick = previous.lastYieldCheckTick();
            lastShouldYield = previous.lastShouldYield();
        }
        states.put(citizenId, new CrowdMoveState(
                direction.x,
                direction.z,
                level.getGameTime(),
                lastYieldCheckTick,
                lastShouldYield));
    }

    static boolean shouldYield(ServerLevel level, CitizenEntity citizen, Vec3 commandTarget) {
        if (level == null || citizen == null || commandTarget == null) {
            return false;
        }
        Vec3 current = citizen.position();
        Direction direction = Direction.from(current, commandTarget);
        if (direction == null) {
            return false;
        }

        ConcurrentMap<UUID, CrowdMoveState> states = states(level);
        long now = level.getGameTime();
        CrowdMoveState ownState = states.get(citizen.getUUID());
        if (hasCachedYieldDecision(direction, ownState, now)) {
            return ownState.lastShouldYield();
        }
        AABB searchBox = new AABB(
                current.x - SEARCH_RADIUS,
                current.y - 0.75D,
                current.z - SEARCH_RADIUS,
                current.x + SEARCH_RADIUS,
                current.y + 1.75D,
                current.z + SEARCH_RADIUS);
        CrowdSpatialIndex spatialIndex = spatialIndex(level);
        int minCellX = cellCoordinate(current.x - SEARCH_RADIUS);
        int maxCellX = cellCoordinate(current.x + SEARCH_RADIUS);
        int minCellZ = cellCoordinate(current.z - SEARCH_RADIUS);
        int maxCellZ = cellCoordinate(current.z + SEARCH_RADIUS);

        int sameNearby = 0;
        int opposingAhead = 0;
        double localFlowDot = 0.0D;
        UUID closestOpposingId = null;
        double closestAhead = Double.MAX_VALUE;

        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                List<CitizenEntity> nearby = spatialIndex.cell(cellX, cellZ);
                if (nearby == null) {
                    continue;
                }
                for (CitizenEntity other : nearby) {
                    if (other == citizen || other.isRemoved() || !other.getBoundingBox().intersects(searchBox)) {
                        continue;
                    }
                    Vec3 offset = other.position().subtract(current);
                    double horizontalDistanceSqr = offset.x * offset.x + offset.z * offset.z;
                    if (horizontalDistanceSqr < 0.0001D || horizontalDistanceSqr > SEARCH_RADIUS * SEARCH_RADIUS) {
                        continue;
                    }

                    Direction otherDirection = directionFor(other, states.get(other.getUUID()), now);
                    if (otherDirection == null) {
                        if (isStationaryBlocker(citizen, other, direction, offset)) {
                            return cacheYieldDecision(states, citizen.getUUID(), direction, now, true);
                        }
                        continue;
                    }

                    double dot = direction.dot(otherDirection);
                    localFlowDot += dot;
                    if (dot > SAME_DOT) {
                        sameNearby++;
                        continue;
                    }
                    if (dot >= OPPOSING_DOT) {
                        continue;
                    }

                    double ahead = offset.x * direction.x + offset.z * direction.z;
                    if (ahead < -0.15D || ahead > FRONT_DISTANCE) {
                        continue;
                    }
                    double lateralSqr = horizontalDistanceSqr - ahead * ahead;
                    if (lateralSqr > LANE_RADIUS * LANE_RADIUS) {
                        continue;
                    }

                    opposingAhead++;
                    if (ahead < closestAhead) {
                        closestAhead = ahead;
                        closestOpposingId = other.getUUID();
                    }
                }
            }
        }

        if (opposingAhead == 0) {
            return cacheYieldDecision(states, citizen.getUUID(), direction, now, false);
        }
        boolean shouldYield = (localFlowDot <= -1.2D && opposingAhead > sameNearby)
                || (opposingAhead >= 2 && sameNearby == 0)
                || (closestOpposingId != null && citizen.getUUID().compareTo(closestOpposingId) > 0);
        return cacheYieldDecision(states, citizen.getUUID(), direction, now, shouldYield);
    }

    static void clear(ServerLevel level, UUID citizenId) {
        if (level != null && citizenId != null) {
            states(level).remove(citizenId);
        }
    }

    static void cleanup(ServerLevel level) {
        if (level == null) {
            return;
        }
        long expireBefore = level.getGameTime() - STATE_TTL_TICKS;
        states(level).entrySet().removeIf(entry -> entry.getValue().gameTime() < expireBefore);
    }

    static void clearServerCaches(MinecraftServer server) {
        if (server == null) {
            STATES.clear();
            SPATIAL_INDEXES.clear();
            return;
        }
        String prefix = serverKey(server) + "|";
        STATES.keySet().removeIf(key -> key.startsWith(prefix));
        SPATIAL_INDEXES.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static boolean isStationaryBlocker(CitizenEntity citizen, CitizenEntity other, Direction direction, Vec3 offset) {
        double horizontalDistanceSqr = offset.x * offset.x + offset.z * offset.z;
        double ahead = offset.x * direction.x + offset.z * direction.z;
        if (ahead < 0.0D || ahead > 1.2D) {
            return false;
        }
        double lateralSqr = horizontalDistanceSqr - ahead * ahead;
        return lateralSqr <= LANE_RADIUS * LANE_RADIUS
                && citizen.getUUID().compareTo(other.getUUID()) > 0;
    }

    private static Direction directionFor(CitizenEntity other, CrowdMoveState state, long now) {
        if (state != null && now - state.gameTime() <= STATE_TTL_TICKS) {
            return new Direction(state.dirX(), state.dirZ());
        }
        Vec3 motion = other.getDeltaMovement();
        double speedSqr = motion.x * motion.x + motion.z * motion.z;
        if (speedSqr < 0.0001D) {
            return null;
        }
        double speed = Math.sqrt(speedSqr);
        return new Direction(motion.x / speed, motion.z / speed);
    }

    private static boolean hasSameDirection(Direction direction, CrowdMoveState state) {
        return state != null && direction.x * state.dirX() + direction.z * state.dirZ() >= YIELD_CACHE_DIRECTION_DOT;
    }

    private static boolean hasCachedYieldDecision(Direction direction, CrowdMoveState state, long now) {
        return hasSameDirection(direction, state)
                && state.lastYieldCheckTick() != Long.MIN_VALUE
                && now >= state.lastYieldCheckTick()
                && now - state.lastYieldCheckTick() <= YIELD_CACHE_TICKS;
    }

    private static boolean cacheYieldDecision(
            ConcurrentMap<UUID, CrowdMoveState> states,
            UUID citizenId,
            Direction direction,
            long now,
            boolean shouldYield) {
        states.put(citizenId, new CrowdMoveState(direction.x, direction.z, now, now, shouldYield));
        return shouldYield;
    }

    private static ConcurrentMap<UUID, CrowdMoveState> states(ServerLevel level) {
        return STATES.computeIfAbsent(levelKey(level), key -> new ConcurrentHashMap<>());
    }

    private static CrowdSpatialIndex spatialIndex(ServerLevel level) {
        CrowdSpatialIndex index = SPATIAL_INDEXES.computeIfAbsent(levelKey(level), key -> new CrowdSpatialIndex());
        index.rebuildIfNeeded(level);
        return index;
    }

    private static int cellCoordinate(double coordinate) {
        return (int) Math.floor(coordinate / GRID_CELL_SIZE);
    }

    private static final java.util.WeakHashMap<MinecraftServer, String> SERVER_KEY_CACHE = new java.util.WeakHashMap<>();

    private static String levelKey(ServerLevel level) {
        return serverKey(level.getServer()) + "|" + level.dimension().location();
    }

    private static String serverKey(MinecraftServer server) {
        if (server == null) return "unknown";
        return SERVER_KEY_CACHE.computeIfAbsent(server, s ->
                s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT));
    }

    private record CrowdMoveState(
            double dirX,
            double dirZ,
            long gameTime,
            long lastYieldCheckTick,
            boolean lastShouldYield) {
    }

    private static final class CrowdSpatialIndex {
        private final Map<Long, List<CitizenEntity>> cells = new HashMap<>();
        private long indexedGameTime = Long.MIN_VALUE;

        private void rebuildIfNeeded(ServerLevel level) {
            long gameTime = level.getGameTime();
            if (indexedGameTime == gameTime) {
                return;
            }
            cells.clear();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof CitizenEntity citizen && !citizen.isRemoved()) {
                    long cellKey = ChunkPos.asLong(cellCoordinate(citizen.getX()), cellCoordinate(citizen.getZ()));
                    cells.computeIfAbsent(cellKey, ignored -> new ArrayList<>()).add(citizen);
                }
            }
            indexedGameTime = gameTime;
        }

        private List<CitizenEntity> cell(int cellX, int cellZ) {
            return cells.get(ChunkPos.asLong(cellX, cellZ));
        }
    }

    private record Direction(double x, double z) {
        private static Direction from(Vec3 from, Vec3 to) {
            double dx = to.x - from.x;
            double dz = to.z - from.z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 0.001D) {
                return null;
            }
            return new Direction(dx / length, dz / length);
        }

        private double dot(Direction other) {
            return x * other.x + z * other.z;
        }
    }
}
