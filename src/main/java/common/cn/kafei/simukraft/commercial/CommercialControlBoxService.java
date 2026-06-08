package common.cn.kafei.simukraft.commercial;

import common.cn.kafei.simukraft.building.BuildingIntegrityService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@SuppressWarnings("null")
public final class CommercialControlBoxService {
    private CommercialControlBoxService() {
    }

    /** buildView: 构建商业控制箱客户端视图。 */
    public static CommercialControlBoxView buildView(ServerLevel level, BlockPos boxPos) {
        CommercialBoxData data = CommercialBoxManager.get(level).getOrCreate(boxPos);
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        CommercialDefinitionLoader.LoadResult loadResult = CommercialDefinitionLoader.loadForBuilding(building);
        CommercialDefinition definition = loadResult.definition();
        synchronizeBoxMetadata(level, data, building, definition);
        CitizenData worker = findAssignedWorker(level, boxPos);
        synchronizeWorkerMetadata(level, worker, definition);
        BuildingIntegrityService.IntegrityPreview integrity = BuildingIntegrityService.preview(level, building);
        return new CommercialControlBoxView(
                boxPos.immutable(),
                building != null,
                building != null ? building.displayName() : "",
                loadResult.valid(),
                definition != null ? definition.name() : "",
                resolveStatusKey(data, building, loadResult, worker),
                data.statusText(),
                data.running(),
                worker != null,
                worker != null ? worker.uuid() : null,
                worker != null ? worker.name() : "",
                building != null && building.cityId() != null ? EconomyService.getCityBalance(level, building.cityId()) : 0.0D,
                building != null,
                building != null ? building.minPos().immutable() : BlockPos.ZERO,
                building != null ? building.maxPos().immutable() : BlockPos.ZERO,
                integrity.available(),
                integrity.percent(),
                integrity.repairableBlocks(),
                integrity.manualRepairBlocks(),
                integrity.repairCost()
        );
    }

    /** buildTradeView: 构建 NPC 商业交易客户端视图。 */
    public static CommercialTradeView buildTradeView(ServerLevel level, BlockPos boxPos, UUID workerId) {
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        CommercialDefinitionLoader.LoadResult loadResult = CommercialDefinitionLoader.loadForBuilding(building);
        CommercialDefinition definition = loadResult.definition();
        if (definition != null) {
            CommercialStockService.restock(level, boxPos, definition);
        }
        CitizenData worker = workerId != null
                ? CitizenService.findCitizen(level, workerId).orElse(null)
                : findAssignedWorker(level, boxPos);
        return new CommercialTradeView(
                boxPos.immutable(),
                worker != null ? worker.uuid() : null,
                definition != null ? definition.name() : "",
                worker != null ? worker.name() : "",
                building != null && building.cityId() != null ? EconomyService.getCityBalance(level, building.cityId()) : 0.0D,
                CommercialBoxManager.get(level).getOrCreate(boxPos).running(),
                definition != null ? playerOfferEntries(level, boxPos, definition) : List.of()
        );
    }

    /** fireWorker: 解雇商业控制箱员工。 */
    public static void fireWorker(ServerLevel level, BlockPos boxPos) {
        CitizenEmploymentService.fireAssigned(level,
                CitizenEmploymentService.workplaceId(CommercialConstants.HIRE_SOURCE_TYPE, CommercialConstants.HIRE_ROLE, boxPos),
                CommercialConstants.HIRE_SOURCE_TYPE,
                CommercialConstants.HIRE_ROLE,
                boxPos,
                "commercial_fired");
        CommercialBoxData data = CommercialBoxManager.get(level).getOrCreate(boxPos);
        data.setRunning(false);
        data.setStatusKey("gui.simukraft.commercial.status.worker_fired");
        data.setStatusText("");
        CommercialBoxManager.get(level).persist(data);
    }

