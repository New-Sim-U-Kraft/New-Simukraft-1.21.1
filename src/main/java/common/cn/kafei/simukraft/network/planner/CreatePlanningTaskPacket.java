package common.cn.kafei.simukraft.network.planner;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.economy.FinanceLedgerService;
import common.cn.kafei.simukraft.job.CityJobMobilityService;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.planner.PlanOperation;
import common.cn.kafei.simukraft.planner.PlannerWorkService;
import common.cn.kafei.simukraft.planner.PlanningTaskData;
import common.cn.kafei.simukraft.planner.PlanningTaskStatus;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public record CreatePlanningTaskPacket(BlockPos buildBoxPos,
                                       BlockPos min,
                                       BlockPos max,
                                       PlanOperation operation,
                                       String fillBlockId,
                                       String sourceBlockId) implements CustomPacketPayload {
    public static final Type<CreatePlanningTaskPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "create_planning_task"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreatePlanningTaskPacket> STREAM_CODEC = StreamCodec.of(CreatePlanningTaskPacket::encode, CreatePlanningTaskPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CreatePlanningTaskPacket packet) {
        buffer.writeBlockPos(packet.buildBoxPos());
        buffer.writeBlockPos(packet.min());
        buffer.writeBlockPos(packet.max());
        buffer.writeEnum(packet.operation());
        buffer.writeUtf(packet.fillBlockId(), 128);
        buffer.writeUtf(packet.sourceBlockId(), 128);
    }

    public static CreatePlanningTaskPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CreatePlanningTaskPacket(buffer.readBlockPos(), buffer.readBlockPos(), buffer.readBlockPos(),
                buffer.readEnum(PlanOperation.class), buffer.readUtf(128), buffer.readUtf(128));
    }

    private static UUID workplaceId(BlockPos pos) {
        return UUID.nameUUIDFromBytes(("build_box:planner@" + pos.toShortString()).getBytes(StandardCharsets.UTF_8));
    }

    public static void handle(CreatePlanningTaskPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos boxPos = packet.buildBoxPos();
        if (!player.blockPosition().closerThan(boxPos, 24.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.build_box.too_far"));
            return;
        }
        if (!level.getBlockState(boxPos).is(ModBlocks.BUILD_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.not_found"));
            return;
        }
        UUID citizenId = CitizenService.findAssignedCitizen(level, workplaceId(boxPos));
        Optional<CitizenData> citizenOptional = citizenId != null ? CitizenService.findCitizen(level, citizenId) : Optional.empty();
        if (citizenOptional.isEmpty() || citizenOptional.get().dead() || citizenOptional.get().jobType() != CityJobType.PLANNER) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.plan_area.no_planner"));
            return;
        }
        CitizenData planner = citizenOptional.get();
        if (planner.cityId() == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.plan_area.no_planner"));
            return;
        }
        // 校验填充/替换所需的方块 id。
        if (packet.operation().needsFillBlock() && invalidBlock(packet.fillBlockId())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.plan_area.invalid_block"));
            return;
        }
        if (packet.operation().needsSourceBlock() && invalidBlock(packet.sourceBlockId())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.plan_area.invalid_block"));
            return;
        }
        BlockPos min = packet.min();
        BlockPos max = packet.max();
        int volume = PlanningTaskData.volume(min, max);
        if (volume <= 0 || volume > ServerConfig.plannerMaxVolume()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.plan_area.too_big", ServerConfig.plannerMaxVolume()));
            return;
        }
        // 按体积一次性预扣城市资金（计费可在配置 GUI 调整）。
        double cost = EconomyService.normalizeAmount(volume * ServerConfig.plannerMoneyPerBlock(packet.operation()));
        UUID cityId = planner.cityId();
        if (cost > 0.0D) {
            if (!EconomyService.canAfford(level, cityId, cost) || !CityService.withdrawFunds(level, cityId, cost)) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.plan_area.not_enough_funds", cost));
                return;
            }
            FinanceLedgerService.record(level, cityId, player, -cost, EconomyService.getCityBalance(level, cityId), FinanceTransactionData.Type.EXPENSE, "planner");
        }

        PlannerWorkService.cancelTask(level, planner.uuid());
        long now = System.currentTimeMillis();
        PlanningTaskData task = new PlanningTaskData(
                UUID.randomUUID(),
                planner.uuid(),
                cityId,
                level.dimension().location().toString(),
                boxPos,
                min,
                max,
                packet.operation(),
                packet.fillBlockId(),
                packet.sourceBlockId(),
                0,
                volume,
                PlanningTaskStatus.QUEUED.id(),
                now,
                now);
        PlannerWorkService.startTask(level, task);
        CitizenService.applyEmployment(level, planner.uuid(), CityJobType.PLANNER, workplaceId(boxPos), boxPos, "");
        CityJobMobilityService.teleportCitizenToWorkplace(level, planner.uuid(), boxPos, CityJobType.PLANNER, CitizenWorkStatus.WORKING, "");
        InfoToastService.success(player, Component.translatable("message.simukraft.plan_area.started", Component.translatable(packet.operation().translationKey()), volume));
    }

    private static boolean invalidBlock(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return true;
        }
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        return id == null || !BuiltInRegistries.BLOCK.containsKey(id);
    }
}
