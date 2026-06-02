package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.economy.FinanceLedgerService;
import common.cn.kafei.simukraft.material.WorkMaterialPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public final class BuildingIntegrityService {
    private BuildingIntegrityService() {
    }

    /**
     * tick：按配置间隔扫描已完成建筑，完整性低于阈值时自动拆除。
     */
    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        int threshold = ServerConfig.buildingIntegrityAutoDemolishThresholdPercent();
        if (threshold <= 0) {
            return;
        }
        int interval = Math.max(20, ServerConfig.buildingIntegrityCheckIntervalTicks());
        if (level.getGameTime() % interval != 0L) {
            return;
        }
        for (PlacedBuildingRecord building : List.copyOf(PlacedBuildingService.getBuildings(level))) {
            IntegritySnapshot snapshot = snapshot(level, building);
            if (!snapshot.available() || snapshot.totalBlocks() <= 0) {
                continue;
            }
            double percent = snapshot.percent();
            if (percent < threshold) {
                autoDemolish(level, building, percent, threshold);
            }
        }
    }

    /**
     * snapshot：计算建筑当前完整性；建筑未加载完整时返回 unavailable，避免误拆未加载区块。
     */
    public static IntegritySnapshot snapshot(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null || building.blocks() == null || building.blocks().isEmpty()) {
            return new IntegritySnapshot(false, 0, 0);
        }
        int total = 0;
        int intact = 0;
        for (BuildingBlockData block : building.blocks()) {
            if (block == null || block.state() == null || block.state().isAir()) {
                continue;
            }
            BlockPos worldPos = resolveWorldPos(building, block.relativePos());
            if (!level.isLoaded(worldPos)) {
                return new IntegritySnapshot(false, 0, 0);
            }
            total++;
            if (matchesStructureBlock(level.getBlockState(worldPos), block.state())) {
                intact++;
            }
        }
        return new IntegritySnapshot(true, total, intact);
    }

    public static IntegrityPreview preview(ServerLevel level, PlacedBuildingRecord building) {
        IntegritySnapshot snapshot = snapshot(level, building);
        if (!snapshot.available()) {
            return new IntegrityPreview(false, 0, 0, 0, 0, 0.0D);
        }
        RepairPlan plan = repairPlan(level, building);
        int repairableBlocks = plan.targets().size();
        return new IntegrityPreview(true, snapshot.totalBlocks(), snapshot.intactBlocks(), repairableBlocks, plan.manualRepairBlocks(), repairCost(repairableBlocks));
    }

    public static double repairCost(int repairableBlocks) {
        return EconomyService.normalizeAmount(Math.max(0, repairableBlocks) * ServerConfig.buildingIntegrityRepairMoneyPerBlock());
    }

    public static RepairResult repair(ServerLevel level, ServerPlayer player, PlacedBuildingRecord building) {
        if (level == null || building == null || building.cityId() == null) {
            return new RepairResult(RepairStatus.NO_BUILDING, 0, 0, 0.0D);
        }
        IntegritySnapshot snapshot = snapshot(level, building);
        if (!snapshot.available()) {
            return new RepairResult(RepairStatus.UNAVAILABLE, 0, 0, 0.0D);
        }
        RepairPlan plan = repairPlan(level, building);
        List<RepairTarget> targets = plan.targets();
        if (targets.isEmpty()) {
            RepairStatus status = plan.manualRepairBlocks() > 0 ? RepairStatus.MATERIALS_REQUIRED : RepairStatus.NO_REPAIR_NEEDED;
            return new RepairResult(status, 0, plan.manualRepairBlocks(), 0.0D);
        }
        double cost = repairCost(targets.size());
        if (cost > 0.0D) {
            if (!EconomyService.canAfford(level, building.cityId(), cost) || !CityService.withdrawFunds(level, building.cityId(), cost)) {
                return new RepairResult(RepairStatus.NOT_ENOUGH_FUNDS, targets.size(), plan.manualRepairBlocks(), cost);
            }
            FinanceLedgerService.record(level, building.cityId(), player, -cost, EconomyService.getCityBalance(level, building.cityId()), FinanceTransactionData.Type.EXPENSE, "building_repair");
        }
        for (RepairTarget target : targets) {
            level.setBlock(target.pos(), target.state(), 3);
        }
        return new RepairResult(RepairStatus.SUCCESS, targets.size(), plan.manualRepairBlocks(), cost);
    }

    private static RepairPlan repairPlan(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null || building.blocks() == null || building.blocks().isEmpty()) {
            return new RepairPlan(List.of(), 0);
        }
        List<RepairTarget> targets = new ArrayList<>();
        int manualRepairBlocks = 0;
        for (BuildingBlockData block : building.blocks()) {
            if (block == null || block.state() == null || block.state().isAir()) {
                continue;
            }
            BlockPos worldPos = resolveWorldPos(building, block.relativePos());
            if (!level.isLoaded(worldPos)
                    || matchesStructureBlock(level.getBlockState(worldPos), block.state())
                    || PlacedBuildingService.isOccupiedByOtherBuilding(level, building.buildingId(), worldPos)) {
                continue;
            }
            if (WorkMaterialPolicy.requiresMaterial(block.state())) {
                manualRepairBlocks++;
                continue;
            }
            targets.add(new RepairTarget(worldPos, block.state()));
        }
        return new RepairPlan(List.copyOf(targets), manualRepairBlocks);
    }

    /**
     * autoDemolish：统一走现有拆除服务，保证 POI、居民和控制箱状态一起清理。
     */
    private static void autoDemolish(ServerLevel level, PlacedBuildingRecord building, double percent, int threshold) {
        if (PlacedBuildingDemolitionService.demolish(level, building)) {
            SimuKraft.LOGGER.info("Simukraft: Auto demolished building {} because integrity {:.1f}% is below {}%",
                    building.displayName(), percent, threshold);
        }
    }

    /**
     * matchesStructureBlock：完整性只比较方块类型，避免门开关、箱子开合等状态变化被当作损坏。
     */
    private static boolean matchesStructureBlock(BlockState current, BlockState expected) {
        return current != null && expected != null && current.is(expected.getBlock());
    }

    /**
     * resolveWorldPos：兼容旧记录中保存相对坐标或世界坐标两种形式。
     */
    private static BlockPos resolveWorldPos(PlacedBuildingRecord building, BlockPos storedPos) {
        if (isInside(storedPos, building.minPos(), building.maxPos())) {
            return storedPos;
        }
        return building.worldOrigin().offset(storedPos);
    }

    private static boolean isInside(BlockPos pos, BlockPos min, BlockPos max) {
        return pos != null
                && pos.getX() >= Math.min(min.getX(), max.getX()) && pos.getX() <= Math.max(min.getX(), max.getX())
                && pos.getY() >= Math.min(min.getY(), max.getY()) && pos.getY() <= Math.max(min.getY(), max.getY())
                && pos.getZ() >= Math.min(min.getZ(), max.getZ()) && pos.getZ() <= Math.max(min.getZ(), max.getZ());
    }

    public record IntegritySnapshot(boolean available, int totalBlocks, int intactBlocks) {
        public double percent() {
            return totalBlocks <= 0 ? 100.0D : intactBlocks * 100.0D / totalBlocks;
        }
    }

    public record IntegrityPreview(boolean available, int totalBlocks, int intactBlocks, int repairableBlocks, int manualRepairBlocks, double repairCost) {
        public double percent() {
            return totalBlocks <= 0 ? 100.0D : intactBlocks * 100.0D / totalBlocks;
        }
    }

    public record RepairResult(RepairStatus status, int repairedBlocks, int manualRepairBlocks, double cost) {
        public boolean success() {
            return status == RepairStatus.SUCCESS || status == RepairStatus.NO_REPAIR_NEEDED;
        }
    }

    public enum RepairStatus {
        SUCCESS,
        NO_BUILDING,
        UNAVAILABLE,
        NO_REPAIR_NEEDED,
        NOT_ENOUGH_FUNDS,
        MATERIALS_REQUIRED
    }

    private record RepairPlan(List<RepairTarget> targets, int manualRepairBlocks) {
    }

    private record RepairTarget(BlockPos pos, BlockState state) {
    }
}
