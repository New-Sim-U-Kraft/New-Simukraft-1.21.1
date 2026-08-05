package client.cn.kafei.simukraft.client.config;

import common.cn.kafei.simukraft.citizen.CitizenNameStyle;
import common.cn.kafei.simukraft.config.MaterialConfigDefaults;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.network.config.ServerConfigSavePacket;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("null")
final class SimuKraftServerConfigDraft {
    double cityChunkPrice;
    boolean blacklistProtection;
    boolean logBlacklistSkippedBlocks;
    boolean claimProtection;
    int populationGrowthIntervalTicks;
    int populationGrowthMaxPerInterval;
    int populationGrowthTimesPerWeek;
    CitizenNameStyle npcNameStyle;
    int familyPregnancyDurationDays;
    int familyPostpartumRecoveryDays;
    double familyMarriageChancePerDay;
    double familyPregnancyChancePerDay;
    double medicalLowHealthThreshold;
    int medicalHealIntervalTicks;
    double medicalHealAmount;
    double medicalDiseaseChancePerDay;
    int medicalDiseaseTreatmentTicks;
    int farmAreaRadius;
    int farmWorkIntervalTicks;
    int farmActionsPerCycle;
    int pathMaxLoadedCitizenEntities;
    int pathMaxActiveCitizens;
    int pathMaxNewRequestsPerTick;
    int pathWorkerThreads;
    int pathLocalRadiusBlocks;
    int pathFarMovementTeleportDistance;
    int pathRepathCooldownTicks;
    int pathCacheTtlTicks;
    boolean pathDebug;
    int buildingIntegrityAutoDemolishThresholdPercent;
    int buildingIntegrityCheckIntervalTicks;
    double buildingIntegrityRepairMoneyPerBlock;
    int npcMaxLevel;
    boolean builderXpGain;
    int builderXpPerBlock;
    boolean plannerXpGain;
    int plannerXpPerBlock;
    double plannerBlocksPerSecond;
    int plannerMaxVolume;
    double plannerMoneyPerBlockRemove;
    double plannerMoneyPerBlockFill;
    double plannerMoneyPerBlockReplace;
    boolean plannerPauseAtNight;
    double builderBlocksPerSecond;
    boolean builderPauseAtNight;
    int logisticsTransferIntervalTicks;
    int logisticsMaxChannelsPerTick;
    int logisticsMaxTransfersPerTick;
    boolean logisticsChargeEnabled;
    int logisticsFreeDistanceBlocks;
    double logisticsBaseCost;
    int logisticsDistanceStepBlocks;
    double logisticsStepCost;
    int logisticsMaxWarehouseContainers;
    int logisticsMaxClientPorts;
    WorkMode workMode;
    boolean materialCategoryMatching;
    int materialWarningCooldownSeconds;
    private final CopyOnWriteArrayList<String> allModeBlockBlacklist = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> basicMaterials = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> materialCategoryGroups = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> expertModeSkipList = new CopyOnWriteArrayList<>();

    private SimuKraftServerConfigDraft() {
    }

    /** live: 从当前 NeoForge 服务端配置创建草稿。 */
    static SimuKraftServerConfigDraft live() {
        SimuKraftServerConfigDraft draft = new SimuKraftServerConfigDraft();
        draft.reloadFromLive();
        return draft;
    }

