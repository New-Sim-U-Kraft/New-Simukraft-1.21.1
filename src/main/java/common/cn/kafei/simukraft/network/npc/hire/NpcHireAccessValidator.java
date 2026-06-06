package common.cn.kafei.simukraft.network.npc.hire;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.commercial.CommercialConstants;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxService;
import common.cn.kafei.simukraft.farmland.FarmlandBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialConstants;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

@SuppressWarnings("null")
final class NpcHireAccessValidator {
    private static final String BUILD_BOX_SOURCE_TYPE = "build_box";
    private static final String BUILDER_ROLE = "builder";
    private static final String PLANNER_ROLE = "planner";

    private NpcHireAccessValidator() {
    }

    /** validateSource: 校验雇佣来源方块、城市归属和操作者管理权限。 */
    @Nullable
    static SourceContext validateSource(ServerPlayer player, ServerLevel level, BlockPos sourcePos, String sourceType, String role) {
        String normalizedSource = normalize(sourceType);
        String normalizedRole = normalize(role);
        if (sourcePos == null || normalizedSource.isBlank() || normalizedRole.isBlank()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.invalid_source"));
            return null;
        }
        if (!player.blockPosition().closerThan(sourcePos, 16.0D)) {
            InfoToastService.warning(player, Component.translatable(tooFarMessage(normalizedSource)));
            return null;
        }
        UUID cityId = resolveCityId(level, sourcePos, normalizedSource, normalizedRole);
        if (cityId == null || !CityService.canManageCity(level, cityId, player.getUUID())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.no_permission"));
            return null;
        }
        return new SourceContext(sourcePos.immutable(), normalizedSource, normalizedRole, cityId);
    }

    /** isHireCandidateForSource: 只展示同城且空闲的候选 NPC。 */
    static boolean isHireCandidateForSource(CitizenData citizen, SourceContext source) {
        return CitizenService.isHireable(citizen) && belongsToSourceCity(citizen, source);
    }

    /** canAssignCitizen: 服务端执行雇佣前再次验证 NPC 和岗位状态。 */
    static boolean canAssignCitizen(ServerPlayer player, ServerLevel level, SourceContext source, CitizenData citizen) {
        if (!CitizenService.isHireable(citizen)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.unavailable", citizen.name()));
            return false;
        }
        if (!belongsToSourceCity(citizen, source)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.wrong_city", citizen.name()));
            return false;
        }
        if (hasAssignedWorker(level, source)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.occupied"));
            return false;
        }
        return true;
    }

    /** canFireCitizen: 解雇必须针对该来源岗位已绑定的 NPC。 */
    static boolean canFireCitizen(ServerPlayer player, SourceContext source, CitizenData citizen) {
        if (citizen.dead() || citizen.child()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.fire_npc.unavailable", citizen.name()));
            return false;
        }
        if (!belongsToSourceCity(citizen, source) || !isAssignedToSource(citizen, source)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.fire_npc.unavailable", citizen.name()));
            return false;
        }
        return true;
    }

    private static UUID resolveCityId(ServerLevel level, BlockPos sourcePos, String sourceType, String role) {
        if (BUILD_BOX_SOURCE_TYPE.equals(sourceType) && isBuildBoxRole(role) && level.getBlockState(sourcePos).is(ModBlocks.BUILD_BOX.get())) {
            return CityChunkManager.get(level).getChunkOwner(new ChunkPos(sourcePos).toLong());
        }
        if (FarmlandBoxService.HIRE_SOURCE_TYPE.equals(sourceType)
                && FarmlandBoxService.HIRE_ROLE.equals(role)
                && level.getBlockState(sourcePos).is(ModBlocks.NSUK_FARMLAND_BOX.get())) {
            return FarmlandBoxService.cityIdFor(level, sourcePos);
        }
        if (IndustrialConstants.HIRE_SOURCE_TYPE.equals(sourceType)
                && IndustrialConstants.HIRE_ROLE.equals(role)
                && level.getBlockState(sourcePos).is(ModBlocks.INDUSTRIAL_CONTROL_BOX.get())) {
            PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, sourcePos);
            return building != null ? building.cityId() : null;
        }
        if (CommercialConstants.HIRE_SOURCE_TYPE.equals(sourceType)
                && CommercialConstants.HIRE_ROLE.equals(role)
                && level.getBlockState(sourcePos).is(ModBlocks.COMMERCIAL_CONTROL_BOX.get())) {
            PlacedBuildingRecord building = CommercialControlBoxService.resolveBuilding(level, sourcePos);
            return building != null ? building.cityId() : null;
        }
        return null;
    }

    private static boolean hasAssignedWorker(ServerLevel level, SourceContext source) {
        if (BUILD_BOX_SOURCE_TYPE.equals(source.sourceType())) {
            return CitizenService.findAssignedCitizen(level, CitizenEmploymentService.workplaceId(source.sourceType(), BUILDER_ROLE, source.sourcePos())) != null
                    || CitizenService.findAssignedCitizen(level, CitizenEmploymentService.workplaceId(source.sourceType(), PLANNER_ROLE, source.sourcePos())) != null;
        }
        return CitizenService.findAssignedCitizen(level, CitizenEmploymentService.workplaceId(source.sourceType(), source.role(), source.sourcePos())) != null;
    }

    private static boolean isAssignedToSource(CitizenData citizen, SourceContext source) {
        UUID expectedWorkplaceId = CitizenEmploymentService.workplaceId(source.sourceType(), source.role(), source.sourcePos());
        boolean samePosition = citizen.workplacePos() == null || source.sourcePos().equals(citizen.workplacePos());
        return expectedWorkplaceId.equals(citizen.workplaceId()) && samePosition;
    }

    private static boolean belongsToSourceCity(CitizenData citizen, SourceContext source) {
        return citizen != null && source != null && source.cityId().equals(citizen.cityId());
    }

    private static boolean isBuildBoxRole(String role) {
        return BUILDER_ROLE.equals(role) || PLANNER_ROLE.equals(role);
    }

    private static String tooFarMessage(String sourceType) {
        if (FarmlandBoxService.HIRE_SOURCE_TYPE.equals(sourceType)) {
            return "message.simukraft.farmland_box.too_far";
        }
        if (IndustrialConstants.HIRE_SOURCE_TYPE.equals(sourceType)) {
            return "message.simukraft.industrial_control_box.too_far";
        }
        if (CommercialConstants.HIRE_SOURCE_TYPE.equals(sourceType)) {
            return "message.simukraft.commercial_control_box.too_far";
        }
        return "message.simukraft.build_box.too_far";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    record SourceContext(BlockPos sourcePos, String sourceType, String role, UUID cityId) {
    }
}
