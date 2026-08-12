package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.citizen.CitizenHousingService;
import common.cn.kafei.simukraft.citizen.CitizenHomeRestService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.network.rts.RtsChunkViewService;
import common.cn.kafei.simukraft.protection.NpcBlockProtectionPolicy;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 已登记建筑和普通方块的 RTS 移动事务。所有方法只在服务端主线程调用。 */
@SuppressWarnings("null")
public final class PlacedBuildingMoveService {
    private static final double MAX_DISTANCE = 128.0D;
    private static final int MAX_MOVED_BLOCKS = 32768;
    private static final long MAX_SURFACE_SCAN_COLUMNS = 65_536L;
    private static final int MOVE_BLOCK_UPDATE_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final ThreadLocal<MoveContext> ACTIVE_BUILDING_MOVE = new ThreadLocal<>();

    private PlacedBuildingMoveService() {
    }

    /** move: 按源位置和目标位置移动单方块或整座已登记建筑。 */
    public static MoveStatus move(ServerLevel level, ServerPlayer player, BlockPos source, BlockPos destination) {
        return move(level, player, source, destination, 0);
    }

    /** move: 按源位置和目标位置移动单方块或整座已登记建筑，并保留客户端高度微调。 */
    public static MoveStatus move(ServerLevel level, ServerPlayer player, BlockPos source, BlockPos destination,
                                  int manualVerticalOffset) {
        return move(level, player, source, destination, manualVerticalOffset, 0);
    }

    /** move: 按抓取点旋转并移动单方块或整座已登记建筑。 */
    public static MoveStatus move(ServerLevel level, ServerPlayer player, BlockPos source, BlockPos destination,
                                  int manualVerticalOffset, int rotationDegrees) {
        if (level == null || player == null || source == null || destination == null) {
            return MoveStatus.INVALID;
        }
        int normalizedRotation = normalizeRotation(rotationDegrees);
        if (normalizedRotation < 0) {
            return MoveStatus.INVALID;
        }
        BlockPos sourcePos = source.immutable();
        BlockPos destinationPos = destination.immutable();
        if (!RtsChunkViewService.isTargetReachable(level, player, sourcePos, MAX_DISTANCE)
                || !RtsChunkViewService.isTargetReachable(level, player, destinationPos, MAX_DISTANCE)) {
            return MoveStatus.TOO_FAR;
        }
        if (level.getBlockState(sourcePos).is(ModBlocks.CITY_CORE.get())) {
            return MoveStatus.INVALID;
        }
        PlacedBuildingRecord building = PlacedBuildingService.findByContainedPos(level, sourcePos);
        if (building != null) {
            if (!canManageBuilding(level, player, building)) {
                return MoveStatus.NO_PERMISSION;
            }
            return moveBuilding(level, building, sourcePos, destinationPos, manualVerticalOffset, normalizedRotation);
        }
        return moveBlock(level, player, sourcePos, destinationPos, normalizedRotation);
    }

    /** moveBlock: 搬运普通方块并覆盖目标方块，不产生掉落物。 */
    private static MoveStatus moveBlock(ServerLevel level, ServerPlayer player, BlockPos source, BlockPos destination,
                                        int rotationDegrees) {
        if ((source.equals(destination) && rotationDegrees == 0) || !level.isAreaLoaded(source, 1) || !level.isAreaLoaded(destination, 1)
                || !player.mayInteract(level, source) || !player.mayInteract(level, destination)) {
            return MoveStatus.INVALID;
        }
        BlockState state = level.getBlockState(source);
        if (state.isAir()) {
            return MoveStatus.INVALID;
        }
        if (NpcBlockProtectionPolicy.isProtected(state)) {
            NpcBlockProtectionPolicy.logSkipped("rts", level, source, state);
            return MoveStatus.INVALID;
        }
        BlockState destinationState = level.getBlockState(destination);
        if (NpcBlockProtectionPolicy.isProtected(destinationState)) {
            NpcBlockProtectionPolicy.logSkipped("rts", level, destination, destinationState);
            return MoveStatus.INVALID;
        }
        CompoundTag blockEntityData = copyBlockEntityData(level.getBlockEntity(source), level);
        clearBlockWithoutDrops(level, source, Block.UPDATE_ALL);
        clearBlockWithoutDrops(level, destination, Block.UPDATE_ALL);
        BlockState placedState = BuildingBlockPlacementService.refreshedPlacementState(level, destination,
                singleChestState(BuildingTransform.rotateState(state, rotationDegrees)));
        level.setBlock(destination, placedState, 3);
        BuildingBlockPlacementService.applyBlockEntityData(level, destination, blockEntityData);
        return MoveStatus.SUCCESS_BLOCK;
    }

