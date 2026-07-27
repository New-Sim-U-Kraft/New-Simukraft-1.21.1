package common.cn.kafei.simukraft.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import common.cn.kafei.simukraft.building.BuildingBuiltinResourceService;
import common.cn.kafei.simukraft.building.BuildingPackageCatalog;
import common.cn.kafei.simukraft.citizen.CitizenLevelService;
import common.cn.kafei.simukraft.citizen.CitizenSkillSnapshot;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionInviteService;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiService;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.commercial.CommercialBoxManager;
import common.cn.kafei.simukraft.commercial.CommercialDefinitionLoader;
import common.cn.kafei.simukraft.commercial.CommercialStockManager;
import common.cn.kafei.simukraft.farmland.FarmlandBoxManager;
import common.cn.kafei.simukraft.industrial.IndustrialBoxManager;
import common.cn.kafei.simukraft.industrial.IndustrialDefinitionLoader;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.medical.DiseaseType;
import common.cn.kafei.simukraft.medical.MedicalDefinitionLoader;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.network.building.BuildingCacheReloadPacket;
import common.cn.kafei.simukraft.network.hud.HudSyncService;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.CitizenWanderService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import common.cn.kafei.simukraft.city.CityMemberData;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public final class SimuKraftCommand {
    private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));

    private SimuKraftCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("simukraft");
        root.then(Commands.literal("reload")
                .requires(source -> source.hasPermission(2))
                .executes(context -> reload(context.getSource()))
                .then(Commands.literal("database")
                        .executes(context -> reloadDatabase(context.getSource())))
                .then(Commands.literal("buildings")
                        .executes(context -> reloadOfficialBuildings(context.getSource()))));
        root.then(Commands.literal("city")
                .then(Commands.literal("permission")
                        .then(Commands.literal("accept")
                                .then(Commands.argument("inviteId", StringArgumentType.word())
                                        .executes(context -> respondPermissionInvite(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "inviteId"),
                                                true))))
                        .then(Commands.literal("reject")
                                .then(Commands.argument("inviteId", StringArgumentType.word())
                                        .executes(context -> respondPermissionInvite(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "inviteId"),
                                                false)))))
                .then(Commands.literal("mayor")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("transfer")
                                .then(Commands.argument("from", EntityArgument.player())
                                        .then(Commands.argument("to", EntityArgument.player())
                                                .executes(context -> transferMayor(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "from"),
                                                        EntityArgument.getPlayer(context, "to"))))))
                        .then(Commands.literal("transfer-by-name")
                                .then(Commands.argument("cityName", StringArgumentType.string())
                                        .then(Commands.argument("to", EntityArgument.player())
                                                .executes(context -> transferMayorByName(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "cityName"),
                                                        EntityArgument.getPlayer(context, "to")))))))
                .then(Commands.literal("funds")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                        .executes(context -> addFundsToSelfCity(
                                                context.getSource(),
                                                DoubleArgumentType.getDouble(context, "amount")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> addFundsToPlayerCity(
                                                        context.getSource(),
                                                        DoubleArgumentType.getDouble(context, "amount"),
                                                        EntityArgument.getPlayer(context, "player"))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                        .executes(context -> removeFundsFromSelfCity(
                                                context.getSource(),
                                                DoubleArgumentType.getDouble(context, "amount")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> removeFundsFromPlayerCity(
                                                        context.getSource(),
                                                        DoubleArgumentType.getDouble(context, "amount"),
                                                        EntityArgument.getPlayer(context, "player"))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D))
                                        .executes(context -> setFundsForSelfCity(
                                                context.getSource(),
                                                DoubleArgumentType.getDouble(context, "amount")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> setFundsForPlayerCity(
                                                        context.getSource(),
                                                        DoubleArgumentType.getDouble(context, "amount"),
                                                        EntityArgument.getPlayer(context, "player")))))))
);
        root.then(Commands.literal("path")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("test")
                        .then(Commands.argument("target", Vec3Argument.vec3())
                                .executes(context -> testNearestCitizenPath(
                                        context.getSource(),
                                        Vec3Argument.getVec3(context, "target"))))
                        .then(Commands.argument("citizen", EntityArgument.entity())
                                .then(Commands.argument("target", Vec3Argument.vec3())
                                        .executes(context -> testSelectedCitizenPath(
                                                context.getSource(),
                                                EntityArgument.getEntity(context, "citizen"),
                                                Vec3Argument.getVec3(context, "target"))))))
                .then(Commands.literal("stress")
                        .then(Commands.argument("target", Vec3Argument.vec3())
                                .executes(context -> stressCityPath(
                                        context.getSource(),
                                        Vec3Argument.getVec3(context, "target"),
                                        -1))
                                .then(Commands.argument("spreadRadius", IntegerArgumentType.integer(0, 128))
                                        .executes(context -> stressCityPath(
                                                context.getSource(),
                                                Vec3Argument.getVec3(context, "target"),
                                                IntegerArgumentType.getInteger(context, "spreadRadius"))))))
                .then(Commands.literal("wander")
                        .executes(context -> wanderCityCitizens(context.getSource(), 12))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 64))
                                .executes(context -> wanderCityCitizens(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("matrix")
                        .executes(context -> matrixHomeCityPath(context.getSource(), 3))
                        .then(Commands.argument("spacing", IntegerArgumentType.integer(1, 16))
                                .executes(context -> matrixHomeCityPath(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "spacing")))))
                .then(Commands.literal("status")
                        .executes(context -> pathStatus(context.getSource())))
                .then(Commands.literal("clear")
                        .executes(context -> clearPathDebug(context.getSource()))));
        root.then(Commands.literal("npc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 500))
                                .executes(context -> spawnCitizensInSelfCity(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("disease")
                        .then(Commands.literal("set")
                                .then(Commands.argument("citizen", EntityArgument.entity())
                                        .then(Commands.argument("disease", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("generic", "cold", "flu", "food_poisoning"), builder))
                                                .executes(context -> setCitizenDisease(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "citizen"),
                                                        StringArgumentType.getString(context, "disease"))))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("citizen", EntityArgument.entity())
                                        .executes(context -> clearCitizenDisease(
                                                context.getSource(),
                                                EntityArgument.getEntity(context, "citizen"))))))
                .then(Commands.literal("xp")
                        .then(Commands.literal("add")
                                .then(Commands.argument("citizen", EntityArgument.entity())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(context -> addNpcXp(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "citizen"),
                                                        IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("citizen", EntityArgument.entity())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(context -> removeNpcXp(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "citizen"),
                                                        IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("citizen", EntityArgument.entity())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(context -> setNpcXp(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "citizen"),
                                                        IntegerArgumentType.getInteger(context, "amount")))))))
                .then(Commands.literal("level")
                        .then(Commands.literal("add")
                                .then(Commands.argument("citizen", EntityArgument.entity())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> addNpcLevel(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "citizen"),
                                                        IntegerArgumentType.getInteger(context, "count"))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("citizen", EntityArgument.entity())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> removeNpcLevel(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "citizen"),
                                                        IntegerArgumentType.getInteger(context, "count"))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("citizen", EntityArgument.entity())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                .executes(context -> setNpcLevel(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "citizen"),
                                                        IntegerArgumentType.getInteger(context, "level"))))))));
        dispatcher.register(root);
    }

    /** addNpcXp：给选中 NPC 增加 XP。 */
    private static int addNpcXp(CommandSourceStack source, Entity entity, int amount) {
        CitizenData citizen = resolveCommandCitizen(source, entity);
        if (citizen == null) return 0;
        ServerLevel level = (ServerLevel) entity.level();
        CitizenLevelService.LevelUpdateResult result = CitizenLevelService.addExperience(level, citizen.uuid(), CityJobType.RESIDENT, amount);
        CitizenSkillSnapshot after = result.after();
        source.sendSuccess(() -> Component.translatable("message.simukraft.command.npc_xp.added",
                citizen.name(), amount, result.before().xp(), after.xp(), after.level()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** removeNpcXp：从选中 NPC 扣除 XP（下限 0）。 */
    private static int removeNpcXp(CommandSourceStack source, Entity entity, int amount) {
        CitizenData citizen = resolveCommandCitizen(source, entity);
        if (citizen == null) return 0;
        ServerLevel level = (ServerLevel) entity.level();
        CitizenLevelService.LevelUpdateResult result = CitizenLevelService.removeExperience(level, citizen.uuid(), CityJobType.RESIDENT, amount);
        CitizenSkillSnapshot after = result.after();
        source.sendSuccess(() -> Component.translatable("message.simukraft.command.npc_xp.removed",
                citizen.name(), amount, result.before().xp(), after.xp(), after.level()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** setNpcXp：将选中 NPC 的 XP 直接设为指定值。 */
    private static int setNpcXp(CommandSourceStack source, Entity entity, int amount) {
        CitizenData citizen = resolveCommandCitizen(source, entity);
        if (citizen == null) return 0;
        ServerLevel level = (ServerLevel) entity.level();
        CitizenLevelService.LevelUpdateResult result = CitizenLevelService.setExperience(level, citizen.uuid(), CityJobType.RESIDENT, amount);
        CitizenSkillSnapshot after = result.after();
        source.sendSuccess(() -> Component.translatable("message.simukraft.command.npc_xp.set",
                citizen.name(), after.xp(), after.level()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** addNpcLevel：给选中 NPC 增加等级。 */
    private static int addNpcLevel(CommandSourceStack source, Entity entity, int count) {
        CitizenData citizen = resolveCommandCitizen(source, entity);
        if (citizen == null) return 0;
        ServerLevel level = (ServerLevel) entity.level();
        CitizenSkillSnapshot current = CitizenLevelService.snapshot(citizen, CityJobType.RESIDENT);
        CitizenLevelService.LevelUpdateResult result = CitizenLevelService.setLevel(level, citizen.uuid(), CityJobType.RESIDENT, current.level() + count);
        source.sendSuccess(() -> Component.translatable("message.simukraft.command.npc_level.added",
                citizen.name(), count, result.before().level(), result.after().level()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** removeNpcLevel：从选中 NPC 扣除等级（下限 1）。 */
    private static int removeNpcLevel(CommandSourceStack source, Entity entity, int count) {
        CitizenData citizen = resolveCommandCitizen(source, entity);
        if (citizen == null) return 0;
        ServerLevel level = (ServerLevel) entity.level();
        CitizenSkillSnapshot current = CitizenLevelService.snapshot(citizen, CityJobType.RESIDENT);
        CitizenLevelService.LevelUpdateResult result = CitizenLevelService.setLevel(level, citizen.uuid(), CityJobType.RESIDENT, current.level() - count);
        source.sendSuccess(() -> Component.translatable("message.simukraft.command.npc_level.removed",
                citizen.name(), count, result.before().level(), result.after().level()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** setNpcLevel：将选中 NPC 的等级直接设为指定值。 */
    private static int setNpcLevel(CommandSourceStack source, Entity entity, int targetLevel) {
        CitizenData citizen = resolveCommandCitizen(source, entity);
        if (citizen == null) return 0;
        ServerLevel level = (ServerLevel) entity.level();
        CitizenLevelService.LevelUpdateResult result = CitizenLevelService.setLevel(level, citizen.uuid(), CityJobType.RESIDENT, targetLevel);
        CitizenSkillSnapshot after = result.after();
        source.sendSuccess(() -> Component.translatable("message.simukraft.command.npc_level.set",
                citizen.name(), after.level()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** setCitizenDisease：给选中的存活 NPC 设置测试疾病并立即持久化。 */
    private static int setCitizenDisease(CommandSourceStack source, Entity entity, String diseaseName) {
        CitizenData citizen = resolveCommandCitizen(source, entity);
        if (citizen == null) {
            return 0;
        }
        DiseaseType disease = DiseaseType.fromName(diseaseName);
        if (disease == DiseaseType.NONE) {
            source.sendFailure(Component.translatable("message.simukraft.command.npc_disease.invalid", diseaseName));
            return 0;
        }
        ServerLevel level = (ServerLevel) entity.level();
        citizen.setDisease(disease, level.getDayTime() / 24_000L);
        CitizenService.save(level, citizen.uuid());
        source.sendSuccess(() -> Component.translatable(
                "message.simukraft.command.npc_disease.set",
                citizen.name(),
                Component.translatable(disease.translationKey())), true);
        return Command.SINGLE_SUCCESS;
    }

    /** clearCitizenDisease：清除选中 NPC 的测试疾病并立即持久化。 */
    private static int clearCitizenDisease(CommandSourceStack source, Entity entity) {
        CitizenData citizen = resolveCommandCitizen(source, entity);
        if (citizen == null) {
            return 0;
        }
        ServerLevel level = (ServerLevel) entity.level();
        citizen.clearDisease();
        CitizenService.save(level, citizen.uuid());
        source.sendSuccess(() -> Component.translatable(
                "message.simukraft.command.npc_disease.cleared", citizen.name()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** resolveCommandCitizen：校验命令目标并解析为可修改的存活 NPC 数据。 */
    private static CitizenData resolveCommandCitizen(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof CitizenEntity citizenEntity) || !(entity.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.translatable("message.simukraft.command.npc_disease.selected_not_citizen"));
            return null;
        }
        CitizenData citizen = CitizenService.ensureCitizen(level, citizenEntity);
        if (citizen == null || citizen.dead()) {
            source.sendFailure(Component.translatable("message.simukraft.command.npc_disease.unavailable"));
            return null;
        }
        return citizen;
    }

    private static int matrixHomeCityPath(CommandSourceStack source, int spacing) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.simukraft.path_debug.player_required"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Optional<CityData> city = CityService.findPlayerCity(level, player.getUUID());
        if (city.isEmpty()) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_required"));
            return 0;
        }
        List<CitizenData> citizens = CitizenService.listCitizensByCity(level, city.get().cityId());
        Vec3 center = player.position();
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(citizens.size())));
        int assigned = 0;
        for (int i = 0; i < citizens.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            double x = center.x + (col - cols / 2.0D + 0.5D) * spacing;
            double z = center.z + (row - cols / 2.0D + 0.5D) * spacing;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
            BlockPos homePos = new BlockPos((int) x, y, (int) z);
            CityPoiData home = CityPoiService.registerPoi(level, city.get().cityId(), homePos, CityPoiType.RESIDENTIAL, 1);
            CitizenService.setHome(level, citizens.get(i).uuid(), home.poiId());
            CitizenNavigationService.requestTestMove(level, citizens.get(i).uuid(), new Vec3(x, y, z), MovementIntent.RETURN_HOME);
            assigned++;
        }
        final int count = assigned;
        final int total = citizens.size();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "matrix home: %d/%d NPCs assigned, %dx%d grid spacing=%d", count, total, cols, cols, spacing)), true);
        return assigned > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    // respondPermissionInvite: 处理聊天点击触发的城市权限邀请接受/拒绝命令。
    private static int respondPermissionInvite(CommandSourceStack source, String inviteId, boolean accepted) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.simukraft.path_debug.player_required"));
            return 0;
        }
        return CityPermissionInviteService.respond(player, inviteId, accepted) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int reload(CommandSourceStack source) {
        BuildingCatalog.reload();
        CommercialDefinitionLoader.clearCache();
        IndustrialDefinitionLoader.clearCache();
        MedicalDefinitionLoader.clearCache();
        int count = 0;
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, new BuildingCacheReloadPacket());
            count++;
        }
        final int reloadedPlayerCount = count;
        source.sendSuccess(() -> Component.translatable("message.simukraft.reload.success", reloadedPlayerCount), true);
        return 1;
    }

    private static int reloadDatabase(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        CitizenManager.get(overworld).saveToSqlite(overworld);
        for (ServerLevel level : source.getServer().getAllLevels()) {
            CityManager.get(level).saveToSqlite(level);
            CityChunkManager.get(level).saveToSqlite(level);
            CityPoiManager.get(level).saveToSqlite(level);
            CommercialBoxManager.get(level).saveToSqlite(level);
            CommercialStockManager.get(level).saveToSqlite(level);
            IndustrialBoxManager.get(level).saveToSqlite(level);
            FarmlandBoxManager.get(level).saveToSqlite(level);
            LogisticsManager.get(level).saveToSqlite(level);
        }
        CitizenManager.get(overworld).reloadFromSqlite(overworld);
        for (ServerLevel level : source.getServer().getAllLevels()) {
            CityManager.get(level).reloadFromSqlite(level);
            CityChunkManager.get(level).reloadFromSqlite(level);
            CityPoiManager.get(level).reloadFromSqlite(level);
            CommercialBoxManager.get(level).reloadFromSqlite(level);
            CommercialStockManager.get(level).reloadFromSqlite(level);
            IndustrialBoxManager.get(level).reloadFromSqlite(level);
            FarmlandBoxManager.get(level).reloadFromSqlite(level);
            LogisticsManager.get(level).reloadFromSqlite(level);
        }
        source.sendSuccess(() -> Component.translatable("message.simukraft.reload.database.success"), true);
        return Command.SINGLE_SUCCESS;
    }

    /** reloadOfficialBuildings：强制用 jar 内置包覆写 official_building.zip，然后重载建筑目录并同步所有客户端。 */
    private static int reloadOfficialBuildings(CommandSourceStack source) {
        BuildingBuiltinResourceService.forceOverwrite(BuildingPackageCatalog.rootDirectory());
        BuildingCatalog.reload();
        int count = 0;
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, new BuildingCacheReloadPacket());
            count++;
        }
        final int syncedCount = count;
        source.sendSuccess(() -> Component.translatable("message.simukraft.reload.buildings.success", syncedCount), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int testNearestCitizenPath(CommandSourceStack source, Vec3 target) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.simukraft.path_debug.player_required"));
            return 0;
        }
        return CitizenNavigationService.debugPathTo(player, target) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int testSelectedCitizenPath(CommandSourceStack source, Entity entity, Vec3 target) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.simukraft.path_debug.player_required"));
            return 0;
        }
        if (!(entity instanceof CitizenEntity citizen)) {
            source.sendFailure(Component.translatable("message.simukraft.path_debug.selected_not_citizen"));
            return 0;
        }
        return CitizenNavigationService.debugPathTo(player, citizen, target) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int clearPathDebug(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.simukraft.path_debug.player_required"));
            return 0;
        }
        CitizenNavigationService.clearDebugPath(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int pathStatus(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.simukraft.path_debug.player_required"));
            return 0;
        }
        return CitizenNavigationService.sendStatus(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int spawnCitizensInSelfCity(CommandSourceStack source, int count) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.simukraft.path_debug.player_required"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Optional<CityData> city = CityService.findPlayerCity(level, player.getUUID());
        if (city.isEmpty()) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_required"));
            return 0;
        }
        int spawned = 0;
        for (int index = 0; index < count; index++) {
            BlockPos spawnGround = CitizenWanderService.randomSpawnGround(level, player.blockPosition().below(), 10);
            if (spawnGround != null && CitizenService.spawnCitizen(level, spawnGround, city.get().cityId(), true).isPresent()) {
                spawned++;
            }
        }
        final int spawnedCount = spawned;
        source.sendSuccess(() -> Component.translatable("message.simukraft.command.city_citizens.spawned", spawnedCount, city.get().cityName()), true);
        return spawned > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int stressCityPath(CommandSourceStack source, Vec3 target, int spreadRadius) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.simukraft.path_debug.player_required"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Optional<CityData> city = CityService.findPlayerCity(level, player.getUUID());
        if (city.isEmpty()) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_required"));
            return 0;
        }
        List<CitizenData> citizens = CitizenService.listCitizensByCity(level, city.get().cityId());
        int resolvedSpreadRadius = resolveStressSpreadRadius(spreadRadius, citizens.size());
        int total = 0;
        int loaded = 0;
        int requested = 0;
        for (CitizenData citizen : citizens) {
            total++;
            if (CitizenTeleportService.findCitizenEntity(level, citizen.uuid()) == null) {
                continue;
            }
            Vec3 assignedTarget = stressTarget(level, target, loaded, citizens.size(), resolvedSpreadRadius);
            loaded++;
            if (CitizenNavigationService.requestTestMove(level, citizen.uuid(), assignedTarget, MovementIntent.RUN)) {
                requested++;
            }
        }
        final int totalCount = total;
        final int loadedCount = loaded;
        final int requestedCount = requested;
        final int spreadCount = resolvedSpreadRadius;
        source.sendSuccess(() -> Component.translatable("message.simukraft.command.path_stress.started", requestedCount, loadedCount, totalCount, spreadCount), true);
        return requested > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int resolveStressSpreadRadius(int configuredRadius, int citizenCount) {
        if (configuredRadius >= 0) {
            return configuredRadius;
        }
        return Math.clamp((int) Math.ceil(Math.sqrt(Math.max(1, citizenCount)) * 0.9D), 8, 64);
    }

    private static Vec3 stressTarget(ServerLevel level, Vec3 center, int index, int total, int spreadRadius) {
        if (spreadRadius <= 0) {
            return center;
        }
        double progress = Math.sqrt((index + 0.5D) / Math.max(1.0D, total));
        double radius = Math.max(1.0D, spreadRadius * progress);
        double angle = index * GOLDEN_ANGLE;
        int baseX = (int) Math.round(center.x + Math.cos(angle) * radius);
        int baseZ = (int) Math.round(center.z + Math.sin(angle) * radius);
        Vec3 openTarget = findOpenGroundTarget(level, baseX, baseZ);
        if (openTarget != null) {
            return openTarget;
        }
        Vec3 fallback = CitizenWanderService.randomTarget(level, center, spreadRadius);
        return fallback != null ? fallback : center;
    }

    private static Vec3 findOpenGroundTarget(ServerLevel level, int baseX, int baseZ) {
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = baseX + dx;
                    int z = baseZ + dz;
                    if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                        continue;
                    }
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos feet = new BlockPos(x, y, z);
                    if (isOpenForStressTarget(level, feet)) {
                        return new Vec3(x + 0.5D, y, z + 0.5D);
                    }
                }
            }
        }
        return null;
    }

    private static boolean isOpenForStressTarget(ServerLevel level, BlockPos feet) {
        return feet != null
                && level.isEmptyBlock(feet)
                && level.isEmptyBlock(feet.above())
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    private static int wanderCityCitizens(CommandSourceStack source, int radius) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.simukraft.path_debug.player_required"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Optional<CityData> city = CityService.findPlayerCity(level, player.getUUID());
        if (city.isEmpty()) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_required"));
            return 0;
        }
        int requested = CitizenWanderService.requestCityWander(level, city.get().cityId(), radius, 500);
        final int requestedCount = requested;
        source.sendSuccess(() -> Component.translatable("message.simukraft.command.path_wander.started", requestedCount, radius), true);
        return requested > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    // 给命令执行者所属城市加款；控制台必须显式指定玩家。
    private static int addFundsToSelfCity(CommandSourceStack source, double amount) {
        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer == null) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.player_required"));
            return 0;
        }
        return addFundsToPlayerCity(source, amount, sourcePlayer);
    }

    // 给指定在线玩家所属城市加款，并记录财政流水。
    private static int addFundsToPlayerCity(CommandSourceStack source, double amount, ServerPlayer targetPlayer) {
        if (targetPlayer == null) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.player_required"));
            return 0;
        }
        double normalizedAmount = EconomyService.normalizeAmount(amount);
        if (normalizedAmount <= 0.0D) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.invalid_amount"));
            return 0;
        }
        ServerLevel level = targetPlayer.serverLevel();
        Optional<CityData> city = CityService.findPlayerCity(level, targetPlayer.getUUID());
        if (city.isEmpty()) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.no_city", targetPlayer.getGameProfile().getName()));
            return 0;
        }
        ServerPlayer actor = source.getPlayer();
        if (!EconomyService.depositCityFunds(level, city.get().cityId(), actor, normalizedAmount, "command_add_funds")) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.failed"));
            return 0;
        }
        syncCityMembersHud(level, city.get());
        double balance = EconomyService.getCityBalance(level, city.get().cityId());
        String amountText = String.format(Locale.ROOT, "%.2f", normalizedAmount);
        String balanceText = String.format(Locale.ROOT, "%.2f", balance);
        source.sendSuccess(() -> Component.translatable(
                "message.simukraft.command.city_funds.added",
                amountText,
                city.get().cityName(),
                targetPlayer.getGameProfile().getName(),
                balanceText
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int removeFundsFromSelfCity(CommandSourceStack source, double amount) {
        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer == null) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.player_required"));
            return 0;
        }
        return removeFundsFromPlayerCity(source, amount, sourcePlayer);
    }

    private static int removeFundsFromPlayerCity(CommandSourceStack source, double amount, ServerPlayer targetPlayer) {
        if (targetPlayer == null) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.player_required"));
            return 0;
        }
        double normalizedAmount = EconomyService.normalizeAmount(amount);
        if (normalizedAmount <= 0.0D) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.invalid_amount"));
            return 0;
        }
        ServerLevel level = targetPlayer.serverLevel();
        Optional<CityData> city = CityService.findPlayerCity(level, targetPlayer.getUUID());
        if (city.isEmpty()) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.no_city", targetPlayer.getGameProfile().getName()));
            return 0;
        }
        ServerPlayer actor = source.getPlayer();
        if (!EconomyService.withdrawCityFunds(level, city.get().cityId(), actor, normalizedAmount, "command_remove_funds")) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.failed"));
            return 0;
        }
        syncCityMembersHud(level, city.get());
        double balance = EconomyService.getCityBalance(level, city.get().cityId());
        String amountText = String.format(Locale.ROOT, "%.2f", normalizedAmount);
        String balanceText = String.format(Locale.ROOT, "%.2f", balance);
        source.sendSuccess(() -> Component.translatable(
                "message.simukraft.command.city_funds.removed",
                amountText,
                city.get().cityName(),
                targetPlayer.getGameProfile().getName(),
                balanceText
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setFundsForSelfCity(CommandSourceStack source, double amount) {
        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer == null) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.player_required"));
            return 0;
        }
        return setFundsForPlayerCity(source, amount, sourcePlayer);
    }

    private static int setFundsForPlayerCity(CommandSourceStack source, double amount, ServerPlayer targetPlayer) {
        if (targetPlayer == null) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.player_required"));
            return 0;
        }
        double normalizedAmount = EconomyService.normalizeAmount(amount);
        ServerLevel level = targetPlayer.serverLevel();
        Optional<CityData> city = CityService.findPlayerCity(level, targetPlayer.getUUID());
        if (city.isEmpty()) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.no_city", targetPlayer.getGameProfile().getName()));
            return 0;
        }
        if (!CityService.setFunds(level, city.get().cityId(), normalizedAmount)) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.failed"));
            return 0;
        }
        syncCityMembersHud(level, city.get());
        String balanceText = String.format(Locale.ROOT, "%.2f", normalizedAmount);
        source.sendSuccess(() -> Component.translatable(
                "message.simukraft.command.city_funds.set",
                city.get().cityName(),
                targetPlayer.getGameProfile().getName(),
                balanceText
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    /** transferMayorByName: OP 通过城市名（支持离线市长）强制将市长转让给目标在线玩家。 */
    private static int transferMayorByName(CommandSourceStack source, String cityName, ServerPlayer toPlayer) {
        ServerLevel level = source.getServer().overworld();
        Optional<CityData> cityOpt = CityService.findCityByName(level, cityName);
        if (cityOpt.isEmpty()) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_mayor.city_not_found", cityName));
            return 0;
        }
        CityData city = cityOpt.get();
        // 找出当前市长成员（支持离线）
        CityMemberData mayorMember = city.members().stream()
                .filter(m -> m.permissionLevel() == CityPermissionLevel.MAYOR)
                .findFirst()
                .orElse(null);
        if (mayorMember == null) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_mayor.failed"));
            return 0;
        }
        UUID mayorId = mayorMember.playerId();
        String mayorName = mayorMember.playerName();
        Optional<CityData> targetCityOpt = CityService.findPlayerCity(level, toPlayer.getUUID());
        if (targetCityOpt.isPresent()) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_mayor.target_has_city",
                    toPlayer.getGameProfile().getName(), targetCityOpt.get().cityName()));
            return 0;
        }
        boolean ok = CityService.transferMayor(level, city.cityId(), mayorId,
                toPlayer.getUUID(), toPlayer.getGameProfile().getName());
        if (!ok) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_mayor.failed"));
            return 0;
        }
        syncCityMembersHud(level, city);
        source.sendSuccess(() -> Component.translatable("message.simukraft.command.city_mayor.success",
                city.cityName(),
                mayorName,
                toPlayer.getGameProfile().getName()), true);
        return Command.SINGLE_SUCCESS;
    }

    /** transferMayor: OP 强制将指定城市市长身份转让给目标玩家（目标不能已有城市）。 */
    private static int transferMayor(CommandSourceStack source, ServerPlayer fromPlayer, ServerPlayer toPlayer) {
        ServerLevel level = fromPlayer.serverLevel();
        Optional<CityData> cityOpt = CityService.findPlayerCity(level, fromPlayer.getUUID());
        if (cityOpt.isEmpty()) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_mayor.no_city", fromPlayer.getGameProfile().getName()));
            return 0;
        }
        CityData city = cityOpt.get();
        if (!city.hasPermission(fromPlayer.getUUID(), CityPermissionLevel.MAYOR)) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_mayor.not_mayor",
                    fromPlayer.getGameProfile().getName(), city.cityName()));
            return 0;
        }
        Optional<CityData> targetCityOpt = CityService.findPlayerCity(level, toPlayer.getUUID());
        if (targetCityOpt.isPresent()) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_mayor.target_has_city",
                    toPlayer.getGameProfile().getName(), targetCityOpt.get().cityName()));
            return 0;
        }
        boolean ok = CityService.transferMayor(level, city.cityId(), fromPlayer.getUUID(),
                toPlayer.getUUID(), toPlayer.getGameProfile().getName());
        if (!ok) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_mayor.failed"));
            return 0;
        }
        syncCityMembersHud(level, city);
        source.sendSuccess(() -> Component.translatable("message.simukraft.command.city_mayor.success",
                city.cityName(),
                fromPlayer.getGameProfile().getName(),
                toPlayer.getGameProfile().getName()), true);
        return Command.SINGLE_SUCCESS;
    }

    // 同一城市的在线成员都需要马上看到新余额。
    private static void syncCityMembersHud(ServerLevel level, CityData city) {
        if (level == null || city == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (city.member(player.getUUID()).isPresent()) {
                HudSyncService.syncToPlayer(player, true);
            }
        }
    }
}
