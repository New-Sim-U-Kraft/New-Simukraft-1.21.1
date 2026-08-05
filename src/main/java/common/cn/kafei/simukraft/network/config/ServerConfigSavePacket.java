package common.cn.kafei.simukraft.network.config;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenNameStyle;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.material.WorkMaterialPolicy;
import common.cn.kafei.simukraft.protection.NpcBlockProtectionPolicy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record ServerConfigSavePacket(
        double cityChunkPrice,
        boolean blacklistProtection,
        boolean logBlacklistSkippedBlocks,
        boolean claimProtection,
        int populationGrowthIntervalTicks,
        int populationGrowthMaxPerInterval,
        int populationGrowthTimesPerWeek,
        CitizenNameStyle npcNameStyle,
        int familyPregnancyDurationDays,
        int familyPostpartumRecoveryDays,
        double familyMarriageChancePerDay,
        double familyPregnancyChancePerDay,
        double medicalLowHealthThreshold,
        int medicalHealIntervalTicks,
        double medicalHealAmount,
        double medicalDiseaseChancePerDay,
        int medicalDiseaseTreatmentTicks,
        int farmAreaRadius,
        int farmWorkIntervalTicks,
        int farmActionsPerCycle,
        int pathMaxLoadedCitizenEntities,
        int pathMaxActiveCitizens,
        int pathMaxNewRequestsPerTick,
        int pathWorkerThreads,
        int pathLocalRadiusBlocks,
        int pathFarMovementTeleportDistance,
        int pathRepathCooldownTicks,
        int pathCacheTtlTicks,
        boolean pathDebug,
        int buildingIntegrityAutoDemolishThresholdPercent,
        int buildingIntegrityCheckIntervalTicks,
        double buildingIntegrityRepairMoneyPerBlock,
        int npcMaxLevel,
        boolean builderXpGain,
        int builderXpPerBlock,
        boolean plannerXpGain,
        int plannerXpPerBlock,
        double plannerBlocksPerSecond,
        int plannerMaxVolume,
        double plannerMoneyPerBlockRemove,
        double plannerMoneyPerBlockFill,
        double plannerMoneyPerBlockReplace,
        boolean plannerPauseAtNight,
        double builderBlocksPerSecond,
        boolean builderPauseAtNight,
        int logisticsTransferIntervalTicks,
        int logisticsMaxChannelsPerTick,
        int logisticsMaxTransfersPerTick,
        boolean logisticsChargeEnabled,
        int logisticsFreeDistanceBlocks,
        double logisticsBaseCost,
        int logisticsDistanceStepBlocks,
        double logisticsStepCost,
        int logisticsMaxWarehouseContainers,
        int logisticsMaxClientPorts,
        boolean creativeMaterials,
        boolean expertMaterials,
        boolean materialCategoryMatching,
        int materialWarningCooldownSeconds,
        List<String> allModeBlockBlacklist,
        List<String> basicMaterials,
        List<String> materialCategoryGroups,
        List<String> expertModeSkipList
) implements CustomPacketPayload {
    public static final Type<ServerConfigSavePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "server_config_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigSavePacket> STREAM_CODEC = StreamCodec.of(ServerConfigSavePacket::encode, ServerConfigSavePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, ServerConfigSavePacket p) {
        buf.writeDouble(p.cityChunkPrice);
        buf.writeBoolean(p.blacklistProtection);
        buf.writeBoolean(p.logBlacklistSkippedBlocks);
        buf.writeBoolean(p.claimProtection);
        buf.writeVarInt(p.populationGrowthIntervalTicks);
        buf.writeVarInt(p.populationGrowthMaxPerInterval);
        buf.writeVarInt(p.populationGrowthTimesPerWeek);
        buf.writeUtf(safeNameStyle(p.npcNameStyle).name(), 16);
        buf.writeVarInt(p.familyPregnancyDurationDays);
        buf.writeVarInt(p.familyPostpartumRecoveryDays);
        buf.writeDouble(p.familyMarriageChancePerDay);
        buf.writeDouble(p.familyPregnancyChancePerDay);
        buf.writeDouble(p.medicalLowHealthThreshold);
        buf.writeVarInt(p.medicalHealIntervalTicks);
        buf.writeDouble(p.medicalHealAmount);
        buf.writeDouble(p.medicalDiseaseChancePerDay);
        buf.writeVarInt(p.medicalDiseaseTreatmentTicks);
        buf.writeVarInt(p.farmAreaRadius);
        buf.writeVarInt(p.farmWorkIntervalTicks);
        buf.writeVarInt(p.farmActionsPerCycle);
        buf.writeVarInt(p.pathMaxLoadedCitizenEntities);
        buf.writeVarInt(p.pathMaxActiveCitizens);
        buf.writeVarInt(p.pathMaxNewRequestsPerTick);
        buf.writeVarInt(p.pathWorkerThreads);
        buf.writeVarInt(p.pathLocalRadiusBlocks);
        buf.writeVarInt(p.pathFarMovementTeleportDistance);
        buf.writeVarInt(p.pathRepathCooldownTicks);
        buf.writeVarInt(p.pathCacheTtlTicks);
        buf.writeBoolean(p.pathDebug);
        buf.writeVarInt(p.buildingIntegrityAutoDemolishThresholdPercent);
        buf.writeVarInt(p.buildingIntegrityCheckIntervalTicks);
        buf.writeDouble(p.buildingIntegrityRepairMoneyPerBlock);
        buf.writeVarInt(p.npcMaxLevel);
        buf.writeBoolean(p.builderXpGain);
        buf.writeVarInt(p.builderXpPerBlock);
        buf.writeBoolean(p.plannerXpGain);
        buf.writeVarInt(p.plannerXpPerBlock);
        buf.writeDouble(p.plannerBlocksPerSecond);
        buf.writeVarInt(p.plannerMaxVolume);
        buf.writeDouble(p.plannerMoneyPerBlockRemove);
        buf.writeDouble(p.plannerMoneyPerBlockFill);
        buf.writeDouble(p.plannerMoneyPerBlockReplace);
        buf.writeBoolean(p.plannerPauseAtNight);
        buf.writeDouble(p.builderBlocksPerSecond);
        buf.writeBoolean(p.builderPauseAtNight);
        buf.writeVarInt(p.logisticsTransferIntervalTicks);
        buf.writeVarInt(p.logisticsMaxChannelsPerTick);
        buf.writeVarInt(p.logisticsMaxTransfersPerTick);
        buf.writeBoolean(p.logisticsChargeEnabled);
        buf.writeVarInt(p.logisticsFreeDistanceBlocks);
        buf.writeDouble(p.logisticsBaseCost);
        buf.writeVarInt(p.logisticsDistanceStepBlocks);
        buf.writeDouble(p.logisticsStepCost);
        buf.writeVarInt(p.logisticsMaxWarehouseContainers);
        buf.writeVarInt(p.logisticsMaxClientPorts);
        buf.writeBoolean(p.creativeMaterials);
        buf.writeBoolean(p.expertMaterials);
        buf.writeBoolean(p.materialCategoryMatching);
        buf.writeVarInt(p.materialWarningCooldownSeconds);
        writeStrings(buf, p.allModeBlockBlacklist);
        writeStrings(buf, p.basicMaterials);
        writeStrings(buf, p.materialCategoryGroups);
        writeStrings(buf, p.expertModeSkipList);
    }

    public static ServerConfigSavePacket decode(RegistryFriendlyByteBuf buf) {
        return new ServerConfigSavePacket(
                buf.readDouble(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), CitizenNameStyle.fromName(buf.readUtf(16)),
                buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readDouble(),
                buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                buf.readDouble(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean(), buf.readDouble(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readDouble(),
                buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                readStrings(buf), readStrings(buf), readStrings(buf), readStrings(buf));
    }

    public static void handle(ServerConfigSavePacket p, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
            return;
        }
        ServerConfig.CITY_CHUNK_PRICE.set(p.cityChunkPrice);
        ServerConfig.ENABLE_BLACKLIST_PROTECTION.set(p.blacklistProtection);
        ServerConfig.LOG_BLACKLIST_SKIPPED_BLOCKS.set(p.logBlacklistSkippedBlocks);
        ServerConfig.ENABLE_CLAIM_PROTECTION.set(p.claimProtection);
        ServerConfig.POPULATION_GROWTH_INTERVAL_TICKS.set(p.populationGrowthIntervalTicks);
        ServerConfig.POPULATION_GROWTH_MAX_PER_INTERVAL.set(p.populationGrowthMaxPerInterval);
        ServerConfig.POPULATION_GROWTH_TIMES_PER_WEEK.set(p.populationGrowthTimesPerWeek);
        ServerConfig.NPC_NAME_STYLE.set(safeNameStyle(p.npcNameStyle));
        ServerConfig.FAMILY_PREGNANCY_DURATION_DAYS.set(Math.clamp(p.familyPregnancyDurationDays, 1, 3));
        ServerConfig.FAMILY_POSTPARTUM_RECOVERY_DAYS.set(p.familyPostpartumRecoveryDays);
        ServerConfig.FAMILY_MARRIAGE_CHANCE_PER_DAY.set(p.familyMarriageChancePerDay);
        ServerConfig.FAMILY_PREGNANCY_CHANCE_PER_DAY.set(p.familyPregnancyChancePerDay);
        ServerConfig.MEDICAL_LOW_HEALTH_THRESHOLD.set(p.medicalLowHealthThreshold);
        ServerConfig.MEDICAL_HEAL_INTERVAL_TICKS.set(p.medicalHealIntervalTicks);
        ServerConfig.MEDICAL_HEAL_AMOUNT.set(p.medicalHealAmount);
        ServerConfig.MEDICAL_DISEASE_CHANCE_PER_DAY.set(p.medicalDiseaseChancePerDay);
        ServerConfig.MEDICAL_DISEASE_TREATMENT_TICKS.set(p.medicalDiseaseTreatmentTicks);
        ServerConfig.FARM_AREA_RADIUS.set(p.farmAreaRadius);
        ServerConfig.FARM_WORK_INTERVAL_TICKS.set(p.farmWorkIntervalTicks);
        ServerConfig.FARM_ACTIONS_PER_CYCLE.set(p.farmActionsPerCycle);
        ServerConfig.PATH_MAX_LOADED_CITIZEN_ENTITIES.set(p.pathMaxLoadedCitizenEntities);
        ServerConfig.PATH_MAX_ACTIVE_CITIZENS.set(p.pathMaxActiveCitizens);
        ServerConfig.PATH_MAX_NEW_REQUESTS_PER_TICK.set(p.pathMaxNewRequestsPerTick);
        ServerConfig.PATH_WORKER_THREADS.set(p.pathWorkerThreads);
        ServerConfig.PATH_LOCAL_RADIUS_BLOCKS.set(p.pathLocalRadiusBlocks);
        ServerConfig.PATH_FAR_MOVEMENT_TELEPORT_DISTANCE.set(p.pathFarMovementTeleportDistance);
        ServerConfig.PATH_REPATH_COOLDOWN_TICKS.set(p.pathRepathCooldownTicks);
        ServerConfig.PATH_CACHE_TTL_TICKS.set(p.pathCacheTtlTicks);
        ServerConfig.PATH_DEBUG.set(p.pathDebug);
        ServerConfig.BUILDING_INTEGRITY_AUTO_DEMOLISH_THRESHOLD_PERCENT.set(p.buildingIntegrityAutoDemolishThresholdPercent);
        ServerConfig.BUILDING_INTEGRITY_CHECK_INTERVAL_TICKS.set(p.buildingIntegrityCheckIntervalTicks);
        ServerConfig.BUILDING_INTEGRITY_REPAIR_MONEY_PER_BLOCK.set(p.buildingIntegrityRepairMoneyPerBlock);
        ServerConfig.NPC_MAX_LEVEL.set(p.npcMaxLevel);
        ServerConfig.BUILDER_XP_GAIN.set(p.builderXpGain);
        ServerConfig.BUILDER_XP_PER_BLOCK.set(p.builderXpPerBlock);
        ServerConfig.PLANNER_XP_GAIN.set(p.plannerXpGain);
        ServerConfig.PLANNER_XP_PER_BLOCK.set(p.plannerXpPerBlock);
        ServerConfig.PLANNER_BLOCKS_PER_SECOND.set(p.plannerBlocksPerSecond);
        ServerConfig.PLANNER_MAX_VOLUME.set(p.plannerMaxVolume);
        ServerConfig.PLANNER_MONEY_PER_BLOCK_REMOVE.set(p.plannerMoneyPerBlockRemove);
        ServerConfig.PLANNER_MONEY_PER_BLOCK_FILL.set(p.plannerMoneyPerBlockFill);
        ServerConfig.PLANNER_MONEY_PER_BLOCK_REPLACE.set(p.plannerMoneyPerBlockReplace);
        ServerConfig.PLANNER_PAUSE_AT_NIGHT.set(p.plannerPauseAtNight);
        ServerConfig.BUILDER_BLOCKS_PER_SECOND.set(p.builderBlocksPerSecond);
        ServerConfig.BUILDER_PAUSE_AT_NIGHT.set(p.builderPauseAtNight);
        ServerConfig.LOGISTICS_TRANSFER_INTERVAL_TICKS.set(p.logisticsTransferIntervalTicks);
        ServerConfig.LOGISTICS_MAX_CHANNELS_PER_TICK.set(p.logisticsMaxChannelsPerTick);
        ServerConfig.LOGISTICS_MAX_TRANSFERS_PER_TICK.set(p.logisticsMaxTransfersPerTick);
        ServerConfig.LOGISTICS_CHARGE_ENABLED.set(p.logisticsChargeEnabled);
        ServerConfig.LOGISTICS_FREE_DISTANCE_BLOCKS.set(p.logisticsFreeDistanceBlocks);
        ServerConfig.LOGISTICS_BASE_COST.set(p.logisticsBaseCost);
        ServerConfig.LOGISTICS_DISTANCE_STEP_BLOCKS.set(p.logisticsDistanceStepBlocks);
        ServerConfig.LOGISTICS_STEP_COST.set(p.logisticsStepCost);
        ServerConfig.LOGISTICS_MAX_WAREHOUSE_CONTAINERS.set(p.logisticsMaxWarehouseContainers);
        ServerConfig.LOGISTICS_MAX_CLIENT_PORTS.set(p.logisticsMaxClientPorts);
        ServerConfig.MATERIALS_CREATIVE_MODE.set(p.creativeMaterials);
        ServerConfig.MATERIALS_EXPERT_MODE.set(p.expertMaterials);
        ServerConfig.MATERIALS_CATEGORY_MATCHING.set(p.materialCategoryMatching);
        ServerConfig.MATERIAL_WARNING_COOLDOWN_SECONDS.set(p.materialWarningCooldownSeconds);
        ServerConfig.ALL_MODE_BLOCK_BLACKLIST.set(List.copyOf(p.allModeBlockBlacklist));
        ServerConfig.BASIC_MATERIALS.set(List.copyOf(p.basicMaterials));
        ServerConfig.MATERIAL_CATEGORY_GROUPS.set(List.copyOf(p.materialCategoryGroups));
        ServerConfig.EXPERT_MODE_SKIP_LIST.set(List.copyOf(p.expertModeSkipList));
        ServerConfig.SPEC.save();
        WorkMaterialPolicy.clearCache();
        NpcBlockProtectionPolicy.clearCache();
    }

    /** safeNameStyle: 防御空网络字段，避免保存配置时写入非法枚举。 */
    private static CitizenNameStyle safeNameStyle(CitizenNameStyle style) {
        return style == null ? CitizenNameStyle.CHINESE : style;
    }

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) {
            buf.writeUtf(s);
        }
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf());
        }
        return list;
    }
}
