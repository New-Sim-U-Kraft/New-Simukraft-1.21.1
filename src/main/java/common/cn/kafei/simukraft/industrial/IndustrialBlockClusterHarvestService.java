package common.cn.kafei.simukraft.industrial;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("null")
public final class IndustrialBlockClusterHarvestService {
    private static final String STATE_KIND = "harvest_block_clusters";
    private static final int LOG_NEIGHBOR_RADIUS = 1;
    private static final int ATTACHED_VALIDATE_RADIUS = 2;
    private static final int ATTACHED_HARVEST_RADIUS = 3;
    private static final int MIN_STUMP_DIG_TICKS = 1;
    private static final int MAX_STUMP_DIG_TICKS = 60;
    private static final double STUMP_DIG_SPEED_MULTIPLIER = 2.5D;
    private static final int SWING_INTERVAL_TICKS = 4;
    private static final long MINING_RUNTIME_TTL_TICKS = 20L * 60L * 10L;
    private static final long MINING_RUNTIME_CLEANUP_INTERVAL_TICKS = 20L * 60L;
    private static final ConcurrentMap<String, MiningRuntime> MINING_RUNTIMES = new ConcurrentHashMap<>();

    private IndustrialBlockClusterHarvestService() {
    }

    /** clearServerCaches: 清理采集运行时计数，避免静态缓存跨存档残留。 */
    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server).toLowerCase(Locale.ROOT);
        MINING_RUNTIMES.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    private static final class MiningRuntime {
        private final AtomicInteger ticks = new AtomicInteger();
        private final AtomicLong lastSeenAt = new AtomicLong();
    }

    /** execute: 执行“区域方块簇采集”通用动作，产物先进入持久化临时携带状态。 */
    public static ActionResult execute(ServerLevel level,
                                       IndustrialBoxManager manager,
                                       IndustrialBoxData data,
                                       PlacedBuildingRecord building,
                                       IndustrialDefinition definition,
                                       IndustrialDefinition.StepDefinition step,
                                       CitizenEntity worker,
                                       UUID workerId) {
        if (level == null || manager == null || data == null || building == null || definition == null || step == null) {
            return ActionResult.INVALID_STEP;
        }
        cleanupMiningRuntimes(level);
        Predicate<BlockState> targetMatcher = blockMatcher(level, step.targetBlockTag());
        if (targetMatcher == null) {
            return ActionResult.INVALID_STEP;
        }
        HarvestConfig config = HarvestConfig.from(level, building, definition.workArea(), step);
        HarvestState state = HarvestState.read(data.machineState(), data.currentStep(), building, config);
        if (!state.plantPositions().isEmpty()) {
            return plantRemaining(level, manager, data, building, definition, step, state, worker);
        }
        if (!state.clusterPositions().isEmpty()) {
            HarvestState activeState = repairActiveState(level, manager, data, step, state);
            if (activeState.clusterPositions().isEmpty()) {
                data.setMachineState("");
                manager.persist(data);
                setStatus(manager, data, "gui.simukraft.industrial.status.harvesting_blocks", "重新扫描树木");
                return ActionResult.SCANNING;
            }
            return harvestActiveCluster(level, manager, data, building, definition, step, activeState, worker, workerId);
        }
        if (IndustrialCarriedItemService.stackCount(level, manager, data) >= config.maxCarryStacks()) {
            return ActionResult.CARRY_FULL;
        }

        QueuedCluster queuedCluster = nextQueuedCluster(level, building, step, targetMatcher, config, state, worker != null ? worker.position() : null);
        if (!queuedCluster.state().equals(state)) {
            persistMachineState(manager, data, queuedCluster.state());
            state = queuedCluster.state();
        }
        if (queuedCluster.cluster() != null) {
            HarvestState next = state.withCluster(queuedCluster.cluster()).withoutQueuedTarget(queuedCluster.cluster().target());
            persistMachineState(manager, data, next);
            setStatus(manager, data, "gui.simukraft.industrial.status.harvesting_trees",
                    "锁定队列树桩 " + shortPos(next.target()) + " 方块 " + next.clusterPositions().size());
            return harvestActiveCluster(level, manager, data, building, definition, step, next, worker, workerId);
        }

        ScanResult scan = scanForNextCluster(level, building, step, targetMatcher, config, state, worker != null ? worker.position() : null);
        if (scan.cluster() != null) {
            HarvestState next = scan.state().withCluster(scan.cluster());
            persistMachineState(manager, data, next);
            setStatus(manager, data, "gui.simukraft.industrial.status.harvesting_trees",
                    "找到树桩 " + shortPos(next.target()) + " 方块 " + next.clusterPositions().size());
            return harvestActiveCluster(level, manager, data, building, definition, step, next, worker, workerId);
        }
        persistMachineState(manager, data, scan.state());
        if (scan.completed()) {
            data.setMachineState("");
            manager.persist(data);
            return ActionResult.AREA_EMPTY;
        }
        return ActionResult.SCANNING;
    }

    private static ActionResult harvestActiveCluster(ServerLevel level,
                                                     IndustrialBoxManager manager,
                                                     IndustrialBoxData data,
                                                     PlacedBuildingRecord building,
                                                     IndustrialDefinition definition,
                                                     IndustrialDefinition.StepDefinition step,
                                                     HarvestState state,
                                                     CitizenEntity worker,
                                                     UUID workerId) {
        if (worker != null && workerId != null && !isCloseEnoughToHarvest(level, step, state.target(), worker)) {
            return moveTowardCluster(level, manager, data, step, state, worker, workerId);
        }
        if (worker == null) {
            return fellActiveCluster(level, manager, data, building, definition, step, state, null);
        }
        return mineStumpThenFellCluster(level, manager, data, building, definition, step, state, worker);
    }

    /** repairActiveState：修复旧存档或异常中缺失树桩目标的采集状态，避免 NPC 原地空等。 */
    private static HarvestState repairActiveState(ServerLevel level,
                                                  IndustrialBoxManager manager,
                                                  IndustrialBoxData data,
                                                  IndustrialDefinition.StepDefinition step,
                                                  HarvestState state) {
        if (state == null || state.clusterPositions().isEmpty()) {
            return state != null ? state : HarvestState.empty(data != null ? data.currentStep() : 0);
        }
        if (isUsableStump(level, step, state.target())) {
            return state;
        }
        BlockPos repairedTarget = chooseStumpFromCluster(level, step, state.clusterPositions());
        if (repairedTarget == null) {
            setStatus(manager, data, "gui.simukraft.industrial.status.harvesting_blocks", "树簇失效，重新扫描");
            return state.withClusterPositions(List.of());
        }
        HarvestState repaired = state.withTarget(repairedTarget);
        persistMachineState(manager, data, repaired);
        setStatus(manager, data, "gui.simukraft.industrial.status.harvesting_trees", "锁定树桩 " + shortPos(repairedTarget));
        return repaired;
    }

    private static ActionResult moveTowardCluster(ServerLevel level,
                                                  IndustrialBoxManager manager,
                                                  IndustrialBoxData data,
                                                  IndustrialDefinition.StepDefinition step,
                                                  HarvestState state,
                                                  CitizenEntity worker,
                                                  UUID workerId) {
        BlockPos target = state.target();
        if (target == null) {
            setStatus(manager, data, "gui.simukraft.industrial.status.harvesting_blocks", "缺少树桩目标");
            return ActionResult.BLOCKED;
        }
        if (isCloseEnoughToHarvest(level, step, target, worker)) {
            CitizenNavigationService.stop(level, workerId);
            return ActionResult.HARVESTED;
        }
        BlockPos stand = nearestStandTarget(level, target, worker.position());
        Vec3 moveTarget = stand != null ? Vec3.atBottomCenterOf(stand) : Vec3.atBottomCenterOf(target);
        if (!CitizenNavigationService.requestMove(level, workerId, moveTarget, MovementIntent.WORK)) {
            setStatus(manager, data, "gui.simukraft.industrial.status.block_action_blocked", "无法前往树桩 " + shortPos(target));
            return ActionResult.BLOCKED;
        }
        setStatus(manager, data, "gui.simukraft.industrial.status.moving", "前往树桩 " + shortPos(target));
        persistMachineState(manager, data, state);
        return ActionResult.MOVING;
    }

    /** mineStumpThenFellCluster：先按正常挖掘进度砍树桩，完成后一次性结算整棵树。 */
    private static ActionResult mineStumpThenFellCluster(ServerLevel level,
                                                         IndustrialBoxManager manager,
                                                         IndustrialBoxData data,
                                                         PlacedBuildingRecord building,
                                                         IndustrialDefinition definition,
                                                         IndustrialDefinition.StepDefinition step,
                                                         HarvestState state,
                                                         CitizenEntity worker) {
        CitizenNavigationService.stop(level, worker.getUUID());
        BlockPos stump = state.target();
        if (stump == null || !level.isLoaded(stump)) {
            persistMachineState(manager, data, state);
            setStatus(manager, data, "gui.simukraft.industrial.status.block_action_blocked", "树桩未加载");
            return ActionResult.BLOCKED;
        }
        BlockState stumpState = level.getBlockState(stump);
        if (stumpState.isAir() || !isTargetBlock(level, stumpState, step)) {
            clearBreakProgress(level, worker, stump);
            resetMiningRuntime(level, data, stump);
            return fellActiveCluster(level, manager, data, building, definition, step, state, worker);
        }

        HarvestState miningState = stump.equals(state.miningTarget()) ? state : state.withMining(stump, 0);
        ItemStack tool = effectiveTool(level, worker, step);
        int digTicks = stumpDigTicks(level, stump, stumpState, tool, step);
        int nextTicks = Math.min(digTicks, nextMiningTicks(level, data, miningState, stump));
        worker.getLookControl().setLookAt(Vec3.atCenterOf(stump));
        if (nextTicks == 1 || nextTicks % SWING_INTERVAL_TICKS == 0) {
            worker.triggerWorkSwing(InteractionHand.MAIN_HAND);
        }
        if (nextTicks < digTicks) {
            int stage = Math.min(9, (int) Math.floor((nextTicks * 10.0D) / digTicks));
            level.destroyBlockProgress(worker.getId(), stump, stage);
            persistMachineState(manager, data, miningState.withMining(stump, nextTicks));
            setStatus(manager, data, "gui.simukraft.industrial.status.harvesting_trees",
                    "挖树桩 " + shortPos(stump) + " " + nextTicks + "/" + digTicks);
            return ActionResult.HARVESTED;
        }

        clearBreakProgress(level, worker, stump);
        resetMiningRuntime(level, data, stump);
        persistMachineState(manager, data, miningState.withMining(stump, nextTicks));
        setStatus(manager, data, "gui.simukraft.industrial.status.harvesting_trees", "准备移除树桩 " + shortPos(stump));
        return fellActiveCluster(level, manager, data, building, definition, step, miningState.withMining(stump, nextTicks), worker);
    }

    private static void setStatus(IndustrialBoxManager manager, IndustrialBoxData data, String key, String text) {
        if (data == null) {
            return;
        }
        data.setStatusKey(key);
        data.setStatusText(text != null ? text : "");
        if (manager != null) {
            manager.persist(data);
            IndustrialControlBoxViewSyncService.syncStatusIfChanged(manager.level(), data);
        }
    }

    /** nextMiningTicks: 运行时累计树桩挖掘进度，避免外层状态刷新导致每 tick 从 0 重来。 */
    private static int nextMiningTicks(ServerLevel level, IndustrialBoxData data, HarvestState state, BlockPos stump) {
        String key = miningRuntimeKey(level, data, stump);
        MiningRuntime runtime = MINING_RUNTIMES.computeIfAbsent(key, ignored -> new MiningRuntime());
        runtime.lastSeenAt.set(level.getGameTime());
        int storedTicks = state != null ? state.miningTicks() : 0;
        while (true) {
            int current = runtime.ticks.get();
            int next = Math.max(current, storedTicks) + 1;
            if (runtime.ticks.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    /** resetMiningRuntime: 树桩完成或失效后释放对应运行时计数。 */
    private static void resetMiningRuntime(ServerLevel level, IndustrialBoxData data, BlockPos stump) {
        MINING_RUNTIMES.remove(miningRuntimeKey(level, data, stump));
    }

    /** cleanupMiningRuntimes: 周期清理长期未访问的挖掘计数，防止运行时缓存积累。 */
    private static void cleanupMiningRuntimes(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime % MINING_RUNTIME_CLEANUP_INTERVAL_TICKS != 0L) {
            return;
        }
        String prefix = SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT) + "|";
        MINING_RUNTIMES.entrySet().removeIf(entry ->
                entry.getKey().startsWith(prefix) && gameTime - entry.getValue().lastSeenAt.get() > MINING_RUNTIME_TTL_TICKS);
    }

    /** miningRuntimeKey: 以存档、维度、控制箱和树桩坐标隔离挖掘计数。 */
    private static String miningRuntimeKey(ServerLevel level, IndustrialBoxData data, BlockPos stump) {
        long box = data != null && data.boxPos() != null ? data.boxPos().asLong() : 0L;
        long pos = stump != null ? stump.asLong() : 0L;
        return SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT) + "|box=" + box + "|stump=" + pos;
    }

    private static boolean isCloseEnoughToHarvest(ServerLevel level, IndustrialDefinition.StepDefinition step, BlockPos target, CitizenEntity worker) {
        if (target == null || worker == null) {
            return false;
        }
        double range = Math.max(1.5D, step.range());
        if (distanceToBlockBoxSqr(worker.position(), target) <= range * range) {
            return true;
        }
        BlockPos stand = nearestStandTarget(level, target, worker.position());
        return stand != null && worker.position().distanceToSqr(Vec3.atBottomCenterOf(stand)) <= 2.25D;
    }

    private static boolean isUsableStump(ServerLevel level, IndustrialDefinition.StepDefinition step, BlockPos pos) {
        return pos != null && level.isLoaded(pos) && isTargetBlock(level, level.getBlockState(pos), step);
    }

    private static BlockPos chooseStumpFromCluster(ServerLevel level, IndustrialDefinition.StepDefinition step, List<BlockPos> clusterPositions) {
        if (clusterPositions == null || clusterPositions.isEmpty()) {
            return null;
        }
        return clusterPositions.stream()
                .filter(pos -> isUsableStump(level, step, pos))
                .min(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                        .thenComparingInt(pos -> pos.getX())
                        .thenComparingInt(pos -> pos.getZ()))
                .map(BlockPos::immutable)
                .orElse(null);
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "?" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** fellActiveCluster：树桩挖掘完成后移除整棵树，并把真实掉落写入临时携带状态。 */
    private static ActionResult fellActiveCluster(ServerLevel level,
                                                  IndustrialBoxManager manager,
                                                  IndustrialBoxData data,
                                                  PlacedBuildingRecord building,
                                                  IndustrialDefinition definition,
                                                  IndustrialDefinition.StepDefinition step,
                                                  HarvestState state,
                                                  CitizenEntity worker) {
        List<BlockPos> remaining = harvestOrder(level, step, state);
        List<BlockPos> blocked = new ArrayList<>();
        List<ItemStack> drops = new ArrayList<>();
        ItemStack tool = effectiveTool(level, worker, step);
        int originalSize = remaining.size();
        int removed = 0;
        setStatus(manager, data, "gui.simukraft.industrial.status.harvesting_trees", "移除整棵树 " + originalSize + " 方块");
        while (!remaining.isEmpty()) {
            BlockPos pos = remaining.removeFirst();
            if (!level.isLoaded(pos)) {
                blocked.add(pos.immutable());
                continue;
            }
            BlockState blockState = level.getBlockState(pos);
            if (blockState.isAir() || IndustrialControlBoxService.isIndustrialControlBox(level, pos)) {
                continue;
            }
            ItemStack effectiveTool = isTargetBlock(level, blockState, step) ? tool : ItemStack.EMPTY;
            List<ItemStack> blockDrops = Block.getDrops(blockState, level, pos, level.getBlockEntity(pos), worker, effectiveTool);
            if (!destroyHarvestedBlock(level, pos, worker)) {
                blocked.add(pos.immutable());
                blocked.addAll(remaining.stream().map(BlockPos::immutable).toList());
                setStatus(manager, data, "gui.simukraft.industrial.status.block_action_blocked",
                        "方块未被移除 " + shortPos(pos) + " 已移除 " + removed + "/" + originalSize);
                SimuKraft.LOGGER.warn("Simukraft: Industrial harvest failed to remove block {} for box {} after removing {}/{} blocks",
                        pos, data.boxPos(), removed, originalSize);
                break;
            }
            removed++;
            drops.addAll(blockDrops);
        }
        boolean inventoryFull = !drops.isEmpty() && !IndustrialCarriedItemService.addItems(level, manager, data, drops);
        if (inventoryFull) {
            dropFailedCarryItems(level, data, worker, drops);
        }
        HarvestState next = state.withClusterPositions(blocked);
        if (inventoryFull) {
            data.setMachineState("");
            manager.persist(data);
            return ActionResult.CARRY_FULL;
        }
        if (!blocked.isEmpty()) {
            persistMachineState(manager, data, next);
            return ActionResult.BLOCKED;
        }
        if (!next.plantPositions().isEmpty()) {
            persistMachineState(manager, data, next);
            return plantRemaining(level, manager, data, building, definition, step, next, worker);
        }
        data.setMachineState("");
        manager.persist(data);
        setStatus(manager, data, "gui.simukraft.industrial.status.harvesting_trees", "已砍倒树木 " + removed + " 方块");
        return IndustrialCarriedItemService.stackCount(level, manager, data) >= Math.max(1, step.maxCarryStacks())
                ? ActionResult.CARRY_FULL
                : ActionResult.HARVESTED;
    }

    /** destroyHarvestedBlock: 走服务端原版破坏流程移除方块，并复查结果避免客户端回滚后反复采集。 */
    private static boolean destroyHarvestedBlock(ServerLevel level, BlockPos pos, CitizenEntity worker) {
        if (level == null || pos == null || level.getBlockState(pos).isAir()) {
            return true;
        }
        BlockState before = level.getBlockState(pos);
        if (worker != null) {
            level.destroyBlock(pos, false, worker);
        } else {
            level.destroyBlock(pos, false);
        }
        if (!level.getBlockState(pos).isAir()) {
            level.removeBlock(pos, false);
        }
        if (!level.getBlockState(pos).isAir()) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        if (!level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (level.getBlockState(pos).isAir()) {
            level.sendBlockUpdated(pos, before, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    /** harvestOrder: 先移除正在挖的树桩，再移除其它树干，最后处理树叶等附着方块。 */
    private static void dropFailedCarryItems(ServerLevel level, IndustrialBoxData data, CitizenEntity worker, List<ItemStack> drops) {
        BlockPos fallback = worker != null ? worker.blockPosition() : data.boxPos();
        for (ItemStack drop : drops) {
            if (drop != null && !drop.isEmpty()) {
                Block.popResource(level, fallback, drop.copy());
            }
        }
        SimuKraft.LOGGER.warn("Simukraft: Failed to save harvested drops for box {}; dropped {} stacks at {}",
                data.boxPos(), drops.size(), fallback);
    }

    private static List<BlockPos> harvestOrder(ServerLevel level, IndustrialDefinition.StepDefinition step, HarvestState state) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        BlockPos target = state.target();
        if (target != null) {
            appendHarvestPos(result, seen, target);
        }
        state.clusterPositions().stream()
                .filter(pos -> pos != null && !pos.equals(target) && level.isLoaded(pos) && isTargetBlock(level, level.getBlockState(pos), step))
                .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                        .thenComparingInt(pos -> pos.getX())
                        .thenComparingInt(pos -> pos.getZ()))
                .forEach(pos -> appendHarvestPos(result, seen, pos));
        state.clusterPositions().stream()
                .filter(pos -> pos != null && !seen.contains(pos))
                .forEach(pos -> appendHarvestPos(result, seen, pos));
        return result;
    }

    private static void appendHarvestPos(List<BlockPos> result, Set<BlockPos> seen, BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (seen.add(immutable)) {
            result.add(immutable);
        }
    }

    /** stumpDigTicks：按方块硬度和 NPC 主手工具估算接近原版挖掘速度的耗时。 */
    private static int stumpDigTicks(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool, IndustrialDefinition.StepDefinition step) {
        if (step != null && step.ticks() > 1) {
            return Math.max(1, step.ticks());
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            return MAX_STUMP_DIG_TICKS;
        }
        float speed = tool != null && !tool.isEmpty() ? tool.getDestroySpeed(state) : 1.0F;
        if (speed <= 0.0F) {
            speed = 1.0F;
        }
        boolean correctTool = !state.requiresCorrectToolForDrops() || (tool != null && tool.isCorrectToolForDrops(state));
        int divisor = correctTool ? 30 : 100;
        int ticks = (int) Math.ceil((hardness * divisor) / (speed * STUMP_DIG_SPEED_MULTIPLIER));
        return Math.max(MIN_STUMP_DIG_TICKS, Math.min(MAX_STUMP_DIG_TICKS, ticks));
    }

    /** effectiveTool: 使用实体真实主手或 JSON 步骤工具计算挖掘速度与掉落，避免视觉手持物尚未同步时按空手处理。 */
    private static ItemStack effectiveTool(ServerLevel level, CitizenEntity worker, IndustrialDefinition.StepDefinition step) {
        ItemStack held = worker != null ? worker.getMainHandItem() : ItemStack.EMPTY;
        if (held != null && !held.isEmpty()) {
            return held;
        }
        if (step == null) {
            return ItemStack.EMPTY;
        }
        IndustrialItemStackSpec spec = !step.itemSpec().isEmpty() ? step.itemSpec() : IndustrialItemStackSpec.of(step.item(), "");
        return spec.stack(1, level.registryAccess());
    }

    /** clearBreakProgress：清理树桩破坏裂纹，避免完成或中断后客户端残留进度。 */
    private static void clearBreakProgress(ServerLevel level, CitizenEntity worker, BlockPos pos) {
        if (worker != null && pos != null) {
            level.destroyBlockProgress(worker.getId(), pos, -1);
        }
    }

    private static ActionResult plantRemaining(ServerLevel level,
                                               IndustrialBoxManager manager,
                                               IndustrialBoxData data,
                                               PlacedBuildingRecord building,
                                               IndustrialDefinition definition,
                                               IndustrialDefinition.StepDefinition step,
                                               HarvestState state,
                                               CitizenEntity worker) {
        if (step.plantItemTag().isBlank()) {
            data.setMachineState("");
            manager.persist(data);
            return ActionResult.HARVESTED;
        }
        IndustrialItemStackSpec plantSpec = IndustrialItemStackSpec.of("", step.plantItemTag(), "", "", "", List.of(), List.of());
        List<BlockPos> remaining = new ArrayList<>(state.plantPositions());
        while (!remaining.isEmpty()) {
            BlockPos plantPos = remaining.removeFirst();
            if (!level.isLoaded(plantPos) || !canPlantAt(level, plantPos, step)) {
                continue;
            }
            ExtractedSapling sapling = extractSapling(level, manager, data, building, definition, step, plantSpec);
            if (sapling.isEmpty()) {
                persistMachineState(manager, data, state.withPlantPositions(remainingWithFirst(plantPos, remaining)));
                return ActionResult.MISSING_INPUTS;
            }
            if (!placeSapling(level, plantPos, sapling.stack())) {
                restoreSapling(level, manager, data, sapling);
                persistMachineState(manager, data, state.withPlantPositions(remainingWithFirst(plantPos, remaining)));
                return ActionResult.BLOCKED;
            }
            if (worker != null && step.swing()) {
                worker.getLookControl().setLookAt(Vec3.atCenterOf(plantPos));
                worker.triggerWorkSwing(InteractionHand.MAIN_HAND);
            }
            persistMachineState(manager, data, state.withPlantPositions(remaining));
            return ActionResult.PLANTED;
        }
        data.setMachineState("");
        manager.persist(data);
        return IndustrialCarriedItemService.stackCount(level, manager, data) >= Math.max(1, step.maxCarryStacks())
                ? ActionResult.CARRY_FULL
                : ActionResult.HARVESTED;
    }

    private static ExtractedSapling extractSapling(ServerLevel level,
                                                   IndustrialBoxManager manager,
                                                   IndustrialBoxData data,
                                                   PlacedBuildingRecord building,
                                                   IndustrialDefinition definition,
                                                   IndustrialDefinition.StepDefinition step,
                                                   IndustrialItemStackSpec plantSpec) {
        Optional<ItemStack> carried = IndustrialCarriedItemService.extractFirstMatching(level, manager, data, plantSpec);
        if (carried.isPresent()) {
            return new ExtractedSapling(carried.get(), true, null);
        }
        if (building == null || definition == null) {
            return ExtractedSapling.empty();
        }
        List<BlockPos> containers = IndustrialControlBoxService.resolveContainerPositions(building, definition,
                containerName(step.input(), step.container(), "input"));
        return IndustrialInventoryService.consumeInputStacks(level, containers, plantSpec, 1)
                .flatMap(stacks -> stacks.stream().findFirst())
                .map(stack -> new ExtractedSapling(stack, false, containers))
                .orElseGet(ExtractedSapling::empty);
    }

    private static void restoreSapling(ServerLevel level, IndustrialBoxManager manager, IndustrialBoxData data, ExtractedSapling sapling) {
        if (sapling.isEmpty()) {
            return;
        }
        if (sapling.fromCarried() || sapling.sourceContainers() == null || sapling.sourceContainers().isEmpty()) {
            IndustrialCarriedItemService.addItems(level, manager, data, List.of(sapling.stack()));
            return;
        }
        ItemStack remaining = sapling.stack().copy();
        for (BlockPos container : sapling.sourceContainers()) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = GenericContainerAccess.insert(level, container, remaining);
        }
        if (!remaining.isEmpty()) {
            IndustrialCarriedItemService.addItems(level, manager, data, List.of(remaining));
        }
    }

    private static boolean placeSapling(ServerLevel level, BlockPos pos, ItemStack sapling) {
        if (!(sapling.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        if (!level.getBlockState(pos).isAir() || !state.canSurvive(level, pos)) {
            return false;
        }
        return level.setBlock(pos, state, 3);
    }

    private static ScanResult scanForNextCluster(ServerLevel level,
                                                 PlacedBuildingRecord building,
                                                 IndustrialDefinition.StepDefinition step,
                                                 Predicate<BlockState> targetMatcher,
                                                 HarvestConfig config,
                                                 HarvestState state,
                                                 Vec3 workerPosition) {
        HarvestState cursor = state.normalizeCursor(building, config);
        Cluster bestCluster = cursor.candidateCluster();
        int processed = 0;
        while (processed < config.scanColumnsPerTick() && cursor.ring() <= config.radius()) {
            Column column = cursor.column(building, config);
            if (column != null && level.isLoaded(new BlockPos(column.x(), config.minY(), column.z()))) {
                List<Cluster> clusters = scanColumn(level, building, step, targetMatcher, config, column.x(), column.z(), workerPosition);
                for (Cluster cluster : clusters) {
                    if (isBetterCandidate(cluster, bestCluster, workerPosition)) {
                        bestCluster = cluster;
                    }
                    cursor = cursor.withQueuedTarget(cluster.target(), workerPosition);
                }
            }
            cursor = cursor.advance(building, config);
            processed++;
        }
        boolean completed = cursor.ring() > config.radius();
        HarvestState resultState = completed ? cursor.withoutQueuedTarget(bestCluster != null ? bestCluster.target() : null) : cursor;
        return new ScanResult(resultState, completed, completed ? bestCluster : null);
    }

    private static List<Cluster> scanColumn(ServerLevel level,
                                            PlacedBuildingRecord building,
                                            IndustrialDefinition.StepDefinition step,
                                            Predicate<BlockState> targetMatcher,
                                            HarvestConfig config,
                                            int x,
                                            int z,
                                            Vec3 workerPosition) {
        List<Cluster> clusters = new ArrayList<>();
        Set<BlockPos> seenTargets = new HashSet<>();
        for (int y = config.minY(); y <= config.maxY(); y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!targetMatcher.test(level.getBlockState(pos))) {
                continue;
            }
            Cluster cluster = buildCluster(level, building, step, targetMatcher, config, pos, workerPosition);
            if (cluster != null && seenTargets.add(cluster.target())) {
                clusters.add(cluster);
            }
        }
        return clusters;
    }

    private static QueuedCluster nextQueuedCluster(ServerLevel level,
                                                   PlacedBuildingRecord building,
                                                   IndustrialDefinition.StepDefinition step,
                                                   Predicate<BlockState> targetMatcher,
                                                   HarvestConfig config,
                                                   HarvestState state,
                                                   Vec3 workerPosition) {
        if (state.queuedTargets().isEmpty()) {
            return new QueuedCluster(state, null);
        }
        HarvestState cleaned = state;
        for (BlockPos target : state.queuedTargets()) {
            if (target == null || !level.isLoaded(target) || !targetMatcher.test(level.getBlockState(target))) {
                cleaned = cleaned.withoutQueuedTarget(target);
                continue;
            }
            Cluster cluster = buildCluster(level, building, step, targetMatcher, config, target, workerPosition);
            if (cluster != null) {
                return new QueuedCluster(cleaned, cluster);
            }
            cleaned = cleaned.withoutQueuedTarget(target);
        }
        return new QueuedCluster(cleaned, null);
    }

    private static Cluster buildCluster(ServerLevel level,
                                        PlacedBuildingRecord building,
                                        IndustrialDefinition.StepDefinition step,
                                        Predicate<BlockState> targetMatcher,
                                        HarvestConfig config,
                                        BlockPos start,
                                        Vec3 workerPosition) {
        Set<BlockPos> logs = connectedLogs(level, targetMatcher, config, start);
        if (logs.isEmpty() || logs.size() >= config.maxClusterBlocks()) {
            return null;
        }
        if (logs.stream().anyMatch(pos -> isInsideAnyBuilding(level, pos))) {
            return null;
        }
        int rootY = logs.stream().mapToInt(BlockPos::getY).min().orElse(start.getY());
        List<BlockPos> roots = logs.stream()
                .filter(pos -> pos.getY() == rootY)
                .filter(pos -> matchesSupport(level, pos.below(), step.supportBlockTag()))
                .sorted(Comparator.comparingDouble((BlockPos pos) -> distanceToTreeSqr(workerPosition, pos))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .toList();
        if (roots.isEmpty()) {
            return null;
        }
        Predicate<BlockState> attachedMatcher = blockMatcher(level, step.attachedBlockTag());
        Set<BlockPos> attached = attachedMatcher != null ? attachedBlocks(level, logs, attachedMatcher, config, ATTACHED_VALIDATE_RADIUS, config.maxClusterBlocks()) : Set.of();
        if (attached.size() < Math.max(0, step.minAttachedBlocks())) {
            return null;
        }
        Set<BlockPos> harvestAttached = attachedMatcher != null
                ? attachedBlocks(level, logs, attachedMatcher, config, ATTACHED_HARVEST_RADIUS, config.maxClusterBlocks() - logs.size())
                : Set.of();
        List<BlockPos> harvest = new ArrayList<>();
        harvest.addAll(harvestAttached);
        harvest.addAll(logs);
        harvest = harvest.stream()
                .filter(pos -> !isInsideAnyBuilding(level, pos))
                .distinct()
                .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed()
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .toList();
        return harvest.isEmpty() ? null : new Cluster(harvest, roots, roots.getFirst());
    }

    /** isBetterCandidate: 扫描完整作业区前缓存距离 NPC 最近的树簇，避免按外圈游标顺序抢目标。 */
    private static boolean isBetterCandidate(Cluster candidate, Cluster currentBest, Vec3 workerPosition) {
        if (candidate == null) {
            return false;
        }
        if (currentBest == null) {
            return true;
        }
        double candidateDistance = clusterDistanceSqr(candidate, workerPosition);
        double bestDistance = clusterDistanceSqr(currentBest, workerPosition);
        int distanceCompare = Double.compare(candidateDistance, bestDistance);
        if (distanceCompare != 0) {
            return distanceCompare < 0;
        }
        return comparePos(candidate.target(), currentBest.target()) < 0;
    }

    private static double clusterDistanceSqr(Cluster cluster, Vec3 workerPosition) {
        return cluster != null ? distanceToTreeSqr(workerPosition, cluster.target()) : Double.MAX_VALUE;
    }

    private static double distanceToTreeSqr(Vec3 workerPosition, BlockPos treePos) {
        if (workerPosition == null || treePos == null) {
            return 0.0D;
        }
        return Vec3.atCenterOf(treePos).distanceToSqr(workerPosition);
    }

    private static int comparePos(BlockPos first, BlockPos second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        int y = Integer.compare(first.getY(), second.getY());
        if (y != 0) {
            return y;
        }
        int x = Integer.compare(first.getX(), second.getX());
        return x != 0 ? x : Integer.compare(first.getZ(), second.getZ());
    }

    private static Set<BlockPos> connectedLogs(ServerLevel level, Predicate<BlockState> targetMatcher, HarvestConfig config, BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        visited.add(start.immutable());
        while (!queue.isEmpty() && visited.size() < config.maxClusterBlocks()) {
            BlockPos current = queue.removeFirst();
            for (int dx = -LOG_NEIGHBOR_RADIUS; dx <= LOG_NEIGHBOR_RADIUS; dx++) {
                for (int dy = -LOG_NEIGHBOR_RADIUS; dy <= LOG_NEIGHBOR_RADIUS; dy++) {
                    for (int dz = -LOG_NEIGHBOR_RADIUS; dz <= LOG_NEIGHBOR_RADIUS; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(dx, dy, dz);
                        if (!config.contains(next) || visited.contains(next) || !level.isLoaded(next) || !targetMatcher.test(level.getBlockState(next))) {
                            continue;
                        }
                        visited.add(next.immutable());
                        queue.add(next.immutable());
                    }
                }
            }
        }
        return Set.copyOf(visited);
    }

    private static Set<BlockPos> attachedBlocks(ServerLevel level,
                                                Set<BlockPos> logs,
                                                Predicate<BlockState> attachedMatcher,
                                                HarvestConfig config,
                                                int radius,
                                                int limit) {
        if (limit <= 0) {
            return Set.of();
        }
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos log : logs) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos pos = log.offset(dx, dy, dz);
                        if (!config.contains(pos) || result.contains(pos) || !level.isLoaded(pos) || !attachedMatcher.test(level.getBlockState(pos))) {
                            continue;
                        }
                        result.add(pos.immutable());
                        if (result.size() >= limit) {
                            return Set.copyOf(result);
                        }
                    }
                }
            }
        }
        return Set.copyOf(result);
    }

    private static boolean canPlantAt(ServerLevel level, BlockPos plantPos, IndustrialDefinition.StepDefinition step) {
        return level.getBlockState(plantPos).isAir() && matchesSupport(level, plantPos.below(), step.supportBlockTag());
    }

    private static boolean isTargetBlock(ServerLevel level, BlockState state, IndustrialDefinition.StepDefinition step) {
        Predicate<BlockState> matcher = blockMatcher(level, step.targetBlockTag());
        return matcher != null && matcher.test(state);
    }

    private static boolean matchesSupport(ServerLevel level, BlockPos pos, String supportTag) {
        BlockState state = level.getBlockState(pos);
        Predicate<BlockState> matcher = blockMatcher(level, supportTag);
        return matcher != null ? matcher.test(state) : !state.isAir();
    }

    private static boolean isInsideAnyBuilding(ServerLevel level, BlockPos pos) {
        return PlacedBuildingService.findByContainedPos(level, pos) != null;
    }

    /** blockMatcher: "#tag" → 标签匹配；无 # 前缀 → 直接方块 ID 匹配；解析失败返回 null。 */
    private static Predicate<BlockState> blockMatcher(ServerLevel level, String tagOrId) {
        if (tagOrId == null || tagOrId.isBlank()) {
            return null;
        }
        try {
            if (tagOrId.startsWith("#")) {
                TagKey<Block> key = TagKey.create(Registries.BLOCK, ResourceLocation.parse(tagOrId.substring(1)));
                return state -> state.is(key);
            }
            ResourceLocation rl = ResourceLocation.parse(tagOrId);
            Block block = BuiltInRegistries.BLOCK.get(rl);
            if (block == null || block == Blocks.AIR && !tagOrId.equals("minecraft:air")) {
                return null;
            }
            return state -> state.is(block);
        } catch (Exception exception) {
            return null;
        }
    }

    private static BlockPos nearestStandTarget(ServerLevel level, BlockPos target, Vec3 origin) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int radius = 1; radius <= 3; radius++) {
            for (int yOffset = 0; yOffset >= -3; yOffset--) {
                for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != radius) {
                            continue;
                        }
                        BlockPos candidate = target.offset(xOffset, yOffset, zOffset);
                        if (!CitizenTeleportService.isSafeLandingPosition(level, candidate)) {
                            continue;
                        }
                        double distance = origin != null ? Vec3.atBottomCenterOf(candidate).distanceToSqr(origin) : 0.0D;
                        if (best == null || distance < bestDistance) {
                            best = candidate.immutable();
                            bestDistance = distance;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static List<BlockPos> remainingWithFirst(BlockPos first, List<BlockPos> remaining) {
        List<BlockPos> result = new ArrayList<>();
        result.add(first.immutable());
        result.addAll(remaining);
        return List.copyOf(result);
    }

    private static void persistMachineState(IndustrialBoxManager manager, IndustrialBoxData data, HarvestState state) {
        data.setMachineState(state.toJson());
        manager.persist(data);
    }

    private static String containerName(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary;
        }
        return fallback;
    }

    private static double distanceToBlockBoxSqr(Vec3 position, BlockPos blockPos) {
        double dx = axisDistance(position.x, blockPos.getX(), blockPos.getX() + 1.0D);
        double dy = axisDistance(position.y, blockPos.getY(), blockPos.getY() + 1.0D);
        double dz = axisDistance(position.z, blockPos.getZ(), blockPos.getZ() + 1.0D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0D;
    }

    private record HarvestConfig(int radius,
                                 int startOffset,
                                 int minX,
                                 int maxX,
                                 int minY,
                                 int maxY,
                                 int minZ,
                                 int maxZ,
                                 boolean excludeBuilding,
                                 int scanColumnsPerTick,
                                 int maxClusterBlocks,
                                 int maxCarryStacks) {
        static HarvestConfig from(ServerLevel level,
                                  PlacedBuildingRecord building,
                                  IndustrialDefinition.WorkAreaDefinition workArea,
                                  IndustrialDefinition.StepDefinition step) {
            IndustrialDefinition.WorkAreaDefinition safeArea = workArea != null ? workArea : IndustrialDefinition.WorkAreaDefinition.none();
            AABB bounds = IndustrialWorkAreaService.workAreaBounds(building, safeArea);
            int minX = (int) Math.floor(bounds.minX);
            int maxX = (int) Math.floor(bounds.maxX) - 1;
            int minY = Math.max(level.getMinBuildHeight(), (int) Math.floor(bounds.minY));
            int maxY = Math.min(level.getMaxBuildHeight() - 1, (int) Math.floor(bounds.maxY) - 1);
            int minZ = (int) Math.floor(bounds.minZ);
            int maxZ = (int) Math.floor(bounds.maxZ) - 1;
            return new HarvestConfig(
                    Math.max(0, safeArea.radius()),
                    Math.max(1, safeArea.startOffset()),
                    minX,
                    maxX,
                    minY,
                    maxY,
                    minZ,
                    maxZ,
                    safeArea.excludeBuilding(),
                    Math.max(1, safeArea.scanColumnsPerTick()),
                    Math.max(1, step.maxClusterBlocks()),
                    Math.max(1, step.maxCarryStacks())
            );
        }

        boolean contains(BlockPos pos) {
            return pos.getX() >= minX
                    && pos.getX() <= maxX
                    && pos.getY() >= minY
                    && pos.getY() <= maxY
                    && pos.getZ() >= minZ
                    && pos.getZ() <= maxZ;
        }
    }

    private record HarvestState(int step,
                                int ring,
                                int x,
                                int z,
                                BlockPos target,
                                BlockPos miningTarget,
                                int miningTicks,
                                List<BlockPos> clusterPositions,
                                List<BlockPos> plantPositions,
                                List<BlockPos> candidateClusterPositions,
                                List<BlockPos> candidatePlantPositions,
                                BlockPos candidateTarget,
                                List<BlockPos> queuedTargets) {
        static HarvestState read(String text, int currentStep, PlacedBuildingRecord building, HarvestConfig config) {
            try {
                JsonObject root = JsonParser.parseString(text != null && !text.isBlank() ? text : "{}").getAsJsonObject();
                if (!STATE_KIND.equals(string(root, "kind", "")) || integer(root, "step", -1) != currentStep) {
                    return initial(currentStep, building, config);
                }
                return new HarvestState(
                        currentStep,
                        Math.max(config.startOffset(), integer(root, "ring", config.startOffset())),
                        integer(root, "x", minX(building) - config.startOffset()),
                        integer(root, "z", minZ(building) - config.startOffset()),
                        readPos(root.get("target")),
                        readPos(root.get("miningTarget")),
                        Math.max(0, integer(root, "miningTicks", 0)),
                        readPositions(root.getAsJsonArray("cluster")),
                        readPositions(root.getAsJsonArray("plant")),
                        readPositions(root.getAsJsonArray("candidateCluster")),
                        readPositions(root.getAsJsonArray("candidatePlant")),
                        readPos(root.get("candidateTarget")),
                        readPositions(root.getAsJsonArray("queuedTargets"))
                );
            } catch (Exception exception) {
                return initial(currentStep, building, config);
            }
        }

        static HarvestState initial(int currentStep, PlacedBuildingRecord building, HarvestConfig config) {
            int ring = Math.max(1, config.startOffset());
            return new HarvestState(currentStep, ring, minX(building) - ring, minZ(building) - ring, null, null, 0, List.of(), List.of(), List.of(), List.of(), null, List.of());
        }

        static HarvestState empty(int currentStep) {
            return new HarvestState(currentStep, 1, 0, 0, null, null, 0, List.of(), List.of(), List.of(), List.of(), null, List.of());
        }

        HarvestState normalizeCursor(PlacedBuildingRecord building, HarvestConfig config) {
            if (ring < config.startOffset()) {
                return initial(step, building, config);
            }
            return this;
        }

        Column column(PlacedBuildingRecord building, HarvestConfig config) {
            if (ring < 1) {
                return null;
            }
            int minX = minX(building) - ring;
            int maxX = maxX(building) + ring;
            int minZ = minZ(building) - ring;
            int maxZ = maxZ(building) + ring;
            boolean onCurrentRing = x == minX || x == maxX || z == minZ || z == maxZ;
            if (!onCurrentRing) {
                return null;
            }
            if (config.excludeBuilding()
                    && x >= minX(building) && x <= maxX(building)
                    && z >= minZ(building) && z <= maxZ(building)) {
                return null;
            }
            return new Column(x, z);
        }

        HarvestState advance(PlacedBuildingRecord building, HarvestConfig config) {
            int currentRing = Math.max(config.startOffset(), ring);
            int minX = minX(building) - currentRing;
            int maxX = maxX(building) + currentRing;
            int minZ = minZ(building) - currentRing;
            int maxZ = maxZ(building) + currentRing;
            int nextX = x;
            int nextZ = z;
            if (z == minZ && x < maxX) {
                nextX++;
            } else if (x == maxX && z < maxZ) {
                nextZ++;
            } else if (z == maxZ && x > minX) {
                nextX--;
            } else if (x == minX && z > minZ + 1) {
                nextZ--;
            } else {
                int nextRing = currentRing + 1;
                return new HarvestState(step, nextRing, minX(building) - nextRing, minZ(building) - nextRing, target, miningTarget, miningTicks, clusterPositions, plantPositions, candidateClusterPositions, candidatePlantPositions, candidateTarget, queuedTargets);
            }
            return new HarvestState(step, currentRing, nextX, nextZ, target, miningTarget, miningTicks, clusterPositions, plantPositions, candidateClusterPositions, candidatePlantPositions, candidateTarget, queuedTargets);
        }

        HarvestState withCluster(Cluster cluster) {
            return new HarvestState(step, ring, x, z, cluster.target(), null, 0, cluster.harvestPositions(), cluster.plantPositions(), List.of(), List.of(), null, queuedTargets);
        }

        HarvestState withTarget(BlockPos pos) {
            return new HarvestState(step, ring, x, z, pos != null ? pos.immutable() : null, null, 0, clusterPositions, plantPositions, candidateClusterPositions, candidatePlantPositions, candidateTarget, queuedTargets);
        }

        HarvestState withClusterPositions(List<BlockPos> positions) {
            return new HarvestState(step, ring, x, z, target, null, 0, copyPositions(positions), plantPositions, candidateClusterPositions, candidatePlantPositions, candidateTarget, queuedTargets);
        }

        HarvestState withPlantPositions(List<BlockPos> positions) {
            return new HarvestState(step, ring, x, z, target, null, 0, clusterPositions, copyPositions(positions), candidateClusterPositions, candidatePlantPositions, candidateTarget, queuedTargets);
        }

        HarvestState withMining(BlockPos pos, int ticks) {
            return new HarvestState(step, ring, x, z, target, pos != null ? pos.immutable() : null, Math.max(0, ticks), clusterPositions, plantPositions, candidateClusterPositions, candidatePlantPositions, candidateTarget, queuedTargets);
        }

        Cluster candidateCluster() {
            if (candidateTarget == null || candidateClusterPositions.isEmpty()) {
                return null;
            }
            return new Cluster(candidateClusterPositions, candidatePlantPositions, candidateTarget);
        }

        HarvestState withQueuedTarget(BlockPos pos, Vec3 origin) {
            if (pos == null || queuedTargets.contains(pos)) {
                return this;
            }
            List<BlockPos> targets = new ArrayList<>(queuedTargets);
            targets.add(pos.immutable());
            targets.sort(Comparator.comparingDouble((BlockPos targetPos) -> distanceToTreeSqr(origin, targetPos))
                    .thenComparingInt(BlockPos::getY)
                    .thenComparingInt(BlockPos::getX)
                    .thenComparingInt(BlockPos::getZ));
            return new HarvestState(step, ring, x, z, target, miningTarget, miningTicks, clusterPositions, plantPositions,
                    candidateClusterPositions, candidatePlantPositions, candidateTarget, List.copyOf(targets));
        }

        HarvestState withoutQueuedTarget(BlockPos pos) {
            if (pos == null || queuedTargets.isEmpty()) {
                return this;
            }
            List<BlockPos> targets = queuedTargets.stream()
                    .filter(targetPos -> !pos.equals(targetPos))
                    .toList();
            return new HarvestState(step, ring, x, z, target, miningTarget, miningTicks, clusterPositions, plantPositions,
                    candidateClusterPositions, candidatePlantPositions, candidateTarget, targets);
        }

        String toJson() {
            JsonObject root = new JsonObject();
            root.addProperty("kind", STATE_KIND);
            root.addProperty("step", step);
            root.addProperty("ring", ring);
            root.addProperty("x", x);
            root.addProperty("z", z);
            if (target != null) {
                root.add("target", posJson(target));
            }
            if (miningTarget != null) {
                root.add("miningTarget", posJson(miningTarget));
                root.addProperty("miningTicks", miningTicks);
            }
            root.add("cluster", positionsJson(clusterPositions));
            root.add("plant", positionsJson(plantPositions));
            if (candidateTarget != null && !candidateClusterPositions.isEmpty()) {
                root.add("candidateTarget", posJson(candidateTarget));
                root.add("candidateCluster", positionsJson(candidateClusterPositions));
                root.add("candidatePlant", positionsJson(candidatePlantPositions));
            }
            if (!queuedTargets.isEmpty()) {
                root.add("queuedTargets", positionsJson(queuedTargets));
            }
            return root.toString();
        }
    }

    private record ScanResult(HarvestState state, boolean completed, Cluster cluster) {
    }

    private record QueuedCluster(HarvestState state, Cluster cluster) {
    }

    private record Cluster(List<BlockPos> harvestPositions, List<BlockPos> plantPositions, BlockPos target) {
        private Cluster {
            harvestPositions = copyPositions(harvestPositions);
            plantPositions = copyPositions(plantPositions);
            target = target != null ? target.immutable() : null;
        }
    }

    private record Column(int x, int z) {
    }

    private record ExtractedSapling(ItemStack stack, boolean fromCarried, List<BlockPos> sourceContainers) {
        private ExtractedSapling {
            stack = stack != null ? stack.copyWithCount(Math.max(1, stack.getCount())) : ItemStack.EMPTY;
            sourceContainers = sourceContainers != null ? List.copyOf(sourceContainers) : List.of();
        }

        static ExtractedSapling empty() {
            return new ExtractedSapling(ItemStack.EMPTY, false, List.of());
        }

        boolean isEmpty() {
            return stack.isEmpty();
        }
    }

    public enum ActionResult {
        HARVESTED,
        PLANTED,
        MOVING,
        SCANNING,
        AREA_EMPTY,
        CARRY_FULL,
        MISSING_INPUTS,
        BLOCKED,
        INVALID_STEP
    }

    private static int minX(PlacedBuildingRecord building) {
        return Math.min(building.minPos().getX(), building.maxPos().getX());
    }

    private static int maxX(PlacedBuildingRecord building) {
        return Math.max(building.minPos().getX(), building.maxPos().getX());
    }

    private static int minZ(PlacedBuildingRecord building) {
        return Math.min(building.minPos().getZ(), building.maxPos().getZ());
    }

    private static int maxZ(PlacedBuildingRecord building) {
        return Math.max(building.minPos().getZ(), building.maxPos().getZ());
    }

    private static List<BlockPos> copyPositions(List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        return positions.stream()
                .filter(pos -> pos != null)
                .map(BlockPos::immutable)
                .toList();
    }

    private static JsonArray positionsJson(List<BlockPos> positions) {
        JsonArray array = new JsonArray();
        for (BlockPos pos : positions) {
            array.add(posJson(pos));
        }
        return array;
    }

    private static JsonArray posJson(BlockPos pos) {
        JsonArray array = new JsonArray();
        array.add(pos.getX());
        array.add(pos.getY());
        array.add(pos.getZ());
        return array;
    }

    private static List<BlockPos> readPositions(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<BlockPos> positions = new ArrayList<>();
        for (JsonElement element : array) {
            BlockPos pos = readPos(element);
            if (pos != null) {
                positions.add(pos);
            }
        }
        return List.copyOf(positions);
    }

    private static BlockPos readPos(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() < 3) {
            return null;
        }
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString().toLowerCase(Locale.ROOT);
        } catch (Exception exception) {
            return fallback;
        }
    }
}
