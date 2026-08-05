package common.cn.kafei.simukraft.network.planner;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("null")
final class PlannerNetworkValidation {
    private static final int MAX_HORIZONTAL_DISTANCE_FROM_BOX = 96;
    private static final int MAX_VERTICAL_DISTANCE_FROM_BOX = 64;

    private PlannerNetworkValidation() {
    }

    static UUID workplaceId(BlockPos pos) {
        return CitizenEmploymentService.workplaceId("build_box", "planner", pos);
    }

    @Nullable
    static PlannerContext validatePlanner(ServerPlayer player, ServerLevel level, BlockPos boxPos) {
        if (!player.blockPosition().closerThan(boxPos, 24.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.build_box.too_far"));
            return null;
        }
        if (!level.getBlockState(boxPos).is(ModBlocks.BUILD_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.not_found"));
            return null;
        }
        UUID citizenId = CitizenService.findAssignedCitizen(level, workplaceId(boxPos));
        Optional<CitizenData> citizenOptional = citizenId != null ? CitizenService.findCitizen(level, citizenId) : Optional.empty();
        if (citizenOptional.isEmpty() || citizenOptional.get().dead() || citizenOptional.get().jobType() != CityJobType.PLANNER) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.plan_area.no_planner"));
            return null;
        }
        CitizenData planner = citizenOptional.get();
        if (planner.cityId() == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.plan_area.no_planner"));
            return null;
        }
        if (!CityService.canManageCity(level, planner.cityId(), player.getUUID())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.plan_area.no_permission"));
            return null;
        }
        return new PlannerContext(planner, planner.cityId());
    }

    static boolean selectionNearBuildBox(BlockPos boxPos, BlockPos min, BlockPos max) {
        int maxHorizontal = Math.max(distanceHorizontal(boxPos, min), distanceHorizontal(boxPos, max));
        int maxVertical = Math.max(Math.abs(boxPos.getY() - min.getY()), Math.abs(boxPos.getY() - max.getY()));
        return maxHorizontal <= MAX_HORIZONTAL_DISTANCE_FROM_BOX && maxVertical <= MAX_VERTICAL_DISTANCE_FROM_BOX;
    }

    static Set<BlockPos> adjacentContainers(ServerLevel level, BlockPos boxPos) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos candidate = boxPos.relative(direction);
            if (level.isLoaded(candidate) && GenericContainerAccess.isContainer(level, candidate)) {
                positions.add(GenericContainerAccess.canonicalContainerPos(level, candidate));
            }
        }
        return positions;
    }

    @Nullable
    static BlockPos validateAdjacentContainer(ServerLevel level, BlockPos boxPos, @Nullable BlockPos chestPos) {
        if (chestPos == null || !level.isLoaded(chestPos) || !GenericContainerAccess.isContainer(level, chestPos)) {
            return null;
        }
        BlockPos canonical = GenericContainerAccess.canonicalContainerPos(level, chestPos);
        return adjacentContainers(level, boxPos).contains(canonical) ? canonical : null;
    }

    static BlockPos min(BlockPos first, BlockPos second) {
        return new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
    }

    static BlockPos max(BlockPos first, BlockPos second) {
        return new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    private static int distanceHorizontal(BlockPos first, BlockPos second) {
        return Math.max(Math.abs(first.getX() - second.getX()), Math.abs(first.getZ() - second.getZ()));
    }

    record PlannerContext(CitizenData planner, UUID cityId) {
    }
}
