package common.cn.kafei.simukraft.event;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingPackVersionChecker;
import common.cn.kafei.simukraft.registry.ModSoundEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("null")
public final class PlayerWelcomeService {
    private static final ResourceLocation FIRST_DREAM_ADVANCEMENT_ID =
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "story/first_dream");
    private static final String FIRST_DREAM_CRITERION = "first_join";
    private static final String FIRST_DREAM_PLAYED_TAG = "simukraft_first_dream_played";
    private static final int FIRST_DREAM_START_DELAY_TICKS = 40;
    private static final int FIRST_DREAM_GRANT_DELAY_TICKS = 220;
    private static final Map<UUID, FirstDreamPendingState> PENDING_FIRST_DREAM = new ConcurrentHashMap<>();

    private PlayerWelcomeService() {
    }

    // handleLogin：玩家进入世界时发送旧版欢迎消息，并调度首次梦境成就。
    public static void handleLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        sendWelcomeMessages(player);
        scheduleFirstDreamSequence(player);
    }

    // tick：延迟播放首次梦境音效并授予成就，避开玩家刚登录时客户端还没稳定的阶段。
    public static void tick(MinecraftServer server) {
        if (server == null || PENDING_FIRST_DREAM.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, FirstDreamPendingState> entry : PENDING_FIRST_DREAM.entrySet()) {
            FirstDreamPendingState state = entry.getValue();
            int remainingTicks = state.remainingTicks() - 1;
            if (remainingTicks > 0) {
                PENDING_FIRST_DREAM.put(entry.getKey(), state.withRemainingTicks(remainingTicks));
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                PENDING_FIRST_DREAM.remove(entry.getKey());
                continue;
            }

            if (state.stage() == FirstDreamStage.WAITING_TO_PLAY_SOUND) {
                player.playNotifySound(ModSoundEvents.FIRST_DREAM.get(), SoundSource.RECORDS, 1.0F, 1.0F);
                PENDING_FIRST_DREAM.put(entry.getKey(),
                        new FirstDreamPendingState(FirstDreamStage.WAITING_TO_GRANT_ADVANCEMENT,
                                FIRST_DREAM_GRANT_DELAY_TICKS));
                continue;
            }

            PENDING_FIRST_DREAM.remove(entry.getKey());
            grantFirstDreamAdvancement(player);
        }
    }

    public static void clearServerCaches(MinecraftServer server) {
        if (server == null) {
            return;
        }
        PENDING_FIRST_DREAM.clear();
    }

    private static void sendWelcomeMessages(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("message.simukraft.welcome.divider"));
        player.sendSystemMessage(Component.translatable("message.simukraft.welcome"));
        player.sendSystemMessage(Component.translatable("message.simukraft.welcome.refactor_edition"));
        player.sendSystemMessage(Component.translatable("message.simukraft.welcome.divider"));
        sendBuildingPackUpdateNotice(player);
    }

    /**
     * 建筑包版本检查提示（仅对 OP 显示）
     * 若本地 official_building.zip 版本低于 JAR 内版本，显示黄色提示并附可点击的覆写指令
     */
    private static void sendBuildingPackUpdateNotice(ServerPlayer player) {
        var local = BuildingPackVersionChecker.localVersion();
        var builtin = BuildingPackVersionChecker.builtinVersion();
        if (local.isEmpty() || builtin.isEmpty()) {
            return;
        }
        if (!BuildingPackVersionChecker.isOlderThan(local.get(), builtin.get())) {
            return;
        }
        String cmd = "/simukraft reload buildings";
        // 第一行：版本信息提示
        player.sendSystemMessage(Component.translatable(
                "message.simukraft.building_pack.outdated", local.get(), builtin.get())
                .withStyle(style -> style.withColor(ChatFormatting.YELLOW)));
        // 第二行：可点击的指令提示
        player.sendSystemMessage(Component.translatable("message.simukraft.building_pack.update_hint")
                .withStyle(style -> style.withColor(ChatFormatting.YELLOW))
                .append(Component.literal("[" + cmd + "]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.YELLOW)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal(cmd))))));
    }

    private static void scheduleFirstDreamSequence(ServerPlayer player) {
        if (hasPlayedFirstDream(player) || PENDING_FIRST_DREAM.containsKey(player.getUUID())) {
            return;
        }
        AdvancementHolder advancement = player.getServer().getAdvancements().get(FIRST_DREAM_ADVANCEMENT_ID);
        if (advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            markFirstDreamPlayed(player);
            return;
        }
        PENDING_FIRST_DREAM.put(player.getUUID(),
                new FirstDreamPendingState(FirstDreamStage.WAITING_TO_PLAY_SOUND, FIRST_DREAM_START_DELAY_TICKS));
    }

    private static void grantFirstDreamAdvancement(ServerPlayer player) {
        AdvancementHolder advancement = player.getServer().getAdvancements().get(FIRST_DREAM_ADVANCEMENT_ID);
        if (advancement == null) {
            SimuKraft.LOGGER.warn("Missing advancement: {}", FIRST_DREAM_ADVANCEMENT_ID);
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            markFirstDreamPlayed(player);
            return;
        }
        if (player.getAdvancements().award(advancement, FIRST_DREAM_CRITERION)) {
            markFirstDreamPlayed(player);
        }
    }

    private static boolean hasPlayedFirstDream(ServerPlayer player) {
        return player.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG)
                .getBoolean(FIRST_DREAM_PLAYED_TAG);
    }

    private static void markFirstDreamPlayed(ServerPlayer player) {
        CompoundTag persistedData = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persistedData.putBoolean(FIRST_DREAM_PLAYED_TAG, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistedData);
    }

    private enum FirstDreamStage {
        WAITING_TO_PLAY_SOUND,
        WAITING_TO_GRANT_ADVANCEMENT
    }

    private record FirstDreamPendingState(FirstDreamStage stage, int remainingTicks) {
        private FirstDreamPendingState withRemainingTicks(int updatedTicks) {
            return new FirstDreamPendingState(stage, updatedTicks);
        }
    }
}