    /** openForWorker: 玩家右键商业员工时打开其所属商业建筑交易界面。 */
    public static boolean openForWorker(ServerLevel level, ServerPlayer player, CitizenData worker) {
        BlockPos boxPos = resolveWorkerBox(level, worker);
        if (level == null || player == null || boxPos == null) {
            return false;
        }
        if (!CommercialTradeAccessValidator.canUseTradeMenu(level, player, boxPos, worker.uuid())) {
            return false;
        }
        return CommercialTradeMenuProvider.open(player, buildTradeView(level, boxPos, worker.uuid()));
    }

    /** interrupt: 当员工状态变化时中断对应商业箱。 */
    public static void interrupt(ServerLevel level, UUID citizenId, String reason) {
        if (level == null || citizenId == null) {
            return;
        }
        for (CommercialBoxData data : CommercialBoxManager.get(level).all()) {
            UUID assigned = CitizenService.findAssignedCitizen(level,
                    CitizenEmploymentService.workplaceId(CommercialConstants.HIRE_SOURCE_TYPE, CommercialConstants.HIRE_ROLE, data.boxPos()));
            if (!citizenId.equals(assigned)) {
                continue;
            }
            data.setRunning(false);
            data.setStatusKey("gui.simukraft.commercial.status.interrupted");
            data.setStatusText(reason != null ? reason : "");
            CommercialBoxManager.get(level).persist(data);
        }
    }

