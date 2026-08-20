package common.cn.kafei.simukraft.mineraldrilling;

import common.cn.kafei.simukraft.building.BuildingIntegrityService;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.PlacedBuildingDemolitionService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import common.cn.kafei.simukraft.registry.ModItems;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinLookupResult;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinService;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlot;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlotState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** MineralDrillingControlBoxService: 提供矿物钻井控制箱的服务端权威业务操作。 */
@SuppressWarnings("null")
public final class MineralDrillingControlBoxService {
    public static final int SHALLOW_DRILL_MIN_Y = 10;

    private MineralDrillingControlBoxService() {
    }

    /** buildView: 生成供菜单绑定使用的有限不可变快照。 */
    public static MineralDrillingControlBoxView buildView(ServerLevel level, BlockPos boxPos) {
        MineralDrillingBoxData data = MineralDrillingBoxManager.get(level).getOrCreate(boxPos);
        int minDepth = level.getMinBuildHeight();
        int maxDepth = Math.max(minDepth, Math.min(boxPos.getY(), level.getMaxBuildHeight() - 1));
        int drillDepth = Math.clamp(data.drillDepth(), minDepth, maxDepth);
        if (drillDepth != data.drillDepth()) {
            data.setDrillDepth(drillDepth);
        }

        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        CitizenData worker = findAssignedWorker(level, boxPos);
        BuildingIntegrityService.IntegrityPreview integrity = BuildingIntegrityService.preview(level, building);
        VirtualVeinLookupResult lookup = VirtualVeinService.getOrCreateField(level, boxPos);
        List<MineralDrillingControlBoxView.VeinMarker> markers = markers(lookup, data.selectedVeinId());
        VirtualVeinSlot selected = selectedSlot(lookup, data.selectedVeinId(), drillDepth);
        if (selected == null) {
            selected = firstSlotAtDepth(lookup, drillDepth);
        }

        MineralDrillingInventory inventory = data.inventory();
        boolean hasBit = !inventory.getItem(MineralDrillingInventory.DRILL_BIT_SLOT).isEmpty();
        String statusKey = resolveStatusKey(data, building, worker, hasBit, selected, lookup);
        return new MineralDrillingControlBoxView(
                boxPos,
                building != null,
                building != null ? building.displayName() : "",
                integrity.available(),
                integrity.percent(),
                integrity.repairableBlocks(),
                integrity.manualRepairBlocks(),
                integrity.repairCost(),
                worker != null,
                worker != null ? worker.uuid() : null,
                worker != null ? worker.name() : "",
                data.running(),
                drillDepth,
                minDepth,
                maxDepth,
                statusKey,
                data.statusText(),
                selected != null ? selected.displayName() : "",
                selected != null ? selected.productId().toString() : "",
                markers,
                building != null,
                building != null ? building.minPos() : BlockPos.ZERO,
                building != null ? building.maxPos() : BlockPos.ZERO,
                !inventory.getItem(MineralDrillingInventory.DRILL_ROD_SLOT).isEmpty(),
                hasBit
        );
    }