    /** moveBuilding: 校验整座建筑的边界和加载状态后覆盖迁移方块与 POI。 */
    private static synchronized MoveStatus moveBuilding(ServerLevel level, PlacedBuildingRecord building,
                                                         BlockPos source, BlockPos destination, int manualVerticalOffset,
                                                         int rotationDegrees) {
        if (building.blocks() == null || building.blocks().isEmpty() || building.blocks().size() > MAX_MOVED_BLOCKS) {
            return MoveStatus.INVALID;
        }
        BlockPos snappedDestination = snapBuildingDestination(level, building, source, destination,
                manualVerticalOffset, rotationDegrees);
        PositionTransform transform = new PositionTransform(source, snappedDestination, rotationDegrees);
        if (source.equals(snappedDestination) && rotationDegrees == 0) {
            return MoveStatus.INVALID;
        }
        List<MoveBlock> blocks = new ArrayList<>(building.blocks().size());
        java.util.Set<BlockPos> oldPositions = ConcurrentHashMap.newKeySet();
        for (BuildingBlockData recorded : building.blocks()) {
            if (recorded == null || recorded.state() == null || recorded.state().isAir()) {
                continue;
            }
            BlockPos oldPos = resolveWorldPos(building, recorded.relativePos());
            BlockPos newPos = transform.apply(oldPos);
            if (oldPos == null || newPos.getY() < level.getMinBuildHeight()
                    || newPos.getY() >= level.getMaxBuildHeight() || !level.isAreaLoaded(oldPos, 1)
                    || !level.isAreaLoaded(newPos, 1) || !level.getWorldBorder().isWithinBounds(newPos)) {
                return MoveStatus.INVALID;
            }
            // 以世界中的现状为准，已损坏的格不应在移动时按建筑蓝图恢复。
            BlockState currentState = level.getBlockState(oldPos);
            if (currentState.isAir()) {
                continue;
            }
            CompoundTag blockEntityData = copyBlockEntityData(level.getBlockEntity(oldPos), level);
            oldPositions.add(oldPos.immutable());
            blocks.add(new MoveBlock(oldPos, newPos, currentState,
                    BuildingTransform.rotateState(currentState, rotationDegrees),
                    blockEntityData != null ? blockEntityData : recorded.copyBlockEntityData(), recorded.originalStructurePos()));
        }
        if (blocks.isEmpty()) {
            return MoveStatus.INVALID;
        }
        BuildingBounds destinationBounds = boundsOf(blocks);
        if (destinationBounds == null) {
            return MoveStatus.INVALID;
        }
        if (ServerConfig.claimProtectionEnabled() && !destinationBoundsInCity(level, building, destinationBounds)) {
            return MoveStatus.OUTSIDE_CITY;
        }
        Map<BlockPos, CityPoiData> residentialPois = snapshotResidentialPois(level, building);
        for (MoveBlock block : blocks) {
            if (oldPositions.contains(block.newPos())) {
                continue;
            }
            if (PlacedBuildingService.isOccupiedByOtherBuilding(level, building.buildingId(), block.newPos())) {
                return MoveStatus.INVALID;
            }
        }

        ACTIVE_BUILDING_MOVE.set(new MoveContext(level, oldPositions));
        try {
            ResidentialBedPoiService.removeRecordedBeds(level, building);
            MedicalBedPoiService.removeRecordedBeds(level, building);
            blocks.forEach(block -> clearBlockWithoutDrops(level, block.oldPos(), MOVE_BLOCK_UPDATE_FLAGS));
            blocks.forEach(block -> clearBlockWithoutDrops(level, block.newPos(), MOVE_BLOCK_UPDATE_FLAGS));
            blocks.forEach(block -> {
                BlockState movedState = preserveMovedChestPair(block, oldPositions);
                BlockState refreshed = BuildingBlockPlacementService.refreshedPlacementState(level, block.newPos(), movedState);
                level.setBlock(block.newPos(), refreshed, MOVE_BLOCK_UPDATE_FLAGS);
                BuildingBlockPlacementService.applyBlockEntityData(level, block.newPos(), block.blockEntityData());
            });
            refreshMovedConnectionStates(level, blocks);
            List<BuildingBlockData> movedBlocks = blocks.stream()
                    .map(block -> new BuildingBlockData(block.newPos(), level.getBlockState(block.newPos()),
                            block.originalStructurePos(), block.blockEntityData()))
                    .toList();
            List<BuildingPoiInstance> movedPois = movePoiInstances(level, building, transform, residentialPois);
            BlockPos movedOrigin = building.worldOrigin() == null ? snappedDestination : transform.apply(building.worldOrigin());
            int totalRotation = BuildingTransform.rotationDegreesFromFacing(building.facing()) + rotationDegrees;
            PlacedBuildingRecord moved = new PlacedBuildingRecord(
                    building.buildingId(), building.cityId(), building.dimensionId(), building.category(),
                    building.buildingFileName(), building.displayName(), building.amount(), building.structureFileName(),
                    BuildingTransform.directionFromRotation(totalRotation).getSerializedName(), movedOrigin, building.structureAnchor(),
                    destinationBounds.min(), destinationBounds.max(), building.completedAt(), movedBlocks,
                    building.poiDefinitions(), movedPois, building.unitDefinitions(), building.unitInstances());
            syncMovedPois(level, moved, transform);
            moved = rebuildMovedUnitInstances(level, moved);
            PlacedBuildingService.register(level, moved);
            syncMovedUnitAssignments(level, moved);
            CitizenHomeRestService.invalidateMovedHomes(level,
                    remapResidentialHomes(level, building.cityId(), residentialPois, transform));
            ResidentialBedPoiService.addRecordedBeds(level, moved);
            MedicalBedPoiService.addRecordedBeds(level, moved);
            if (building.cityId() != null) {
                CitizenHousingService.fillVacantHomes(level, building.cityId());
            }
        } finally {
            ACTIVE_BUILDING_MOVE.remove();
        }
        return MoveStatus.SUCCESS_BUILDING;
    }