    /** onRemoved: 商业控制箱被破坏时清理状态、库存和雇佣。 */
    public static void onRemoved(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return;
        }
        fireWorker(level, boxPos);
        CommercialBoxManager.get(level).remove(boxPos);
        CommercialStockService.removeBox(level, boxPos);
    }

    /** resolveBuilding: 解析商业控制箱所属商业建筑。 */
    public static PlacedBuildingRecord resolveBuilding(ServerLevel level, BlockPos boxPos) {
        PlacedBuildingRecord building = PlacedBuildingService.findByContainedPos(level, boxPos);
        if (building == null) {
            return null;
        }
        String category = building.category() != null ? building.category().toLowerCase(Locale.ROOT) : "";
        return "commercial".equals(category) || "commerce".equals(category) ? building : null;
    }

    /** findAssignedWorker: 查找分配给商业控制箱的员工。 */
    public static CitizenData findAssignedWorker(ServerLevel level, BlockPos boxPos) {
        return CitizenEmploymentService.findAssigned(level, CommercialConstants.HIRE_SOURCE_TYPE, CommercialConstants.HIRE_ROLE, boxPos)
                .orElse(null);
    }

    /** resolveWorkerBox: 解析商业员工绑定的控制箱位置。 */
    public static BlockPos resolveWorkerBox(ServerLevel level, CitizenData worker) {
        if (level == null || worker == null || worker.workplaceId() == null || worker.workplacePos() == null) {
            return null;
        }
        BlockPos boxPos = worker.workplacePos();
        UUID expectedWorkplaceId = CitizenEmploymentService.workplaceId(CommercialConstants.HIRE_SOURCE_TYPE, CommercialConstants.HIRE_ROLE, boxPos);
        if (!expectedWorkplaceId.equals(worker.workplaceId()) || !isCommercialControlBox(level, boxPos)) {
            return null;
        }
        CitizenData assigned = findAssignedWorker(level, boxPos);
        return assigned != null && worker.uuid().equals(assigned.uuid()) ? boxPos.immutable() : null;
    }

    /** synchronizeAssignedWorkerMetadata: 同步被雇佣员工的商业职业 ID。 */
    public static void synchronizeAssignedWorkerMetadata(ServerLevel level, BlockPos boxPos) {
        PlacedBuildingRecord building = resolveBuilding(level, boxPos);
        CommercialDefinitionLoader.LoadResult loadResult = CommercialDefinitionLoader.loadForBuilding(building);
        CommercialDefinition definition = loadResult.definition();
        CitizenData worker = findAssignedWorker(level, boxPos);
        synchronizeWorkerMetadata(level, worker, definition);
        if (level == null || building == null || !loadResult.valid() || worker == null) {
            return;
        }
        CommercialBoxData data = CommercialBoxManager.get(level).getOrCreate(boxPos);
        data.setRunning(true);
        data.setStatusKey("gui.simukraft.commercial.status.open");
        data.setStatusText("");
        CommercialBoxManager.get(level).persist(data);
    }

    /** isCommercialControlBox: 判断位置是否为商业控制箱。 */
    public static boolean isCommercialControlBox(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.COMMERCIAL_CONTROL_BOX.get());
    }

    private static void synchronizeBoxMetadata(ServerLevel level, CommercialBoxData data, PlacedBuildingRecord building, CommercialDefinition definition) {
        if (data == null) {
            return;
        }
        boolean changed = false;
        if (building != null && !building.buildingId().toString().equals(data.buildingId())) {
            data.setBuildingId(building.buildingId().toString());
            changed = true;
        }
        if (definition != null && !definition.id().equals(data.definitionId())) {
            data.setDefinitionId(definition.id());
            changed = true;
        }
        if (changed && level != null) {
            CommercialBoxManager.get(level).persist(data);
        }
    }

    private static void synchronizeWorkerMetadata(ServerLevel level, CitizenData worker, CommercialDefinition definition) {
        if (level == null || worker == null || definition == null || definition.job().id().isBlank()) {
            return;
        }
        if (definition.job().id().equals(worker.jobId())) {
            return;
        }
        worker.setJobIdRaw(definition.job().id());
        CitizenService.save(level, worker.uuid());
    }

    private static String resolveStatusKey(CommercialBoxData data, PlacedBuildingRecord building, CommercialDefinitionLoader.LoadResult loadResult, CitizenData worker) {
        if (building == null) {
            return "gui.simukraft.commercial.status.no_building";
        }
        if (!loadResult.valid()) {
            return "gui.simukraft.commercial.status.invalid_definition";
        }
        if (worker == null) {
            return "gui.simukraft.commercial.status.no_worker";
        }
        if (!data.statusKey().isBlank()) {
            return data.statusKey();
        }
        return data.running() ? "gui.simukraft.commercial.status.open" : "gui.simukraft.commercial.status.closed";
    }

    private static List<CommercialTradeView.OfferEntry> playerOfferEntries(ServerLevel level, BlockPos boxPos, CommercialDefinition definition) {
        return definition.playerOffers().stream()
                .map(offer -> offerEntry(level, boxPos, offer))
                .toList();
    }

    private static CommercialTradeView.OfferEntry offerEntry(ServerLevel level, BlockPos boxPos, CommercialOffer offer) {
        CommercialOffer.StockRule rule = offer.stock();
        CommercialStockData stock = rule != null && rule.sqliteBacked() ? CommercialStockManager.get(level).get(boxPos, rule.itemId()) : null;
        int materialStock = rule != null && rule.materialBacked() ? CommercialTradeSupplyService.availableForOffer(level, boxPos, offer) : 0;
        return new CommercialTradeView.OfferEntry(
                offer.id(),
                resourceEntries(offer.cost()),
                resourceEntries(offer.result()),
                rule != null ? rule.itemId() : "",
                stock != null ? stock.currentStock() : materialStock,
                stock != null ? stock.maxStock() : rule != null && rule.materialBacked() ? Math.max(rule.max(), materialStock) : 0,
                rule != null && rule.sqliteBacked() ? rule.restockInterval() : 0L,
                rule != null && rule.sqliteBacked() ? rule.restockAmount() : 0
        );
    }

    private static List<CommercialTradeView.ResourceEntry> resourceEntries(List<CommercialResource> resources) {
        return resources.stream()
                .map(resource -> new CommercialTradeView.ResourceEntry(
                        resource.type().name().toLowerCase(Locale.ROOT),
                        resource.itemId(),
                        resource.count(),
                        resource.money()
                ))
                .toList();
    }
}
