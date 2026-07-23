package common.cn.kafei.simukraft.path;

import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Opens wooden doors for arriving citizens and closes them once the opener (and any other
 * citizen still in the doorway) has cleared the opening.
 */
final class CitizenDoorService {
    private static final double DOOR_INTERACT_RANGE_SQR = 9.0D;
    private static final double DOOR_CLEAR_RANGE_SQR = 2.25D;
    private static final double DOOR_DOORWAY_RANGE_SQR = 1.44D;
    private static final int MAX_TRACKED_DOORS = 128;

    private CitizenDoorService() {
    }

    /** A wooden door a citizen opened, tracked so it can be closed once cleared. */
    record OpenedDoor(UUID citizenId, long openedAt) {
    }

    /**
     * Opens the wooden door the citizen is arriving at, recording it so it can be closed once the
     * citizen has cleared the opening. Only doors this follower actually flips from closed to open
     * are tracked; a door already open is left as the world set it.
     */
    static void tryOpenWoodenDoor(ServerLevel level, CitizenEntity citizen, PathWaypoint waypoint, Map<Long, OpenedDoor> openedDoors) {
        if (level == null || citizen == null || waypoint == null) {
            return;
        }
        if (citizen.position().distanceToSqr(Vec3.atCenterOf(waypoint.blockPos())) > DOOR_INTERACT_RANGE_SQR + 4.0D) {
            return;
        }
        BlockPos doorPos = lowerWoodenDoorPos(level, waypoint.blockPos());
        if (doorPos != null) {
            if (citizen.position().distanceToSqr(Vec3.atCenterOf(doorPos)) <= DOOR_INTERACT_RANGE_SQR) {
                BlockState state = level.getBlockState(doorPos);
                if (state.getBlock() instanceof DoorBlock doorBlock && isClosedWoodenLowerDoor(state)) {
                    doorBlock.setOpen(citizen, level, state, doorPos, true);
                    trackOpenedDoor(openedDoors, level, citizen, doorPos);
                }
            }
        }
    }

    private static void trackOpenedDoor(Map<Long, OpenedDoor> openedDoors, ServerLevel level, CitizenEntity citizen, BlockPos pos) {
        if (openedDoors.size() >= MAX_TRACKED_DOORS) {
            evictOldestDoor(openedDoors);
        }
        openedDoors.put(pos.asLong(), new OpenedDoor(citizen.getUUID(), level.getGameTime()));
    }

    private static void evictOldestDoor(Map<Long, OpenedDoor> openedDoors) {
        Long oldestKey = null;
        long oldestAt = Long.MAX_VALUE;
        for (Map.Entry<Long, OpenedDoor> entry : openedDoors.entrySet()) {
            if (entry.getValue().openedAt() < oldestAt) {
                oldestAt = entry.getValue().openedAt();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            openedDoors.remove(oldestKey);
        }
    }

    /**
     * Closes every tracked wooden door whose opener has cleared the opening (or is gone),
     * re-reading the live block first so a door a player removed or re-closed is never forced and
     * so the door is not slammed on another citizen still in the doorway.
     */
    static void processOpenedDoors(ServerLevel level, Map<Long, OpenedDoor> openedDoors, Set<UUID> activeCitizenIds) {
        if (openedDoors.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<Long, OpenedDoor>> iterator = openedDoors.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<Long, OpenedDoor> entry = iterator.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            BlockState state = level.getBlockState(pos);
            if (!isCloseableWoodenDoor(state)) {
                iterator.remove();
                continue;
            }
            CitizenEntity opener = CitizenTeleportService.findCitizenEntity(level, entry.getValue().citizenId());
            boolean cleared = opener == null
                    || horizontalDistanceSqr(opener.position(), Vec3.atCenterOf(pos)) > DOOR_CLEAR_RANGE_SQR;
            if (!cleared) {
                continue;
            }
            if (isOtherCitizenInDoorway(level, activeCitizenIds, pos, entry.getValue().citizenId())) {
                continue;
            }
            closeWoodenDoor(level, opener, pos, state);
            iterator.remove();
        }
    }

    private static boolean isCloseableWoodenDoor(BlockState state) {
        return isOpenWoodenLowerDoor(state);
    }

    private static void closeWoodenDoor(ServerLevel level, CitizenEntity citizen, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock doorBlock && isOpenWoodenLowerDoor(state)) {
            doorBlock.setOpen(citizen, level, state, pos, false);
        }
    }

    private static boolean isOtherCitizenInDoorway(ServerLevel level, Set<UUID> activeCitizenIds, BlockPos pos, UUID excludeId) {
        Vec3 center = Vec3.atCenterOf(pos);
        for (UUID id : activeCitizenIds) {
            if (id.equals(excludeId)) {
                continue;
            }
            CitizenEntity other = CitizenTeleportService.findCitizenEntity(level, id);
            if (other != null && horizontalDistanceSqr(other.position(), center) <= DOOR_DOORWAY_RANGE_SQR) {
                return true;
            }
        }
        return false;
    }

    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private static boolean isOpenWoodenLowerDoor(BlockState state) {
        return DoorBlock.isWoodenDoor(state)
                && state.getBlock() instanceof DoorBlock
                && state.hasProperty(DoorBlock.OPEN)
                && state.getValue(DoorBlock.OPEN)
                && state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && !isPoweredBarrier(state);
    }

    // A redstone-held door must not be fought: closing it would just snap back open.
    private static boolean isPoweredBarrier(BlockState state) {
        return state.hasProperty(BlockStateProperties.POWERED)
                && state.getValue(BlockStateProperties.POWERED);
    }

    private static BlockPos lowerWoodenDoorPos(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isClosedWoodenLowerDoor(state)) {
            return pos;
        }
        if (isWoodenDoorUpper(state)) {
            BlockPos below = pos.below();
            if (isClosedWoodenLowerDoor(level.getBlockState(below))) {
                return below;
            }
        }
        return null;
    }

    private static boolean isClosedWoodenLowerDoor(BlockState state) {
        return DoorBlock.isWoodenDoor(state)
                && state.getBlock() instanceof DoorBlock
                && state.hasProperty(DoorBlock.OPEN)
                && !state.getValue(DoorBlock.OPEN)
                && state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private static boolean isWoodenDoorUpper(BlockState state) {
        return DoorBlock.isWoodenDoor(state)
                && state.getBlock() instanceof DoorBlock
                && state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
    }
}
