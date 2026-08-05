package common.cn.kafei.simukraft.farmland;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenHomeRestService;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.material.WorkContainerService;
import common.cn.kafei.simukraft.medical.MedicalService;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import common.cn.kafei.simukraft.registry.ModBlocks;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 农田盒耕作服务：按“挖水槽 -> 耕地 -> 播种 -> 收割”的阶段逐格推进。
 * NPC 必须先移动到目标格旁边，再由服务端提交对应方块变化，避免瞬间批量改田。
 */

@SuppressWarnings("null")
public final class FarmlandFarmingService {
    private static final ConcurrentMap<String, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();
    private static final long IDLE_INTERVAL_TICKS = 40L;
    private static final long MOVE_INTERVAL_TICKS = 8L;
    private static final String STATUS_MISSING_CHEST = "gui.simukraft.farmland.status.missing_chest";
    private static final String STATUS_MISSING_SEED = "gui.simukraft.farmland.status.missing_seed";
    private static final String STATUS_WAITING_GROWTH = "gui.simukraft.farmland.status.waiting_growth";

    private FarmlandFarmingService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        long gameTime = level.getGameTime();
        FarmlandBoxManager manager = FarmlandBoxManager.get(level);
        LevelRuntime runtime = runtime(level);
        for (FarmlandBoxData data : manager.all()) {
            if (!data.running()) {
                runtime.boxes.remove(data.boxPos());
                continue;
            }
            BoxRuntime boxRuntime = runtime.boxes.computeIfAbsent(data.boxPos(), pos -> new BoxRuntime());
            FarmlandFarmerVisualService.refresh(level, data, boxRuntime.visualTool, boxRuntime.visualActive);
            if (gameTime < boxRuntime.nextActionTick) {
                continue;
            }
            tickBox(level, manager, data, boxRuntime, gameTime);
        }
    }

    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server).toLowerCase(Locale.ROOT);
        RUNTIMES.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    private static void tickBox(ServerLevel level, FarmlandBoxManager manager, FarmlandBoxData data, BoxRuntime boxRuntime, long gameTime) {
        BlockPos boxPos = data.boxPos();
        if (!level.isLoaded(boxPos)) {
            idle(boxRuntime, gameTime);
            return;
        }
        if (!level.getBlockState(boxPos).is(ModBlocks.NSUK_FARMLAND_BOX.get())) {
            data.setRunning(false);
            manager.persist(data);
            clearActiveTarget(boxRuntime);
            return;
        }
        if (!data.isConfigured()) {
            data.setRunning(false);
            manager.persist(data);
            clearActiveTarget(boxRuntime);
            return;
        }

        CitizenData farmer = FarmlandBoxService.findAssignedFarmer(level, boxPos);
        if (farmer == null) {
            data.setRunning(false);
            manager.persist(data);
            clearActiveTarget(boxRuntime);
            return;
        }
        if (MedicalService.isOnMedicalLeave(farmer, level.getDayTime() / 24_000L)) {
            clearActiveTarget(boxRuntime);
            boxRuntime.setVisual(ItemStack.EMPTY, false);
            idle(boxRuntime, gameTime);
            return;
        }
        if (CitizenHomeRestService.isRestTime(level)) {
            clearActiveTarget(boxRuntime);
            boxRuntime.setVisual(ItemStack.EMPTY, false);
            idle(boxRuntime, gameTime);
            return;
        }
        if (CitizenSelfFeedingService.isSelfFeeding(level, farmer.uuid())) {
            boxRuntime.setVisual(ItemStack.EMPTY, false);
            idle(boxRuntime, gameTime);
            return;
        }
        CitizenEntity farmerEntity = CitizenTeleportService.findCitizenEntity(level, farmer.uuid());
        if (farmerEntity == null) {
            idle(boxRuntime, gameTime);
            return;
        }

        List<BlockPos> chestPositions = FarmlandBoxService.resolveAdjacentChests(level, boxPos);
        if (chestPositions.isEmpty()) {
            clearActiveTarget(boxRuntime);
            setFarmerStatus(level, farmer, boxRuntime, farmerStatusLabel(level, STATUS_MISSING_CHEST, data.crop()), CitizenWorkStatus.WORKING, "missing_chest");
            boxRuntime.setVisual(ItemStack.EMPTY, false);
            idle(boxRuntime, gameTime);
            return;
        }

        FarmlandWorkTarget target = resolveTarget(level, data, chestPositions, boxRuntime);
        if (target == null) {
            String phaseKey = hasSeedlessPlantingWork(level, data, chestPositions) ? "missing_seed" : "waiting_growth";
            String label = farmerStatusLabel(level, "missing_seed".equals(phaseKey) ? STATUS_MISSING_SEED : STATUS_WAITING_GROWTH, data.crop());
            setFarmerStatus(level, farmer, boxRuntime, label, CitizenWorkStatus.WORKING, phaseKey);
            boxRuntime.setVisual(ItemStack.EMPTY, false);
            idle(boxRuntime, gameTime);
            return;
        }

        setFarmerStatus(level, farmer, boxRuntime, farmerStatusLabel(level, target.phase().translationKey(), data.crop()), CitizenWorkStatus.WORKING, target.phase().id());
        boxRuntime.setVisual(FarmlandFarmerVisualService.toolFor(data.crop(), target.phase()), false);
        FarmlandFarmerVisualService.apply(farmerEntity, boxRuntime.visualTool, boxRuntime.visualActive);
        if (!isCloseEnoughToWork(level, boxPos, farmer, farmerEntity, target)) {
            boxRuntime.nextActionTick = gameTime + MOVE_INTERVAL_TICKS;
            return;
        }

        FarmlandWorkResult result = applyTarget(level, data, chestPositions, target);
        if (result == FarmlandWorkResult.PROCESSED) {
            farmerEntity.triggerWorkSwing(InteractionHand.MAIN_HAND);
            clearActiveTarget(boxRuntime);
            boxRuntime.nextActionTick = gameTime + Math.max(1, ServerConfig.farmWorkIntervalTicks());
        } else if (result == FarmlandWorkResult.WAITING_SEED) {
            clearActiveTarget(boxRuntime);
            setFarmerStatus(level, farmer, boxRuntime, farmerStatusLabel(level, STATUS_MISSING_SEED, data.crop()), CitizenWorkStatus.WORKING, "missing_seed");
            boxRuntime.nextActionTick = gameTime + IDLE_INTERVAL_TICKS;
        } else {
            clearActiveTarget(boxRuntime);
            boxRuntime.nextActionTick = gameTime + 1L;
        }
    }

    private static FarmlandWorkTarget resolveTarget(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions, BoxRuntime boxRuntime) {
        FarmlandWorkTarget active = boxRuntime.activeTarget;
        if (active != null && needsWork(level, data, chestPositions, active.phase(), active.cropPos())) {
            return active;
        }
        clearActiveTarget(boxRuntime);
        for (FarmlandWorkPhase phase : FarmlandWorkPhase.ORDERED) {
            if (phase == FarmlandWorkPhase.PLANT && !hasSeed(level, chestPositions, data.crop())) {
                continue;
            }
            if (phase == FarmlandWorkPhase.BONEMEAL && !hasBoneMeal(level, chestPositions)) {
                continue;
            }
            FarmlandWorkTarget target = findTarget(level, data, chestPositions, boxRuntime, phase);
            if (target != null) {
                boxRuntime.activeTarget = target;
                return target;
            }
        }
        return null;
    }

    private static FarmlandWorkTarget findTarget(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions, BoxRuntime boxRuntime, FarmlandWorkPhase phase) {
        FarmlandPlot plot = data.plot();
        int cells = scanCellCount(plot, phase);
        int start = boxRuntime.cursor(phase);
        for (int inspected = 0; inspected < cells; inspected++) {
            int index = start + inspected;
            BlockPos cropPos = scanCellAt(plot, phase, index);
            if (isSkippedBoxCell(data.boxPos(), cropPos) || !level.isLoaded(cropPos)) {
                continue;
            }
            if (needsWork(level, data, chestPositions, phase, cropPos)) {
                boxRuntime.setCursor(phase, index + 1);
                return new FarmlandWorkTarget(phase, cropPos.immutable());
            }
        }
        boxRuntime.setCursor(phase, start + cells);
        return null;
    }

    private static int scanCellCount(FarmlandPlot plot, FarmlandWorkPhase phase) {
        return usesGroupedFarmScan(phase) ? FarmlandWorkGeometry.groupedFarmCellCount(plot) : Math.max(1, plot.cellCount());
    }

    private static BlockPos scanCellAt(FarmlandPlot plot, FarmlandWorkPhase phase, int index) {
        return usesGroupedFarmScan(phase) ? FarmlandWorkGeometry.groupedFarmCellAt(plot, index) : plot.cellAt(index);
    }

    // usesGroupedFarmScan：这些阶段必须整块做完，再跨过水槽进入下一块。
    private static boolean usesGroupedFarmScan(FarmlandWorkPhase phase) {
        return phase == FarmlandWorkPhase.TILL || phase == FarmlandWorkPhase.PLANT
                || phase == FarmlandWorkPhase.BONEMEAL || phase == FarmlandWorkPhase.HARVEST;
    }

    private static boolean needsWork(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions, FarmlandWorkPhase phase, BlockPos cropPos) {
        return switch (phase) {
            case DIG_WATER -> needsWaterWork(level, data, chestPositions, cropPos);
            case TILL -> needsTillWork(level, data, cropPos);
            case PLANT -> needsPlantWork(level, data, cropPos);
            case BONEMEAL -> needsBonemealWork(level, data, chestPositions, cropPos);
            case HARVEST -> needsHarvestWork(level, data, cropPos);
        };
    }

    private static boolean needsWaterWork(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions, BlockPos cropPos) {
        FarmlandPlot plot = data.plot();
        if (!FarmlandWorkGeometry.isWaterTrough(plot, cropPos)) {
            return false;
        }
        BlockPos soilPos = cropPos.below();
        if (isProtected(level, cropPos, level.getBlockState(cropPos), data.boxPos(), chestPositions)
                || isProtected(level, soilPos, level.getBlockState(soilPos), data.boxPos(), chestPositions)) {
            return false;
        }
        BlockState cropState = level.getBlockState(cropPos);
        BlockState soilState = level.getBlockState(soilPos);
        return !cropState.isAir() || !hasWater(soilState);
    }

    private static boolean needsTillWork(ServerLevel level, FarmlandBoxData data, BlockPos cropPos) {
        FarmCrop crop = data.crop();
        if (FarmlandWorkGeometry.isWaterTrough(data.plot(), cropPos) || crop == null || !crop.shouldPlantAt(cropPos.getX(), cropPos.getZ())) {
            return false;
        }
        BlockState cropState = level.getBlockState(cropPos);
        BlockState soilState = level.getBlockState(cropPos.below());
        return isCropCellFree(cropState) && isTillable(soilState);
    }

    private static boolean needsPlantWork(ServerLevel level, FarmlandBoxData data, BlockPos cropPos) {
        FarmCrop crop = data.crop();
        if (FarmlandWorkGeometry.isWaterTrough(data.plot(), cropPos) || crop == null || !crop.shouldPlantAt(cropPos.getX(), cropPos.getZ())) {
            return false;
        }
        BlockState cropState = level.getBlockState(cropPos);
        BlockState soilState = level.getBlockState(cropPos.below());
        return isCropCellFree(cropState) && soilState.is(Blocks.FARMLAND);
    }

    private static boolean needsHarvestWork(ServerLevel level, FarmlandBoxData data, BlockPos cropPos) {
        FarmCrop crop = data.crop();
        if (crop == null || FarmlandWorkGeometry.isWaterTrough(data.plot(), cropPos)) {
            return false;
        }
        BlockState cropState = level.getBlockState(cropPos);
        if (crop.isStem()) {
            return crop.isOwnPlant(cropState) && hasProduceAround(level, crop, cropPos);
        }
        return crop.isOwnPlant(cropState) && crop.isMatureFull(cropState);
    }

    private static FarmlandWorkResult applyTarget(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions, FarmlandWorkTarget target) {
        return switch (target.phase()) {
            case DIG_WATER -> applyWaterWork(level, data, chestPositions, target.cropPos());
            case TILL -> applyTillWork(level, data, target.cropPos());
            case PLANT -> applyPlantWork(level, data, chestPositions, target.cropPos());
            case BONEMEAL -> applyBonemealWork(level, data, chestPositions, target.cropPos());
            case HARVEST -> applyHarvestWork(level, data, chestPositions, target.cropPos());
        };
    }

    private static FarmlandWorkResult applyWaterWork(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions, BlockPos cropPos) {
        if (!needsWaterWork(level, data, chestPositions, cropPos)) {
            return FarmlandWorkResult.SKIPPED;
        }
        BlockPos soilPos = cropPos.below();
        BlockState cropState = level.getBlockState(cropPos);
        if (!cropState.isAir()) {
            harvestBlock(level, chestPositions, cropPos, cropState);
        }
        BlockState soilState = level.getBlockState(soilPos);
        if (!hasWater(soilState)) {
            if (!soilState.isAir()) {
                harvestBlock(level, chestPositions, soilPos, soilState);
            }
            level.setBlock(soilPos, Blocks.WATER.defaultBlockState(), 3);
        }
        return FarmlandWorkResult.PROCESSED;
    }

    private static boolean hasWater(BlockState state) {
        if (state.is(Blocks.WATER)) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return state.getValue(BlockStateProperties.WATERLOGGED);
        }
        return false;
    }

    private static FarmlandWorkResult applyTillWork(ServerLevel level, FarmlandBoxData data, BlockPos cropPos) {
        if (!needsTillWork(level, data, cropPos)) {
            return FarmlandWorkResult.SKIPPED;
        }
        level.setBlock(cropPos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
        return FarmlandWorkResult.PROCESSED;
    }

    private static FarmlandWorkResult applyPlantWork(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions, BlockPos cropPos) {
        if (!needsPlantWork(level, data, cropPos)) {
            return FarmlandWorkResult.SKIPPED;
        }
        FarmCrop crop = data.crop();
        if (!consumeSeed(level, chestPositions, crop)) {
            return FarmlandWorkResult.WAITING_SEED;
        }
        level.setBlock(cropPos, crop.plantState(), 3);
        return FarmlandWorkResult.PROCESSED;
    }

    private static FarmlandWorkResult applyHarvestWork(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions, BlockPos cropPos) {
        if (!needsHarvestWork(level, data, cropPos)) {
            return FarmlandWorkResult.SKIPPED;
        }
        FarmCrop crop = data.crop();
        BlockState cropState = level.getBlockState(cropPos);
        if (crop.isStem()) {
            return harvestProduceAround(level, chestPositions, crop, cropPos) ? FarmlandWorkResult.PROCESSED : FarmlandWorkResult.SKIPPED;
        }
        harvestBlock(level, chestPositions, cropPos, cropState);
        replantAfterHarvest(level, chestPositions, crop, cropPos);
        return FarmlandWorkResult.PROCESSED;
    }

    private static void replantAfterHarvest(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop, BlockPos cropPos) {
        if (crop == null || crop.isStem() || !crop.shouldPlantAt(cropPos.getX(), cropPos.getZ())) {
            return;
        }
        BlockState cropState = level.getBlockState(cropPos);
        BlockState soilState = level.getBlockState(cropPos.below());
        if (isCropCellFree(cropState) && soilState.is(Blocks.FARMLAND) && consumeSeed(level, chestPositions, crop)) {
            level.setBlock(cropPos, crop.plantState(), 3);
        }
    }

    private static boolean isCloseEnoughToWork(ServerLevel level, BlockPos boxPos, CitizenData farmer, CitizenEntity farmerEntity, FarmlandWorkTarget target) {
        Vec3 targetCenter = Vec3.atCenterOf(target.cropPos());
        if (farmerEntity.position().distanceToSqr(targetCenter) <= FarmlandWorkGeometry.ACTION_REACH * FarmlandWorkGeometry.ACTION_REACH) {
            return true;
        }
        Vec3 anchor = FarmlandWorkGeometry.workAnchorFor(level, boxPos, target.cropPos());
        CitizenNavigationService.requestMove(level, farmer.uuid(), anchor, MovementIntent.WORK);
        return false;
    }

    private static boolean hasSeedlessPlantingWork(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions) {
        if (hasSeed(level, chestPositions, data.crop())) {
            return false;
        }
        FarmlandPlot plot = data.plot();
        int cells = Math.max(1, plot.cellCount());
        for (int index = 0; index < cells; index++) {
            BlockPos cropPos = plot.cellAt(index);
            if (!isSkippedBoxCell(data.boxPos(), cropPos) && level.isLoaded(cropPos) && needsPlantWork(level, data, cropPos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean needsBonemealWork(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions, BlockPos cropPos) {
        FarmCrop crop = data.crop();
        if (crop == null || FarmlandWorkGeometry.isWaterTrough(data.plot(), cropPos)) return false;
        BlockState state = level.getBlockState(cropPos);
        if (!crop.isOwnPlant(state) || crop.isMatureFull(state)) return false;
        return state.getBlock() instanceof BonemealableBlock b
                && b.isValidBonemealTarget(level, cropPos, state)
                && hasBoneMeal(level, chestPositions);
    }

    private static FarmlandWorkResult applyBonemealWork(ServerLevel level, FarmlandBoxData data, List<BlockPos> chestPositions, BlockPos cropPos) {
        if (!needsBonemealWork(level, data, chestPositions, cropPos)) return FarmlandWorkResult.SKIPPED;
        if (!consumeBoneMeal(level, chestPositions)) return FarmlandWorkResult.SKIPPED;
        BlockState state = level.getBlockState(cropPos);
        ((BonemealableBlock) state.getBlock()).performBonemeal(level, level.getRandom(), cropPos, state);
        return FarmlandWorkResult.PROCESSED;
    }

    private static boolean hasBoneMeal(ServerLevel level, List<BlockPos> chestPositions) {
        return WorkContainerService.hasItem(level, chestPositions, Items.BONE_MEAL);
    }

    private static boolean consumeBoneMeal(ServerLevel level, List<BlockPos> chestPositions) {
        return WorkContainerService.consumeItem(level, chestPositions, Items.BONE_MEAL);
    }

    private static boolean hasSeed(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop) {
        if (crop == null) {
            return false;
        }
        return WorkContainerService.hasItem(level, chestPositions, crop.seed());
    }

    private static boolean consumeSeed(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop) {
        if (crop == null) {
            return false;
        }
        return WorkContainerService.consumeItem(level, chestPositions, crop.seed());
    }

    private static void harvestBlock(ServerLevel level, List<BlockPos> chestPositions, BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        depositDrops(level, chestPositions, drops, pos);
    }

    private static boolean harvestProduceAround(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop, BlockPos stemPos) {
        boolean harvested = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos producePos = stemPos.relative(direction);
            if (!level.isLoaded(producePos)) {
                continue;
            }
            BlockState produceState = level.getBlockState(producePos);
            if (crop.isProduce(produceState)) {
                harvestBlock(level, chestPositions, producePos, produceState);
                harvested = true;
            }
        }
        return harvested;
    }

    private static boolean hasProduceAround(ServerLevel level, FarmCrop crop, BlockPos stemPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos producePos = stemPos.relative(direction);
            if (level.isLoaded(producePos) && crop.isProduce(level.getBlockState(producePos))) {
                return true;
            }
        }
        return false;
    }

    private static void depositDrops(ServerLevel level, List<BlockPos> chestPositions, List<ItemStack> drops, BlockPos fallbackPos) {
        WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, fallbackPos);
    }

    private static void setFarmerStatus(ServerLevel level, CitizenData farmer, BoxRuntime boxRuntime, String label, CitizenWorkStatus status, String phaseKey) {
        String safeLabel = label != null ? label : "";
        String detail = "farm:" + phaseKey;
        if (safeLabel.equals(boxRuntime.lastStatusLabel)
                && detail.equals(farmer.workNeedDetail())
                && farmer.workStatusType() == status) {
            return;
        }
        boxRuntime.lastStatusLabel = safeLabel;
        farmer.setWorkStatus(status);
        farmer.setStatusLabel(safeLabel);
        farmer.setWorkNeedDetail(detail);
        CitizenService.save(level, farmer.uuid());
    }

    // farmerStatusLabel：把服务端农民状态保存为可翻译组件 JSON，由客户端语言文件决定显示文本。
    private static String farmerStatusLabel(ServerLevel level, String translationKey, FarmCrop crop) {
        Component cropName = crop != null
                ? Component.translatable(crop.translationKey())
                : Component.translatable("gui.simukraft.farmland_box.none");
        return Component.Serializer.toJson(Component.translatable(translationKey, cropName), level.registryAccess());
    }

    private static boolean isCropCellFree(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    private static boolean isTillable(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT);
    }

    private static boolean isSkippedBoxCell(BlockPos boxPos, BlockPos cropPos) {
        return cropPos.getX() == boxPos.getX() && cropPos.getZ() == boxPos.getZ();
    }

    private static boolean isProtected(ServerLevel level, BlockPos pos, BlockState state, BlockPos boxPos, List<BlockPos> chestPositions) {
        if (pos.equals(boxPos) || chestPositions.contains(pos)) {
            return true;
        }
        if (state.is(Blocks.BEDROCK)) {
            return true;
        }
        if (state.is(ModBlocks.NSUK_FARMLAND_BOX.get()) || state.is(ModBlocks.BUILD_BOX.get()) || state.is(ModBlocks.CITY_CORE.get())) {
            return true;
        }
        return GenericContainerAccess.isContainer(level, pos);
    }

    private static void idle(BoxRuntime boxRuntime, long gameTime) {
        boxRuntime.nextActionTick = gameTime + IDLE_INTERVAL_TICKS;
    }

    private static void clearActiveTarget(BoxRuntime boxRuntime) {
        boxRuntime.activeTarget = null;
    }

    private static LevelRuntime runtime(ServerLevel level) {
        return RUNTIMES.computeIfAbsent(runtimeKey(level), ignored -> new LevelRuntime());
    }

    private static String runtimeKey(ServerLevel level) {
        return SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT);
    }

    private static final class LevelRuntime {
        private final ConcurrentMap<BlockPos, BoxRuntime> boxes = new ConcurrentHashMap<>();
    }

    private static final class BoxRuntime {
        private final int[] cursors = new int[FarmlandWorkPhase.values().length];
        private volatile FarmlandWorkTarget activeTarget;
        private long nextActionTick;
        private String lastStatusLabel = "";
        private ItemStack visualTool = ItemStack.EMPTY;
        private boolean visualActive;

        private int cursor(FarmlandWorkPhase phase) {
            return cursors[phase.ordinal()];
        }
        private void setCursor(FarmlandWorkPhase phase, int cursor) {
            cursors[phase.ordinal()] = cursor;
        }
        private void setVisual(ItemStack tool, boolean active) {
            visualTool = FarmlandFarmerVisualService.normalize(tool);
            visualActive = active;
        }
    }
}
