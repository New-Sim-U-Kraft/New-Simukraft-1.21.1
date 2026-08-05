package common.cn.kafei.simukraft.event;

import common.cn.kafei.simukraft.building.MedicalBedPoiService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.ResidentialBedPoiService;
import common.cn.kafei.simukraft.building.controlbox.ResidentialControlBoxService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.poi.CityPoiService;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxService;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.farmland.FarmlandBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.medical.MedicalControlBoxService;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingControlBoxService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public final class CityPlacementRestrictionHandler {
    private CityPlacementRestrictionHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        ItemStack itemStack = event.getItemStack();
        if (!(itemStack.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        Direction face = event.getFace();
        if (face == null) {
            return;
        }
        BlockPos targetPos = resolveTargetPos(event.getPos(), face, level);
        if (!shouldBlockPlacement(level, targetPos, blockItem.getBlock(), event.getEntity())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Block block = event.getPlacedBlock().getBlock();
        Level level = (Level) event.getLevel();
        if (shouldBlockPlacement(level, event.getPos(), block, player)) {
            event.setCanceled(true);
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                serverLevel.removeBlock(event.getPos(), false);
            }
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            ResidentialBedPoiService.handleBlockPlaced(serverLevel, event.getPos(), event.getPlacedBlock());
            MedicalBedPoiService.handleBlockPlaced(serverLevel, event.getPos(), event.getPlacedBlock());
            registerPoiForPlacedBlock(serverLevel, event.getPos(), block, player);
        }
    }

    @SubscribeEvent
    public static void onMultiBlockPlaced(BlockEvent.EntityMultiPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Level level = (Level) event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (net.neoforged.neoforge.common.util.BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
            BlockState placedState = snapshot.getCurrentState();
            Block block = placedState.getBlock();
            if (shouldBlockPlacement(level, snapshot.getPos(), block, player)) {
                event.setCanceled(true);
                return;
            }
            ResidentialBedPoiService.handleBlockPlaced(serverLevel, snapshot.getPos(), placedState);
            MedicalBedPoiService.handleBlockPlaced(serverLevel, snapshot.getPos(), placedState);
            registerPoiForPlacedBlock(serverLevel, snapshot.getPos(), block, player);
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState brokenState = serverLevel.getBlockState(pos);
        Block block = brokenState.getBlock();
        // 权限拦截：控制箱方块需要官员及以上才可直接破坏
        if (isBuildingControlBox(block) && !isDemolishAllowed(serverLevel, pos, block, event.getPlayer())) {
            event.setCanceled(true);
            return;
        }
        ResidentialBedPoiService.handleBlockBroken(serverLevel, pos, brokenState);
        MedicalBedPoiService.handleBlockBroken(serverLevel, pos, brokenState);
        if (poiTypeForBlock(block).isPresent()) {
            CityPoiService.deactivatePoi(serverLevel, pos);
        }
    }

    private static boolean isBuildingControlBox(Block block) {
        return block == ModBlocks.RESIDENTIAL_CONTROL_BOX.get()
                || block == ModBlocks.COMMERCIAL_CONTROL_BOX.get()
                || block == ModBlocks.INDUSTRIAL_CONTROL_BOX.get()
                || block == ModBlocks.MINERAL_DRILLING_CONTROL_BOX.get()
                || block == ModBlocks.MEDICAL_CONTROL_BOX.get()
                || block == ModBlocks.NSUK_FARMLAND_BOX.get();
    }

    private static boolean isDemolishAllowed(ServerLevel level, BlockPos pos, Block block, Player player) {
        if (player.isCreative() || player.hasPermissions(2)) {
            return true;
        }
        UUID cityId = resolveBuildingCityId(level, pos, block);
        if (cityId == null || !CityService.canManageCity(level, cityId, player.getUUID())) {
            if (player instanceof ServerPlayer serverPlayer) {
                InfoToastService.warning(serverPlayer, Component.translatable("message.simukraft.no_permission"));
            }
            return false;
        }
        return true;
    }

    private static UUID resolveBuildingCityId(ServerLevel level, BlockPos pos, Block block) {
        if (block == ModBlocks.NSUK_FARMLAND_BOX.get()) {
            return FarmlandBoxService.cityIdFor(level, pos);
        }
        PlacedBuildingRecord building = null;
        if (block == ModBlocks.RESIDENTIAL_CONTROL_BOX.get()) {
            building = ResidentialControlBoxService.findBuilding(level, pos);
        } else if (block == ModBlocks.COMMERCIAL_CONTROL_BOX.get()) {
            building = CommercialControlBoxService.resolveBuilding(level, pos);
        } else if (block == ModBlocks.INDUSTRIAL_CONTROL_BOX.get()) {
            building = IndustrialControlBoxService.resolveBuilding(level, pos);
        } else if (block == ModBlocks.MINERAL_DRILLING_CONTROL_BOX.get()) {
            building = MineralDrillingControlBoxService.resolveBuilding(level, pos);
        } else if (block == ModBlocks.MEDICAL_CONTROL_BOX.get()) {
            building = MedicalControlBoxService.resolveBuilding(level, pos);
        }
        return building != null ? building.cityId() : null;
    }

    private static BlockPos resolveTargetPos(BlockPos clickedPos, Direction face, Level level) {
        BlockState clickedState = level.getBlockState(clickedPos);
        return clickedState.canBeReplaced() ? clickedPos : clickedPos.relative(face);
    }

    private static boolean shouldBlockPlacement(Level level, BlockPos targetPos, Block block, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (player.isCreative()) {
            return false;
        }
        if (!isRestrictedBlock(block)) {
            return false;
        }
        if (!ServerConfig.claimProtectionEnabled()) {
            return false;
        }
        return getPlacementCity(serverLevel, targetPos, player).isEmpty();
    }

    private static Optional<CityData> getPlacementCity(ServerLevel serverLevel, BlockPos targetPos, Player player) {
        Optional<CityData> playerCity = CityService.findPlayerCity(serverLevel, player.getUUID());
        if (playerCity.isEmpty()) {
            sendDeniedMessage(player);
            return Optional.empty();
        }
        UUID ownerCityId = CityChunkManager.get(serverLevel).getChunkOwner(new ChunkPos(targetPos).toLong());
        if (playerCity.get().cityId().equals(ownerCityId)) {
            return playerCity;
        }
        sendDeniedMessage(player);
        return Optional.empty();
    }

    private static void registerPoiForPlacedBlock(ServerLevel level, BlockPos pos, Block block, Player player) {
        Optional<CityPoiType> poiType = poiTypeForBlock(block);
        if (poiType.isEmpty()) {
            return;
        }
        getPlacementCity(level, pos, player).ifPresent(city -> CityPoiService.registerPoi(level, city.cityId(), pos, poiType.get(), poiCapacity(poiType.get())));
    }

    private static Optional<CityPoiType> poiTypeForBlock(Block block) {
        if (block == ModBlocks.RESIDENTIAL_CONTROL_BOX.get()) {
            return Optional.empty();
        }
        if (block == ModBlocks.COMMERCIAL_CONTROL_BOX.get()) {
            return Optional.of(CityPoiType.COMMERCIAL);
        }
        if (block == ModBlocks.INDUSTRIAL_CONTROL_BOX.get()) {
            return Optional.of(CityPoiType.INDUSTRIAL);
        }
        if (block == ModBlocks.MINERAL_DRILLING_CONTROL_BOX.get()) {
            return Optional.of(CityPoiType.INDUSTRIAL);
        }
        if (block == ModBlocks.MEDICAL_CONTROL_BOX.get()) {
            return Optional.of(CityPoiType.MEDICAL);
        }
        if (block == ModBlocks.OTHER_CONTROL_BOX.get()) {
            return Optional.of(CityPoiType.OTHER);
        }
        if (block == ModBlocks.NSUK_FARMLAND_BOX.get()) {
            return Optional.of(CityPoiType.FARMLAND);
        }
        if (block == ModBlocks.LOGISTICS_SERVER_BOX.get()) {
            return Optional.of(CityPoiType.STORAGE);
        }
        if (block == ModBlocks.LOGISTICS_CLIENT_BOX.get()) {
            return Optional.of(CityPoiType.LOGISTICS);
        }
        return Optional.empty();
    }

    private static int poiCapacity(CityPoiType type) {
        return switch (type) {
            case RESIDENTIAL -> 0;
            case COMMERCIAL, INDUSTRIAL, FARMLAND -> 2;
            case MEDICAL, LOGISTICS, STORAGE, GATHERING, DEFENSE, OTHER -> 1;
        };
    }

    private static boolean isRestrictedBlock(Block block) {
        return block == ModBlocks.BUILD_BOX.get()
                || block == ModBlocks.NSUK_FARMLAND_BOX.get()
                || block == ModBlocks.LOGISTICS_SERVER_BOX.get()
                || block == ModBlocks.LOGISTICS_CLIENT_BOX.get()
                || block == ModBlocks.RESIDENTIAL_CONTROL_BOX.get()
                || block == ModBlocks.COMMERCIAL_CONTROL_BOX.get()
                || block == ModBlocks.INDUSTRIAL_CONTROL_BOX.get()
                || block == ModBlocks.MINERAL_DRILLING_CONTROL_BOX.get()
                || block == ModBlocks.MEDICAL_CONTROL_BOX.get()
                || block == ModBlocks.OTHER_CONTROL_BOX.get();
    }

    private static void sendDeniedMessage(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            InfoToastService.warning(serverPlayer, Component.translatable("message.simukraft.city_placement.outside_city"));
        }
    }
}