    /** syncMovedPois: 保留 POI UUID、容量、激活状态并修正其世界坐标。 */
    private static void syncMovedPois(ServerLevel level, PlacedBuildingRecord movedBuilding,
                                      PositionTransform transform) {
        if (movedBuilding.cityId() == null) {
            return;
        }
        CityPoiManager manager = CityPoiManager.get(level);
        for (BuildingPoiInstance movedPoi : movedBuilding.poiInstances()) {
            CityPoiData registeredPoi = manager.getPoiAt(transform.invert(movedPoi.worldPos()));
            if (registeredPoi != null && (!movedBuilding.cityId().equals(registeredPoi.cityId())
                    || movedPoi.poiType() != registeredPoi.type())) {
                registeredPoi = null;
            }
            UUID poiId = registeredPoi != null ? registeredPoi.poiId() : stablePoiId(movedPoi, movedBuilding.dimensionId());
            boolean active = registeredPoi == null || registeredPoi.active();
            manager.registerPoi(poiId, movedBuilding.cityId(), movedPoi.worldPos(), movedPoi.poiType(), movedPoi.capacity());
            if (!active) {
                manager.deactivatePoi(poiId);
            }
        }
    }

    /** movePoiInstances: 固化真实 UUID，并合并旧记录中同位置的重复 POI。 */
    private static List<BuildingPoiInstance> movePoiInstances(ServerLevel level, PlacedBuildingRecord building, PositionTransform transform,
                                                               Map<BlockPos, CityPoiData> residentialPois) {
        CityPoiManager manager = building.cityId() != null ? CityPoiManager.get(level) : null;
        Map<String, BuildingPoiInstance> movedPois = new LinkedHashMap<>();
        for (BuildingPoiInstance poi : building.poiInstances()) {
            CityPoiData registeredPoi = poi.poiType() == CityPoiType.RESIDENTIAL
                    ? residentialPois.get(poi.worldPos())
                    : manager != null ? manager.getPoiAt(poi.worldPos()) : null;
            String key = registeredPoi != null && building.cityId().equals(registeredPoi.cityId())
                    && poi.poiType() == registeredPoi.type()
                    ? registeredPoi.poiId().toString()
                    : poi.key();
            BuildingPoiInstance movedPoi = new BuildingPoiInstance(
                    key, poi.poiType(), poi.capacity(), transform.apply(poi.worldPos()));
            movedPois.putIfAbsent(movedPoi.poiType().name() + "@" + movedPoi.worldPos().asLong(), movedPoi);
        }
        residentialPois.forEach((oldPos, poi) -> {
            BlockPos movedPos = transform.apply(oldPos);
            String key = poi.poiId().toString();
            movedPois.putIfAbsent(CityPoiType.RESIDENTIAL.name() + "@" + movedPos.asLong(),
                    new BuildingPoiInstance(key, CityPoiType.RESIDENTIAL, poi.capacity(), movedPos));
        });
        return List.copyOf(movedPois.values());
    }

