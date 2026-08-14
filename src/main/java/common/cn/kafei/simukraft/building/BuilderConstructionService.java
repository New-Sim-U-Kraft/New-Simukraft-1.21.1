package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenHomeRestService;
import common.cn.kafei.simukraft.citizen.CitizenLevelService;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenWorkplaceMoveService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.network.rts.RtsBuildingBoundsRequestPacket;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.job.CityJobAssignmentService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.material.WorkContainerService;
import common.cn.kafei.simukraft.material.WorkMaterialCache;
import common.cn.kafei.simukraft.material.WorkMaterialNotificationService;
import common.cn.kafei.simukraft.material.WorkMaterialResult;
import common.cn.kafei.simukraft.material.NpcWorkMaterialService;
import common.cn.kafei.simukraft.medical.MedicalService;
import common.cn.kafei.simukraft.protection.NpcBlockProtectionPolicy;
import common.cn.kafei.simukraft.registry.ModBlocks;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import common.cn.kafei.simukraft.util.NpcWorkChunkLoadService;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("null")
public final class BuilderConstructionService {
    // 建筑结构变换后的方块列表缓存，避免每个 tick 重复解析 NBT/旋转坐标。
    private static final ConcurrentMap<String, CompletableFuture<CachedStructure>> STRUCTURE_CACHE = new ConcurrentHashMap<>();
    // 每个存档维度独立的施工运行时，不能跨存档复用。
    private static final ConcurrentMap<String, LevelRuntime> LEVEL_RUNTIMES = new ConcurrentHashMap<>();
    private static final int SAVE_BLOCK_INTERVAL = 12;
    private static final long SAVE_INTERVAL_MS = 1500L;
    private static final long MATERIAL_RETRY_INTERVAL_TICKS = 40L;
    private static final int WORK_AREA_PADDING_BLOCKS = 4;
    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())),
            new BuilderIoThreadFactory()
    );

    private BuilderConstructionService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        // 先异步恢复 SQLite 中的未完成任务，再按 NPC 逐个推进施工。
        hydrateTasks(level, runtime);
        for (TaskRuntime taskRuntime : runtime.tasksByCitizen.values()) {
            Optional<CitizenData> citizen = CitizenService.findCitizen(level, taskRuntime.task.citizenId());
            if (citizen.isEmpty() || citizen.get().dead()) {
                interruptTask(level, taskRuntime.task.citizenId(), citizen.isPresent() ? "citizen_dead" : "citizen_missing");
                continue;
            }
            if (CitizenSelfFeedingService.isSelfFeeding(level, citizen.get().uuid())) {
                continue;
            }
            tickTask(level, citizen.get(), runtime, taskRuntime);
        }
        if (level.getGameTime() % 40L == 0L) {
            flushDirtyTasks(level, runtime);
            flushPendingBuilderXp(level, runtime);
        }
    }

    public static void startTask(ServerLevel level, BuildingTaskData task) {
        if (level == null || task == null || task.citizenId() == null) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        TaskRuntime taskRuntime = new TaskRuntime(task);
        TaskRuntime replaced = runtime.tasksByCitizen.put(task.citizenId(), taskRuntime);
        if (replaced != null) {
            NpcWorkChunkLoadService.release(level, replaced.task.taskId());
        }
        runtime.hydrated = true;
        SimuSqliteStorage.saveBuildingTask(level, task);
        taskRuntime.lastSavedAt = task.updatedAt();
        taskRuntime.lastSavedIndex = task.currentBlockIndex();
        NpcWorkChunkLoadService.acquire(level, task.taskId(), task.buildBoxPos());
        primeStructureLoad(task);
    }

    public static void cancelTask(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return;
        }
        TaskRuntime removed = runtime(level).tasksByCitizen.remove(citizenId);
        if (removed != null) NpcWorkChunkLoadService.release(level, removed.task.taskId());
        SimuSqliteStorage.deleteBuildingTask(level, citizenId);
    }

    // hasActiveBuildTask：供职业外观判断建筑师是否需要播放施工动作。
    public static boolean hasActiveBuildTask(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return false;
        }
        TaskRuntime taskRuntime = runtime(level).tasksByCitizen.get(citizenId);
        if (taskRuntime == null) {
            return false;
        }
        BuildingTaskStatus status = BuildingTaskStatus.from(taskRuntime.task.status());
        return taskRuntime.task.currentBlockIndex() < taskRuntime.task.totalBlocks()
                && status != BuildingTaskStatus.COMPLETED
                && status != BuildingTaskStatus.INTERRUPTED
                && !status.isPaused();
    }

    // findBuildBoxPos：供上班恢复逻辑按市民找到当前施工控制盒，不触发额外扫描或数据库读取。
    public static BlockPos findBuildBoxPos(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return null;
        }
        TaskRuntime taskRuntime = runtime(level).tasksByCitizen.get(citizenId);
        return taskRuntime != null && taskRuntime.task != null ? taskRuntime.task.buildBoxPos() : null;
    }

    public static void interruptTask(ServerLevel level, UUID citizenId, String reason) {
        if (level == null || citizenId == null) {
            return;
        }
        TaskRuntime removed = runtime(level).tasksByCitizen.remove(citizenId);
        if (removed != null) {
            NpcWorkChunkLoadService.release(level, removed.task.taskId());
            CitizenService.findCitizen(level, citizenId)
                    .filter(citizen -> !citizen.dead())
                    .ifPresent(citizen -> flushPendingBuilderXp(level, citizen, removed));
            // 原来先写入 INTERRUPTED 状态再立刻删除同一行，落库结果只有"删除"，写入是无效动作。
            // 直接在调用线程入队删除，顺序才和 persistTask 的写入可比。
            SimuSqliteStorage.deleteBuildingTask(level, citizenId);
        }
        SimuKraft.LOGGER.info("Simukraft: Building task interrupted for {} ({})", citizenId, reason != null ? reason : "unknown");
    }

    public static void interruptTasksByBuildBox(ServerLevel level, BlockPos buildBoxPos, String reason) {
        if (level == null || buildBoxPos == null) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        runtime.tasksByCitizen.values().stream()
                .filter(taskRuntime -> buildBoxPos.equals(taskRuntime.task.buildBoxPos()))
                .map(taskRuntime -> taskRuntime.task.citizenId())
                .toList()
                .forEach(citizenId -> CitizenEmploymentService.fire(level, citizenId, "build_box", "builder", buildBoxPos, reason));
    }

    public static void flush(ServerLevel level) {
        if (level == null) {
            return;
        }
        LevelRuntime runtime = LEVEL_RUNTIMES.remove(runtimeKey(level));
        if (runtime == null) {
            return;
        }
        runtime.tasksByCitizen.values().forEach(taskRuntime -> {
            CitizenService.findCitizen(level, taskRuntime.task.citizenId())
                    .filter(citizen -> !citizen.dead())
                    .ifPresent(citizen -> flushPendingBuilderXp(level, citizen, taskRuntime));
            NpcWorkChunkLoadService.release(level, taskRuntime.task.taskId());
            // 服务器关闭时统一标记离线暂停，避免重进游戏后任务被当作正在施工。
            BuildingTaskData paused = taskRuntime.task.withStatus(BuildingTaskStatus.PAUSED_OFFLINE);
            taskRuntime.task = paused;
            SimuSqliteStorage.saveBuildingTask(level, paused);
        });
    }

    // 清理指定存档的施工运行时缓存，结构变换缓存不跨存档保留。
    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server).toLowerCase(Locale.ROOT);
        LEVEL_RUNTIMES.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
        STRUCTURE_CACHE.clear();
    }

    private static void tickTask(ServerLevel level, CitizenData citizen, LevelRuntime runtime, TaskRuntime taskRuntime) {
        BuildingTaskData task = taskRuntime.task;
        // 建筑师失业、控制盒被拆、夜间休息都在服务端判定，防止客户端伪造流程。
        if (citizen.jobType() != CityJobType.BUILDER || citizen.workplaceId() == null) {
            interruptTask(level, citizen.uuid(), "builder_not_assigned");
            return;
        }
        if (MedicalService.isOnMedicalLeave(citizen, level.getDayTime() / 24_000L)) {
            pauseForMedicalLeave(level, citizen, taskRuntime);
            return;
        }
        if (!level.getBlockState(task.buildBoxPos()).is(ModBlocks.BUILD_BOX.get())) {
            CitizenEmploymentService.fire(level, citizen.uuid(), "build_box", "builder", task.buildBoxPos(), "build_box_removed");
            return;
        }
        if (shouldRest(level)) {
            pauseTask(level, citizen, taskRuntime, BuildingTaskStatus.PAUSED_RESTING,
                    translatedStatusLabel(level, "status.simukraft.builder.resting_night", task.displayName()));
            return;
        }
        if (BuildingTaskStatus.from(task.status()).isPaused()) {
            task = resumeTask(level, citizen, taskRuntime);
        }
        if (taskRuntime.chestCloseAtTick > 0 && level.getGameTime() >= taskRuntime.chestCloseAtTick) {
            playChestAnimation(level, taskRuntime, false);
            taskRuntime.chestCloseAtTick = 0;
        }
        BuildingTaskStatus currentStatus = BuildingTaskStatus.from(task.status());
        if (currentStatus == BuildingTaskStatus.WAITING_MATERIALS) {
            if (level.getGameTime() < taskRuntime.nextMaterialRetryTick) {
                syncCitizenTaskState(level, citizen, taskRuntime, task, null);
                return;
            }
            taskRuntime.materialCache.markDirty();
        }
        boolean wasWaiting = currentStatus == BuildingTaskStatus.WAITING_MATERIALS;
        CachedStructure cached = resolveCached(task);
        if (cached == null || cached.blocks().isEmpty()) {
            syncCitizenTaskState(level, citizen, taskRuntime, task, null);
            return;
        }
        ensureWorkAreaTickets(level, taskRuntime, cached);
        syncCitizenTaskState(level, citizen, taskRuntime, task, cached);
        int placed = 0;
        int index = Math.max(0, task.currentBlockIndex());
        LayerRange currentLayer = resolveCurrentLayerRange(cached, index);
        int maxIndexExclusive = currentLayer != null ? Math.min(cached.blocks().size(), currentLayer.endIndex() + 1) : cached.blocks().size();
        int blockBudget = consumeBuildBudget(taskRuntime, citizen);
        // 每 tick 只放置当前层的一小段，降低大建筑一次性 setBlock 带来的卡顿。
        while (index < maxIndexExclusive && placed < blockBudget) {
            BuildingBlockData block = cached.blocks().get(index);
            BlockPos worldPos = block.relativePos();
            BlockState targetState = block.state();
            BlockState currentState = level.getBlockState(worldPos);
            if (currentState.equals(targetState)) {
                BuildingBlockPlacementService.applyBlockEntityData(level, worldPos, block.copyBlockEntityData());
                index++;
                continue;
            }
            if (NpcBlockProtectionPolicy.isProtected(currentState)) {
                NpcBlockProtectionPolicy.logSkipped("builder", level, worldPos, currentState);
                index++;
                placed++;
                continue;
            }
            // 不替换空气：跳过结构中的空气方块，保留原有方块
            if (targetState.isAir() && !task.replaceWithAir()) {
                index++;
                placed++;
                continue;
            }
            // 先检查目标区块是否加载，未加载则跳过（不消耗材料），等待下一 tick 重试
            if (!level.isAreaLoaded(worldPos, 4)) {
                break;
            }
            WorkMaterialResult materialResult = BuilderMaterialService.tryConsumeForBlock(level, taskRuntime.materialCache, targetState);
            if (!materialResult.available()) {
                markWaitingForMaterials(level, citizen, taskRuntime, task, materialResult);
                return;
            }
            if (wasWaiting) {
                playChestAnimation(level, taskRuntime, true);
                taskRuntime.chestCloseAtTick = level.getGameTime() + 20;
                wasWaiting = false;
            }
            // 拆除原有方块时掉落物存入工作箱子，与 replaceWithAir 无关
            if (!currentState.isAir()) {
                List<ItemStack> obstructionDrops = Block.getDrops(currentState, level, worldPos, level.getBlockEntity(worldPos));
                if (!obstructionDrops.isEmpty()) {
                    WorkContainerService.depositDropsOrDrop(level, WorkContainerService.adjacentContainers(level, task.buildBoxPos()), obstructionDrops, worldPos);
                }
            }
            level.setBlock(worldPos, BuildingBlockPlacementService.refreshedPlacementState(level, worldPos, targetState), 3);
            BuildingBlockPlacementService.applyBlockEntityData(level, worldPos, block.copyBlockEntityData());
            spawnBuildParticles(level, worldPos);
            addPendingBuilderXp(taskRuntime, 1);
            index++;
            placed++;
        }
        if (index == task.currentBlockIndex()) {
            return;
        }
        BuildingTaskData updated = task.withProgress(index, index >= cached.blocks().size() ? BuildingTaskStatus.COMPLETED : BuildingTaskStatus.BUILDING);
        taskRuntime.task = updated;
        taskRuntime.dirty = true;
        taskRuntime.missingMaterialName = "";
        taskRuntime.nextMaterialRetryTick = 0L;
        syncCitizenTaskState(level, citizen, taskRuntime, updated, cached);
        if (shouldPersist(taskRuntime, updated)) {
            persistTask(level, taskRuntime, updated);
        }
        if (index >= cached.blocks().size()) {
            completeTask(level, citizen, runtime, taskRuntime, updated, cached);
        }
    }

    /** ensureWorkAreaTickets：按实际结构边界维持施工方块及其邻域的区块加载。 */
    private static void ensureWorkAreaTickets(ServerLevel level, TaskRuntime taskRuntime, CachedStructure cached) {
        if (taskRuntime.workAreaTicketsLoaded) {
            return;
        }
        BlockPos firstPos = cached.blocks().getFirst().relativePos();
        int minX = firstPos.getX();
        int minY = firstPos.getY();
        int minZ = firstPos.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        for (BuildingBlockData block : cached.blocks()) {
            BlockPos blockPos = block.relativePos();
            minX = Math.min(minX, blockPos.getX());
            minY = Math.min(minY, blockPos.getY());
            minZ = Math.min(minZ, blockPos.getZ());
            maxX = Math.max(maxX, blockPos.getX());
            maxY = Math.max(maxY, blockPos.getY());
            maxZ = Math.max(maxZ, blockPos.getZ());
        }
        NpcWorkChunkLoadService.loadWorkArea(level, taskRuntime.task.taskId(),
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), WORK_AREA_PADDING_BLOCKS);
        taskRuntime.workAreaTicketsLoaded = true;
    }

    private static void completeTask(ServerLevel level, CitizenData citizen, LevelRuntime runtime, TaskRuntime taskRuntime, BuildingTaskData task, CachedStructure cached) {
        BuildingBlockPlacementService.placeStructureEntities(level, cached.entities(), task.rotationDegrees());
        UUID cityId = task.cityId();
        List<BuildingPoiInstance> poiInstances = resolvePoiInstances(cached.sourceBlocks(), cached.blocks(), task);
        if (cityId != null) {
            // 完工时才注册 POI，保证未建完的住宅/岗位不会提前参与居民分配。
            for (BuildingPoiInstance poi : poiInstances) {
                CityPoiManager.get(level).registerPoi(stablePoiId(poi), cityId, poi.worldPos(), poi.poiType(), poi.capacity());
            }
            CityJobAssignmentService.invalidate(cityId);
        }
        int minX = task.origin().getX(), minY = task.origin().getY(), minZ = task.origin().getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        for (BuildingBlockData b : cached.blocks()) {
            BlockPos p = b.relativePos();
            if (p.getX() < minX) minX = p.getX(); if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() < minY) minY = p.getY(); if (p.getY() > maxY) maxY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ(); if (p.getZ() > maxZ) maxZ = p.getZ();
        }
        BlockPos minPos = new BlockPos(minX, minY, minZ);
        BlockPos maxPos = new BlockPos(maxX, maxY, maxZ);
        List<BuildingUnitDefinition> unitDefs = resolveUnitDefinitions(task);
        List<BuildingUnitInstance> unitInsts = buildUnitInstances(unitDefs, poiInstances, task.origin(), task.rotationDegrees());
        if (cityId != null && !unitInsts.isEmpty()) {
            CityPoiManager poiManager = CityPoiManager.get(level);
            for (BuildingUnitInstance unit : unitInsts) {
                for (java.util.UUID poiId : unit.poiIds()) {
                    common.cn.kafei.simukraft.city.poi.CityPoiData poi = poiManager.getPoi(poiId);
                    if (poi != null) {
                        poiManager.updatePoiUnitId(poiId, unit.unitId());
                    }
                }
            }
        }
        PlacedBuildingRecord placedBuilding = new PlacedBuildingRecord(UUID.randomUUID(), cityId, task.dimensionId(), task.category(), task.buildingFileName(), task.displayName(), task.amount(), task.structureFileName(), BuildingTransform.directionFromRotation(task.rotationDegrees()).getSerializedName(), task.origin(), BlockPos.ZERO, minPos, maxPos, System.currentTimeMillis(), cached.blocks(), task.poiDefinitions(), poiInstances, unitDefs, unitInsts);
        PlacedBuildingService.register(level, placedBuilding);
        RtsBuildingBoundsRequestPacket.refreshNearbyPlayers(level, placedBuilding);
        ResidentialBedPoiService.addRecordedBeds(level, placedBuilding);
        MedicalBedPoiService.addRecordedBeds(level, placedBuilding);
        if (placedBuilding.cityId() != null) {
            common.cn.kafei.simukraft.citizen.CitizenHousingService.fillVacantHomes(level, placedBuilding.cityId());
        }
        ConstructionCompletionNotificationService.notifyCompleted(level, citizen, task);
        flushPendingBuilderXp(level, citizen, taskRuntime);
        NpcWorkChunkLoadService.release(level, task.taskId());
        runtime.tasksByCitizen.remove(citizen.uuid(), taskRuntime);
        SimuSqliteStorage.deleteBuildingTask(level, citizen.uuid());
        CitizenEmploymentService.clearAfterJobFinished(level, citizen.uuid());
        SimuKraft.LOGGER.info("Simukraft: Building completed by {} for {}", citizen.name(), task.displayName());
    }

    private static void hydrateTasks(ServerLevel level, LevelRuntime runtime) {
        if (runtime.hydrated) {
            return;
        }
        CompletableFuture<List<BuildingTaskData>> loadFuture = runtime.loadFuture;
        if (loadFuture == null) {
            // SQLite 读取放到 IO 线程，避免世界 tick 被磁盘读取阻塞。
            runtime.loadFuture = CompletableFuture.supplyAsync(() -> SimuSqliteStorage.loadBuildingTasks(level), IO_EXECUTOR);
            return;
        }
        if (!loadFuture.isDone()) {
            return;
        }
        try {
            loadFuture.join().forEach(task -> {
                boolean restoredFromOfflinePause = BuildingTaskStatus.from(task.status()) == BuildingTaskStatus.PAUSED_OFFLINE;
                BuildingTaskData resumed = restoredFromOfflinePause
                        ? task.withStatus(BuildingTaskStatus.BUILDING)
                        : task;
                TaskRuntime taskRuntime = new TaskRuntime(resumed);
                TaskRuntime existing = runtime.tasksByCitizen.putIfAbsent(resumed.citizenId(), taskRuntime);
                if (existing == null) {
                    NpcWorkChunkLoadService.acquire(level, resumed.taskId(), resumed.buildBoxPos());
                }
                primeStructureLoad(resumed);
                restoreBuilderEmployment(level, resumed);
                if (existing == null && restoredFromOfflinePause && !shouldRest(level)) {
                    taskRuntime.dirty = true;
                    persistTask(level, taskRuntime, resumed);
                    CitizenService.findCitizen(level, resumed.citizenId())
                            .filter(citizen -> !citizen.dead())
                            .ifPresent(citizen -> {
                                citizen.setWorkStatus(CitizenWorkStatus.WORKING);
                                CitizenService.save(level, citizen.uuid());
                                CitizenWorkplaceMoveService.returnToWorkplace(level, citizen);
                            });
                }
            });
            runtime.hydrated = true;
        } catch (CompletionException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to hydrate building tasks for {}", level.dimension().location(), exception);
            runtime.loadFuture = null;
        }
    }

    // restoreBuilderEmployment：任务从 SQLite 恢复时反向校正职业绑定，避免居民表旧数据导致开服后被判定失业。
    private static void restoreBuilderEmployment(ServerLevel level, BuildingTaskData task) {
        if (level == null || task == null || task.citizenId() == null) {
            return;
        }
        CitizenEmploymentService.assignForSource(level, task.citizenId(), "build_box", "builder", task.buildBoxPos(), CitizenWorkStatus.WORKING, task.displayName());
    }

    private static void flushDirtyTasks(ServerLevel level, LevelRuntime runtime) {
        runtime.tasksByCitizen.values().forEach(taskRuntime -> {
            if (!taskRuntime.dirty) {
                return;
            }
            persistTask(level, taskRuntime, taskRuntime.task);
        });
    }

    private static void addPendingBuilderXp(TaskRuntime taskRuntime, int placedBlocks) {
        if (taskRuntime == null || placedBlocks <= 0 || !ServerConfig.builderXpGainEnabled()) {
            return;
        }
        int xpPerBlock = ServerConfig.builderXpPerBlock();
        if (xpPerBlock <= 0) {
            return;
        }
        int xpToAdd = (int) Math.min(Integer.MAX_VALUE, (long) placedBlocks * xpPerBlock);
        taskRuntime.pendingBuilderXp.updateAndGet(current -> (int) Math.min(Integer.MAX_VALUE, (long) current + xpToAdd));
    }

    private static void flushPendingBuilderXp(ServerLevel level, LevelRuntime runtime) {
        if (level == null || runtime == null) {
            return;
        }
        runtime.tasksByCitizen.values().forEach(taskRuntime ->
                CitizenService.findCitizen(level, taskRuntime.task.citizenId())
                        .filter(citizen -> !citizen.dead())
                        .ifPresent(citizen -> flushPendingBuilderXp(level, citizen, taskRuntime)));
    }

    private static void flushPendingBuilderXp(ServerLevel level, CitizenData citizen, TaskRuntime taskRuntime) {
        if (level == null || citizen == null || taskRuntime == null) {
            return;
        }
        int xpToAdd = taskRuntime.pendingBuilderXp.getAndSet(0);
        if (xpToAdd <= 0) {
            return;
        }
        CitizenLevelService.LevelUpdateResult result = CitizenLevelService.addExperience(level, citizen.uuid(), CityJobType.BUILDER, xpToAdd);
        if (result.leveledUp()) {
            SimuKraft.LOGGER.info("Simukraft: Builder {} leveled up to Lv.{} after gaining {} xp", citizen.name(), result.after().level(), xpToAdd);
        }
    }

    private static void playChestAnimation(ServerLevel level, TaskRuntime taskRuntime, boolean open) {
        int viewerCount = open ? 1 : 0;
        for (BlockPos pos : taskRuntime.materialCache.getContainerPositions()) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock) {
                level.blockEvent(pos, state.getBlock(), 1, viewerCount);
            }
        }
    }

    private static void spawnBuildParticles(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        level.sendParticles(
                ParticleTypes.CLOUD,
                pos.getX() + 0.5D,
                pos.getY() + 0.18D,
                pos.getZ() + 0.5D,
                4,
                0.18D,
                0.12D,
                0.18D,
                0.015D
        );
    }

    private static int consumeBuildBudget(TaskRuntime taskRuntime, CitizenData citizen) {
        if (taskRuntime == null || citizen == null) {
            return 0;
        }
        taskRuntime.buildProgressAccumulator += builderBlocksPerTick(citizen);
        int blockBudget = Math.min(128, (int) taskRuntime.buildProgressAccumulator);
        taskRuntime.buildProgressAccumulator -= blockBudget;
        return blockBudget;
    }

    private static double builderBlocksPerTick(CitizenData citizen) {
        return CitizenLevelService.blocksPerTick(citizen, CityJobType.BUILDER, ServerConfig.builderBlocksPerSecond());
    }

    private static boolean shouldPersist(TaskRuntime taskRuntime, BuildingTaskData task) {
        return task.currentBlockIndex() - taskRuntime.lastSavedIndex >= SAVE_BLOCK_INTERVAL
                || task.updatedAt() - taskRuntime.lastSavedAt >= SAVE_INTERVAL_MS
                || BuildingTaskStatus.COMPLETED.id().equalsIgnoreCase(task.status())
                || BuildingTaskStatus.from(task.status()).isPaused();
    }

    private static void pauseTask(ServerLevel level, CitizenData citizen, TaskRuntime taskRuntime, BuildingTaskStatus status, String statusLabel) {
        BuildingTaskData task = taskRuntime.task;
        if (BuildingTaskStatus.from(task.status()) == status
                && citizen.workStatusType() == CitizenWorkStatus.RESTING
                && statusLabel.equals(citizen.statusLabel())) {
            return;
        }
        BuildingTaskData paused = task.withStatus(status);
        taskRuntime.task = paused;
        taskRuntime.dirty = true;
        taskRuntime.lastPhaseKey = status.id();
        citizen.setWorkStatus(CitizenWorkStatus.RESTING);
        citizen.setStatusLabel(statusLabel);
        citizen.setWorkNeedDetail("build:" + paused.taskId() + ":" + status.id());
        CitizenService.save(level, citizen.uuid());
        persistTask(level, taskRuntime, paused);
    }

    private static BuildingTaskData resumeTask(ServerLevel level, CitizenData citizen, TaskRuntime taskRuntime) {
        BuildingTaskData resumed = taskRuntime.task.withStatus(BuildingTaskStatus.BUILDING);
        taskRuntime.task = resumed;
        taskRuntime.dirty = true;
        taskRuntime.lastPhaseKey = "";
        citizen.setWorkStatus(CitizenWorkStatus.WORKING);
        CitizenService.save(level, citizen.uuid());
        persistTask(level, taskRuntime, resumed);
        CitizenWorkplaceMoveService.returnToWorkplace(level, citizen);
        return resumed;
    }

    private static boolean shouldRest(ServerLevel level) {
        if (!ServerConfig.builderPauseAtNight()) {
            return false;
        }
        return CitizenHomeRestService.isRestTime(level);
    }

    /*
     * 保存使用快照，防止落库的是 taskRuntime 正在变化的中间状态。
     * 入队必须在调用线程完成：IO_EXECUTOR 是 2~4 线程的池，若把 save 和 delete 分别丢进池里，
     * 它们到达写队列的先后顺序不确定，已完成建筑的任务行可能被一条迟到的 save 复活。
     * SimuSqliteStorage.saveBuildingTask 只做"抓快照 + 入队"，不碰 JDBC，因此可以直接调用；
     * "保存完成后发现快照已过期就再存一次" 也不再需要，写队列按行合并，后来的快照覆盖旧快照。
     */
    private static void persistTask(ServerLevel level, TaskRuntime taskRuntime, BuildingTaskData snapshot) {
        SimuSqliteStorage.saveBuildingTask(level, snapshot);
        taskRuntime.lastSavedIndex = snapshot.currentBlockIndex();
        taskRuntime.lastSavedAt = snapshot.updatedAt();
        taskRuntime.dirty = !snapshot.equals(taskRuntime.task);
    }

    private static void primeStructureLoad(BuildingTaskData task) {
        STRUCTURE_CACHE.computeIfAbsent(structureKey(task), ignored ->
                CompletableFuture.supplyAsync(() -> loadStructure(task), IO_EXECUTOR)
        );
    }

    private static CachedStructure resolveCached(BuildingTaskData task) {
        CompletableFuture<CachedStructure> future = STRUCTURE_CACHE.computeIfAbsent(structureKey(task), ignored ->
                CompletableFuture.supplyAsync(() -> loadStructure(task), IO_EXECUTOR)
        );
        if (!future.isDone()) {
            return null;
        }
        try {
            return future.join();
        } catch (CompletionException exception) {
            SimuKraft.LOGGER.error("Simukraft: Failed to preload structure {}", structureKey(task), exception);
            return null;
        }
    }

    private static CachedStructure loadStructure(BuildingTaskData task) {
        Optional<BuildingStructure> structureOptional = BuildingStructureService.loadStructure(task);
        if (structureOptional.isEmpty()) {
            return null;
        }
        BuildingStructure structure = structureOptional.get();
        List<BuildingBlockData> sourceBlocks = List.copyOf(structure.blocks());
        List<BuildingBlockData> placedBlocks = BuildingStructureService.resolvePlacedBlocks(structure, task.origin(), task.rotationDegrees()).stream()
                .sorted(Comparator.comparingInt((BuildingBlockData b) -> b.relativePos().getY())
                        .thenComparingInt(b -> b.state().getFluidState().isEmpty() ? 0 : 1)
                        .thenComparingInt(b -> b.relativePos().getX())
                        .thenComparingInt(b -> b.relativePos().getZ()))
                .toList();
        List<BuildingEntityData> placedEntities = BuildingStructureService.resolvePlacedEntities(structure, task.origin(), task.rotationDegrees());
        return new CachedStructure(placedBlocks, sourceBlocks, placedEntities, buildLayerRanges(placedBlocks));
    }

    private static LevelRuntime runtime(ServerLevel level) {
        return LEVEL_RUNTIMES.computeIfAbsent(runtimeKey(level), ignored -> new LevelRuntime());
    }

    private static String runtimeKey(ServerLevel level) {
        return SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT);
    }

    private static String structureKey(BuildingTaskData task) {
        // 同一个建筑文件放在不同原点/旋转时，世界坐标不同，必须使用不同缓存键。
        String structureName = task.structureFileName() == null || task.structureFileName().isBlank()
                ? task.buildingFileName()
                : task.structureFileName();
        return task.category() + ":" + structureName + ":" + task.origin().toShortString() + ":" + task.rotationDegrees();
    }

    private static void syncCitizenTaskState(ServerLevel level, CitizenData citizen, TaskRuntime taskRuntime, BuildingTaskData task, CachedStructure cached) {
        if (level == null || citizen == null || task == null) {
            return;
        }
        String phaseKey;
        String statusLabel;
        String workNeedDetail;
        BuildingTaskStatus status = BuildingTaskStatus.from(task.status());
        // 状态文本同时写入持久化数据，客户端实体重载后也能恢复显示。
        if (status == BuildingTaskStatus.WAITING_MATERIALS) {
            String materialId = taskRuntime.missingMaterialName == null || taskRuntime.missingMaterialName.isBlank()
                    ? "unknown"
                    : taskRuntime.missingMaterialName;
            phaseKey = "missing:" + materialId;
            statusLabel = NpcWorkMaterialService.missingMaterialStatus(materialId);
            workNeedDetail = "build:" + task.taskId() + ":missing=" + materialId;
        } else if (status.isPaused()) {
            phaseKey = status.id();
            statusLabel = translatedStatusLabel(level,
                    status == BuildingTaskStatus.PAUSED_RESTING
                            ? "status.simukraft.builder.resting_night"
                            : "status.simukraft.builder.paused",
                    task.displayName());
            workNeedDetail = "build:" + task.taskId() + ":" + status.id();
        } else if (cached == null) {
            phaseKey = "loading";
            statusLabel = translatedStatusLabel(level, "status.simukraft.builder.preparing", task.displayName());
            workNeedDetail = "build:" + task.taskId() + ":loading";
        } else {
            LayerRange layerRange = resolveCurrentLayerRange(cached, task.currentBlockIndex());
            if (layerRange == null) {
                phaseKey = "finalizing";
                statusLabel = translatedStatusLabel(level, "status.simukraft.builder.finishing", task.displayName());
                workNeedDetail = "build:" + task.taskId() + ":finalizing";
            } else {
                int layerNumber = layerRange.layerIndex() + 1;
                int totalLayers = cached.layerRanges().size();
                phaseKey = "layer:" + layerNumber + "/" + totalLayers;
                statusLabel = translatedStatusLabel(level, "status.simukraft.builder.building_layer",
                        task.displayName(), layerNumber, totalLayers);
                workNeedDetail = "build:" + task.taskId() + ":layer=" + layerNumber + "/" + totalLayers;
            }
        }
        CitizenWorkStatus workStatus = status.isPaused() ? CitizenWorkStatus.RESTING : CitizenWorkStatus.WORKING;
        if (phaseKey.equals(taskRuntime.lastPhaseKey)
                && workStatus == citizen.workStatusType()
                && statusLabel.equals(citizen.statusLabel())
                && workNeedDetail.equals(citizen.workNeedDetail())) {
            return;
        }
        citizen.setWorkStatus(workStatus);
        citizen.setStatusLabel(statusLabel);
        citizen.setWorkNeedDetail(workNeedDetail);
        taskRuntime.lastPhaseKey = phaseKey;
        CitizenService.save(level, citizen.uuid());
    }

    // translatedStatusLabel: 服务端只保存翻译组件 JSON，客户端按当前语言渲染。
    private static String translatedStatusLabel(ServerLevel level, String translationKey, Object... args) {
        return Component.Serializer.toJson(Component.translatable(translationKey, args), level.registryAccess());
    }

    private static void markWaitingForMaterials(ServerLevel level, CitizenData citizen, TaskRuntime taskRuntime, BuildingTaskData task, WorkMaterialResult materialResult) {
        if (level.getGameTime() < taskRuntime.nextMaterialRetryTick && BuildingTaskStatus.from(task.status()) == BuildingTaskStatus.WAITING_MATERIALS) {
            syncCitizenTaskState(level, citizen, taskRuntime, task, null);
            return;
        }
        taskRuntime.materialCache.markDirty();
        BuildingTaskData waitingTask = task.withStatus(BuildingTaskStatus.WAITING_MATERIALS);
        taskRuntime.task = waitingTask;
        taskRuntime.dirty = true;
        taskRuntime.missingMaterialName = BuilderMaterialService.describe(materialResult.requested());
        taskRuntime.nextMaterialRetryTick = level.getGameTime() + MATERIAL_RETRY_INTERVAL_TICKS;
        syncCitizenTaskState(level, citizen, taskRuntime, waitingTask, null);
        WorkMaterialNotificationService.notifyMissing(level, task.cityId(), task.taskId(), task.displayName(), citizen.name(), materialResult);
        persistTask(level, taskRuntime, waitingTask);
    }

    private static List<LayerRange> buildLayerRanges(List<BuildingBlockData> blocks) {
        if (blocks.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<LayerRange> ranges = new java.util.ArrayList<>();
        int layerStartIndex = 0;
        int currentY = blocks.getFirst().relativePos().getY();
        int layerIndex = 0;
        for (int i = 1; i < blocks.size(); i++) {
            int blockY = blocks.get(i).relativePos().getY();
            if (blockY == currentY) {
                continue;
            }
            ranges.add(new LayerRange(layerIndex++, currentY, layerStartIndex, i - 1));
            currentY = blockY;
            layerStartIndex = i;
        }
        ranges.add(new LayerRange(layerIndex, currentY, layerStartIndex, blocks.size() - 1));
        return List.copyOf(ranges);
    }

    private static LayerRange resolveCurrentLayerRange(CachedStructure cached, int currentBlockIndex) {
        if (cached == null || cached.layerRanges().isEmpty()) {
            return null;
        }
        int index = Math.max(0, currentBlockIndex);
        for (LayerRange layerRange : cached.layerRanges()) {
            if (index <= layerRange.endIndex()) {
                return layerRange;
            }
        }
        return null;
    }

    private static BlockPos resolvePoiPosition(List<BuildingBlockData> sourceBlocks, BuildingPoiDefinition poi, BlockPos origin, int rotationDegrees) {
        if (poi.poiType() == CityPoiType.RESIDENTIAL) {
            for (BuildingBlockData block : sourceBlocks) {
                if (CitizenHomeRestService.isResidentialBedHead(block.state())) {
                    return origin.offset(BuildingTransform.rotatePosition(block.relativePos(), rotationDegrees));
                }
            }
        }
        String key = poi.id().toLowerCase(java.util.Locale.ROOT);
        for (BuildingBlockData block : sourceBlocks) {
            String blockId = BuiltInRegistries.BLOCK.getKey(block.state().getBlock()).toString().toLowerCase(java.util.Locale.ROOT);
            if (blockId.contains(key) || (poi.poiType() != CityPoiType.RESIDENTIAL && blockId.contains("control_box"))) {
                return origin.offset(BuildingTransform.rotatePosition(block.relativePos(), rotationDegrees));
            }
        }
        return origin;
    }

    private static List<BuildingPoiInstance> resolvePoiInstances(List<BuildingBlockData> sourceBlocks, List<BuildingBlockData> placedBlocks, BuildingTaskData task) {
        List<BuildingPoiDefinition> pois = task.poiDefinitions();
        List<BuildingPoiInstance> rewritten = new java.util.ArrayList<>();
        if (shouldRegisterResidentialBeds(task.category(), pois)) {
            rewritten.addAll(resolveResidentialBedInstances(placedBlocks, task.dimensionId()));
        }
        if (shouldRegisterMedicalBeds(task.category(), pois, placedBlocks)) {
            rewritten.addAll(resolveMedicalBedInstances(placedBlocks, task.dimensionId()));
        }
        for (BuildingPoiDefinition poi : pois) {
            if (poi.poiType() == CityPoiType.RESIDENTIAL || poi.poiType() == CityPoiType.MEDICAL) {
                continue;
            }
            BlockPos pos = resolvePoiPosition(sourceBlocks, poi, task.origin(), task.rotationDegrees());
            String key = UUID.nameUUIDFromBytes((task.dimensionId() + ":" + poi.id() + "@" + pos.toShortString()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            rewritten.add(new BuildingPoiInstance(key, poi.poiType(), poi.capacity(), pos));
        }
        return List.copyOf(rewritten);
    }

    private static boolean shouldRegisterResidentialBeds(String category, List<BuildingPoiDefinition> pois) {
        return isResidentialCategory(category)
                || pois.stream().anyMatch(poi -> poi.poiType() == CityPoiType.RESIDENTIAL);
    }

    /** pauseForMedicalLeave：医疗休假期间暂停任务但保留原岗位和任务进度。 */
    private static void pauseForMedicalLeave(ServerLevel level, CitizenData citizen, TaskRuntime taskRuntime) {
        BuildingTaskData task = taskRuntime.task;
        if (BuildingTaskStatus.from(task.status()) == BuildingTaskStatus.PAUSED_RESTING) {
            return;
        }
        BuildingTaskData paused = task.withStatus(BuildingTaskStatus.PAUSED_RESTING);
        taskRuntime.task = paused;
        taskRuntime.dirty = true;
        taskRuntime.lastPhaseKey = BuildingTaskStatus.PAUSED_RESTING.id();
        persistTask(level, taskRuntime, paused);
    }

    private static boolean shouldRegisterMedicalBeds(String category,
                                                      List<BuildingPoiDefinition> pois,
                                                      List<BuildingBlockData> placedBlocks) {
        return "medical".equalsIgnoreCase(category)
                || pois.stream().anyMatch(poi -> poi.poiType() == CityPoiType.MEDICAL)
                || placedBlocks.stream().anyMatch(block -> block.state().is(ModBlocks.MEDICAL_CONTROL_BOX.get()));
    }

    private static List<BuildingPoiInstance> resolveResidentialBedInstances(List<BuildingBlockData> placedBlocks, String dimensionId) {
        String scope = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
        return placedBlocks.stream()
                .filter(block -> CitizenHomeRestService.isResidentialBedHead(block.state()))
                .map(block -> block.relativePos().immutable())
                .distinct()
                .map(pos -> new BuildingPoiInstance(
                        UUID.nameUUIDFromBytes((scope + ":bed:" + pos.toShortString()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                        CityPoiType.RESIDENTIAL,
                        1,
                        pos
                ))
                .toList();
    }

    public static List<BuildingPoiInstance> resolveResidentialBedPois(PlacedBuildingRecord building) {
        if (building == null || !isResidentialCategory(building.category())) {
            return List.of();
        }
        return resolveResidentialBedInstances(building.blocks(), building.dimensionId());
    }

    /** resolveMedicalBedPois：从医疗建筑记录中重建白床医疗 POI。 */
    public static List<BuildingPoiInstance> resolveMedicalBedPois(PlacedBuildingRecord building) {
        if (!isMedicalBuildingRecord(building)) {
            return List.of();
        }
        return resolveMedicalBedInstances(building.blocks(), building.dimensionId());
    }

    /** isMedicalBuildingRecord：兼容旧医疗分类，并通过 POI 或控制箱识别公共医院。 */
    private static boolean isMedicalBuildingRecord(PlacedBuildingRecord building) {
        if (building == null) {
            return false;
        }
        return "medical".equalsIgnoreCase(building.category())
                || building.poiDefinitions().stream().anyMatch(poi -> poi.poiType() == CityPoiType.MEDICAL)
                || building.poiInstances().stream().anyMatch(poi -> poi.poiType() == CityPoiType.MEDICAL)
                || building.blocks().stream().anyMatch(block -> block.state().is(ModBlocks.MEDICAL_CONTROL_BOX.get()));
    }

    private static List<BuildingPoiInstance> resolveMedicalBedInstances(List<BuildingBlockData> placedBlocks, String dimensionId) {
        String scope = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
        return placedBlocks.stream()
                .filter(block -> MedicalBedPoiService.isWhiteBedHead(block.state()))
                .map(block -> block.relativePos().immutable())
                .distinct()
                .map(pos -> new BuildingPoiInstance(
                        UUID.nameUUIDFromBytes((scope + ":medical_bed:" + pos.toShortString()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                        CityPoiType.MEDICAL,
                        1,
                        pos
                ))
                .toList();
    }

    private static boolean isResidentialCategory(String category) {
        return "residential".equalsIgnoreCase(category);
    }

    private static UUID stablePoiId(BuildingPoiInstance poi) {
        try {
            return UUID.fromString(poi.key());
        } catch (IllegalArgumentException exception) {
            return UUID.nameUUIDFromBytes((poi.poiType().name() + "@" + poi.worldPos().toShortString()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private record CachedStructure(List<BuildingBlockData> blocks,
                                   List<BuildingBlockData> sourceBlocks,
                                   List<BuildingEntityData> entities,
                                   List<LayerRange> layerRanges) {
    }

    private static List<BuildingUnitDefinition> resolveUnitDefinitions(BuildingTaskData task) {
        BuildingCatalog.BuildingDefinition def = BuildingCatalog.findBuilding(task.category(), task.buildingFileName()).orElse(null);
        if (def == null) return List.of();
        return BuildingMetadataReader.readUnitDefinitions(def);
    }

    /** buildUnitInstances：将住宅床位 POI 按原始结构坐标归属到户型单元。 */
    static List<BuildingUnitInstance> buildUnitInstances(
            List<BuildingUnitDefinition> unitDefs,
            List<BuildingPoiInstance> poiInstances,
            BlockPos anchor,
            int rotationDegrees) {
        if (unitDefs.isEmpty()) return List.of();
        java.util.Map<String, List<java.util.UUID>> labelToPoiIds = new java.util.LinkedHashMap<>();
        for (BuildingUnitDefinition def : unitDefs) {
            labelToPoiIds.put(def.label(), new java.util.ArrayList<>());
        }
        for (BuildingPoiInstance poi : poiInstances) {
            if (poi.poiType() != common.cn.kafei.simukraft.city.poi.CityPoiType.RESIDENTIAL) continue;
            BlockPos relative = BuildingTransform.inverseRotatePosition(
                    poi.worldPos().subtract(anchor),
                    rotationDegrees
            );
            for (BuildingUnitDefinition def : unitDefs) {
                if (def.contains(relative)) {
                    labelToPoiIds.get(def.label()).add(stablePoiId(poi));
                    break;
                }
            }
        }
        return labelToPoiIds.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> new BuildingUnitInstance(java.util.UUID.randomUUID(), e.getKey(), java.util.List.copyOf(e.getValue())))
                .toList();
    }

    private record LayerRange(int layerIndex, int y, int startIndex, int endIndex) {
    }

    private static final class LevelRuntime {
        private final ConcurrentMap<UUID, TaskRuntime> tasksByCitizen = new ConcurrentHashMap<>();
        private volatile boolean hydrated;
        private volatile CompletableFuture<List<BuildingTaskData>> loadFuture;
    }

    private static final class TaskRuntime {
        private volatile BuildingTaskData task;
        private final WorkMaterialCache materialCache;
        private volatile boolean dirty;

        private volatile int lastSavedIndex;
        private volatile long lastSavedAt;
        private volatile String lastPhaseKey = "";
        private volatile String missingMaterialName = "";
        private volatile long nextMaterialRetryTick;
        private volatile long chestCloseAtTick;
        private volatile boolean workAreaTicketsLoaded;
        private double buildProgressAccumulator;
        private final AtomicInteger pendingBuilderXp = new AtomicInteger();

        private TaskRuntime(BuildingTaskData task) {
            this.task = task;
            this.materialCache = new WorkMaterialCache(task.buildBoxPos());
            this.lastSavedIndex = task.currentBlockIndex();
            this.lastSavedAt = task.updatedAt();
        }
    }

    private static final class BuilderIoThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "simukraft-builder-io-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
