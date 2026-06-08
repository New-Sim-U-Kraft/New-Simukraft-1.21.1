package client.cn.kafei.simukraft.client.config;

import common.cn.kafei.simukraft.config.MaterialConfigDefaults;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.material.WorkMaterialPolicy;
import common.cn.kafei.simukraft.protection.NpcBlockProtectionPolicy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class SimuKraftServerConfigDraft {
    double cityChunkPrice;
    boolean blacklistProtection;
    boolean logBlacklistSkippedBlocks;
    int populationGrowthIntervalTicks;
    int populationGrowthMaxPerInterval;
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
        populationGrowthIntervalTicks = ServerConfig.POPULATION_GROWTH_INTERVAL_TICKS.get();
        populationGrowthMaxPerInterval = ServerConfig.POPULATION_GROWTH_MAX_PER_INTERVAL.get();
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
        populationGrowthIntervalTicks = 24_000;
        populationGrowthMaxPerInterval = 1;
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
        workMode = WorkMode.NORMAL;
        materialCategoryMatching = true;
        materialWarningCooldownSeconds = 20;
        setAllModeBlockBlacklist(MaterialConfigDefaults.ALL_MODE_BLOCK_BLACKLIST);
        setBasicMaterials(MaterialConfigDefaults.BASIC_MATERIALS);
        setMaterialCategoryGroups(MaterialConfigDefaults.MATERIAL_CATEGORY_GROUPS);
        setExpertModeSkipList(MaterialConfigDefaults.EXPERT_MODE_SKIP_LIST);
    }

    /** saveToLive: 保存草稿到 NeoForge 配置并清理材料策略缓存。 */
    void saveToLive() {
        ServerConfig.CITY_CHUNK_PRICE.set(cityChunkPrice);
        ServerConfig.ENABLE_BLACKLIST_PROTECTION.set(blacklistProtection);
        ServerConfig.LOG_BLACKLIST_SKIPPED_BLOCKS.set(logBlacklistSkippedBlocks);
        ServerConfig.POPULATION_GROWTH_INTERVAL_TICKS.set(populationGrowthIntervalTicks);
        ServerConfig.POPULATION_GROWTH_MAX_PER_INTERVAL.set(populationGrowthMaxPerInterval);
        ServerConfig.FARM_AREA_RADIUS.set(farmAreaRadius);
        ServerConfig.FARM_WORK_INTERVAL_TICKS.set(farmWorkIntervalTicks);
        ServerConfig.FARM_ACTIONS_PER_CYCLE.set(farmActionsPerCycle);
        ServerConfig.PATH_MAX_LOADED_CITIZEN_ENTITIES.set(pathMaxLoadedCitizenEntities);
        ServerConfig.PATH_MAX_ACTIVE_CITIZENS.set(pathMaxActiveCitizens);
        ServerConfig.PATH_MAX_NEW_REQUESTS_PER_TICK.set(pathMaxNewRequestsPerTick);
        ServerConfig.PATH_WORKER_THREADS.set(pathWorkerThreads);
        ServerConfig.PATH_LOCAL_RADIUS_BLOCKS.set(pathLocalRadiusBlocks);
        ServerConfig.PATH_FAR_MOVEMENT_TELEPORT_DISTANCE.set(pathFarMovementTeleportDistance);
        ServerConfig.PATH_REPATH_COOLDOWN_TICKS.set(pathRepathCooldownTicks);
        ServerConfig.PATH_CACHE_TTL_TICKS.set(pathCacheTtlTicks);
        ServerConfig.PATH_DEBUG.set(pathDebug);
        ServerConfig.BUILDING_INTEGRITY_AUTO_DEMOLISH_THRESHOLD_PERCENT.set(buildingIntegrityAutoDemolishThresholdPercent);
        ServerConfig.BUILDING_INTEGRITY_CHECK_INTERVAL_TICKS.set(buildingIntegrityCheckIntervalTicks);
        ServerConfig.BUILDING_INTEGRITY_REPAIR_MONEY_PER_BLOCK.set(buildingIntegrityRepairMoneyPerBlock);
        ServerConfig.NPC_MAX_LEVEL.set(npcMaxLevel);
        ServerConfig.BUILDER_XP_GAIN.set(builderXpGain);
        ServerConfig.BUILDER_XP_PER_BLOCK.set(builderXpPerBlock);
        ServerConfig.PLANNER_XP_GAIN.set(plannerXpGain);
        ServerConfig.PLANNER_XP_PER_BLOCK.set(plannerXpPerBlock);
        ServerConfig.PLANNER_BLOCKS_PER_SECOND.set(plannerBlocksPerSecond);
        ServerConfig.PLANNER_MAX_VOLUME.set(plannerMaxVolume);
        ServerConfig.PLANNER_MONEY_PER_BLOCK_REMOVE.set(plannerMoneyPerBlockRemove);
        ServerConfig.PLANNER_MONEY_PER_BLOCK_FILL.set(plannerMoneyPerBlockFill);
        ServerConfig.PLANNER_MONEY_PER_BLOCK_REPLACE.set(plannerMoneyPerBlockReplace);
        ServerConfig.PLANNER_PAUSE_AT_NIGHT.set(plannerPauseAtNight);
        ServerConfig.BUILDER_BLOCKS_PER_SECOND.set(builderBlocksPerSecond);
        ServerConfig.BUILDER_PAUSE_AT_NIGHT.set(builderPauseAtNight);
        ServerConfig.MATERIALS_CREATIVE_MODE.set(workMode == WorkMode.CREATIVE);
        ServerConfig.MATERIALS_EXPERT_MODE.set(workMode == WorkMode.EXPERT);
        ServerConfig.MATERIALS_CATEGORY_MATCHING.set(materialCategoryMatching);
        ServerConfig.MATERIAL_WARNING_COOLDOWN_SECONDS.set(materialWarningCooldownSeconds);
        ServerConfig.ALL_MODE_BLOCK_BLACKLIST.set(List.copyOf(allModeBlockBlacklist));
        ServerConfig.BASIC_MATERIALS.set(List.copyOf(basicMaterials));
        ServerConfig.MATERIAL_CATEGORY_GROUPS.set(List.copyOf(materialCategoryGroups));
        ServerConfig.EXPERT_MODE_SKIP_LIST.set(List.copyOf(expertModeSkipList));
        ServerConfig.SPEC.save();
        WorkMaterialPolicy.clearCache();
        NpcBlockProtectionPolicy.clearCache();
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
