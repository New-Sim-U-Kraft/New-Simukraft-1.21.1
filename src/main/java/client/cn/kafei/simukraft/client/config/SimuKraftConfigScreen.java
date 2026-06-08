package client.cn.kafei.simukraft.client.config;

import net.minecraft.client.gui.screens.Screen;

public final class SimuKraftConfigScreen {
    private SimuKraftConfigScreen() {
    }

    /** createRoot: 创建旧版风格配置选择页。 */
    public static Screen createRoot(Screen parent) {
        return SimuKraftConfigSelectionScreen.create(parent);
    }
}