    /** reloadFromLive: 丢弃草稿并重新读取当前配置。 */
    void reloadFromLive() {
        cityChunkPrice = ServerConfig.CITY_CHUNK_PRICE.get();
        blacklistProtection = ServerConfig.ENABLE_BLACKLIST_PROTECTION.get();
        logBlacklistSkippedBlocks = ServerConfig.LOG_BLACKLIST_SKIPPED_BLOCKS.get();
        claimProtection = ServerConfig.ENABLE_CLAIM_PROTECTION.get();
        populationGrowthIntervalTicks = ServerConfig.POPULATION_GROWTH_INTERVAL_TICKS.get();
        populationGrowthMaxPerInterval = ServerConfig.POPULATION_GROWTH_MAX_PER_INTERVAL.get();
        populationGrowthTimesPerWeek = ServerConfig.POPULATION_GROWTH_TIMES_PER_WEEK.get();
        npcNameStyle = ServerConfig.NPC_NAME_STYLE.get();
        familyPregnancyDurationDays = ServerConfig.FAMILY_PREGNANCY_DURATION_DAYS.get();
        familyPostpartumRecoveryDays = ServerConfig.FAMILY_POSTPARTUM_RECOVERY_DAYS.get();
        familyMarriageChancePerDay = ServerConfig.FAMILY_MARRIAGE_CHANCE_PER_DAY.get();
        familyPregnancyChancePerDay = ServerConfig.FAMILY_PREGNANCY_CHANCE_PER_DAY.get();
        medicalLowHealthThreshold = ServerConfig.MEDICAL_LOW_HEALTH_THRESHOLD.get();
        medicalHealIntervalTicks = ServerConfig.MEDICAL_HEAL_INTERVAL_TICKS.get();
        medicalHealAmount = ServerConfig.MEDICAL_HEAL_AMOUNT.get();
        medicalDiseaseChancePerDay = ServerConfig.MEDICAL_DISEASE_CHANCE_PER_DAY.get();
        medicalDiseaseTreatmentTicks = ServerConfig.MEDICAL_DISEASE_TREATMENT_TICKS.get();
        farmAreaRadius = ServerConfig.FARM_AREA_RADIUS.get();
        farmWorkIntervalTicks = ServerConfig.FARM_WORK_INTERVAL_TICKS.get();
        farmActionsPerCycle = ServerConfig.FARM_ACTIONS_PER_CYCLE.get();
        pathMaxLoadedCitizenEntities = ServerConfig.PATH_MAX_LOADED_CITIZEN_ENTITIES.get();
        pathMaxActiveCitizens = ServerConfig.PATH_MAX_ACTIVE_CITIZENS.get();
        pathMaxNewRequestsPerTick = ServerConfig.PATH_MAX_NEW_REQUESTS_PER_TICK.get();
        pathWorkerThreads = ServerConfig.PATH_WORKER_THREADS.get();
        pathLocalRadiusBlocks = ServerConfig.PATH_LOCAL_RADIUS_BLOCKS.get();
        pathFarMovementTeleportDistance = ServerConfig.PATH_FAR_MOVEMENT_TELEPORT_DISTANCE.get();
        pathRepathCooldownTicks = ServerConfig.PATH_REPATH_COOLDOWN_TICKS.get();
        pathCacheTtlTicks = ServerConfig.PATH_CACHE_TTL_TICKS.get();
        pathDebug = ServerConfig.PATH_DEBUG.get();
        buildingIntegrityAutoDemolishThresholdPercent = ServerConfig.BUILDING_INTEGRITY_AUTO_DEMOLISH_THRESHOLD_PERCENT.get();
        buildingIntegrityCheckIntervalTicks = ServerConfig.BUILDING_INTEGRITY_CHECK_INTERVAL_TICKS.get();
        buildingIntegrityRepairMoneyPerBlock = ServerConfig.BUILDING_INTEGRITY_REPAIR_MONEY_PER_BLOCK.get();
        npcMaxLevel = ServerConfig.NPC_MAX_LEVEL.get();
        builderXpGain = ServerConfig.BUILDER_XP_GAIN.get();
        builderXpPerBlock = ServerConfig.BUILDER_XP_PER_BLOCK.get();
        plannerXpGain = ServerConfig.PLANNER_XP_GAIN.get();
        plannerXpPerBlock = ServerConfig.PLANNER_XP_PER_BLOCK.get();
        plannerBlocksPerSecond = ServerConfig.PLANNER_BLOCKS_PER_SECOND.get();
        plannerMaxVolume = ServerConfig.PLANNER_MAX_VOLUME.get();
        plannerMoneyPerBlockRemove = ServerConfig.PLANNER_MONEY_PER_BLOCK_REMOVE.get();
        plannerMoneyPerBlockFill = ServerConfig.PLANNER_MONEY_PER_BLOCK_FILL.get();
        plannerMoneyPerBlockReplace = ServerConfig.PLANNER_MONEY_PER_BLOCK_REPLACE.get();
        plannerPauseAtNight = ServerConfig.PLANNER_PAUSE_AT_NIGHT.get();
        builderBlocksPerSecond = ServerConfig.BUILDER_BLOCKS_PER_SECOND.get();
        builderPauseAtNight = ServerConfig.BUILDER_PAUSE_AT_NIGHT.get();
        logisticsTransferIntervalTicks = ServerConfig.LOGISTICS_TRANSFER_INTERVAL_TICKS.get();
        logisticsMaxChannelsPerTick = ServerConfig.LOGISTICS_MAX_CHANNELS_PER_TICK.get();
        logisticsMaxTransfersPerTick = ServerConfig.LOGISTICS_MAX_TRANSFERS_PER_TICK.get();
        logisticsChargeEnabled = ServerConfig.LOGISTICS_CHARGE_ENABLED.get();
        logisticsFreeDistanceBlocks = ServerConfig.LOGISTICS_FREE_DISTANCE_BLOCKS.get();
        logisticsBaseCost = ServerConfig.LOGISTICS_BASE_COST.get();
        logisticsDistanceStepBlocks = ServerConfig.LOGISTICS_DISTANCE_STEP_BLOCKS.get();
        logisticsStepCost = ServerConfig.LOGISTICS_STEP_COST.get();
        logisticsMaxWarehouseContainers = ServerConfig.LOGISTICS_MAX_WAREHOUSE_CONTAINERS.get();
        logisticsMaxClientPorts = ServerConfig.LOGISTICS_MAX_CLIENT_PORTS.get();
        workMode = WorkMode.from(ServerConfig.MATERIALS_CREATIVE_MODE.get(), ServerConfig.MATERIALS_EXPERT_MODE.get());
        materialCategoryMatching = ServerConfig.MATERIALS_CATEGORY_MATCHING.get();
        materialWarningCooldownSeconds = ServerConfig.MATERIAL_WARNING_COOLDOWN_SECONDS.get();
        setAllModeBlockBlacklist(ServerConfig.allModeBlockBlacklist());
        setBasicMaterials(ServerConfig.basicMaterials());
        setMaterialCategoryGroups(ServerConfig.materialCategoryGroups());
        setExpertModeSkipList(ServerConfig.expertModeSkipList());
    }

