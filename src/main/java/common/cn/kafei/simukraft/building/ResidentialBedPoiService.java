package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.building.controlbox.ResidentialControlBoxService;
import common.cn.kafei.simukraft.building.controlbox.ResidentialControlBoxView;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxBoundsUpdatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class ResidentialBedPoiService {
    private static final ConcurrentMap<String, Set<BlockPos>> RECORDED_BED_HEADS = new ConcurrentHashMap<>();

    private ResidentialBedPoiService() {
    }

    public static void handleBlockBroken(ServerLevel level, BlockPos pos, BlockState brokenState) {
        if (level == null || pos == null || brokenState == null) {
            return;
        }
        BlockPos bedHeadPos = resolveBedHeadPos(pos, brokenState);
        if (bedHeadPos == null) {
            return;
        }
        CityPoiData poi = CityPoiManager.get(level).getPoiAt(bedHeadPos);
        if (poi != null && poi.type() == CityPoiType.RESIDENTIAL) {
            boolean changed = CityPoiManager.get(level).deactivatePoi(poi.poiId());
            if (changed) {
                syncBuildingBounds(level, bedHeadPos);
            }
        }
    }

    public static void handleBlockPlaced(ServerLevel level, BlockPos pos, BlockState placedState) {
        if (level == null || pos == null || placedState == null || !isRedBedHead(placedState)) {
            return;
        }
        CityPoiData existingPoi = CityPoiManager.get(level).getPoiAt(pos);
        if (existingPoi == null || existingPoi.type() != CityPoiType.RESIDENTIAL || existingPoi.active()) {
            return;
        }
        if (!isRecordedResidentialBed(level, pos)) {
            return;
        }
        CityPoiManager.get(level).registerPoi(existingPoi.poiId(), existingPoi.cityId(), pos, CityPoiType.RESIDENTIAL, existingPoi.capacity());
        syncBuildingBounds(level, pos);
    }

    public static void addRecordedBeds(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null) {
            return;
        }
        Set<BlockPos> bedHeads = recordedBedHeads(level);
        building.poiInstances().stream()
                .filter(instance -> instance.poiType() == CityPoiType.RESIDENTIAL)
                .map(instance -> instance.worldPos().immutable())
                .forEach(bedHeads::add);
    }

    public static void removeRecordedBeds(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null) {
            return;
        }
        Set<BlockPos> bedHeads = RECORDED_BED_HEADS.get(common.cn.kafei.simukraft.util.SaveScopedCacheKey.levelKey(level));
        if (bedHeads == null) {
            return;
        }
        building.poiInstances().stream()
                .filter(instance -> instance.poiType() == CityPoiType.RESIDENTIAL)
                .map(BuildingPoiInstance::worldPos)
                .forEach(bedHeads::remove);
    }

    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = common.cn.kafei.simukraft.util.SaveScopedCacheKey.serverKey(server);
        RECORDED_BED_HEADS.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    private static BlockPos resolveBedHeadPos(BlockPos pos, BlockState state) {
        if (isRedBedHead(state)) {
            return pos.immutable();
        }
        if (!state.is(Blocks.RED_BED)
                || !state.hasProperty(BlockStateProperties.BED_PART)
                || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                || state.getValue(BlockStateProperties.BED_PART) != BedPart.FOOT) {
            return null;
        }
        return pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING)).immutable();
    }

    private static boolean isRecordedResidentialBed(ServerLevel level, BlockPos bedHeadPos) {
        return recordedBedHeads(level).contains(bedHeadPos.immutable());
    }

    private static Set<BlockPos> recordedBedHeads(ServerLevel level) {
        return RECORDED_BED_HEADS.computeIfAbsent(
                common.cn.kafei.simukraft.util.SaveScopedCacheKey.levelKey(level),
                ignored -> loadRecordedBedHeads(level)
        );
    }

    private static Set<BlockPos> loadRecordedBedHeads(ServerLevel level) {
        Set<BlockPos> bedHeads = ConcurrentHashMap.newKeySet();
        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            building.poiInstances().stream()
                    .filter(instance -> instance.poiType() == CityPoiType.RESIDENTIAL)
                    .map(instance -> instance.worldPos().immutable())
                    .forEach(bedHeads::add);
        }
        return bedHeads;
    }

    private static void syncBuildingBounds(ServerLevel level, BlockPos bedHeadPos) {
        PlacedBuildingRecord building = PlacedBuildingService.findByPoiPos(level, bedHeadPos);
        if (building == null) {
            return;
        }
        syncBuildingBounds(level, building);
    }

    public static void syncBuildingBounds(ServerLevel level, PlacedBuildingRecord building) {
        BlockPos controlBoxPos = resolveControlBoxPos(level, building);
        if (controlBoxPos == null) {
            return;
        }
        ResidentialControlBoxView view = ResidentialControlBoxService.buildView(level, controlBoxPos);
        ResidentialControlBoxBoundsUpdatePacket packet = new ResidentialControlBoxBoundsUpdatePacket(
                view.controlBoxPos(),
                view.hasBuildingBounds(),
                view.boundsMin(),
                view.boundsMax(),
                view.residentialPoiPositions()
        );
        PacketDistributor.sendToPlayersNear(level, null, controlBoxPos.getX() + 0.5D, controlBoxPos.getY() + 0.5D, controlBoxPos.getZ() + 0.5D, 64.0D, packet);
    }

    public static BlockPos resolveControlBoxPos(ServerLevel level, PlacedBuildingRecord building) {
        for (BuildingPoiInstance instance : building.poiInstances()) {
            if (instance.poiType() != CityPoiType.RESIDENTIAL && level.getBlockState(instance.worldPos()).is(common.cn.kafei.simukraft.registry.ModBlocks.RESIDENTIAL_CONTROL_BOX.get())) {
                return instance.worldPos();
            }
        }
        return building.blocks().stream()
                .filter(block -> block.state().is(common.cn.kafei.simukraft.registry.ModBlocks.RESIDENTIAL_CONTROL_BOX.get()))
                .map(BuildingBlockData::relativePos)
                .filter(pos -> level.getBlockState(pos).is(common.cn.kafei.simukraft.registry.ModBlocks.RESIDENTIAL_CONTROL_BOX.get()))
                .findFirst()
                .orElse(null);
    }

    private static boolean isRedBedHead(BlockState state) {
        return state.is(Blocks.RED_BED)
                && (!state.hasProperty(BlockStateProperties.BED_PART)
                || state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD);
    }
}