    /** snapshotResidentialPois：在搬迁前快照建筑内实际登记的住宅床位 POI。 */
    private static Map<BlockPos, CityPoiData> snapshotResidentialPois(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null || building.cityId() == null) {
            return Map.of();
        }
        CityPoiManager poiManager = CityPoiManager.get(level);
        Map<BlockPos, CityPoiData> snapshot = new LinkedHashMap<>();
        for (CityPoiData poi : poiManager.allPois()) {
            if (poi.type() == CityPoiType.RESIDENTIAL && building.cityId().equals(poi.cityId())
                    && contains(building.minPos(), building.maxPos(), poi.pos())) {
                snapshot.put(poi.pos().immutable(), poi);
            }
        }
        for (BuildingPoiInstance instance : building.poiInstances()) {
            if (instance.poiType() != CityPoiType.RESIDENTIAL) {
                continue;
            }
            CityPoiData poi = poiManager.getPoiAt(instance.worldPos());
            if (poi != null && poi.type() == CityPoiType.RESIDENTIAL
                    && building.cityId().equals(poi.cityId())) {
                snapshot.putIfAbsent(instance.worldPos().immutable(), poi);
            }
        }
        return Map.copyOf(snapshot);
    }

    /** remapResidentialHomes：按床位搬迁后的坐标修复住宅 POI 和居民 homeId。 */
    private static Set<UUID> remapResidentialHomes(ServerLevel level, UUID cityId,
                                                    Map<BlockPos, CityPoiData> oldPois, PositionTransform transform) {
        if (level == null || cityId == null || oldPois.isEmpty()) {
            return Set.of();
        }
        CityPoiManager poiManager = CityPoiManager.get(level);
        Map<UUID, UUID> remap = new LinkedHashMap<>();
        Set<UUID> movedHomePoiIds = new LinkedHashSet<>();
        oldPois.forEach((oldPos, oldPoi) -> {
            BlockPos newPos = transform.apply(oldPos);
            CityPoiData movedPoi = poiManager.getPoiAt(newPos);
            if (movedPoi == null || movedPoi.type() != CityPoiType.RESIDENTIAL
                    || !cityId.equals(movedPoi.cityId())) {
                movedPoi = poiManager.registerPoi(oldPoi.poiId(), cityId, newPos,
                        CityPoiType.RESIDENTIAL, oldPoi.capacity());
                if (!oldPoi.active()) {
                    poiManager.deactivatePoi(movedPoi.poiId());
                }
            }
            remap.put(oldPoi.poiId(), movedPoi.poiId());
            movedHomePoiIds.add(oldPoi.poiId());
            movedHomePoiIds.add(movedPoi.poiId());
        });
        if (!remap.isEmpty()) {
            CitizenHousingService.remapHomes(level, cityId, remap);
        }
        return Set.copyOf(movedHomePoiIds);
    }

    /** canManageBuilding: 复用城市官方权限规则保护整体建筑移动。 */
    private static boolean canManageBuilding(ServerLevel level, ServerPlayer player, PlacedBuildingRecord building) {
        if (player.hasPermissions(2)) {
            return true;
        }
        if (building.cityId() == null) {
            return false;
        }
        CityData city = CityManager.get(level).getCity(building.cityId()).orElse(null);
        return city != null && city.hasPermission(player.getUUID(), CityPermissionLevel.OFFICIAL);
    }

    /** snapBuildingDestination: 用建筑实际方块脚印的最高地表重新计算控制方块目标高度。 */
    private static BlockPos snapBuildingDestination(ServerLevel level, PlacedBuildingRecord building,
                                                    BlockPos source, BlockPos destination, int manualVerticalOffset,
                                                    int rotationDegrees) {
        if (source.getX() == destination.getX() && source.getZ() == destination.getZ() && rotationDegrees == 0) {
            return destination;
        }
        BuildingFootprint footprint = resolveBuildingFootprint(building, source, rotationDegrees);
        if (footprint == null) {
            return destination;
        }
        long width = (long) footprint.maxX() - footprint.minX() + 1L;
        long length = (long) footprint.maxZ() - footprint.minZ() + 1L;
        if (width <= 0L || length <= 0L || width * length > MAX_SURFACE_SCAN_COLUMNS) {
            return destination;
        }
        int highestSurfaceY = level.getMinBuildHeight();
        for (int relativeX = footprint.minX(); relativeX <= footprint.maxX(); relativeX++) {
            int targetX = destination.getX() + relativeX;
            for (int relativeZ = footprint.minZ(); relativeZ <= footprint.maxZ(); relativeZ++) {
                int targetZ = destination.getZ() + relativeZ;
                if (!level.hasChunk(SectionPos.blockToSectionCoord(targetX), SectionPos.blockToSectionCoord(targetZ))) {
                    return destination;
                }
                highestSurfaceY = Math.max(highestSurfaceY,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ));
            }
        }
        int snappedY = highestSurfaceY - footprint.minY() + manualVerticalOffset;
        return new BlockPos(destination.getX(), snappedY, destination.getZ());
    }

    /** resolveBuildingFootprint: 从登记的非空气方块计算真实横向脚印和最低高度。 */
    private static BuildingFootprint resolveBuildingFootprint(PlacedBuildingRecord building, BlockPos source,
                                                              int rotationDegrees) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BuildingBlockData recorded : building.blocks()) {
            if (recorded == null || recorded.state() == null || recorded.state().isAir()) {
                continue;
            }
            BlockPos pos = BuildingTransform.rotatePosition(
                    resolveWorldPos(building, recorded.relativePos()).subtract(source), rotationDegrees);
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return minY == Integer.MAX_VALUE ? null : new BuildingFootprint(minX, maxX, minY, minZ, maxZ);
    }

    /** resolveWorldPos: 兼容旧记录的相对坐标与当前记录的世界坐标。 */
    private static BlockPos resolveWorldPos(PlacedBuildingRecord building, BlockPos storedPos) {
        if (storedPos == null) {
            return BlockPos.ZERO;
        }
        if (contains(building.minPos(), building.maxPos(), storedPos)) {
            return storedPos;
        }
        return building.worldOrigin().offset(storedPos);
    }

    private static boolean contains(BlockPos min, BlockPos max, BlockPos pos) {
        return pos.getX() >= Math.min(min.getX(), max.getX()) && pos.getX() <= Math.max(min.getX(), max.getX())
                && pos.getY() >= Math.min(min.getY(), max.getY()) && pos.getY() <= Math.max(min.getY(), max.getY())
                && pos.getZ() >= Math.min(min.getZ(), max.getZ()) && pos.getZ() <= Math.max(min.getZ(), max.getZ());
    }

    /** destinationBoundsInCity：校验建筑旋转和搬迁后的完整边界均位于所属城市领地。 */
    private static boolean destinationBoundsInCity(ServerLevel level, PlacedBuildingRecord building, BuildingBounds bounds) {
        if (building.cityId() == null) {
            return false;
        }
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();
        return BuildingTerritoryValidator.boundsInChunks(
                Math.min(min.getX(), max.getX()), Math.max(min.getX(), max.getX()),
                Math.min(min.getZ(), max.getZ()), Math.max(min.getZ(), max.getZ()),
                CityChunkManager.get(level).getCityChunks(building.cityId())
        );
    }

    private static CompoundTag copyBlockEntityData(BlockEntity entity, ServerLevel level) {
        return entity == null ? null : entity.saveWithoutMetadata(level.registryAccess());
    }

    /** refreshMovedConnectionStates: 所有方块落位后重算栅栏、墙和铁栅栏的连接状态。 */
    private static void refreshMovedConnectionStates(ServerLevel level, List<MoveBlock> blocks) {
        for (MoveBlock block : blocks) {
            BlockState currentState = level.getBlockState(block.newPos());
            BlockState refreshedState = BuildingBlockPlacementService.refreshedPlacementState(level, block.newPos(), currentState);
            if (!currentState.equals(refreshedState)) {
                level.setBlock(block.newPos(), refreshedState, MOVE_BLOCK_UPDATE_FLAGS);
            }
        }
    }

    /** syncMovedUnitAssignments: 重新写入旋转后住宅 POI 的单元归属，防止运行时缓存残留。 */
    private static void syncMovedUnitAssignments(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null || building.unitInstances().isEmpty()) {
            return;
        }
        CityPoiManager poiManager = CityPoiManager.get(level);
        for (BuildingUnitInstance unit : building.unitInstances()) {
            for (UUID poiId : unit.poiIds()) {
                poiManager.updatePoiUnitId(poiId, unit.unitId());
            }
        }
    }

    /** rebuildMovedUnitInstances: 依据旋转后的 POI、原点和朝向重建住宅单元归属。 */
    private static PlacedBuildingRecord rebuildMovedUnitInstances(ServerLevel level, PlacedBuildingRecord building) {
        List<BuildingUnitInstance> unitInstances = BuildingUnitResolver.resolveUnitInstances(building, CityPoiManager.get(level));
        if (unitInstances.isEmpty() && !building.unitInstances().isEmpty()) {
            return building;
        }
        if (unitInstances.equals(building.unitInstances())) {
            return building;
        }
        return new PlacedBuildingRecord(
                building.buildingId(), building.cityId(), building.dimensionId(), building.category(),
                building.buildingFileName(), building.displayName(), building.amount(), building.structureFileName(),
                building.facing(), building.worldOrigin(), building.structureAnchor(), building.minPos(), building.maxPos(),
                building.completedAt(), building.blocks(), building.poiDefinitions(), building.poiInstances(),
                building.unitDefinitions(), unitInstances);
    }

    /** isMovingBuildingBlock: 判断控制箱移除是否属于 RTS 整体搬迁事务。 */
    public static boolean isMovingBuildingBlock(ServerLevel level, BlockPos pos) {
        MoveContext context = ACTIVE_BUILDING_MOVE.get();
        return context != null && context.level() == level && context.sourcePositions().contains(pos);
    }

    /** clearBlockWithoutDrops: 先移除方块实体，再替换状态，防止容器 onRemove 抛出库存。 */
    private static void clearBlockWithoutDrops(ServerLevel level, BlockPos pos, int updateFlags) {
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), updateFlags);
    }

    /** singleChestState: 单独搬运大箱子半边时规范为独立小箱子。 */
    private static BlockState singleChestState(BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
            return state.setValue(ChestBlock.TYPE, ChestType.SINGLE);
        }
        return state;
    }

    /** preserveMovedChestPair: 仅在另一半也属于同一搬运事务时保留大箱子连接状态。 */
    private static BlockState preserveMovedChestPair(MoveBlock block, java.util.Set<BlockPos> oldPositions) {
        BlockState originalState = block.originalState();
        BlockState movedState = block.movedState();
        if (!(originalState.getBlock() instanceof ChestBlock) || !originalState.hasProperty(ChestBlock.TYPE)
                || originalState.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return movedState;
        }
        BlockPos partner = block.oldPos().relative(ChestBlock.getConnectedDirection(originalState));
        return oldPositions.contains(partner) ? movedState : singleChestState(movedState);
    }

    /** boundsOf: 计算旋转后实际方块的世界边界，用于领地校验和建筑登记。 */
    private static BuildingBounds boundsOf(List<MoveBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (MoveBlock block : blocks) {
            BlockPos pos = block.newPos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new BuildingBounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    /** normalizeRotation: 仅接受四分之一圈旋转，拒绝客户端伪造的任意角度。 */
    private static int normalizeRotation(int rotationDegrees) {
        if (Math.floorMod(rotationDegrees, 90) != 0) {
            return -1;
        }
        return Math.floorMod(rotationDegrees, 360);
    }

    private static UUID stablePoiId(BuildingPoiInstance poi, String dimensionId) {
        try {
            return UUID.fromString(poi.key());
        } catch (IllegalArgumentException exception) {
            String scope = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
            return UUID.nameUUIDFromBytes((scope + ":" + poi.poiType().name().toLowerCase() + ":" + poi.worldPos().toShortString())
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    /** BuildingFootprint: 已登记非空气方块的真实横向范围和最低高度。 */
    private record BuildingFootprint(int minX, int maxX, int minY, int minZ, int maxZ) {
    }

    private record BuildingBounds(BlockPos min, BlockPos max) {
    }

    public enum MoveStatus {
        SUCCESS_BLOCK,
        SUCCESS_BUILDING,
        TOO_FAR,
        NO_PERMISSION,
        OUTSIDE_CITY,
        INVALID
    }

    private record MoveBlock(BlockPos oldPos, BlockPos newPos, BlockState originalState, BlockState movedState,
                              CompoundTag blockEntityData, BlockPos originalStructurePos) {
    }

    /** PositionTransform: 以抓取方块为轴，把旧坐标映射到旋转后的目标坐标。 */
    private record PositionTransform(BlockPos source, BlockPos destination, int rotationDegrees) {
        private BlockPos apply(BlockPos oldPos) {
            return destination.offset(BuildingTransform.rotatePosition(oldPos.subtract(source), rotationDegrees));
        }

        private BlockPos invert(BlockPos movedPos) {
            return source.offset(BuildingTransform.inverseRotatePosition(movedPos.subtract(destination), rotationDegrees));
        }
    }

    private record MoveContext(ServerLevel level, java.util.Set<BlockPos> sourcePositions) {
    }
}
