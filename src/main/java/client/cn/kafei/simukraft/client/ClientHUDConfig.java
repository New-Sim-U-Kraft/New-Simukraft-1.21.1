package client.cn.kafei.simukraft.client;

import common.cn.kafei.simukraft.config.ClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Locale;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class ClientHUDConfig {
    public enum Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        TOP_CENTER,
        BOTTOM_CENTER
    }

    private ClientHUDConfig() {
    }

    /** getAnchor: 从客户端配置读取 HUD 锚点。 */
    public static Anchor getAnchor() {
        try {
            return Anchor.valueOf(ClientConfig.hudAnchorName().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Anchor.TOP_RIGHT;
        }
    }

    /** setAnchor: 写入 HUD 锚点配置。 */
    public static void setAnchor(Anchor newAnchor) {
        ClientConfig.HUD_ANCHOR.set((newAnchor != null ? newAnchor : Anchor.TOP_RIGHT).name());
    }

    /** getPosX: 从客户端配置读取 HUD X 偏移。 */
    public static int getPosX() {
        return ClientConfig.hudPosX();
    }

    /** setPosX: 写入 HUD X 偏移配置。 */
    public static void setPosX(int newPosX) {
        ClientConfig.HUD_POS_X.set(newPosX);
    }

    /** getPosY: 从客户端配置读取 HUD Y 偏移。 */
    public static int getPosY() {
        return ClientConfig.hudPosY();
    }

    /** setPosY: 写入 HUD Y 偏移配置。 */
    public static void setPosY(int newPosY) {
        ClientConfig.HUD_POS_Y.set(newPosY);
    }

    /** calculatePosition: 按锚点和偏移计算 HUD 绘制坐标。 */
    public static int[] calculatePosition(int screenWidth, int screenHeight, int textWidth) {
        Anchor currentAnchor = getAnchor();
        int currentPosX = getPosX();
        int currentPosY = getPosY();

        int x;
        int y;
        switch (currentAnchor) {
            case TOP_LEFT -> {
                x = currentPosX;
                y = currentPosY;
            }
            case TOP_RIGHT -> {
                x = screenWidth - textWidth + currentPosX;
                y = currentPosY;
            }
            case BOTTOM_LEFT -> {
                x = currentPosX;
                y = screenHeight - 10 + currentPosY;
            }
            case BOTTOM_RIGHT -> {
                x = screenWidth - textWidth + currentPosX;
                y = screenHeight - 10 + currentPosY;
            }
            case TOP_CENTER -> {
                x = (screenWidth - textWidth) / 2 + currentPosX;
                y = currentPosY;
            }
            case BOTTOM_CENTER -> {
                x = (screenWidth - textWidth) / 2 + currentPosX;
                y = screenHeight - 10 + currentPosY;
            }
            default -> {
                x = screenWidth - textWidth - 5;
                y = 5;
            }
        }
        return new int[]{x, y};
    }

    /** reset: 重置 HUD 配置为默认值。 */
    public static void reset() {
        ClientConfig.resetHudDefaults();
    }
}
