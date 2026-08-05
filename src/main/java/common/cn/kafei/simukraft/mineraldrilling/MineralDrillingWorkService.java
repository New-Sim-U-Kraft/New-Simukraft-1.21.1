package common.cn.kafei.simukraft.mineraldrilling;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.registry.ModItems;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinConsumption;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinLookupResult;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinLocatedSlot;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinService;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/** MineralDrillingWorkService: 按矿脉周期推进已启动钻井并生成原版产物。 */
@SuppressWarnings("null")
public final class MineralDrillingWorkService {
    private static final int MAX_BOXES_PER_TICK = 32;
    private static final ConcurrentMap<String, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();

    private MineralDrillingWorkService() {
    }

    /** tick: 分片推进当前维度的钻井，避免控制箱数量增长时每 tick 全量处理。 */
    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        List<MineralDrillingBoxData> boxes = MineralDrillingBoxManager.get(level).all();
        if (boxes.isEmpty()) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        int start = Math.floorMod(runtime.cursor.getAndAdd(MAX_BOXES_PER_TICK), boxes.size());
        int limit = Math.min(MAX_BOXES_PER_TICK, boxes.size());
        for (int offset = 0; offset < limit; offset++) {
            process(level, boxes.get((start + offset) % boxes.size()), runtime, level.getGameTime());
        }
    }

    /** clearBox: 移除单个控制箱的运行时计时，避免拆除后残留缓存。 */
    public static void clearBox(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return;
        }
        LevelRuntime runtime = RUNTIMES.get(runtimeKey(level));
        if (runtime != null) {
            runtime.nextProductionTicks.remove(boxPos.immutable());
        }
    }

    /** clearServerCaches: 关服时释放跨维度运行时状态，防止切档复用旧计时。 */
    public static void clearServerCaches(MinecraftServer server) {
        if (server == null) {
            return;
        }
        String serverKey = SaveScopedCacheKey.serverKey(server).toLowerCase(Locale.ROOT);
        RUNTIMES.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    /** process: 对一个已启动且已到周期的控制箱执行一次原子采掘。 */
    private static void process(ServerLevel level,
                                MineralDrillingBoxData data,
                                LevelRuntime runtime,
                                long gameTime) {
        if (data == null) {
            return;
        }
        BlockPos boxPos = data.boxPos();
        if (!data.running()) {
            runtime.nextProductionTicks.remove(boxPos);
            return;
        }
        if (!level.isLoaded(boxPos)) {
            return;
        }
        if (!MineralDrillingControlBoxService.isControlBox(level, boxPos)) {
            runtime.nextProductionTicks.remove(boxPos);
            return;
        }

        Long nextTick = runtime.nextProductionTicks.get(boxPos);
        if (nextTick != null && gameTime < nextTick) {
            return;
        }

        MineralDrillingBoxManager manager = MineralDrillingBoxManager.get(level);
        PlacedBuildingRecord building = MineralDrillingControlBoxService.resolveBuilding(level, boxPos);
        if (building == null) {
            pause(manager, data, "gui.simukraft.mineral_drilling.status.no_building");
            runtime.nextProductionTicks.remove(boxPos);
            return;
        }
        CitizenData worker = MineralDrillingControlBoxService.findAssignedWorker(level, boxPos);
        if (worker == null) {
            pause(manager, data, "gui.simukraft.mineral_drilling.status.no_worker");
            runtime.nextProductionTicks.remove(boxPos);
            return;
        }

        MineralDrillingInventory inventory = data.inventory();
        ItemStack bit = inventory.getItem(MineralDrillingInventory.DRILL_BIT_SLOT);
        if (!supportsDepth(bit, data.drillDepth())) {
            pause(manager, data, data.drillDepth() >= MineralDrillingControlBoxService.SHALLOW_DRILL_MIN_Y
                    ? "gui.simukraft.mineral_drilling.status.requires_shallow_bit"
                    : "gui.simukraft.mineral_drilling.status.requires_deep_bit");
            runtime.nextProductionTicks.remove(boxPos);
            return;
        }

        List<VirtualVeinLocatedSlot> locatedSlots = VirtualVeinService.findVeinsAtY(
                level, drillTargetPos(boxPos, data.drillDepth()));
        if (locatedSlots.isEmpty()) {
            VirtualVeinLookupResult lookup = VirtualVeinService.getOrCreateField(level, boxPos);
            pause(manager, data, lookup.isReady()
                    ? "gui.simukraft.mineral_drilling.status.no_vein_at_depth"
                    : "gui.simukraft.mineral_drilling.status.vein_unavailable");
            runtime.nextProductionTicks.remove(boxPos);
            return;
        }

        List<ProductionPlan> plans = productionPlans(locatedSlots);
        if (plans == null) {
            pause(manager, data, "gui.simukraft.mineral_drilling.status.invalid_product");
            runtime.nextProductionTicks.remove(boxPos);
            return;
        }

        if (nextTick == null) {
            runtime.nextProductionTicks.put(boxPos, gameTime + productionPeriod(plans));
            return;
        }

        List<ItemStack> plannedOutputs = plans.stream()
                .map(plan -> new ItemStack(plan.product(), plan.requested()))
                .toList();
        if (!MineralDrillingOutputService.canStoreAll(level, building, plannedOutputs)) {
            pause(manager, data, "gui.simukraft.mineral_drilling.status.output_full");
            runtime.nextProductionTicks.remove(boxPos);
            return;
        }

        List<ItemStack> producedOutputs = new ArrayList<>(plans.size());
        List<VirtualVeinConsumption> consumptions = new ArrayList<>(plans.size());
        for (ProductionPlan plan : plans) {
            var consumption = VirtualVeinService.consume(
                    level, drillTargetPos(boxPos, data.drillDepth()), plan.slot().veinId(), plan.requested());
            if (consumption.isEmpty()) {
                if (!producedOutputs.isEmpty()) {
                    MineralDrillingOutputService.storeAll(level, building, producedOutputs);
                }
                pause(manager, data, "gui.simukraft.mineral_drilling.status.vein_depleted");
                runtime.nextProductionTicks.remove(boxPos);
                return;
            }
            consumptions.add(consumption.get());
            if (consumption.get().consumed() > 0) {
                producedOutputs.add(new ItemStack(plan.product(), consumption.get().consumed()));
            }
        }

        int produced = producedOutputs.stream().mapToInt(ItemStack::getCount).sum();
        if (produced <= 0 || !MineralDrillingOutputService.storeAll(level, building, producedOutputs)) {
            SimuKraft.LOGGER.error("Simukraft: Mineral drilling output changed after capacity check at {}", boxPos);
            pause(manager, data, "gui.simukraft.mineral_drilling.status.output_full");
            runtime.nextProductionTicks.remove(boxPos);
            return;
        }
        boolean drillBitBroken = consumeDrillBitDurability(level, inventory, produced);
        data.setSelectedVeinId(plans.getFirst().slot().veinId());

        if (consumptions.stream().allMatch(VirtualVeinConsumption::depleted)) {
            data.setRunning(false);
            data.setStatusKey("gui.simukraft.mineral_drilling.status.vein_depleted");
            data.setStatusText("");
            manager.persist(data);
            runtime.nextProductionTicks.remove(boxPos);
            return;
        }
        if (drillBitBroken) {
            data.setRunning(false);
            data.setStatusKey("gui.simukraft.mineral_drilling.status.no_drill_bit");
            data.setStatusText("");
            manager.persist(data);
            markWorkerWorking(level, worker);
            runtime.nextProductionTicks.remove(boxPos);
            return;
        }
        data.setStatusKey("gui.simukraft.mineral_drilling.status.running");
        data.setStatusText("");
        manager.persist(data);
        markWorkerWorking(level, worker);
        runtime.nextProductionTicks.put(boxPos, gameTime + productionPeriod(plans));
    }

    /** drillTargetPos: 保持控制箱所在矿区 X/Z，并使用钻井深度参与矿脉范围校验。 */
    static BlockPos drillTargetPos(BlockPos boxPos, int drillDepth) {
        return new BlockPos(boxPos.getX(), drillDepth, boxPos.getZ());
    }

    /** productionPlans: 为同一 Y 层的全部活动矿脉生成平分产量的生产计划。 */
    private static List<ProductionPlan> productionPlans(List<VirtualVeinLocatedSlot> locatedSlots) {
        int veinCount = locatedSlots.size();
        List<ProductionPlan> plans = new ArrayList<>(veinCount);
        for (VirtualVeinLocatedSlot located : locatedSlots) {
            VirtualVeinSlot slot = located.slot();
            Item product = BuiltInRegistries.ITEM.getOptional(slot.productId()).orElse(Items.AIR);
            if (product == Items.AIR) {
                return null;
            }
            int dividedAmount = productionAmountPerVein(slot.amount(), veinCount);
            int requested = Math.min(dividedAmount, slot.remainingReserve());
            if (requested > 0) {
                plans.add(new ProductionPlan(slot, product, requested));
            }
        }
        return List.copyOf(plans);
    }

    /** productionAmountPerVein: 按命中矿脉数量平分单次产量，确保有效矿脉至少产出一件。 */
    static int productionAmountPerVein(int amount, int veinCount) {
        if (amount <= 0 || veinCount <= 0) {
            return 0;
        }
        return Math.max(1, amount / veinCount);
    }

    /** productionPeriod: 使用本轮矿脉中最短周期，避免重叠矿脉因周期不同被长期跳过。 */
    private static int productionPeriod(List<ProductionPlan> plans) {
        return Math.max(1, plans.stream().mapToInt(plan -> plan.slot().periodTicks()).min().orElse(1));
    }

    /** supportsDepth: 校验钻头类型与当前目标深度是否匹配。 */
    private static boolean supportsDepth(ItemStack bit, int depth) {
        return depth >= MineralDrillingControlBoxService.SHALLOW_DRILL_MIN_Y
                ? bit.is(ModItems.SHALLOW_DRILL_BIT.get())
                : bit.is(ModItems.DEEP_DRILL_BIT.get());
    }

    /** consumeDrillBitDurability: 每成功写入一个产物扣除钻头一点耐久，并在损坏后清空槽位。 */
    private static boolean consumeDrillBitDurability(ServerLevel level,
                                                     MineralDrillingInventory inventory,
                                                     int produced) {
        if (level == null || inventory == null || produced <= 0) {
            return false;
        }
        synchronized (inventory) {
            ItemStack bit = inventory.getItem(MineralDrillingInventory.DRILL_BIT_SLOT);
            if (bit.isEmpty()) {
                return true;
            }
            if (!bit.isDamageableItem()) {
                inventory.setItem(MineralDrillingInventory.DRILL_BIT_SLOT, ItemStack.EMPTY);
                return true;
            }
            bit.hurtAndBreak(produced, level, (net.minecraft.server.level.ServerPlayer) null, ignored -> {
            });
            inventory.setItem(MineralDrillingInventory.DRILL_BIT_SLOT, bit);
            return bit.isEmpty();
        }
    }

    /** pause: 暂停控制箱并写入一次可翻译的阻塞状态。 */
    private static void pause(MineralDrillingBoxManager manager, MineralDrillingBoxData data, String statusKey) {
        if (!data.running() && statusKey.equals(data.statusKey())) {
            return;
        }
        data.setRunning(false);
        data.setStatusKey(statusKey);
        data.setStatusText("");
        manager.persist(data);
    }

    /** markWorkerWorking: 刷新钻井工的工作状态，使 NPC 状态面板与控制箱保持一致。 */
    private static void markWorkerWorking(ServerLevel level, CitizenData worker) {
        worker.setWorkStatus(CitizenWorkStatus.WORKING);
        worker.setStatusLabel("gui.simukraft.mineral_drilling.status.running");
        CitizenService.save(level, worker.uuid());
    }

    /** runtime: 获取当前存档维度隔离的运行时计时器。 */
    private static LevelRuntime runtime(ServerLevel level) {
        return RUNTIMES.computeIfAbsent(runtimeKey(level), ignored -> new LevelRuntime());
    }

    /** runtimeKey: 生成存档和维度隔离的计时器键。 */
    private static String runtimeKey(ServerLevel level) {
        return SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT);
    }

    private record ProductionPlan(VirtualVeinSlot slot, Item product, int requested) {
    }

    private static final class LevelRuntime {
        private final ConcurrentMap<BlockPos, Long> nextProductionTicks = new ConcurrentHashMap<>();
        private final AtomicInteger cursor = new AtomicInteger();
    }
}
