package common.cn.kafei.simukraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;

public final class ClientConfig {
    public static final String DEFAULT_HUD_ANCHOR = "TOP_RIGHT";
    public static final int DEFAULT_HUD_POS_X = -5;
    public static final int DEFAULT_HUD_POS_Y = 5;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> HUD_ANCHOR;
    public static final ModConfigSpec.IntValue HUD_POS_X;
    public static final ModConfigSpec.IntValue HUD_POS_Y;
    public static final ModConfigSpec.BooleanValue PATH_DEBUG_REQUEST_ON_TOGGLE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("hud");
        HUD_ENABLED = builder
                .comment("Whether the Sim-U-Kraft HUD is displayed.")
                .translation("config.simukraft.client.hud.enabled")
                .define("enabled", true);
        HUD_ANCHOR = builder
                .comment("HUD anchor: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP_CENTER, BOTTOM_CENTER.")
                .translation("config.simukraft.client.hud.anchor")
                .define("anchor", DEFAULT_HUD_ANCHOR, ClientConfig::isHudAnchor);
        HUD_POS_X = builder
                .comment("HUD X offset from the selected anchor.")
                .translation("config.simukraft.client.hud.posX")
                .defineInRange("posX", DEFAULT_HUD_POS_X, -4096, 4096);
        HUD_POS_Y = builder
                .comment("HUD Y offset from the selected anchor.")
                .translation("config.simukraft.client.hud.posY")
                .defineInRange("posY", DEFAULT_HUD_POS_Y, -4096, 4096);
        builder.pop();
        builder.push("path_debug");
        PATH_DEBUG_REQUEST_ON_TOGGLE = builder
                .comment("Whether Alt+P requests latest NPC paths from the server when path debug is shown.")
                .translation("config.simukraft.client.pathDebug.requestOnToggle")
                .define("requestOnToggle", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    /** hudEnabled: 判断 HUD 是否启用。 */
    public static boolean hudEnabled() {
        return HUD_ENABLED.get();
    }

    /** hudAnchorName: 获取规范化 HUD 锚点名。 */
    public static String hudAnchorName() {
        String value = HUD_ANCHOR.get();
        return isHudAnchor(value) ? value.toUpperCase(Locale.ROOT) : DEFAULT_HUD_ANCHOR;
    }

    /** hudPosX: 获取 HUD X 偏移。 */
    public static int hudPosX() {
        return HUD_POS_X.get();
    }

    /** hudPosY: 获取 HUD Y 偏移。 */
    public static int hudPosY() {
        return HUD_POS_Y.get();
    }

    /** pathDebugRequestOnToggle: 判断显示寻路调试时是否请求服务端刷新。 */
    public static boolean pathDebugRequestOnToggle() {
        return PATH_DEBUG_REQUEST_ON_TOGGLE.get();
    }

    /** resetHudDefaults: 重置 HUD 位置到默认值。 */
    public static void resetHudDefaults() {
        HUD_ANCHOR.set(DEFAULT_HUD_ANCHOR);
        HUD_POS_X.set(DEFAULT_HUD_POS_X);
        HUD_POS_Y.set(DEFAULT_HUD_POS_Y);
        SPEC.save();
    }

    /** isHudAnchor: 校验 HUD 锚点配置值。 */
    private static boolean isHudAnchor(Object value) {
        if (!(value instanceof String string) || string.isBlank()) {
            return false;
        }
        return switch (string.toUpperCase(Locale.ROOT)) {
            case "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT", "TOP_CENTER", "BOTTOM_CENTER" -> true;
            default -> false;
        };
    }
}
