package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 医疗白床 POI 的激活与失效维护。 */
@SuppressWarnings("null")
public final class MedicalBedPoiService {
    private static final ConcurrentMap<String, Set<BlockPos>> RECORDED_BED_HEADS = new ConcurrentHashMap<>();

    private MedicalBedPoiService() {
    }

    /** handleBlockBroken：拆除医疗白床时停用对应 POI。 */
    public static void handleBlockBroken(ServerLevel level, BlockPos pos, BlockState brokenState) {
        BlockPos headPos = resolveBedHeadPos(pos, brokenState);
        if (headPos == null) {
            return;
        }
        CityPoiData poi = CityPoiManager.get(level).getPoiAt(headPos);
        if (poi != null && poi.type() == CityPoiType.MEDICAL) {
            CityPoiManager.get(level).deactivatePoi(poi.poiId());
        }
    }

    /** handleBlockPlaced：修复已登记白床时重新激活 POI。 */
    public static void handleBlockPlaced(ServerLevel level, BlockPos pos, BlockState placedState) {
        if (level == null || pos == null || !isWhiteBedHead(placedState)) {
            return;
        }
        CityPoiData poi = CityPoiManager.get(level).getPoiAt(pos);
        if (poi == null || poi.type() != CityPoiType.MEDICAL || poi.active() || !recordedBedHeads(level).contains(pos.immutable())) {
            return;
        }
        CityPoiManager.get(level).registerPoi(poi.poiId(), poi.cityId(), pos, CityPoiType.MEDICAL, poi.capacity());
    }

    /** addRecordedBeds：记录已建成医疗建筑的白床坐标。 */
    public static void addRecordedBeds(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null) {
            return;
        }
        Set<BlockPos> beds = recordedBedHeads(level);
        building.poiInstances().stream()
                .filter(instance -> instance.poiType() == CityPoiType.MEDICAL)
                .map(BuildingPoiInstance::worldPos)
                .map(BlockPos::immutable)
                .forEach(beds::add);
    }

    /** removeRecordedBeds：建筑拆除时释放白床记录。 */
    public static void removeRecordedBeds(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null) {
            return;
        }
        Set<BlockPos> beds = RECORDED_BED_HEADS.get(common.cn.kafei.simukraft.util.SaveScopedCacheKey.levelKey(level));
        if (beds != null) {
            building.poiInstances().stream()
                    .filter(instance -> instance.poiType() == CityPoiType.MEDICAL)
                    .map(BuildingPoiInstance::worldPos)
                    .forEach(beds::remove);
        }
    }

    /** clearServerCaches：切换存档时清理医疗床缓存。 */
    public static void clearServerCaches(MinecraftServer server) {
        String prefix = common.cn.kafei.simukraft.util.SaveScopedCacheKey.serverKey(server) + "|";
        RECORDED_BED_HEADS.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** resolveBedHeadPos：把白床任一半部解析为床头坐标。 */
    public static BlockPos resolveBedHeadPos(BlockPos pos, BlockState state) {
        if (isWhiteBedHead(state)) {
            return pos.immutable();
        }
        if (!state.is(Blocks.WHITE_BED)
                || !state.hasProperty(BlockStateProperties.BED_PART)
                || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                || state.getValue(BlockStateProperties.BED_PART) != BedPart.FOOT) {
            return null;
        }
        return pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING)).immutable();
    }

    /** isWhiteBedHead：判断方块是否为白床床头。 */
    public static boolean isWhiteBedHead(BlockState state) {
        return state != null && state.is(Blocks.WHITE_BED)
                && (!state.hasProperty(BlockStateProperties.BED_PART)
                || state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD);
    }

    private static Set<BlockPos> recordedBedHeads(ServerLevel level) {
        return RECORDED_BED_HEADS.computeIfAbsent(common.cn.kafei.simukraft.util.SaveScopedCacheKey.levelKey(level), ignored -> {
            Set<BlockPos> beds = ConcurrentHashMap.newKeySet();
            for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
                building.poiInstances().stream()
                        .filter(instance -> instance.poiType() == CityPoiType.MEDICAL)
                        .map(BuildingPoiInstance::worldPos)
                        .map(BlockPos::immutable)
                        .forEach(beds::add);
            }
            return beds;
        });
    }
}