    /** setDrillDepth: 校验并持久化纵向滑杆选择的钻井深度。 */
    public static boolean setDrillDepth(ServerLevel level, BlockPos boxPos, int requestedDepth) {
        if (!isControlBox(level, boxPos)) {
            return false;
        }
        int minDepth = level.getMinBuildHeight();
        int maxDepth = Math.max(minDepth, Math.min(boxPos.getY(), level.getMaxBuildHeight() - 1));
        int depth = Math.clamp(requestedDepth, minDepth, maxDepth);
        MineralDrillingBoxManager manager = MineralDrillingBoxManager.get(level);
        MineralDrillingBoxData data = manager.getOrCreate(boxPos);
        VirtualVeinLookupResult lookup = VirtualVeinService.getOrCreateField(level, boxPos);
        VirtualVeinSlot slot = firstSlotAtDepth(lookup, depth);

        // 数据锁与库存锁保持与 toTag 相同的顺序，保证扣杆和深度更新是一个原子操作。
        synchronized (data.inventory()) {
            synchronized (data) {
                int additionalSegments = MineralDrillingDepthCost.additionalSegments(
                        boxPos.getY(), data.lowestReachedDepth(), depth);
                ItemStack rods = data.inventory().getItem(MineralDrillingInventory.DRILL_ROD_SLOT);
                if (additionalSegments > rods.getCount()) {
                    setStatus(manager, data,
                            "gui.simukraft.mineral_drilling.status.insufficient_drill_rods", "");
                    return false;
                }
                if (additionalSegments > 0) {
                    data.inventory().removeItem(MineralDrillingInventory.DRILL_ROD_SLOT,
                            additionalSegments);
                }
                if (depth < data.lowestReachedDepth()) {
                    data.recordLowestReachedDepth(depth);
                }
                data.setDrillDepth(depth);
                data.setSelectedVeinId(slot != null ? slot.veinId() : "");
                if (data.running() && (!drillBitSupportsDepth(data.inventory(), depth) || slot == null)) {
                    data.setRunning(false);
                }
                data.setStatusKey(slot != null
                        ? "gui.simukraft.mineral_drilling.status.depth_selected"
                        : "gui.simukraft.mineral_drilling.status.no_vein_at_depth");
                data.setStatusText("");
                manager.persist(data);
                return requestedDepth == depth;
            }
        }
    }

    /** setDepth: 为菜单事件回调提供简短的深度更新别名。 */
    public static boolean setDepth(ServerLevel level, BlockPos boxPos, int requestedDepth) {
        return setDrillDepth(level, boxPos, requestedDepth);
    }

    /** toggleRunning: 仅在服务端前置条件全部满足后启动钻井。 */
    public static boolean toggleRunning(ServerLevel level, BlockPos boxPos) {
        if (!isControlBox(level, boxPos)) {
            return false;
        }
        MineralDrillingBoxManager manager = MineralDrillingBoxManager.get(level);
        MineralDrillingBoxData data = manager.getOrCreate(boxPos);
        if (data.running()) {
            data.setRunning(false);
            setStatus(manager, data, "gui.simukraft.mineral_drilling.status.paused", "");
            return true;
        }
        if (resolveBuilding(level, boxPos) == null) {
            setStatus(manager, data, "gui.simukraft.mineral_drilling.status.no_building", "");
            return false;
        }
        if (findAssignedWorker(level, boxPos) == null) {
            setStatus(manager, data, "gui.simukraft.mineral_drilling.status.no_worker", "");
            return false;
        }
        MineralDrillingInventory inventory = data.inventory();
        if (inventory.getItem(MineralDrillingInventory.DRILL_BIT_SLOT).isEmpty()) {
            setStatus(manager, data, "gui.simukraft.mineral_drilling.status.no_drill_bit", "");
            return false;
        }
        if (!drillBitSupportsDepth(inventory, data.drillDepth())) {
            String key = data.drillDepth() >= SHALLOW_DRILL_MIN_Y
                    ? "gui.simukraft.mineral_drilling.status.requires_shallow_bit"
                    : "gui.simukraft.mineral_drilling.status.requires_deep_bit";
            setStatus(manager, data, key, "");
            return false;
        }
        VirtualVeinLookupResult lookup = VirtualVeinService.getOrCreateField(level, boxPos);
        VirtualVeinSlot slot = selectedSlot(lookup, data.selectedVeinId(), data.drillDepth());
        if (slot == null) {
            slot = firstSlotAtDepth(lookup, data.drillDepth());
        }
        if (slot == null) {
            setStatus(manager, data, lookup.isReady()
                    ? "gui.simukraft.mineral_drilling.status.no_vein_at_depth"
                    : lookupStatusKey(lookup), "");
            return false;
        }
        data.setSelectedVeinId(slot.veinId());
        data.setRunning(true);
        setStatus(manager, data, "gui.simukraft.mineral_drilling.status.running", "");
        return true;
    }

    /** stop: 以幂等方式暂停指定钻井控制箱。 */
    public static void stop(ServerLevel level, BlockPos boxPos, String reason) {
        if (level == null || boxPos == null) {
            return;
        }
        MineralDrillingBoxData data = MineralDrillingBoxManager.get(level).get(boxPos);
        if (data == null) {
            return;
        }
        data.setRunning(false);
        setStatus(MineralDrillingBoxManager.get(level), data,
                "gui.simukraft.mineral_drilling.status.interrupted", reason);
    }

