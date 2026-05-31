package common.cn.kafei.simukraft.farmland;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.job.CityJobAssignmentService;
import common.cn.kafei.simukraft.job.CityJobMobilityService;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 农田盒服务端入口：城市归属校验、配置修改、视图构建、雇佣绑定。
 * 所有改变世界/数据的操作都在这里集中校验，再由 Manager 持久化、由网络包同步客户端。
 */
@SuppressWarnings("null")
public final class FarmlandBoxService {
    public static final String HIRE_SOURCE_TYPE = "farmland_box";
    public static final String HIRE_ROLE = "farmer";

    private FarmlandBoxService() {
    }

    // 与雇佣系统一致的合成 workplaceId：NpcHireAssignPacket 用相同公式绑定农民到农田盒。
    public static UUID hireWorkplaceId(BlockPos boxPos) {
        return UUID.nameUUIDFromBytes((HIRE_SOURCE_TYPE + ":" + HIRE_ROLE + "@" + boxPos.toShortString()).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID cityIdFor(ServerLevel level, BlockPos boxPos) {
        return CityChunkManager.get(level).getChunkOwner(new ChunkPos(boxPos).toLong());
    }

    public static CitizenData findAssignedFarmer(ServerLevel level, BlockPos boxPos) {
        UUID farmerId = CitizenService.findAssignedCitizen(level, hireWorkplaceId(boxPos));
        if (farmerId == null) {
            return null;
        }
        CitizenData data = CitizenService.findCitizen(level, farmerId).orElse(null);
        if (data == null || data.dead() || data.jobType() != CityJobType.FARMER) {
            return null;
        }
        return data;
    }

    public static FarmlandBoxView buildView(ServerLevel level, BlockPos boxPos) {
        FarmlandBoxData data = FarmlandBoxManager.get(level).get(boxPos);
        boolean hasCity = cityIdFor(level, boxPos) != null;
        CitizenData farmer = findAssignedFarmer(level, boxPos);
        if (data == null) {
            return new FarmlandBoxView(boxPos.immutable(), hasCity, "", false, BlockPos.ZERO, BlockPos.ZERO, false, BlockPos.ZERO, false, farmer != null, farmer != null ? farmer.name() : "");
        }
        FarmlandPlot plot = data.plot();
        BlockPos chest = resolveAdjacentChest(level, boxPos);
        return new FarmlandBoxView(
                boxPos.immutable(),
                hasCity,
                data.crop() != null ? data.crop().id() : "",
                plot != null,
                plot != null ? plot.min() : BlockPos.ZERO,
                plot != null ? plot.max() : BlockPos.ZERO,
                chest != null,
                chest != null ? chest : BlockPos.ZERO,
                data.running(),
                farmer != null,
                farmer != null ? farmer.name() : "");
    }

    public static final int MAX_AREA_HALF_EXTENT = 16;

    // 按作物 id 设置作物（来自客户端弹出菜单的选择）。运行中不允许改，避免半种半收的混乱状态。
    public static boolean setCrop(ServerLevel level, BlockPos boxPos, String cropId) {
        FarmCrop crop = FarmCrop.fromId(cropId);
        if (crop == null) {
            return false;
        }
        FarmlandBoxManager manager = FarmlandBoxManager.get(level);
        FarmlandBoxData data = manager.getOrCreate(boxPos);
        if (data.running()) {
            return false;
        }
        data.setCrop(crop);
        manager.persist(data);
        return true;
    }

    // 按客户端框选的 min/max 设置作业区域。Y 强制取农田盒所在层，作业平面始终水平。
    public static boolean setArea(ServerLevel level, BlockPos boxPos, BlockPos min, BlockPos max) {
        if (min == null || max == null) {
            return false;
        }
        FarmlandBoxManager manager = FarmlandBoxManager.get(level);
        FarmlandBoxData data = manager.getOrCreate(boxPos);
        if (data.running()) {
            return false;
        }
        int cropY = boxPos.getY();
        BlockPos clampedMin = new BlockPos(min.getX(), cropY, min.getZ());
        BlockPos clampedMax = new BlockPos(max.getX(), cropY, max.getZ());
        FarmlandPlot candidate = new FarmlandPlot(clampedMin, clampedMax);
        // 限制区域尺寸，避免一次性框选超大区域拖慢服务器。
        int maxSide = MAX_AREA_HALF_EXTENT * 2 + 1;
        if (candidate.width() > maxSide || candidate.depth() > maxSide) {
            return false;
        }
        // 防止两个农田盒的作业区域重叠导致互相抢格子。
        for (FarmlandBoxData other : manager.all()) {
            if (other.boxPos().equals(data.boxPos()) || other.plot() == null) {
                continue;
            }
            if (candidate.intersects(other.plot())) {
                return false;
            }
        }
        data.setPlot(candidate);
        manager.persist(data);
        return true;
    }

    // 开始/停止耕作。开始前要求作物+区域已配置、有相邻仓储箱、已雇佣农民，避免空转。
    public static ToggleResult toggleRunning(ServerLevel level, BlockPos boxPos) {
        FarmlandBoxManager manager = FarmlandBoxManager.get(level);
        FarmlandBoxData data = manager.getOrCreate(boxPos);
        if (data.running()) {
            data.setRunning(false);
            manager.persist(data);
            return ToggleResult.STOPPED;
        }
        if (!data.isConfigured()) {
            return ToggleResult.NOT_CONFIGURED;
        }
        if (resolveAdjacentChest(level, boxPos) == null) {
            return ToggleResult.NO_CHEST;
        }
        if (findAssignedFarmer(level, boxPos) == null) {
            return ToggleResult.NO_FARMER;
        }
        data.setRunning(true);
        manager.persist(data);
        return ToggleResult.STARTED;
    }

    // 强制停止耕作（例如农民被解雇时）。
    public static void toggleRunningOff(ServerLevel level, BlockPos boxPos) {
        FarmlandBoxManager manager = FarmlandBoxManager.get(level);
        FarmlandBoxData data = manager.get(boxPos);
        if (data != null && data.running()) {
            data.setRunning(false);
            manager.persist(data);
        }
    }

    // 农田盒被移除时清理配置并自动解雇该盒的农民（FARMLAND POI 注销由放置事件处理器负责）。
    public static void onRemoved(ServerLevel level, BlockPos boxPos) {
        FarmlandBoxManager.get(level).remove(boxPos);
        UUID farmerId = CitizenService.findAssignedCitizen(level, hireWorkplaceId(boxPos));
        if (farmerId != null) {
            CityJobAssignmentService.clearAssignment(level, farmerId);
            CityJobMobilityService.resetCitizenAfterFire(level, farmerId);
        }
    }

    /**
     * 自动检测仓储箱：只认紧贴农田盒六个面的容器，按固定方向顺序取第一个，其它位置一律不考虑。
     */
    public static BlockPos resolveAdjacentChest(ServerLevel level, BlockPos boxPos) {
        for (Direction direction : Direction.values()) {
            BlockPos candidate = boxPos.relative(direction);
            if (level.isLoaded(candidate) && GenericContainerAccess.isContainer(level, candidate)) {
                return GenericContainerAccess.canonicalContainerPos(level, candidate);
            }
        }
        return null;
    }

    public enum ToggleResult {
        STARTED,
        STOPPED,
        NOT_CONFIGURED,
        NO_CHEST,
        NO_FARMER
    }
}