    /** resetToDefaults: 恢复为 ServerConfig 中定义的默认值。 */
    void resetToDefaults() {
        cityChunkPrice = 10.0D;
        blacklistProtection = true;
        logBlacklistSkippedBlocks = true;
        claimProtection = true;
        populationGrowthIntervalTicks = 24_000;
        populationGrowthMaxPerInterval = 1;
        populationGrowthTimesPerWeek = 7;
        npcNameStyle = CitizenNameStyle.CHINESE;
        familyPregnancyDurationDays = 3;
        familyPostpartumRecoveryDays = 1;
        familyMarriageChancePerDay = 0.05D;
        familyPregnancyChancePerDay = 0.10D;
        medicalLowHealthThreshold = 8.0D;
        medicalHealIntervalTicks = 100;
        medicalHealAmount = 1.0D;
        medicalDiseaseChancePerDay = 0.02D;
        medicalDiseaseTreatmentTicks = 24_000;
        farmAreaRadius = 3;
        farmWorkIntervalTicks = 20;
        farmActionsPerCycle = 4;
        pathMaxLoadedCitizenEntities = 180;
        pathMaxActiveCitizens = 64;
        pathMaxNewRequestsPerTick = 3;
        pathWorkerThreads = 4;
        pathLocalRadiusBlocks = 96;
        pathFarMovementTeleportDistance = 96;
        pathRepathCooldownTicks = 60;
        pathCacheTtlTicks = 1200;
        pathDebug = false;
        buildingIntegrityAutoDemolishThresholdPercent = 30;
        buildingIntegrityCheckIntervalTicks = 200;
        buildingIntegrityRepairMoneyPerBlock = 0.05D;
        npcMaxLevel = 20;
        builderXpGain = true;
        builderXpPerBlock = 1;
        plannerXpGain = true;
        plannerXpPerBlock = 1;
        plannerBlocksPerSecond = 2.0D;
        plannerMaxVolume = 8192;
        plannerMoneyPerBlockRemove = 0.02D;
        plannerMoneyPerBlockFill = 0.02D;
        plannerMoneyPerBlockReplace = 0.04D;
        plannerPauseAtNight = true;
        builderBlocksPerSecond = 1.0D;
        builderPauseAtNight = true;
        logisticsTransferIntervalTicks = 100;
        logisticsMaxChannelsPerTick = 32;
        logisticsMaxTransfersPerTick = 64;
        logisticsChargeEnabled = true;
        logisticsFreeDistanceBlocks = 256;
        logisticsBaseCost = 0.02D;
        logisticsDistanceStepBlocks = 64;
        logisticsStepCost = 0.01D;
        logisticsMaxWarehouseContainers = 64;
        logisticsMaxClientPorts = 32;
        workMode = WorkMode.NORMAL;
        materialCategoryMatching = true;
        materialWarningCooldownSeconds = 20;
        setAllModeBlockBlacklist(MaterialConfigDefaults.ALL_MODE_BLOCK_BLACKLIST);
        setBasicMaterials(MaterialConfigDefaults.BASIC_MATERIALS);
        setMaterialCategoryGroups(MaterialConfigDefaults.MATERIAL_CATEGORY_GROUPS);
        setExpertModeSkipList(MaterialConfigDefaults.EXPERT_MODE_SKIP_LIST);
    }

