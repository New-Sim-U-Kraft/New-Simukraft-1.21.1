package client.cn.kafei.simukraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class ClientHUDOverlay {
    private static final int HUD_COLOR = 0xFFFFFF;
    private static final String SEPARATOR = " | ";
    private static final String[] WEEKDAYS = {
            "weekday.sunday", "weekday.monday", "weekday.tuesday", "weekday.wednesday",
            "weekday.thursday", "weekday.friday", "weekday.saturday"
    };

    // 字段缓存
    private static List<String> cachedFields = List.of();
    private static int cachedDay = Integer.MIN_VALUE;
    private static int cachedWorldPopulation = Integer.MIN_VALUE;
    private static String cachedCityName = "";
    private static double cachedFunds = Double.NaN;
    private static int cachedCityPopulation = Integer.MIN_VALUE;
    private static CityPermissionLevel cachedPermissionLevel = CityPermissionLevel.CITIZEN;
    private static boolean cachedCreativeMode = false;

    private ClientHUDOverlay() {}

    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Objects.requireNonNull(Minecraft.getInstance());
        if (!ClientConfig.hudEnabled() || mc.player == null || mc.screen != null
                || mc.gui.getDebugOverlay().showDebugScreen()) {
            return;
        }
        try {
            Font font = Objects.requireNonNull(mc.font);
            List<String> fields = getOrBuildFields(font,
                    ClientSimukraftData.getCurrentDay(),
                    ClientSimukraftData.getCurrentPopulation(),
                    ClientSimukraftData.getCurrentCityName(),
                    ClientSimukraftData.getCurrentCityFunds(),
                    ClientSimukraftData.getCurrentCityPopulation(),
                    ClientSimukraftData.getPermissionLevel(),
                    ClientSimukraftData.isCreativeMode());

            int maxWidth = ClientConfig.hudMaxWidth();
            List<String> lines = wrapFieldsToLines(font, fields, maxWidth);
            GuiGraphics g = event.getGuiGraphics();
            int widestLine = widestLineWidth(font, lines);
            int[] pos = ClientHUDConfig.calculatePosition(g.guiWidth(), g.guiHeight(), widestLine);
            ClientHUDConfig.Anchor anchor = ClientHUDConfig.getAnchor();
            int lineStep = font.lineHeight + 2;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int lw = font.width(line);
                int x = switch (anchor) {
                    case TOP_RIGHT, BOTTOM_RIGHT -> pos[0] + widestLine - lw;
                    case TOP_CENTER, BOTTOM_CENTER -> pos[0] + (widestLine - lw) / 2;
                    default -> pos[0];
                };
                g.drawString(font, line, x, pos[1] + i * lineStep, HUD_COLOR, true);
            }
        } catch (RuntimeException ignored) {}
    }

    /** getDisplayLines: 供编辑器使用，返回按指定宽度换行后的行列表。 */
    public static List<String> getDisplayLines(Font font, int maxWidth) {
        List<String> fields = getOrBuildFields(font,
                ClientSimukraftData.getCurrentDay(),
                ClientSimukraftData.getCurrentPopulation(),
                ClientSimukraftData.getCurrentCityName(),
                ClientSimukraftData.getCurrentCityFunds(),
                ClientSimukraftData.getCurrentCityPopulation(),
                ClientSimukraftData.getPermissionLevel(),
                ClientSimukraftData.isCreativeMode());
        return wrapFieldsToLines(font, fields, maxWidth);
    }

    /** getCurrentDisplayText: 兼容旧接口，返回单行完整文本（编辑器fallback用）。 */
    public static String getCurrentDisplayText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return "";
        List<String> fields = getOrBuildFields(mc.font,
                ClientSimukraftData.getCurrentDay(),
                ClientSimukraftData.getCurrentPopulation(),
                ClientSimukraftData.getCurrentCityName(),
                ClientSimukraftData.getCurrentCityFunds(),
                ClientSimukraftData.getCurrentCityPopulation(),
                ClientSimukraftData.getPermissionLevel(),
                ClientSimukraftData.isCreativeMode());
        return String.join(SEPARATOR, fields);
    }

    /** wrapFieldsToLines: 将字段列表按最大宽度分行，字段整体不拆断。0=不限制（单行）。 */
    public static List<String> wrapFieldsToLines(Font font, List<String> fields, int maxWidth) {
        if (maxWidth <= 0 || fields.isEmpty()) {
            return List.of(String.join(SEPARATOR, fields));
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lineWidth = 0;
        int sepWidth = font.width(SEPARATOR);
        for (String field : fields) {
            int fw = font.width(field);
            if (line.isEmpty()) {
                line.append(field);
                lineWidth = fw;
            } else if (lineWidth + sepWidth + fw <= maxWidth) {
                line.append(SEPARATOR).append(field);
                lineWidth += sepWidth + fw;
            } else {
                lines.add(line.toString());
                line = new StringBuilder(field);
                lineWidth = fw;
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    /** widestLineWidth: 返回多行中最宽一行的像素宽度。 */
    public static int widestLineWidth(Font font, List<String> lines) {
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        return w;
    }

    private static List<String> getOrBuildFields(Font font, int currentDay, int worldPopulation,
            String cityName, double funds, int cityPopulation,
            CityPermissionLevel permissionLevel, boolean creativeMode) {
        String safeCityName = safeText(cityName);
        if (currentDay == cachedDay
                && worldPopulation == cachedWorldPopulation
                && cityPopulation == cachedCityPopulation
                && permissionLevel == cachedPermissionLevel
                && creativeMode == cachedCreativeMode
                && Double.compare(funds, cachedFunds) == 0
                && safeCityName.equals(cachedCityName)) {
            return cachedFields;
        }
        cachedDay = currentDay;
        cachedWorldPopulation = worldPopulation;
        cachedCityName = safeCityName;
        cachedFunds = funds;
        cachedCityPopulation = cityPopulation;
        cachedPermissionLevel = permissionLevel;
        cachedCreativeMode = creativeMode;

        String weekDay = Component.translatable(WEEKDAYS[Math.floorMod(currentDay - 1, WEEKDAYS.length)]).getString();
        List<String> fields = new ArrayList<>();
        if (!safeCityName.isEmpty()) {
            String fundsDisplay = String.format(Locale.US, "%.2f", funds);
            // 权限+城市名合为一个字段，不可拆断
            fields.add(permissionPrefix(permissionLevel) + " " + Component.translatable("hud.simukraft.city", safeCityName).getString());
            fields.add(Component.translatable("hud.simukraft.funds", fundsDisplay).getString());
            fields.add(weekDay);
            fields.add(Component.translatable("hud.simukraft.world_population", worldPopulation).getString());
            fields.add(Component.translatable("hud.simukraft.city_population", cityPopulation).getString());
        } else {
            fields.add(weekDay);
            fields.add(Component.translatable("hud.simukraft.world_population", worldPopulation).getString());
        }
        cachedFields = List.copyOf(fields);
        // 字体宽度已通过 widestLineWidth 按需计算，无需提前缓存
        return cachedFields;
    }

    private static String permissionPrefix(CityPermissionLevel level) {
        CityPermissionLevel safeLevel = level != null ? level : CityPermissionLevel.CITIZEN;
        return "[" + Component.translatable("hud.simukraft.permission." + safeLevel.name().toLowerCase(Locale.ROOT)).getString() + "]";
    }

    private static String safeText(String value) {
        return value != null ? value : "";
    }

    public static void resetCache() {
        cachedFields = List.of();
        cachedDay = Integer.MIN_VALUE;
        cachedWorldPopulation = Integer.MIN_VALUE;
        cachedCityName = "";
        cachedFunds = Double.NaN;
        cachedCityPopulation = Integer.MIN_VALUE;
        cachedPermissionLevel = CityPermissionLevel.CITIZEN;
        cachedCreativeMode = false;
    }
}
