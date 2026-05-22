package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class ResidentialBedPoiService {
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
            CityPoiManager.get(level).deactivatePoi(poi.poiId());
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
        PlacedBuildingRecord building = PlacedBuildingService.findByPoiPos(level, pos);
        if (building == null || !isRecordedResidentialBed(building, pos)) {
            return;
        }
        CityPoiManager.get(level).registerPoi(existingPoi.poiId(), existingPoi.cityId(), pos, CityPoiType.RESIDENTIAL, existingPoi.capacity());
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

    private static boolean isRecordedResidentialBed(PlacedBuildingRecord building, BlockPos bedHeadPos) {
        return building.poiInstances().stream()
                .anyMatch(instance -> instance.poiType() == CityPoiType.RESIDENTIAL && bedHeadPos.equals(instance.worldPos()));
    }

    private static boolean isRedBedHead(BlockState state) {
        return state.is(Blocks.RED_BED)
                && (!state.hasProperty(BlockStateProperties.BED_PART)
                || state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD);
    }
}