    /** fireWorker: 释放稳定钻井岗位并暂停控制箱，供拆除和系统清理调用。 */
    public static void fireWorker(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return;
        }
        CitizenEmploymentService.fireAssigned(
                level,
                CitizenEmploymentService.workplaceId(
                        MineralDrillingConstants.HIRE_SOURCE_TYPE,
                        MineralDrillingConstants.HIRE_ROLE,
                        boxPos),
                MineralDrillingConstants.HIRE_SOURCE_TYPE,
                MineralDrillingConstants.HIRE_ROLE,
                boxPos,
                "mineral_driller_fired"
        );
        MineralDrillingBoxData data = MineralDrillingBoxManager.get(level).getOrCreate(boxPos);
        data.setRunning(false);
        setStatus(MineralDrillingBoxManager.get(level), data,
                "gui.simukraft.mineral_drilling.status.worker_fired", "");
    }

    /** fireWorker: 校验操作者权限和预期员工后执行界面解雇，避免陈旧菜单误解雇新员工。 */
    public static boolean fireWorker(
            ServerLevel level, ServerPlayer player, BlockPos boxPos, UUID expectedWorkerId) {
        if (!isControlBox(level, boxPos) || player == null || expectedWorkerId == null
                || player.distanceToSqr(boxPos.getCenter()) > 64.0D) {
            return false;
        }
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        if (building == null || !canManageBuilding(level, player, building)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.no_permission"));
            return false;
        }
        CitizenData assignedWorker = findAssignedWorker(level, boxPos);
        if (assignedWorker == null || !expectedWorkerId.equals(assignedWorker.uuid())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.not_found"));
            return false;
        }
        fireWorker(level, boxPos);
        return true;
    }

    /** interrupt: 暂停所有分配给指定市民的钻井控制箱。 */
    public static void interrupt(ServerLevel level, UUID citizenId, String reason) {
        if (level == null || citizenId == null) {
            return;
        }
        for (MineralDrillingBoxData data : MineralDrillingBoxManager.get(level).all()) {
            CitizenData worker = findAssignedWorker(level, data.boxPos());
            if (worker != null && citizenId.equals(worker.uuid())) {
                stop(level, data.boxPos(), reason);
            }
        }
    }

    /** repairBuilding: 将建筑修复与费用结算委托给共享建筑服务。 */
    public static BuildingIntegrityService.RepairResult repairBuilding(
            ServerLevel level, ServerPlayer player, BlockPos boxPos) {
        return BuildingIntegrityService.repair(level, player, resolveBuilding(level, boxPos));
    }

    /** demolish: 校验城市管理权限后调用共享拆除流程。 */
    public static boolean demolish(ServerLevel level, ServerPlayer player, BlockPos boxPos) {
        if (!isControlBox(level, boxPos) || player == null || !player.blockPosition().closerThan(boxPos, 8.0D)) {
            return false;
        }
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        if (building == null || !canManageBuilding(level, player, building)) {
            return false;
        }
        fireWorker(level, boxPos);
        return PlacedBuildingDemolitionService.demolish(level, building);
    }

    /** onRemoved: 掉落真实工具库存，并清理雇佣关系与持久化状态。 */
    public static void onRemoved(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return;
        }
        MineralDrillingWorkService.clearBox(level, boxPos);
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        MineralDrillingBoxManager manager = MineralDrillingBoxManager.get(level);
        MineralDrillingBoxData data = manager.get(boxPos);
        fireWorker(level, boxPos);
        if (data != null && !data.inventory().isEmpty()) {
            Containers.dropContents(level, boxPos, data.inventory());
        }
        manager.remove(boxPos);
        if (building != null) {
            PlacedBuildingService.unregister(level, building.buildingId());
        }
    }

    /** resolveBuilding: 仅解析包含控制箱的工业建筑，避免误关联相交的其他分类建筑。 */
    public static PlacedBuildingRecord resolveBuilding(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return null;
        }
        for (PlacedBuildingRecord record : PlacedBuildingService.getBuildings(level)) {
            if (!isIndustrialCategory(record.category()) || !inside(record, boxPos) || !isDrillingPlatform(record)) {
                continue;
            }
            if (containsDrillingControlBox(record, boxPos)) {
                return record;
            }
        }
        return null;
    }

    /** isDrillingPlatform: 只接受建筑 JSON 明确声明的钻井平台。 */
    private static boolean isDrillingPlatform(PlacedBuildingRecord record) {
        return record != null
                && BuildingCatalog.findBuilding(record.category(), record.buildingFileName())
                .map(BuildingCatalog.BuildingDefinition::isDrillingPlatform)
                .orElse(false);
    }

    /** isIndustrialCategory: 限制钻井控制箱只能绑定工业类建筑记录。 */
    private static boolean isIndustrialCategory(String category) {
        String normalized = category == null ? "" : category.toLowerCase(Locale.ROOT);
        return "industry".equals(normalized) || "industrial".equals(normalized);
    }

    /** inside: 检查坐标是否位于建筑记录的包围盒内。 */
    private static boolean inside(PlacedBuildingRecord record, BlockPos pos) {
        BlockPos min = record.minPos();
        BlockPos max = record.maxPos();
        return pos.getX() >= Math.min(min.getX(), max.getX())
                && pos.getX() <= Math.max(min.getX(), max.getX())
                && pos.getY() >= Math.min(min.getY(), max.getY())
                && pos.getY() <= Math.max(min.getY(), max.getY())
                && pos.getZ() >= Math.min(min.getZ(), max.getZ())
                && pos.getZ() <= Math.max(min.getZ(), max.getZ());
    }

    /** containsDrillingControlBox: 验证建筑结构记录确实声明了当前钻井控制箱。 */
    private static boolean containsDrillingControlBox(PlacedBuildingRecord record, BlockPos boxPos) {
        if (record == null || boxPos == null || record.blocks() == null || record.worldOrigin() == null) {
            return false;
        }
        for (BuildingBlockData block : record.blocks()) {
            if (block == null || block.relativePos() == null || block.state() == null
                    || !block.state().is(ModBlocks.MINERAL_DRILLING_CONTROL_BOX.get())) {
                continue;
            }
            BlockPos stored = block.relativePos();
            // 新记录保存世界坐标，旧记录保存相对坐标；两个候选位置都检查可兼容迁移前后的存档。
            if (boxPos.equals(stored) || boxPos.equals(record.worldOrigin().offset(stored))) {
                return true;
            }
        }
        return false;
    }

    /** findAssignedWorker: 查询绑定到指定钻井控制箱的市民。 */
    public static CitizenData findAssignedWorker(ServerLevel level, BlockPos boxPos) {
        return CitizenEmploymentService.findAssigned(
                level,
                MineralDrillingConstants.HIRE_SOURCE_TYPE,
                MineralDrillingConstants.HIRE_ROLE,
                boxPos
        ).orElse(null);
    }

    /** isControlBox: 检查位置当前是否仍为矿物钻井控制箱。 */
    public static boolean isControlBox(ServerLevel level, BlockPos boxPos) {
        return level != null && boxPos != null && level.isLoaded(boxPos)
                && level.getBlockState(boxPos).is(ModBlocks.MINERAL_DRILLING_CONTROL_BOX.get());
    }

    private static List<MineralDrillingControlBoxView.VeinMarker> markers(
            VirtualVeinLookupResult lookup, String selectedVeinId) {
        if (!lookup.isReady()) {
            return List.of();
        }
        List<MineralDrillingControlBoxView.VeinMarker> markers = new ArrayList<>();
        for (VirtualVeinSlot slot : lookup.profile().slots()) {
            if (slot.state() == VirtualVeinSlotState.EMPTY) {
                continue;
            }
            markers.add(new MineralDrillingControlBoxView.VeinMarker(
                    slot.veinId(),
                    slot.displayName(),
                    slot.productId().toString(),
                    slot.minY(),
                    slot.maxY(),
                    slot.remainingReserve(),
                    slot.veinId().equals(selectedVeinId)
            ));
        }
        return List.copyOf(markers);
    }

    private static VirtualVeinSlot selectedSlot(
            VirtualVeinLookupResult lookup, String selectedVeinId, int depth) {
        if (!lookup.isReady() || selectedVeinId == null || selectedVeinId.isBlank()) {
            return null;
        }
        for (var slot : lookup.profile().slots()) {
            if (slot.state() == VirtualVeinSlotState.ACTIVE
                    && slot.veinId().equals(selectedVeinId)
                    && slot.acceptsY(depth)) {
                return slot;
            }
        }
        return null;
    }

    private static VirtualVeinSlot firstSlotAtDepth(VirtualVeinLookupResult lookup, int depth) {
        if (!lookup.isReady()) {
            return null;
        }
        for (var slot : lookup.profile().slots()) {
            if (slot.state() == VirtualVeinSlotState.ACTIVE && slot.acceptsY(depth)) {
                return slot;
            }
        }
        return null;
    }

    private static boolean drillBitSupportsDepth(MineralDrillingInventory inventory, int depth) {
        ItemStack bit = inventory.getItem(MineralDrillingInventory.DRILL_BIT_SLOT);
        return depth >= SHALLOW_DRILL_MIN_Y
                ? bit.is(ModItems.SHALLOW_DRILL_BIT.get())
                : bit.is(ModItems.DEEP_DRILL_BIT.get());
    }

    private static String resolveStatusKey(
            MineralDrillingBoxData data,
            PlacedBuildingRecord building,
            CitizenData worker,
            boolean hasBit,
            VirtualVeinSlot selected,
            VirtualVeinLookupResult lookup) {
        if (building == null) {
            return "gui.simukraft.mineral_drilling.status.no_building";
        }
        if (worker == null) {
            return "gui.simukraft.mineral_drilling.status.no_worker";
        }
        if (!hasBit) {
            return "gui.simukraft.mineral_drilling.status.no_drill_bit";
        }
        if (!drillBitSupportsDepth(data.inventory(), data.drillDepth())) {
            return data.drillDepth() >= SHALLOW_DRILL_MIN_Y
                    ? "gui.simukraft.mineral_drilling.status.requires_shallow_bit"
                    : "gui.simukraft.mineral_drilling.status.requires_deep_bit";
        }
        if (!lookup.isReady()) {
            return lookupStatusKey(lookup);
        }
        if (selected == null) {
            return "gui.simukraft.mineral_drilling.status.no_vein_at_depth";
        }
        if (!data.statusKey().isBlank()) {
            return data.statusKey();
        }
        return data.running()
                ? "gui.simukraft.mineral_drilling.status.running"
                : "gui.simukraft.mineral_drilling.status.idle";
    }

    private static String lookupStatusKey(VirtualVeinLookupResult lookup) {
        if (lookup == null) {
            return "gui.simukraft.mineral_drilling.status.vein_unavailable";
        }
        return switch (lookup.status()) {
            case NOT_OVERWORLD -> "gui.simukraft.mineral_drilling.status.overworld_only";
            case DEFINITIONS_UNAVAILABLE -> "gui.simukraft.mineral_drilling.status.no_definitions";
            case DATABASE_UNAVAILABLE -> "gui.simukraft.mineral_drilling.status.database_unavailable";
            case UNSUPPORTED_WORLDGEN -> "gui.simukraft.mineral_drilling.status.unsupported_worldgen";
            case READY -> "gui.simukraft.mineral_drilling.status.no_vein_at_depth";
        };
    }

    private static void setStatus(
            MineralDrillingBoxManager manager,
            MineralDrillingBoxData data,
            String statusKey,
            String statusText) {
        data.setStatusKey(statusKey);
        data.setStatusText(statusText == null ? "" : statusText);
        manager.persist(data);
    }

    private static boolean canManageBuilding(
            ServerLevel level, ServerPlayer player, PlacedBuildingRecord building) {
        if (player.hasPermissions(2)) {
            return true;
        }
        return building.cityId() != null
                && CityService.canManageCity(level, building.cityId(), player.getUUID());
    }
}
