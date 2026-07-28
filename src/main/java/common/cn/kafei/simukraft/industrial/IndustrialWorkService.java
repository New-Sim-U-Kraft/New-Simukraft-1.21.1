package common.cn.kafei.simukraft.industrial;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenHomeRestService;
import common.cn.kafei.simukraft.citizen.CitizenJobVisualService;
import common.cn.kafei.simukraft.citizen.CitizenLevelService;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenSkillSnapshot;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.medical.MedicalService;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class IndustrialWorkService {
    private static final ConcurrentMap<String, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();
    private static final long IDLE_RETRY_TICKS = 40L;
    private static final long MOVE_RETRY_TICKS = 8L;

    private IndustrialWorkService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        IndustrialBoxManager manager = IndustrialBoxManager.get(level);
        LevelRuntime runtime = runtime(level);
        long gameTime = level.getGameTime();
        for (IndustrialBoxData data : manager.all()) {
            BoxRuntime boxRuntime = runtime.boxes.computeIfAbsent(data.boxPos(), ignored -> new BoxRuntime());
            // 重启后首次 tick：从持久化的 stepElapsedTicks 恢复计时起点
            if (!boxRuntime.progressRestored) {
                boxRuntime.progressRestored = true;
                if (data.stepElapsedTicks() > 0) {
                    boxRuntime.stepStartedAt = gameTime - data.stepElapsedTicks();
                    data.setStepElapsedTicks(0);
                }
            }
            if (!data.running()) {
                boxRuntime.reset();
                if (gameTime < boxRuntime.nextTick || !tryAutoStartStoppedBox(level, manager, data)) {
                    if (gameTime >= boxRuntime.nextTick) {
                        boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
                    }
                    continue;
                }
            }
            if (gameTime < boxRuntime.nextTick) {
                continue;
            }
            tickBox(level, manager, data, boxRuntime, gameTime);
        }
    }

    public static void flush(ServerLevel level) {
        if (level != null) {
            syncRuntimeProgressToData(level);
            IndustrialBoxManager.get(level).saveToSqlite(level);
        }
    }

    // 关服前将内存中的步骤计时进度写回 data，确保重启后能从断点恢复
    private static void syncRuntimeProgressToData(ServerLevel level) {
        LevelRuntime runtime = RUNTIMES.get(SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT));
        if (runtime == null) return;
        IndustrialBoxManager manager = IndustrialBoxManager.get(level);
        long gameTime = level.getGameTime();
        for (IndustrialBoxData data : manager.all()) {
            BoxRuntime br = runtime.boxes.get(data.boxPos());
            if (br != null && br.stepStartedAt > 0) {
                data.setStepElapsedTicks(gameTime - br.stepStartedAt);
            }
        }
    }

    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server).toLowerCase(Locale.ROOT);
        RUNTIMES.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
        IndustrialBlockClusterHarvestService.clearServerCaches(server);
        IndustrialControlBoxViewSyncService.clearServerCaches(server);
    }

    // tryAutoStartStoppedBox：重进后自动恢复可工作的工业盒，但保留手动暂停/解雇/中断的停机语义。
    private static boolean tryAutoStartStoppedBox(ServerLevel level, IndustrialBoxManager manager, IndustrialBoxData data) {
        if (isExplicitlyStoppedStatus(data.statusKey()) || !IndustrialControlBoxService.isIndustrialControlBox(level, data.boxPos())) {
            return false;
        }
        PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, data.boxPos());
        IndustrialDefinitionLoader.LoadResult loadResult = IndustrialDefinitionLoader.loadForBuilding(building);
        IndustrialDefinition definition = loadResult.definition();
        if (building == null || !loadResult.valid() || definition == null) {
            return false;
        }
        IndustrialControlBoxService.synchronizeBoxMetadata(level, data, building, definition);
        CitizenData worker = IndustrialControlBoxService.findAssignedWorker(level, data.boxPos());
        if (worker == null || worker.sick()) {
            return false;
        }
        IndustrialDefinition.RecipeDefinition recipe = definition.recipeById(data.selectedRecipeId());
        if (recipe == null || recipe.steps().isEmpty()) {
            return false;
        }
        data.setRunning(true);
        data.setMachineState("");
        data.setStatusKey("gui.simukraft.industrial.status.running");
        data.setStatusText("");
        manager.persist(data);
        return true;
    }

    private static boolean isExplicitlyStoppedStatus(String statusKey) {
        return switch (statusKey != null ? statusKey : "") {
            // 手动停机：需玩家重新操作
            case "gui.simukraft.industrial.status.paused",
                 "gui.simukraft.industrial.status.worker_fired",
                 "gui.simukraft.industrial.status.interrupted",
                 // 启动前提不满足：条件满足时不自动恢复，需玩家手动点"开始"
                 "gui.simukraft.industrial.status.no_building",
                 "gui.simukraft.industrial.status.invalid_definition",
                 "gui.simukraft.industrial.status.no_worker",
                 "gui.simukraft.industrial.status.no_recipe",
                 // 初始/选配方未启动状态
                 "gui.simukraft.industrial.status.recipe_selected",
                 "" -> true;
            default -> false;
        };
    }

    private static void tickBox(ServerLevel level, IndustrialBoxManager manager, IndustrialBoxData data, BoxRuntime boxRuntime, long gameTime) {
        if (!level.isLoaded(data.boxPos())) {
            return;
        }
        if (!IndustrialControlBoxService.isIndustrialControlBox(level, data.boxPos())) {
            IndustrialCarriedItemService.dropAndClear(level, manager, data, data.boxPos());
            manager.remove(data.boxPos());
            boxRuntime.reset();
            return;
        }
        PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, data.boxPos());
        IndustrialDefinitionLoader.LoadResult loadResult = IndustrialDefinitionLoader.loadForBuilding(building);
        IndustrialDefinition definition = loadResult.definition();
        CitizenData worker = IndustrialControlBoxService.findAssignedWorker(level, data.boxPos());
        if (building == null) {
            setStatus(manager, data, "gui.simukraft.industrial.status.no_building", "");
            boxRuntime.reset();
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
            return;
        }
        if (!loadResult.valid() || definition == null) {
            setStatus(manager, data, "gui.simukraft.industrial.status.invalid_definition", String.join(",", loadResult.errors()));
            boxRuntime.reset();
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
            return;
        }
        IndustrialControlBoxService.synchronizeBoxMetadata(level, data, building, definition);
        if (worker == null) {
            setStatus(manager, data, "gui.simukraft.industrial.status.no_worker", "");
            boxRuntime.reset();
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
            return;
        }
        if (worker.sick()) {
            // 工人生病时自动暂停，running 改 false；康复后由 tryAutoStartStoppedBox 自动恢复
            setStatus(manager, data, "gui.simukraft.industrial.status.worker_sick", "");
            data.setRunning(false);
            CitizenJobVisualService.clearMainHandOverride(worker.uuid());
            boxRuntime.reset();
            manager.persist(data);
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
            return;
        }
        if (MedicalService.isOnMedicalLeave(worker, level.getDayTime() / 24_000L)) {
            CitizenJobVisualService.clearMainHandOverride(worker.uuid());
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
            return;
        }
        IndustrialEntitySpawnService.ensureSpawned(level, manager, data, building, definition);
        if (CitizenHomeRestService.isRestTime(level)) {
            // 夜间睡眠前保存当前步骤已消耗 tick，防止重启后计时丢失
            if (boxRuntime.stepStartedAt > 0 && data.stepElapsedTicks() == 0) {
                data.setStepElapsedTicks(gameTime - boxRuntime.stepStartedAt);
                manager.persist(data);
            }
            setStatus(manager, data, "gui.simukraft.industrial.status.resting", "");
            CitizenJobVisualService.clearMainHandOverride(worker.uuid());
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
            return;
        }
        if (CitizenSelfFeedingService.isSelfFeeding(level, worker.uuid())) {
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
            return;
        }
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, worker.uuid());
        if (entity == null) {
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
            return;
        }
        IndustrialDefinition.RecipeDefinition recipe = definition.recipeById(data.selectedRecipeId());
        if (recipe == null || recipe.steps().isEmpty()) {
            // 配方丢失时停止运行，避免 running=true 持续空转
            data.setRunning(false);
            setStatus(manager, data, "gui.simukraft.industrial.status.no_recipe", "");
            boxRuntime.reset();
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
            return;
        }
        if (data.currentStep() >= recipe.steps().size()) {
            data.setCurrentStep(0);
            manager.persist(data);
        }
        IndustrialDefinition.StepDefinition step = recipe.steps().get(data.currentStep());
        // use_item 步骤前检查NPC是否在上次记录的工作位置，不在则先导航过去
        if ("use_item".equals(step.type().toLowerCase(Locale.ROOT)) && data.workerWorkPos() != Long.MIN_VALUE) {
            Vec3 workCenter = Vec3.atBottomCenterOf(BlockPos.of(data.workerWorkPos()));
            if (entity.position().distanceToSqr(workCenter) > 16.0) {
                CitizenNavigationService.requestMove(level, worker.uuid(), workCenter, MovementIntent.WORK);
                boxRuntime.nextTick = gameTime + MOVE_RETRY_TICKS;
                return;
            }
        }
        StepResult result = executeStep(level, manager, data, boxRuntime, building, definition, recipe, worker, entity, step, gameTime);
        if (result == StepResult.PROGRESSED) {
            // 移动步骤完成时记录工作位置，用于后续纯计时步骤（如 use_item）的位置恢复
            if (step.type().toLowerCase(Locale.ROOT).startsWith("move_to")) {
                data.setWorkerWorkPos(entity.blockPosition().asLong());
            }
            advanceStep(manager, data, recipe, boxRuntime);
            boxRuntime.nextTick = gameTime + 1L;
        } else if (result == StepResult.WAITING_MOVE) {
            if (shouldSkipTimedOutStep(step, boxRuntime, gameTime)) {
                advanceStep(manager, data, recipe, boxRuntime);
                boxRuntime.nextTick = gameTime + 1L;
                return;
            }
            boxRuntime.nextTick = gameTime + MOVE_RETRY_TICKS;
        } else if (result == StepResult.WAITING_RETRY) {
            if (shouldSkipTimedOutStep(step, boxRuntime, gameTime)) {
                advanceStep(manager, data, recipe, boxRuntime);
                boxRuntime.nextTick = gameTime + 1L;
                return;
            }
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
        } else {
            boxRuntime.timeoutStartAt = 0L;
            boxRuntime.nextTick = gameTime + 1L;
        }
    }

    /**
     * shouldSkipTimedOutStep: 仅允许 JSON 显式声明的步骤在等待超时后跳过，避免缺材料或箱子满时误推进。
     */
    private static boolean shouldSkipTimedOutStep(IndustrialDefinition.StepDefinition step, BoxRuntime boxRuntime, long gameTime) {
        if (!step.skipOnTimeout() || step.timeoutTicks() <= 0) {
            return false;
        }
        if (boxRuntime.timeoutStartAt == 0L) {
            boxRuntime.timeoutStartAt = gameTime;
        }
        return gameTime - boxRuntime.timeoutStartAt >= step.timeoutTicks();
    }

    private static StepResult executeStep(ServerLevel level,
                                          IndustrialBoxManager manager,
                                          IndustrialBoxData data,
                                          BoxRuntime boxRuntime,
                                          PlacedBuildingRecord building,
                                          IndustrialDefinition definition,
                                          IndustrialDefinition.RecipeDefinition recipe,
                                          CitizenData worker,
                                          CitizenEntity entity,
                                          IndustrialDefinition.StepDefinition step,
                                          long gameTime) {
        String type = step.type().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "set_held_item" -> {
                IndustrialItemStackSpec spec = !step.itemSpec().isEmpty()
                        ? step.itemSpec()
                        : IndustrialItemStackSpec.of(recipe.effectiveHeldItem(definition.heldItem()), "");
                CitizenJobVisualService.setMainHandOverride(worker.uuid(), spec.stack(1, level.registryAccess()));
                yield StepResult.PROGRESSED;
            }
            case "move_to" -> moveTo(level, data, boxRuntime, building, definition, worker, entity, step);
            case "move_to_container", "move_to_chest" -> moveToContainer(level, data, boxRuntime, building, definition, worker, entity, step);
            case "move_to_entity" -> moveToEntity(level, data, boxRuntime, building, definition, worker, entity, step);
            case "look_at" -> lookAt(building, definition, entity, step);
            case "look_at_container", "look_at_chest" -> lookAtContainer(level, data, building, definition, entity, step);
            case "require_inputs" -> requireInputs(level, manager, data, building, definition, recipe, step);
            case "require_output_space" -> requireOutputSpace(level, manager, data, building, definition, recipe, step);
            case "use_item" -> useItem(entity, boxRuntime, step, gameTime);
            case "craft_recipe" -> craftRecipe(level, manager, data, building, definition, recipe, worker, step, false);
            case "craft_available_recipe", "craft_all_recipe" -> craftRecipe(level, manager, data, building, definition, recipe, worker, step, true);
            case "real_machine_recipe" -> realMachineRecipe(level, manager, data, building, definition, recipe, worker, entity, step, gameTime);
            case "inspect_container", "open_container" -> inspectContainer(level, manager, data, boxRuntime, building, definition, step, gameTime);
            case "breed_entities", "breed_animals" -> entityAction(manager, data,
                    IndustrialEntityActionService.breed(level, building, definition, step),
                    "gui.simukraft.industrial.status.breeding");
            case "slaughter_entities", "slaughter_animals" -> entityAction(manager, data,
                    IndustrialEntityActionService.slaughter(level, building, definition, step, entity),
                    "gui.simukraft.industrial.status.slaughtering");
            case "require_drops", "require_drop_items", "has_drops" -> entityAction(manager, data,
                    IndustrialEntityActionService.requireDrops(level, building, definition, step, entity),
                    "gui.simukraft.industrial.status.collecting_drops");
            case "collect_drops" -> collectDrops(level, manager, data, boxRuntime, building, definition, worker, entity, step);
            case "shear_entities", "shear_sheep" -> step.ticks() > 0
                    ? shearWithAnimation(level, manager, data, boxRuntime, building, definition, entity, step, gameTime)
                    : entityAction(manager, data,
                            IndustrialEntityActionService.shear(level, building, definition, step, entity),
                            "gui.simukraft.industrial.status.shearing");
            case "place_block", "set_block" -> blockAction(manager, data,
                    IndustrialBlockActionService.placeBlock(level, building, definition, step, entity),
                    "gui.simukraft.industrial.status.placing_block", step);
            case "place_fluid", "place_liquid" -> blockAction(manager, data,
                    IndustrialBlockActionService.placeFluid(level, building, definition, step, entity),
                    "gui.simukraft.industrial.status.placing_fluid", step);
            case "destroy_block", "break_block", "remove_block" -> blockAction(manager, data,
                    IndustrialBlockActionService.destroyBlock(level, building, definition, step, entity),
                    "gui.simukraft.industrial.status.destroying_block", step);
            case "require_block", "wait_for_block", "find_block", "check_block" -> requireBlock(manager, data,
                    IndustrialBlockActionService.requireBlock(level, building, definition, step), step);
            case "harvest_block_clusters", "harvest_blocks" -> harvestBlockClusters(level, manager, data, building, definition, worker, entity, step);
            case "deposit_carried_items", "store_carried_items", "put_carried_items" -> depositCarriedItems(level, manager, data, boxRuntime, building, definition, step, gameTime);
            case "insert_item", "store_item", "put_item" -> insertItem(level, manager, data, boxRuntime, building, definition, step, gameTime);
            case "fill_item", "fill_slot", "refill_item", "refill_slot" -> fillItem(manager, data, entity, step,
                    IndustrialItemFillService.fill(level, building, definition, step, entity.position()));
            case "set_status" -> {
                setStatus(manager, data,
                        !step.statusKey().isBlank() ? step.statusKey() : "gui.simukraft.industrial.status.running",
                        step.statusText());
                yield StepResult.PROGRESSED;
            }
            default -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.invalid_step", type);
                yield StepResult.WAITING_RETRY;
            }
        };
    }

    private static StepResult harvestBlockClusters(ServerLevel level,
                                                   IndustrialBoxManager manager,
                                                   IndustrialBoxData data,
                                                   PlacedBuildingRecord building,
                                                   IndustrialDefinition definition,
                                                   CitizenData worker,
                                                   CitizenEntity entity,
                                                   IndustrialDefinition.StepDefinition step) {
        IndustrialBlockClusterHarvestService.ActionResult result = IndustrialBlockClusterHarvestService.execute(
                level, manager, data, building, definition, step, entity, worker.uuid());
        return switch (result) {
            case HARVESTED -> {
                if (!"gui.simukraft.industrial.status.harvesting_trees".equals(data.statusKey())
                        || data.statusText().isBlank()) {
                    setStatus(manager, data, "gui.simukraft.industrial.status.harvesting_trees", "");
                }
                yield StepResult.WAITING;
            }
            case PLANTED -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.planting_sapling", "");
                yield StepResult.WAITING;
            }
            case MOVING -> {
                if (!"gui.simukraft.industrial.status.moving".equals(data.statusKey())
                        || data.statusText().isBlank()) {
                    setStatus(manager, data, "gui.simukraft.industrial.status.moving", "");
                }
                yield StepResult.WAITING_MOVE;
            }
            case SCANNING -> {
                if (!"gui.simukraft.industrial.status.harvesting_blocks".equals(data.statusKey())
                        || data.statusText().isBlank()) {
                    setStatus(manager, data, "gui.simukraft.industrial.status.harvesting_blocks", "");
                }
                yield StepResult.WAITING;
            }
            case AREA_EMPTY -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.waiting_trees", "");
                yield StepResult.PROGRESSED;
            }
            case CARRY_FULL -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.carry_full", "");
                yield StepResult.PROGRESSED;
            }
            case MISSING_INPUTS -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.missing_inputs", step.plantItemTag());
                yield StepResult.WAITING_RETRY;
            }
            case BLOCKED -> {
                if (!"gui.simukraft.industrial.status.block_action_blocked".equals(data.statusKey())
                        || data.statusText().isBlank()) {
                    setStatus(manager, data, "gui.simukraft.industrial.status.block_action_blocked", step.type());
                }
                yield StepResult.WAITING_RETRY;
            }
            case INVALID_STEP -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.invalid_step", step.type());
                yield StepResult.WAITING_RETRY;
            }
        };
    }

    private static StepResult depositCarriedItems(ServerLevel level,
                                                  IndustrialBoxManager manager,
                                                  IndustrialBoxData data,
                                                  BoxRuntime boxRuntime,
                                                  PlacedBuildingRecord building,
                                                  IndustrialDefinition definition,
                                                  IndustrialDefinition.StepDefinition step,
                                                  long gameTime) {
        String containerId = containerName(step.container(), step.output(), "output");
        List<BlockPos> containers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerId);
        if (containers.isEmpty()) {
            setStatus(manager, data, "gui.simukraft.industrial.status.missing_container", containerId);
            return StepResult.WAITING_RETRY;
        }
        if (!IndustrialCarriedItemService.hasItems(level, manager, data)) {
            IndustrialCarriedItemService.clear(manager, data);
            setStatus(manager, data, "gui.simukraft.industrial.status.running", "");
            return StepResult.PROGRESSED;
        }
        int stepKey = Objects.hash(data.currentStep(), step.type(), containerId);
        if (boxRuntime.activeStep != stepKey) {
            boxRuntime.activeStep = stepKey;
            if (boxRuntime.stepStartedAt == 0) {
                boxRuntime.stepStartedAt = gameTime;
            }
            setContainersOpen(level, containers, true);
            setStatus(manager, data, "gui.simukraft.industrial.status.depositing_carried_items", "");
            return StepResult.WAITING;
        }
        if (gameTime - boxRuntime.stepStartedAt < Math.max(1, step.ticks())) {
            return StepResult.WAITING;
        }
        IndustrialCarriedItemService.DepositResult result = IndustrialCarriedItemService.depositToContainers(level, manager, data, containers, step.itemSpecs());
        if (result == IndustrialCarriedItemService.DepositResult.SUCCESS) {
            setContainersOpen(level, containers, false);
            setStatus(manager, data, "gui.simukraft.industrial.status.running", "");
            return StepResult.PROGRESSED;
        }
        setContainersOpen(level, containers, false);
        setStatus(manager, data,
                result == IndustrialCarriedItemService.DepositResult.MISSING_CONTAINER
                        ? "gui.simukraft.industrial.status.missing_container"
                        : "gui.simukraft.industrial.status.output_full",
                containerId);
        return StepResult.WAITING_RETRY;
    }

    private static StepResult fillItem(IndustrialBoxManager manager,
                                       IndustrialBoxData data,
                                       CitizenEntity entity,
                                       IndustrialDefinition.StepDefinition step,
                                       IndustrialItemFillService.ActionResult result) {
        return switch (result) {
            case SUCCESS -> {
                if (step.swing()) {
                    entity.triggerWorkSwing(InteractionHand.MAIN_HAND);
                }
                setStatus(manager, data, "gui.simukraft.industrial.status.running", "");
                yield StepResult.PROGRESSED;
            }
            case MISSING_TARGET -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.missing_point", step.point());
                yield StepResult.WAITING_RETRY;
            }
            case INVALID_STEP -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.invalid_step", step.type());
                yield StepResult.WAITING_RETRY;
            }
            case MISSING_INPUTS -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.missing_inputs", step.item());
                yield StepResult.WAITING_RETRY;
            }
            case TARGET_BLOCKED -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.machine_input_blocked", step.point());
                yield StepResult.WAITING_RETRY;
            }
        };
    }

    private static StepResult requireBlock(IndustrialBoxManager manager,
                                           IndustrialBoxData data,
                                           IndustrialBlockActionService.ActionResult result,
                                           IndustrialDefinition.StepDefinition step) {
        return switch (result) {
            case SUCCESS -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.running", "");
                yield StepResult.PROGRESSED;
            }
            case MISSING_TARGET -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.missing_point", step.point());
                yield StepResult.WAITING_RETRY;
            }
            case INVALID_BLOCK, INVALID_FLUID -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.invalid_step", step.type());
                yield StepResult.WAITING_RETRY;
            }
            case MISSING_INPUTS -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.missing_inputs", "");
                yield StepResult.WAITING_RETRY;
            }
            case BLOCKED -> {
                String detail = !step.statusText().isBlank() ? step.statusText() : step.block();
                setStatus(manager, data, "gui.simukraft.industrial.status.waiting_block", detail);
                yield StepResult.WAITING_RETRY;
            }
        };
    }

    private static StepResult insertItem(ServerLevel level,
                                         IndustrialBoxManager manager,
                                         IndustrialBoxData data,
                                         BoxRuntime boxRuntime,
                                         PlacedBuildingRecord building,
                                         IndustrialDefinition definition,
                                         IndustrialDefinition.StepDefinition step,
                                         long gameTime) {
        String containerId = containerName(step.container(), step.output(), "output");
        List<BlockPos> containers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerId);
        ItemStack stack = step.itemSpec().stack(Math.max(1, step.count()), level.registryAccess());
        if (containers.isEmpty()) {
            setStatus(manager, data, "gui.simukraft.industrial.status.missing_container", containerId);
            return StepResult.WAITING_RETRY;
        }
        if (stack.isEmpty()) {
            setStatus(manager, data, "gui.simukraft.industrial.status.invalid_step", step.type());
            return StepResult.WAITING_RETRY;
        }
        int stepKey = Objects.hash(data.currentStep(), step.type(), containerId, step.itemSpec().displayKey(), step.count());
        if (boxRuntime.activeStep != stepKey) {
            boxRuntime.activeStep = stepKey;
            if (boxRuntime.stepStartedAt == 0) {
                boxRuntime.stepStartedAt = gameTime;
            }
            setContainersOpen(level, containers, true);
            setStatus(manager, data, "gui.simukraft.industrial.status.inspecting_container", "");
            return StepResult.WAITING;
        }
        int openTicks = step.ticks() > 1 ? step.ticks() : 12;
        if (gameTime - boxRuntime.stepStartedAt < openTicks) {
            return StepResult.WAITING;
        }
        if (!IndustrialInventoryService.hasOutputSpace(level, containers, List.of(stack.copy()))
                || !IndustrialInventoryService.insertItem(level, containers, stack)) {
            setContainersOpen(level, containers, false);
            setStatus(manager, data, "gui.simukraft.industrial.status.output_full", "");
            return StepResult.WAITING_RETRY;
        }
        setContainersOpen(level, containers, false);
        setStatus(manager, data, "gui.simukraft.industrial.status.running", "");
        return StepResult.PROGRESSED;
    }

    private static StepResult blockAction(IndustrialBoxManager manager,
                                          IndustrialBoxData data,
                                          IndustrialBlockActionService.ActionResult result,
                                          String successStatusKey,
                                          IndustrialDefinition.StepDefinition step) {
        return switch (result) {
            case SUCCESS -> {
                setStatus(manager, data, successStatusKey, "");
                yield StepResult.PROGRESSED;
            }
            case MISSING_TARGET -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.missing_point", step.point());
                yield StepResult.WAITING_RETRY;
            }
            case INVALID_BLOCK, INVALID_FLUID -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.invalid_step", step.type());
                yield StepResult.WAITING_RETRY;
            }
            case MISSING_INPUTS -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.missing_inputs", "");
                yield StepResult.WAITING_RETRY;
            }
            case BLOCKED -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.block_action_blocked", step.type());
                yield StepResult.WAITING_RETRY;
            }
        };
    }

    private static StepResult moveTo(ServerLevel level,
                                     IndustrialBoxData data,
                                     BoxRuntime boxRuntime,
                                     PlacedBuildingRecord building,
                                     IndustrialDefinition definition,
                                     CitizenData worker,
                                     CitizenEntity entity,
                                     IndustrialDefinition.StepDefinition step) {
        BlockPos target = IndustrialControlBoxService.resolvePoint(building, definition, step.point(), entity.position());
        if (target == null) {
            setStatus(IndustrialBoxManager.get(level), data, "gui.simukraft.industrial.status.missing_point", step.point());
            return StepResult.WAITING_RETRY;
        }
        double range = Math.max(0.2D, step.range());
        Vec3 targetCenter = Vec3.atBottomCenterOf(target);
        if (entity.position().distanceToSqr(targetCenter) <= range * range) {
            CitizenNavigationService.stop(level, worker.uuid());
            return StepResult.PROGRESSED;
        }
        setStatus(IndustrialBoxManager.get(level), data, "gui.simukraft.industrial.status.moving", step.point());
        CitizenNavigationService.requestMove(level, worker.uuid(), targetCenter, MovementIntent.WORK);
        boxRuntime.resetStep(data.currentStep());
        return StepResult.WAITING_MOVE;
    }

    /**
     * moveToContainer: 让 NPC 走到指定容器旁边的可站立格。
     */
    private static StepResult moveToContainer(ServerLevel level,
                                              IndustrialBoxData data,
                                              BoxRuntime boxRuntime,
                                              PlacedBuildingRecord building,
                                              IndustrialDefinition definition,
                                              CitizenData worker,
                                              CitizenEntity entity,
                                              IndustrialDefinition.StepDefinition step) {
        String containerId = targetContainerName(step, "input");
        List<BlockPos> containers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerId);
        if (containers.isEmpty()) {
            setStatus(IndustrialBoxManager.get(level), data, "gui.simukraft.industrial.status.missing_container", containerId);
            return StepResult.WAITING_RETRY;
        }
        double range = Math.max(0.2D, step.range());
        ContainerMoveTarget target = containerMoveTarget(level, containers, entity.position());
        double arrivalRange = target.hasStandTarget() ? Math.min(range, 0.65D) : range;
        if (entity.position().distanceToSqr(target.position()) <= arrivalRange * arrivalRange
                || (!target.hasStandTarget() && isNearContainer(entity.position(), containers, range))) {
            CitizenNavigationService.stop(level, worker.uuid());
            return StepResult.PROGRESSED;
        }
        setStatus(IndustrialBoxManager.get(level), data, "gui.simukraft.industrial.status.moving", containerId);
        CitizenNavigationService.requestMove(level, worker.uuid(), target.position(), MovementIntent.WORK);
        boxRuntime.resetStep(data.currentStep());
        return StepResult.WAITING_MOVE;
    }

    private static StepResult moveToEntity(ServerLevel level,
                                            IndustrialBoxData data,
                                            BoxRuntime boxRuntime,
                                            PlacedBuildingRecord building,
                                            IndustrialDefinition definition,
                                            CitizenData worker,
                                            CitizenEntity entity,
                                            IndustrialDefinition.StepDefinition step) {
        var target = IndustrialEntityActionService.nearestShearable(level, building, definition, step, entity);
        if (target.isEmpty()) {
            setStatus(IndustrialBoxManager.get(level), data, "gui.simukraft.industrial.status.waiting_regrowth", "");
            return StepResult.WAITING_RETRY;
        }
        double range = Math.max(1.5D, step.range());
        if (entity.position().distanceToSqr(target.get().position()) <= range * range) {
            CitizenNavigationService.stop(level, worker.uuid());
            return StepResult.PROGRESSED;
        }
        setStatus(IndustrialBoxManager.get(level), data, "gui.simukraft.industrial.status.moving", "");
        CitizenNavigationService.requestMove(level, worker.uuid(), target.get().position(), MovementIntent.WORK);
        boxRuntime.resetStep(data.currentStep());
        return StepResult.WAITING_MOVE;
    }

    private static StepResult shearWithAnimation(ServerLevel level,
                                                  IndustrialBoxManager manager,
                                                  IndustrialBoxData data,
                                                  BoxRuntime boxRuntime,
                                                  PlacedBuildingRecord building,
                                                  IndustrialDefinition definition,
                                                  CitizenEntity entity,
                                                  IndustrialDefinition.StepDefinition step,
                                                  long gameTime) {
        var target = IndustrialEntityActionService.nearestShearable(level, building, definition, step, entity);
        if (target.isEmpty()) {
            setStatus(manager, data, "gui.simukraft.industrial.status.missing_entities", "");
            return StepResult.WAITING_RETRY;
        }
        int stepKey = boxRuntimeStepKey(entity, step);
        if (boxRuntime.activeStep != stepKey) {
            boxRuntime.activeStep = stepKey;
            if (boxRuntime.stepStartedAt == 0) {
                boxRuntime.stepStartedAt = gameTime;
            }
        }
        entity.getLookControl().setLookAt(target.get());
        long elapsed = gameTime - boxRuntime.stepStartedAt;
        if (elapsed % 8 == 0) {
            entity.triggerWorkSwing(InteractionHand.MAIN_HAND);
        }
        if (elapsed < step.ticks()) {
            setStatus(manager, data, "gui.simukraft.industrial.status.shearing", "");
            return StepResult.WAITING;
        }
        return entityAction(manager, data,
                IndustrialEntityActionService.shear(level, building, definition, step, entity),
                "gui.simukraft.industrial.status.shearing");
    }

    private static StepResult collectDrops(ServerLevel level,
                                           IndustrialBoxManager manager,
                                           IndustrialBoxData data,
                                           BoxRuntime boxRuntime,
                                           PlacedBuildingRecord building,
                                           IndustrialDefinition definition,
                                           CitizenData worker,
                                           CitizenEntity entity,
                                           IndustrialDefinition.StepDefinition step) {
        if (IndustrialCarriedItemService.stackCount(level, manager, data) >= Math.max(1, step.maxCarryStacks())) {
            setStatus(manager, data, "gui.simukraft.industrial.status.carry_full", "");
            return StepResult.PROGRESSED;
        }
        var target = IndustrialEntityActionService.nearestDrop(level, building, definition, step, entity);
        if (target.isEmpty()) {
            setStatus(manager, data,
                    IndustrialCarriedItemService.hasItems(level, manager, data)
                            ? "gui.simukraft.industrial.status.collecting_drops"
                            : "gui.simukraft.industrial.status.missing_drops",
                    "");
            return StepResult.PROGRESSED;
        }
        double range = Math.max(1.5D, step.range());
        if (entity.position().distanceToSqr(target.get().position()) > range * range) {
            setStatus(manager, data, "gui.simukraft.industrial.status.collecting_drops", "");
            CitizenNavigationService.requestMove(level, worker.uuid(), target.get().position(), MovementIntent.WORK);
            boxRuntime.resetStep(data.currentStep());
            return StepResult.WAITING_MOVE;
        }
        CitizenNavigationService.stop(level, worker.uuid());
        IndustrialEntityActionService.ActionResult result = IndustrialEntityActionService.collectReachableDrops(
                level, manager, data, building, definition, step, entity);
        return switch (result) {
            case SUCCESS -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.collecting_drops", "");
                boolean full = IndustrialCarriedItemService.stackCount(level, manager, data) >= Math.max(1, step.maxCarryStacks());
                boolean empty = IndustrialEntityActionService.nearestDrop(level, building, definition, step, entity).isEmpty();
                yield full || empty ? StepResult.PROGRESSED : StepResult.WAITING;
            }
            case CARRY_FULL -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.carry_full", "");
                yield StepResult.PROGRESSED;
            }
            case MISSING_DROPS -> {
                setStatus(manager, data,
                        IndustrialCarriedItemService.hasItems(level, manager, data)
                                ? "gui.simukraft.industrial.status.collecting_drops"
                                : "gui.simukraft.industrial.status.missing_drops",
                        "");
                yield IndustrialCarriedItemService.hasItems(level, manager, data) ? StepResult.PROGRESSED : StepResult.WAITING_RETRY;
            }
            case STORAGE_FAILED -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.carried_storage_failed", "");
                yield StepResult.WAITING_RETRY;
            }
            case MISSING_ENTITIES, MISSING_INPUTS, OUTPUT_FULL -> entityAction(manager, data, result,
                    "gui.simukraft.industrial.status.collecting_drops");
        };
    }

    private static StepResult lookAt(PlacedBuildingRecord building, IndustrialDefinition definition, CitizenEntity entity, IndustrialDefinition.StepDefinition step) {
        BlockPos target = IndustrialControlBoxService.resolvePoint(building, definition, step.point(), entity.position());
        if (target == null) {
            return StepResult.WAITING_RETRY;
        }
        Vec3 center = Vec3.atCenterOf(target);
        entity.getLookControl().setLookAt(center.x, center.y, center.z);
        return StepResult.PROGRESSED;
    }

    /**
     * lookAtContainer: 让 NPC 面朝指定容器，配合开箱/放入产物表现。
     */
    private static StepResult lookAtContainer(ServerLevel level,
                                              IndustrialBoxData data,
                                              PlacedBuildingRecord building,
                                              IndustrialDefinition definition,
                                              CitizenEntity entity,
                                              IndustrialDefinition.StepDefinition step) {
        String containerId = targetContainerName(step, "input");
        BlockPos target = IndustrialControlBoxService.resolveContainerPosition(building, definition, containerId, entity.position());
        if (target == null) {
            setStatus(IndustrialBoxManager.get(level), data, "gui.simukraft.industrial.status.missing_container", containerId);
            return StepResult.WAITING_RETRY;
        }
        Vec3 center = Vec3.atCenterOf(target);
        entity.getLookControl().setLookAt(center.x, center.y, center.z);
        return StepResult.PROGRESSED;
    }

    private static StepResult requireInputs(ServerLevel level,
                                            IndustrialBoxManager manager,
                                            IndustrialBoxData data,
                                            PlacedBuildingRecord building,
                                            IndustrialDefinition definition,
                                            IndustrialDefinition.RecipeDefinition recipe,
                                            IndustrialDefinition.StepDefinition step) {
        List<BlockPos> containers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerName(step.container(), step.input(), "input"));
        if (containers.isEmpty() || !IndustrialInventoryService.hasInputs(level, containers, recipe.inputs())) {
            setStatus(manager, data, "gui.simukraft.industrial.status.missing_inputs", "");
            return StepResult.WAITING_RETRY;
        }
        return StepResult.PROGRESSED;
    }

    private static StepResult requireOutputSpace(ServerLevel level,
                                                 IndustrialBoxManager manager,
                                                 IndustrialBoxData data,
                                                 PlacedBuildingRecord building,
                                                 IndustrialDefinition definition,
                                                 IndustrialDefinition.RecipeDefinition recipe,
                                                 IndustrialDefinition.StepDefinition step) {
        List<BlockPos> containers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerName(step.container(), step.output(), "output"));
        List<ItemStack> worstCaseOutputs = recipe.outputs().stream()
                .map(output -> output.spec().stack(output.baseAmount() + Math.max(0, output.randomRange()), level.registryAccess()))
                .filter(stack -> !stack.isEmpty())
                .toList();
        if (containers.isEmpty() || !IndustrialInventoryService.hasOutputSpace(level, containers, worstCaseOutputs)) {
            setStatus(manager, data, "gui.simukraft.industrial.status.output_full", "");
            return StepResult.WAITING_RETRY;
        }
        return StepResult.PROGRESSED;
    }

    private static StepResult useItem(CitizenEntity entity, BoxRuntime boxRuntime, IndustrialDefinition.StepDefinition step, long gameTime) {
        if (boxRuntime.activeStep != boxRuntimeStepKey(entity, step)) {
            boxRuntime.activeStep = boxRuntimeStepKey(entity, step);
            // stepStartedAt > 0 说明是重启恢复的计时起点，直接沿用；否则从当前 gameTime 开始
            if (boxRuntime.stepStartedAt == 0) {
                boxRuntime.stepStartedAt = gameTime;
            }
            boxRuntime.swingDone = false;
        }
        if (step.swing() && !boxRuntime.swingDone) {
            entity.triggerWorkSwing(InteractionHand.MAIN_HAND);
            boxRuntime.swingDone = true;
        }
        return gameTime - boxRuntime.stepStartedAt >= Math.max(1, step.ticks()) ? StepResult.PROGRESSED : StepResult.WAITING;
    }

    private static StepResult inspectContainer(ServerLevel level,
                                               IndustrialBoxManager manager,
                                               IndustrialBoxData data,
                                               BoxRuntime boxRuntime,
                                               PlacedBuildingRecord building,
                                               IndustrialDefinition definition,
                                               IndustrialDefinition.StepDefinition step,
                                               long gameTime) {
        List<BlockPos> containers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerName(step.container(), step.input(), "input"));
        if (containers.isEmpty()) {
            setStatus(manager, data, "gui.simukraft.industrial.status.missing_container", "");
            return StepResult.WAITING_RETRY;
        }
        int stepKey = Objects.hash(step.type(), step.container(), step.input(), step.ticks());
        if (boxRuntime.activeStep != stepKey) {
            boxRuntime.activeStep = stepKey;
            if (boxRuntime.stepStartedAt == 0) {
                boxRuntime.stepStartedAt = gameTime;
            }
            setContainersOpen(level, containers, true);
            setStatus(manager, data, "gui.simukraft.industrial.status.inspecting_container", "");
            return StepResult.WAITING;
        }
        if (gameTime - boxRuntime.stepStartedAt < Math.max(1, step.ticks())) {
            return StepResult.WAITING;
        }
        setContainersOpen(level, containers, false);
        return StepResult.PROGRESSED;
    }

    private static StepResult craftRecipe(ServerLevel level,
                                          IndustrialBoxManager manager,
                                          IndustrialBoxData data,
                                          PlacedBuildingRecord building,
                                          IndustrialDefinition definition,
                                          IndustrialDefinition.RecipeDefinition recipe,
                                          CitizenData worker,
                                          IndustrialDefinition.StepDefinition step,
                                          boolean craftAllAvailable) {
        List<BlockPos> inputContainers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerName(step.input(), step.container(), "input"));
        List<BlockPos> outputContainers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerName(step.output(), step.container(), "output"));
        CitizenSkillSnapshot skill = CitizenLevelService.snapshot(worker, CityJobType.INDUSTRIAL_WORKER);
        double multiplier = 1.0D + Math.max(0, skill.level() - 1) * 0.05D;
        IndustrialDefinition.RecipeDefinition effectiveRecipe = stepRecipe(recipe, step);
        boolean crafted = craftAllAvailable
                ? IndustrialInventoryService.craftAvailableRecipe(level, inputContainers, outputContainers, effectiveRecipe, multiplier, level.random)
                : IndustrialInventoryService.craftRecipe(level, inputContainers, outputContainers, effectiveRecipe, multiplier, level.random);
        if (!crafted) {
            setStatus(manager, data, "gui.simukraft.industrial.status.craft_blocked", "");
            return StepResult.WAITING_RETRY;
        }
        CitizenLevelService.addExperience(level, worker.uuid(), CityJobType.INDUSTRIAL_WORKER, 2);
        setCitizenStatus(level, worker, "gui.simukraft.industrial.status.running", "");
        setStatus(manager, data, "gui.simukraft.industrial.status.running", "");
        return StepResult.PROGRESSED;
    }

    private static IndustrialDefinition.RecipeDefinition stepRecipe(IndustrialDefinition.RecipeDefinition recipe, IndustrialDefinition.StepDefinition step) {
        if (!step.inputsOverride() && !step.outputsOverride()) {
            return recipe;
        }
        return new IndustrialDefinition.RecipeDefinition(
                recipe.id(),
                recipe.name(),
                recipe.heldItem(),
                step.inputsOverride() ? step.inputs() : recipe.inputs(),
                step.outputsOverride() ? step.outputs() : recipe.outputs(),
                recipe.steps()
        );
    }

    private static StepResult realMachineRecipe(ServerLevel level,
                                                IndustrialBoxManager manager,
                                                IndustrialBoxData data,
                                                PlacedBuildingRecord building,
                                                IndustrialDefinition definition,
                                                IndustrialDefinition.RecipeDefinition recipe,
                                                CitizenData worker,
                                                CitizenEntity entity,
                                                IndustrialDefinition.StepDefinition step,
                                                long gameTime) {
        IndustrialMachineOperationService.Result result = IndustrialMachineOperationService.execute(level, manager, data, building, definition, recipe, worker, entity, step, gameTime);
        return switch (result) {
            case PROGRESSED -> StepResult.PROGRESSED;
            case WAITING -> StepResult.WAITING;
            case WAITING_RETRY -> StepResult.WAITING_RETRY;
            case NEEDS_INPUT -> {
                IndustrialStepRewindService.rewindForMachineInput(manager, data, recipe, step);
                yield StepResult.WAITING_RETRY;
            }
        };
    }

    private static StepResult entityAction(IndustrialBoxManager manager, IndustrialBoxData data, IndustrialEntityActionService.ActionResult result, String successStatusKey) {
        return switch (result) {
            case SUCCESS -> {
                setStatus(manager, data, successStatusKey, "");
                yield StepResult.PROGRESSED;
            }
            case MISSING_ENTITIES -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.missing_entities", "");
                yield StepResult.WAITING_RETRY;
            }
            case MISSING_DROPS -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.missing_drops", "");
                yield StepResult.WAITING_RETRY;
            }
            case MISSING_INPUTS -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.missing_inputs", "");
                yield StepResult.WAITING_RETRY;
            }
            case OUTPUT_FULL -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.output_full", "");
                yield StepResult.WAITING_RETRY;
            }
            case CARRY_FULL -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.carry_full", "");
                yield StepResult.PROGRESSED;
            }
            case STORAGE_FAILED -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.carried_storage_failed", "");
                yield StepResult.WAITING_RETRY;
            }
        };
    }

    private static void setContainersOpen(ServerLevel level, List<BlockPos> containers, boolean open) {
        for (BlockPos container : containers) {
            if (container == null || !level.isLoaded(container)) {
                continue;
            }
            BlockState state = level.getBlockState(container);
            if (state.hasProperty(BarrelBlock.OPEN)) {
                level.setBlock(container, state.setValue(BarrelBlock.OPEN, open), 3);
            }
            level.blockEvent(container, state.getBlock(), 1, open ? 1 : 0);
        }
    }

    private static void advanceStep(IndustrialBoxManager manager, IndustrialBoxData data, IndustrialDefinition.RecipeDefinition recipe, BoxRuntime boxRuntime) {
        int next = data.currentStep() + 1;
        data.setCurrentStep(next >= recipe.steps().size() ? 0 : next);
        data.setMachineState("");
        data.setStepElapsedTicks(0);
        // 一轮配方循环结束时清空工作位置
        if (next >= recipe.steps().size()) {
            data.setWorkerWorkPos(Long.MIN_VALUE);
        }
        boxRuntime.reset();
        manager.persist(data);
    }

    private static void setStatus(IndustrialBoxManager manager, IndustrialBoxData data, String statusKey, String statusText) {
        String safeText = statusText != null ? statusText : "";
        if (Objects.equals(data.statusKey(), statusKey) && Objects.equals(data.statusText(), safeText)) {
            return;
        }
        data.setStatusKey(statusKey);
        data.setStatusText(safeText);
        manager.persist(data);
        IndustrialControlBoxViewSyncService.syncStatusIfChanged(manager.level(), data);
    }

    private static void setCitizenStatus(ServerLevel level, CitizenData worker, String statusKey, String needDetail) {
        if (worker == null) {
            return;
        }
        worker.setWorkStatus(CitizenWorkStatus.WORKING);
        worker.setStatusLabel(statusKey);
        worker.setWorkNeedDetail(needDetail != null ? needDetail : "");
        CitizenService.save(level, worker.uuid());
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

    /**
     * targetContainerName: 解析移动/朝向类步骤中的容器名。
     */
    private static String targetContainerName(IndustrialDefinition.StepDefinition step, String fallback) {
        if (step == null) {
            return fallback;
        }
        if (step.container() != null && !step.container().isBlank()) {
            return step.container();
        }
        if (step.input() != null && !step.input().isBlank()) {
            return step.input();
        }
        if (step.output() != null && !step.output().isBlank()) {
            return step.output();
        }
        return fallback;
    }

    /**
     * isNearContainer: 判断 NPC 是否已经处于任意目标容器的交互距离内。
     */
    private static boolean isNearContainer(Vec3 position, List<BlockPos> containers, double range) {
        if (position == null || containers == null || containers.isEmpty()) {
            return false;
        }
        double maxDistanceSqr = range * range;
        for (BlockPos container : containers) {
            if (distanceToBlockBoxSqr(position, container) <= maxDistanceSqr) {
                return true;
            }
        }
        return false;
    }

    /**
     * containerMoveTarget: 优先让 NPC 走到容器旁可站点，找不到时回退到最近容器中心。
     */
    private static ContainerMoveTarget containerMoveTarget(ServerLevel level, List<BlockPos> containers, Vec3 origin) {
        BlockPos bestStand = null;
        double bestStandDistance = Double.MAX_VALUE;
        BlockPos bestContainer = null;
        double bestContainerDistance = Double.MAX_VALUE;
        for (BlockPos container : containers) {
            double containerDistance = origin != null ? distanceToBlockBoxSqr(origin, container) : 0.0D;
            if (bestContainer == null || containerDistance < bestContainerDistance) {
                bestContainer = container;
                bestContainerDistance = containerDistance;
            }
            BlockPos stand = containerStandTarget(level, container, origin);
            if (stand == null) {
                continue;
            }
            double standDistance = origin != null ? Vec3.atBottomCenterOf(stand).distanceToSqr(origin) : 0.0D;
            if (bestStand == null || standDistance < bestStandDistance) {
                bestStand = stand;
                bestStandDistance = standDistance;
            }
        }
        if (bestStand != null) {
            return new ContainerMoveTarget(Vec3.atBottomCenterOf(bestStand), true);
        }
        return new ContainerMoveTarget(Vec3.atBottomCenterOf(bestContainer), false);
    }

    /**
     * containerStandTarget: 从容器周围向下查找可站立格，不向上搜索屋顶。
     */
    private static BlockPos containerStandTarget(ServerLevel level, BlockPos container, Vec3 origin) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int radius = 1; radius <= 2; radius++) {
            for (int yOffset = 0; yOffset >= -3; yOffset--) {
                for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != radius) {
                            continue;
                        }
                        BlockPos candidate = container.offset(xOffset, yOffset, zOffset);
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

    /**
     * distanceToBlockBoxSqr: 计算实体位置到方块包围盒的最近距离平方。
     */
    private static double distanceToBlockBoxSqr(Vec3 position, BlockPos blockPos) {
        if (position == null || blockPos == null) {
            return Double.MAX_VALUE;
        }
        double dx = axisDistance(position.x, blockPos.getX(), blockPos.getX() + 1.0D);
        double dy = axisDistance(position.y, blockPos.getY(), blockPos.getY() + 1.0D);
        double dz = axisDistance(position.z, blockPos.getZ(), blockPos.getZ() + 1.0D);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * axisDistance: 计算一个坐标轴上点到区间的最近距离。
     */
    private static double axisDistance(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0D;
    }

    private static int boxRuntimeStepKey(CitizenEntity entity, IndustrialDefinition.StepDefinition step) {
        return Objects.hash(entity.getUUID(), step.type(), step.ticks(), step.swing());
    }

    private static LevelRuntime runtime(ServerLevel level) {
        return RUNTIMES.computeIfAbsent(SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT), ignored -> new LevelRuntime());
    }

    private enum StepResult {
        PROGRESSED,
        WAITING,
        WAITING_MOVE,
        WAITING_RETRY
    }

    private static final class LevelRuntime {
        private final ConcurrentMap<BlockPos, BoxRuntime> boxes = new ConcurrentHashMap<>();
    }

    private static final class BoxRuntime {
        private long nextTick;
        private int activeStep = Integer.MIN_VALUE;
        private long stepStartedAt;
        private long timeoutStartAt;
        private boolean swingDone;
        private boolean progressRestored; // 本次会话已从持久化恢复过计时进度，防止二次恢复

        private void reset() {
            activeStep = Integer.MIN_VALUE;
            stepStartedAt = 0L;
            timeoutStartAt = 0L;
            swingDone = false;
        }

        private void resetStep(int currentStep) {
            if (activeStep != currentStep) {
                reset();
            }
        }
    }

    private record ContainerMoveTarget(Vec3 position, boolean hasStandTarget) {
    }

}