    /** saveToLive: 发包至服务端保存配置。 */
    void saveToLive() {
        PacketDistributor.sendToServer(new ServerConfigSavePacket(
                cityChunkPrice, blacklistProtection, logBlacklistSkippedBlocks, claimProtection,
                populationGrowthIntervalTicks, populationGrowthMaxPerInterval, populationGrowthTimesPerWeek, npcNameStyle,
                familyPregnancyDurationDays, familyPostpartumRecoveryDays, familyMarriageChancePerDay, familyPregnancyChancePerDay,
                medicalLowHealthThreshold, medicalHealIntervalTicks, medicalHealAmount, medicalDiseaseChancePerDay, medicalDiseaseTreatmentTicks,
                farmAreaRadius, farmWorkIntervalTicks, farmActionsPerCycle,
                pathMaxLoadedCitizenEntities, pathMaxActiveCitizens, pathMaxNewRequestsPerTick,
                pathWorkerThreads, pathLocalRadiusBlocks, pathFarMovementTeleportDistance,
                pathRepathCooldownTicks, pathCacheTtlTicks, pathDebug,
                buildingIntegrityAutoDemolishThresholdPercent, buildingIntegrityCheckIntervalTicks,
                buildingIntegrityRepairMoneyPerBlock, npcMaxLevel,
                builderXpGain, builderXpPerBlock, plannerXpGain, plannerXpPerBlock,
                plannerBlocksPerSecond, plannerMaxVolume,
                plannerMoneyPerBlockRemove, plannerMoneyPerBlockFill, plannerMoneyPerBlockReplace,
                plannerPauseAtNight, builderBlocksPerSecond, builderPauseAtNight,
                logisticsTransferIntervalTicks, logisticsMaxChannelsPerTick, logisticsMaxTransfersPerTick,
                logisticsChargeEnabled, logisticsFreeDistanceBlocks, logisticsBaseCost,
                logisticsDistanceStepBlocks, logisticsStepCost,
                logisticsMaxWarehouseContainers, logisticsMaxClientPorts,
                workMode == WorkMode.CREATIVE, workMode == WorkMode.EXPERT,
                materialCategoryMatching, materialWarningCooldownSeconds,
                List.copyOf(allModeBlockBlacklist), List.copyOf(basicMaterials),
                List.copyOf(materialCategoryGroups), List.copyOf(expertModeSkipList)
        ));
    }

    List<String> allModeBlockBlacklist() {
        return allModeBlockBlacklist;
    }

    void setAllModeBlockBlacklist(List<String> values) {
        replaceList(allModeBlockBlacklist, values);
    }

    List<String> basicMaterials() {
        return basicMaterials;
    }

    void setBasicMaterials(List<String> values) {
        replaceList(basicMaterials, values);
    }

    List<String> materialCategoryGroups() {
        return materialCategoryGroups;
    }

    void setMaterialCategoryGroups(List<String> values) {
        replaceList(materialCategoryGroups, values);
    }

    List<String> expertModeSkipList() {
        return expertModeSkipList;
    }

    void setExpertModeSkipList(List<String> values) {
        replaceList(expertModeSkipList, values);
    }

    private static void replaceList(CopyOnWriteArrayList<String> target, List<String> values) {
        target.clear();
        if (values == null) {
            return;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .forEach(target::add);
    }

    enum WorkMode {
        NORMAL("gui.simukraft.config.mode.normal"),
        CREATIVE("gui.simukraft.config.mode.creative"),
        EXPERT("gui.simukraft.config.mode.expert");

        private final String translationKey;

        WorkMode(String translationKey) {
            this.translationKey = translationKey;
        }

        String translationKey() {
            return translationKey;
        }

        /** from: 根据旧版两个布尔配置合成单选模式。 */
        static WorkMode from(boolean creative, boolean expert) {
            if (creative) {
                return CREATIVE;
            }
            return expert ? EXPERT : NORMAL;
        }
    }
}
