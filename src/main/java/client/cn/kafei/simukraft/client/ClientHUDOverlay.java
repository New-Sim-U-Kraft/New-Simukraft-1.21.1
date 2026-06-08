package client.cn.kafei.simukraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Locale;
import java.util.Objects;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class ClientHUDOverlay {
    private static final int HUD_COLOR = 0xFFFFFF;
    private static final String[] WEEKDAYS = {
            "weekday.sunday",
            "weekday.monday",
            "weekday.tuesday",
            "weekday.wednesday",
            "weekday.thursday",
            "weekday.friday",
            "weekday.saturday"
    };
    private static String cachedDisplayText = "";
    private static int cachedTextWidth = 0;
    private static int cachedDay = Integer.MIN_VALUE;
    private static int cachedWorldPopulation = Integer.MIN_VALUE;
    private static String cachedCityName = "";
    private static double cachedFunds = Double.NaN;
    private static int cachedCityPopulation = Integer.MIN_VALUE;
    private static CityPermissionLevel cachedPermissionLevel = CityPermissionLevel.CITIZEN;
    private static boolean cachedCreativeMode = false;

    private ClientHUDOverlay() {
    }

    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Objects.requireNonNull(Minecraft.getInstance());
        if (!ClientConfig.hudEnabled() || minecraft.player == null || minecraft.screen != null || minecraft.gui.getDebugOverlay().showDebugScreen()) {
            return;
        }

        try {
            int currentDay = ClientSimukraftData.getCurrentDay();
            int worldPopulation = ClientSimukraftData.getCurrentPopulation();
            String cityName = ClientSimukraftData.getCurrentCityName();
            double funds = ClientSimukraftData.getCurrentCityFunds();
            int cityPopulation = ClientSimukraftData.getCurrentCityPopulation();
            CityPermissionLevel permissionLevel = ClientSimukraftData.getPermissionLevel();
            boolean creativeMode = ClientSimukraftData.isCreativeMode();
            var font = Objects.requireNonNull(minecraft.font);

            String displayText = getOrBuildDisplayText(font, currentDay, worldPopulation, cityName, funds, cityPopulation, permissionLevel, creativeMode);
            GuiGraphics guiGraphics = event.getGuiGraphics();
            int[] position = ClientHUDConfig.calculatePosition(guiGraphics.guiWidth(), guiGraphics.guiHeight(), cachedTextWidth);

            guiGraphics.drawString(font, displayText, position[0], position[1], HUD_COLOR, true);
        } catch (RuntimeException ignored) {
        }
    }

    private static String getOrBuildDisplayText(net.minecraft.client.gui.Font font, int currentDay, int worldPopulation, String cityName, double funds, int cityPopulation, CityPermissionLevel permissionLevel, boolean creativeMode) {
        String safeCityName = safeText(cityName);
        if (currentDay == cachedDay
                && worldPopulation == cachedWorldPopulation
                && cityPopulation == cachedCityPopulation
                && permissionLevel == cachedPermissionLevel
                && creativeMode == cachedCreativeMode
                && Double.compare(funds, cachedFunds) == 0
                && safeCityName.equals(cachedCityName)) {
            return cachedDisplayText;
        }

        cachedDay = currentDay;
        cachedWorldPopulation = worldPopulation;
        cachedCityName = safeCityName;
        cachedFunds = funds;
        cachedCityPopulation = cityPopulation;
        cachedPermissionLevel = permissionLevel;
        cachedCreativeMode = creativeMode;

        String weekDayKey = Objects.requireNonNull(WEEKDAYS[Math.floorMod(currentDay - 1, WEEKDAYS.length)]);
        String weekDay = Component.translatable(weekDayKey).getString();
        StringBuilder statusLine = new StringBuilder(128);

        if (!safeCityName.isEmpty()) {
            String fundsDisplay = String.format(Locale.US, "%.2f", funds);
            statusLine.append(permissionPrefix(permissionLevel)).append(' ');
            statusLine.append(Component.translatable("hud.simukraft.city", safeCityName).getString()).append(" | ");
            statusLine.append(Component.translatable("hud.simukraft.funds", fundsDisplay).getString()).append(" | ");
            statusLine.append(weekDay).append(" | ");
            statusLine.append(Component.translatable("hud.simukraft.world_population", worldPopulation).getString()).append(" | ");
            statusLine.append(Component.translatable("hud.simukraft.city_population", cityPopulation).getString());
        } else {
            statusLine.append(weekDay).append(" | ");
            statusLine.append(Component.translatable("hud.simukraft.world_population", worldPopulation).getString());
        }

        cachedDisplayText = statusLine.toString();
        cachedTextWidth = font.width(cachedDisplayText);
        return cachedDisplayText;
    }

    // permissionPrefix: 按真实城市权限生成 HUD 前缀。
    private static String permissionPrefix(CityPermissionLevel permissionLevel) {
        CityPermissionLevel safeLevel = permissionLevel != null ? permissionLevel : CityPermissionLevel.CITIZEN;
        String key = "hud.simukraft.permission." + safeLevel.name().toLowerCase(Locale.ROOT);
        return "[" + Component.translatable(key).getString() + "]";
    }

    private static String safeText(String value) {
        return value != null ? value : "";
    }

    // 清理 HUD 文本缓存，切换存档后强制重新按新世界数据计算宽度和内容。
    public static void resetCache() {
        cachedDisplayText = "";
        cachedTextWidth = 0;
        cachedDay = Integer.MIN_VALUE;
        cachedWorldPopulation = Integer.MIN_VALUE;
        cachedCityName = "";
        cachedFunds = Double.NaN;
        cachedCityPopulation = Integer.MIN_VALUE;
        cachedPermissionLevel = CityPermissionLevel.CITIZEN;
        cachedCreativeMode = false;
    }
}
