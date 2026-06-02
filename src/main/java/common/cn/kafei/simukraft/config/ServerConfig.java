package common.cn.kafei.simukraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue CITY_CHUNK_PRICE;
    private static final ModConfigSpec.IntValue POPULATION_GROWTH_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue POPULATION_GROWTH_MAX_PER_INTERVAL;
    private static final ModConfigSpec.DoubleValue BUILDER_BLOCKS_PER_SECOND;
    private static final ModConfigSpec.IntValue NPC_MAX_LEVEL;
    private static final ModConfigSpec.BooleanValue BUILDER_XP_GAIN;
    private static final ModConfigSpec.IntValue BUILDER_XP_PER_BLOCK;
    private static final ModConfigSpec.BooleanValue MATERIALS_CREATIVE_MODE;
    private static final ModConfigSpec.BooleanValue MATERIALS_EXPERT_MODE;
    private static final ModConfigSpec.BooleanValue MATERIALS_CATEGORY_MATCHING;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BASIC_MATERIALS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> MATERIAL_CATEGORY_GROUPS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXPERT_MODE_SKIP_LIST;
    private static final ModConfigSpec.IntValue MATERIAL_WARNING_COOLDOWN_SECONDS;
    private static final ModConfigSpec.BooleanValue BUILDER_PAUSE_AT_NIGHT;
    private static final ModConfigSpec.IntValue BUILDER_REST_START_TIME;
    private static final ModConfigSpec.IntValue BUILDER_REST_END_TIME;
    private static final ModConfigSpec.IntValue PATH_MAX_LOADED_CITIZEN_ENTITIES;
    private static final ModConfigSpec.IntValue PATH_MAX_ACTIVE_CITIZENS;
    private static final ModConfigSpec.IntValue PATH_MAX_NEW_REQUESTS_PER_TICK;
    private static final ModConfigSpec.IntValue PATH_WORKER_THREADS;
    private static final ModConfigSpec.IntValue PATH_LOCAL_RADIUS_BLOCKS;
    private static final ModConfigSpec.IntValue PATH_FAR_MOVEMENT_TELEPORT_DISTANCE;
    private static final ModConfigSpec.IntValue PATH_REPATH_COOLDOWN_TICKS;
    private static final ModConfigSpec.IntValue PATH_CACHE_TTL_TICKS;
    private static final ModConfigSpec.BooleanValue PATH_DEBUG;
    private static final ModConfigSpec.IntValue BUILDING_INTEGRITY_AUTO_DEMOLISH_THRESHOLD_PERCENT;
    private static final ModConfigSpec.IntValue BUILDING_INTEGRITY_CHECK_INTERVAL_TICKS;
    private static final ModConfigSpec.DoubleValue BUILDING_INTEGRITY_REPAIR_MONEY_PER_BLOCK;
    private static final ModConfigSpec.IntValue FARM_AREA_RADIUS;
    private static final ModConfigSpec.IntValue FARM_WORK_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue FARM_ACTIONS_PER_CYCLE;
    private static final ModConfigSpec.DoubleValue PLANNER_BLOCKS_PER_SECOND;
    private static final ModConfigSpec.IntValue PLANNER_MAX_VOLUME;
    private static final ModConfigSpec.DoubleValue PLANNER_MONEY_PER_BLOCK_REMOVE;
    private static final ModConfigSpec.DoubleValue PLANNER_MONEY_PER_BLOCK_FILL;
    private static final ModConfigSpec.DoubleValue PLANNER_MONEY_PER_BLOCK_REPLACE;
    private static final ModConfigSpec.BooleanValue PLANNER_XP_GAIN;
    private static final ModConfigSpec.IntValue PLANNER_XP_PER_BLOCK;
    private static final ModConfigSpec.BooleanValue PLANNER_PAUSE_AT_NIGHT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("economy");
        CITY_CHUNK_PRICE = builder.defineInRange("cityChunkPrice", 10.0D, 0.0D, 1_000_000.0D);
        builder.pop();
        builder.push("population");
        POPULATION_GROWTH_INTERVAL_TICKS = builder.defineInRange("growthIntervalTicks", 24_000, 20, 2_400_000);
        POPULATION_GROWTH_MAX_PER_INTERVAL = builder.defineInRange("growthMaxPerInterval", 1, 0, 100);
        builder.pop();
        builder.push("construction");
        BUILDER_BLOCKS_PER_SECOND = builder
                .comment(
                        "Base blocks one level 1 builder can place per second.",
                        "The final speed scales with NPC level like the legacy version."
                )
                .translation("config.simukraft.construction.builderBlocksPerSecond")
                .defineInRange("builderBlocksPerSecond", 1.0D, 0.1D, 20.0D);
        BUILDER_PAUSE_AT_NIGHT = builder
                .comment("Whether builder tasks pause during the configured night/rest time.")
                .define("builderPauseAtNight", true);
        BUILDER_REST_START_TIME = builder
                .comment("Day time when builders stop working. Minecraft night starts around 13000.")
                .defineInRange("builderRestStartTime", 13000, 0, 23999);
        BUILDER_REST_END_TIME = builder
                .comment("Day time when builders resume working. Minecraft morning starts around 0.")
                .defineInRange("builderRestEndTime", 0, 0, 23999);
        builder.pop();
        builder.push("npc_leveling");
        NPC_MAX_LEVEL = builder
                .comment("Maximum NPC profession level.")
                .translation("config.simukraft.npc_leveling.maxLevel")
                .defineInRange("maxLevel", 20, 1, 20);
        BUILDER_XP_GAIN = builder
                .comment("Whether builders gain profession experience when placing blocks.")
                .translation("config.simukraft.npc_leveling.builderEnableXpGain")
                .define("builderEnableXpGain", true);
        BUILDER_XP_PER_BLOCK = builder
                .comment("Builder profession experience gained per placed block.")
                .translation("config.simukraft.npc_leveling.builderXpPerBlock")
                .defineInRange("builderXpPerBlock", 1, 0, 100);
        builder.pop();
        builder.push("materials");
        MATERIALS_CREATIVE_MODE = builder
                .comment(
                        "Whether NPC work consumes no materials.",
                        "When enabled, material checks are bypassed. This takes priority over expert mode."
                )
                .translation("config.simukraft.materials.enableCreativeMode")
                .define("enableCreativeMode", false);
        MATERIALS_EXPERT_MODE = builder
                .comment(
                        "Whether expert material mode is enabled.",
                        "Expert mode requires every build block except entries in expertModeSkipList.",
                        "Normal mode uses basicMaterials and materialCategoryGroups."
                )
                .translation("config.simukraft.materials.enableExpertMode")
                .define("enableExpertMode", false);
        MATERIALS_CATEGORY_MATCHING = builder
                .comment(
                        "Whether normal mode allows category material matching.",
                        "When enabled, a configured category header item can satisfy matching member blocks."
                )
                .translation("config.simukraft.materials.enableMaterialCategoryMatching")
                .define("enableMaterialCategoryMatching", true);
        BASIC_MATERIALS = builder
                .comment(
                        "Normal mode material list.",
                        "Only these single block/item ids require materials directly.",
                        "Format: modid:item_name"
                )
                .translation("config.simukraft.materials.basicMaterials")
                .defineListAllowEmpty("basicMaterials",
                        () -> MaterialConfigDefaults.BASIC_MATERIALS,
                        () -> "minecraft:stone",
                        ServerConfig::isStringEntry);
        MATERIAL_CATEGORY_GROUPS = builder
                .comment(
                        "Normal mode category matching groups.",
                        "Format: group_name|headers|members",
                        "Headers are items players may provide for matching member blocks.",
                        "If headers are empty, all members can replace each other."
                )
                .translation("config.simukraft.materials.materialCategoryGroups")
                .defineListAllowEmpty("materialCategoryGroups",
                        () -> MaterialConfigDefaults.MATERIAL_CATEGORY_GROUPS,
                        () -> "custom_group||minecraft:stone",
                        ServerConfig::isStringEntry);
        EXPERT_MODE_SKIP_LIST = builder
                .comment(
                        "Expert mode skip list.",
                        "Blocks in this list do not consume materials in expert mode.",
                        "Format: modid:block_name"
                )
                .translation("config.simukraft.materials.expertModeSkipList")
                .defineListAllowEmpty("expertModeSkipList",
                        () -> MaterialConfigDefaults.EXPERT_MODE_SKIP_LIST,
                        () -> "minecraft:bedrock",
                        ServerConfig::isStringEntry);
        MATERIAL_WARNING_COOLDOWN_SECONDS = builder
                .comment("Cooldown in seconds before repeating the same missing material popup.")
                .translation("config.simukraft.materials.warningCooldownSeconds")
                .defineInRange("warningCooldownSeconds", 20, 1, 300);
        builder.pop();
        builder.push("npc_pathfinding");
        PATH_MAX_LOADED_CITIZEN_ENTITIES = builder
                .comment("Maximum loaded citizen entities allowed to start new pathing work.")
                .translation("config.simukraft.npc_pathfinding.maxLoadedCitizenEntities")
                .defineInRange("maxLoadedCitizenEntities", 180, 1, 1000);
        PATH_MAX_ACTIVE_CITIZENS = builder
                .comment("Maximum citizens that may actively follow custom paths at once.")
                .translation("config.simukraft.npc_pathfinding.maxActivePathingCitizens")
                .defineInRange("maxActivePathingCitizens", 64, 1, 5000);
        PATH_MAX_NEW_REQUESTS_PER_TICK = builder
                .comment("Maximum new path requests converted to snapshots per server tick.")
                .translation("config.simukraft.npc_pathfinding.maxNewPathRequestsPerTick")
                .defineInRange("maxNewPathRequestsPerTick", 3, 0, 32);
        PATH_WORKER_THREADS = builder
                .comment("Background worker threads used for pure snapshot path calculations.")
                .translation("config.simukraft.npc_pathfinding.pathWorkerThreads")
                .defineInRange("pathWorkerThreads", 4, 1, 16);
        PATH_LOCAL_RADIUS_BLOCKS = builder
                .comment("Maximum nearby pathing radius. Longer movement uses controlled teleport.")
                .translation("config.simukraft.npc_pathfinding.localPathRadiusBlocks")
                .defineInRange("localPathRadiusBlocks", 96, 16, 256);
        PATH_FAR_MOVEMENT_TELEPORT_DISTANCE = builder
                .comment("Distance where NPCs stop attempting full local pathing and teleport instead.")
                .translation("config.simukraft.npc_pathfinding.farMovementTeleportDistance")
                .defineInRange("farMovementTeleportDistance", 96, 16, 512);
        PATH_REPATH_COOLDOWN_TICKS = builder
                .comment("Cooldown after a failed path before the same NPC may request another path.")
                .translation("config.simukraft.npc_pathfinding.repathCooldownTicks")
                .defineInRange("repathCooldownTicks", 60, 0, 1200);
        PATH_CACHE_TTL_TICKS = builder
                .comment("Ticks before successful in-memory path cache entries expire.")
                .translation("config.simukraft.npc_pathfinding.pathCacheTtlTicks")
                .defineInRange("pathCacheTtlTicks", 1200, 0, 24000);
        PATH_DEBUG = builder
                .comment("Whether NPC pathfinding debug logs are enabled.")
                .translation("config.simukraft.npc_pathfinding.debugPathfinding")
                .define("debugPathfinding", false);
        builder.pop();
        builder.push("building_integrity");
        BUILDING_INTEGRITY_AUTO_DEMOLISH_THRESHOLD_PERCENT = builder
                .comment(
                        "Completed buildings below this integrity percentage are automatically demolished.",
                        "Set to 0 to disable automatic demolition."
                )
                .translation("config.simukraft.building_integrity.autoDemolishThresholdPercent")
                .defineInRange("autoDemolishThresholdPercent", 30, 0, 100);
        BUILDING_INTEGRITY_CHECK_INTERVAL_TICKS = builder
                .comment("Ticks between completed building integrity checks.")
                .translation("config.simukraft.building_integrity.checkIntervalTicks")
                .defineInRange("checkIntervalTicks", 200, 20, 24000);
        BUILDING_INTEGRITY_REPAIR_MONEY_PER_BLOCK = builder
                .comment("City funds charged per repaired building block.")
                .translation("config.simukraft.building_integrity.repairMoneyPerBlock")
                .defineInRange("repairMoneyPerBlock", 0.05D, 0.0D, 1000.0D);
        builder.pop();
        builder.push("farming");
        FARM_AREA_RADIUS = builder
                .comment("Half-size of the square farmland box work area. Radius 3 means a 7x7 field around the box.")
                .translation("config.simukraft.farming.areaRadius")
                .defineInRange("areaRadius", 3, 1, 16);
        FARM_WORK_INTERVAL_TICKS = builder
                .comment("Ticks between farmer work cycles for one farmland box. Higher values are lighter on the server.")
                .translation("config.simukraft.farming.workIntervalTicks")
                .defineInRange("workIntervalTicks", 20, 1, 1200);
        FARM_ACTIONS_PER_CYCLE = builder
                .comment("Maximum till/plant/harvest actions one farmland box performs per work cycle.")
                .translation("config.simukraft.farming.actionsPerCycle")
                .defineInRange("actionsPerCycle", 4, 1, 64);
        builder.pop();
        builder.push("planner");
        PLANNER_BLOCKS_PER_SECOND = builder
                .comment("Base blocks a level 1 planner processes per second. Scales with NPC level like the builder.")
                .translation("config.simukraft.planner.blocksPerSecond")
                .defineInRange("blocksPerSecond", 2.0D, 0.1D, 40.0D);
        PLANNER_MAX_VOLUME = builder
                .comment("Maximum number of blocks (volume) a single planning task may cover.")
                .translation("config.simukraft.planner.maxVolume")
                .defineInRange("maxVolume", 8192, 1, 200000);
        PLANNER_MONEY_PER_BLOCK_REMOVE = builder
                .comment("City funds charged up-front per block for a REMOVE planning task.")
                .translation("config.simukraft.planner.moneyPerBlockRemove")
                .defineInRange("moneyPerBlockRemove", 0.02D, 0.0D, 1000.0D);
        PLANNER_MONEY_PER_BLOCK_FILL = builder
                .comment("City funds charged up-front per block for a FILL planning task.")
                .translation("config.simukraft.planner.moneyPerBlockFill")
                .defineInRange("moneyPerBlockFill", 0.02D, 0.0D, 1000.0D);
        PLANNER_MONEY_PER_BLOCK_REPLACE = builder
                .comment("City funds charged up-front per block for a REPLACE planning task.")
                .translation("config.simukraft.planner.moneyPerBlockReplace")
                .defineInRange("moneyPerBlockReplace", 0.04D, 0.0D, 1000.0D);
        PLANNER_XP_GAIN = builder
                .comment("Whether planners gain profession experience when processing blocks.")
                .translation("config.simukraft.planner.enableXpGain")
                .define("enableXpGain", true);
        PLANNER_XP_PER_BLOCK = builder
                .comment("Planner profession experience gained per processed block.")
                .translation("config.simukraft.planner.xpPerBlock")
                .defineInRange("xpPerBlock", 1, 0, 100);
        PLANNER_PAUSE_AT_NIGHT = builder
                .comment("Whether planner tasks pause during the configured night/rest time (shares the builder rest window).")
                .translation("config.simukraft.planner.pauseAtNight")
                .define("pauseAtNight", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    public static double cityChunkPrice() {
        return CITY_CHUNK_PRICE.get();
    }

    public static int populationGrowthIntervalTicks() {
        return POPULATION_GROWTH_INTERVAL_TICKS.get();
    }

    public static int populationGrowthMaxPerInterval() {
        return POPULATION_GROWTH_MAX_PER_INTERVAL.get();
    }

    public static double builderBlocksPerSecond() {
        return BUILDER_BLOCKS_PER_SECOND.get();
    }

    public static int npcMaxLevel() {
        return NPC_MAX_LEVEL.get();
    }

    public static boolean builderXpGainEnabled() {
        return BUILDER_XP_GAIN.get();
    }

    public static int builderXpPerBlock() {
        return BUILDER_XP_PER_BLOCK.get();
    }

    public static boolean builderPauseAtNight() {
        return BUILDER_PAUSE_AT_NIGHT.get();
    }

    public static int builderRestStartTime() {
        return BUILDER_REST_START_TIME.get();
    }

    public static int builderRestEndTime() {
        return BUILDER_REST_END_TIME.get();
    }

    public static boolean materialsCreativeMode() {
        return MATERIALS_CREATIVE_MODE.get();
    }

    public static boolean materialsExpertMode() {
        return MATERIALS_EXPERT_MODE.get();
    }

    public static boolean materialCategoryMatchingEnabled() {
        return MATERIALS_CATEGORY_MATCHING.get();
    }

    public static List<String> basicMaterials() {
        return copyStringList(BASIC_MATERIALS.get());
    }

    public static List<String> materialCategoryGroups() {
        return copyStringList(MATERIAL_CATEGORY_GROUPS.get());
    }

    public static List<String> expertModeSkipList() {
        return copyStringList(EXPERT_MODE_SKIP_LIST.get());
    }

    public static int materialWarningCooldownTicks() {
        return MATERIAL_WARNING_COOLDOWN_SECONDS.get() * 20;
    }

    public static int pathMaxLoadedCitizenEntities() {
        return PATH_MAX_LOADED_CITIZEN_ENTITIES.get();
    }

    public static int pathMaxActiveCitizens() {
        return PATH_MAX_ACTIVE_CITIZENS.get();
    }

    public static int pathMaxNewRequestsPerTick() {
        return PATH_MAX_NEW_REQUESTS_PER_TICK.get();
    }

    public static int pathWorkerThreads() {
        return PATH_WORKER_THREADS.get();
    }

    public static int pathLocalRadiusBlocks() {
        return PATH_LOCAL_RADIUS_BLOCKS.get();
    }

    public static int pathFarMovementTeleportDistance() {
        return PATH_FAR_MOVEMENT_TELEPORT_DISTANCE.get();
    }

    public static int pathRepathCooldownTicks() {
        return PATH_REPATH_COOLDOWN_TICKS.get();
    }

    public static int pathCacheTtlTicks() {
        return PATH_CACHE_TTL_TICKS.get();
    }

    public static boolean pathDebugEnabled() {
        return PATH_DEBUG.get();
    }

    public static int buildingIntegrityAutoDemolishThresholdPercent() {
        return BUILDING_INTEGRITY_AUTO_DEMOLISH_THRESHOLD_PERCENT.get();
    }

    public static int buildingIntegrityCheckIntervalTicks() {
        return BUILDING_INTEGRITY_CHECK_INTERVAL_TICKS.get();
    }

    public static double buildingIntegrityRepairMoneyPerBlock() {
        return BUILDING_INTEGRITY_REPAIR_MONEY_PER_BLOCK.get();
    }

    public static int farmAreaRadius() {
        return FARM_AREA_RADIUS.get();
    }

    public static int farmWorkIntervalTicks() {
        return FARM_WORK_INTERVAL_TICKS.get();
    }

    public static int farmActionsPerCycle() {
        return FARM_ACTIONS_PER_CYCLE.get();
    }

    public static double plannerBlocksPerSecond() {
        return PLANNER_BLOCKS_PER_SECOND.get();
    }

    public static int plannerMaxVolume() {
        return PLANNER_MAX_VOLUME.get();
    }

    public static double plannerMoneyPerBlock(common.cn.kafei.simukraft.planner.PlanOperation operation) {
        return switch (operation) {
            case REMOVE -> PLANNER_MONEY_PER_BLOCK_REMOVE.get();
            case FILL -> PLANNER_MONEY_PER_BLOCK_FILL.get();
            case REPLACE -> PLANNER_MONEY_PER_BLOCK_REPLACE.get();
        };
    }

    public static boolean plannerXpGainEnabled() {
        return PLANNER_XP_GAIN.get();
    }

    public static int plannerXpPerBlock() {
        return PLANNER_XP_PER_BLOCK.get();
    }

    public static boolean plannerPauseAtNight() {
        return PLANNER_PAUSE_AT_NIGHT.get();
    }

    private static boolean isStringEntry(Object value) {
        return value instanceof String string && !string.isBlank();
    }

    private static List<String> copyStringList(List<? extends String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
