package client.cn.kafei.simukraft.client.config;

import client.cn.kafei.simukraft.client.ClientHUDConfig;
import client.cn.kafei.simukraft.client.ClientHUDOverlay;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import common.cn.kafei.simukraft.config.ClientConfig;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SimuKraftClientConfigScreen {
    private static final int WINDOW_WIDTH = 280;
    private static final int WINDOW_HEIGHT = 240;
    private static final int HEADER_HEIGHT = 36;

    private SimuKraftClientConfigScreen() {
    }

    /** create: 创建客户端配置页。 */
    public static Screen create(Screen parent) {
        return new ModularUIScreen(SimuKraftConfigWidgets.screenUi(createUi(parent)), Component.translatable("gui.simukraft.config.client"));
    }

    /** createUi: 按旧版小窗口样式组装客户端配置。 */
    private static UIElement createUi(Screen parent) {
        UIElement window = SimuKraftConfigWidgets.window(WINDOW_WIDTH, WINDOW_HEIGHT);
        window.addChild(SimuKraftConfigWidgets.header(Component.translatable("gui.simukraft.config.client"), HEADER_HEIGHT));
        UIElement body = SimuKraftConfigWidgets.column(12, 6);

        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.hud_enabled"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.HUD_ENABLED.get(), ClientConfig.HUD_ENABLED::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.hud_anchor"),
                SimuKraftConfigWidgets.selector(anchorValues(), ClientHUDConfig.getAnchor(), SimuKraftClientConfigScreen::anchorText, ClientHUDConfig::setAnchor)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.hud_pos_x"),
                SimuKraftConfigWidgets.intField(ClientConfig.HUD_POS_X.get(), -4096, 4096, ClientConfig.HUD_POS_X::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.hud_pos_y"),
                SimuKraftConfigWidgets.intField(ClientConfig.HUD_POS_Y.get(), -4096, 4096, ClientConfig.HUD_POS_Y::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.path_debug_request"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.PATH_DEBUG_REQUEST_ON_TOGGLE.get(), ClientConfig.PATH_DEBUG_REQUEST_ON_TOGGLE::set)));
        body.addChild(footer(parent));
        window.addChild(body);
        return SimuKraftConfigWidgets.screenRoot(window);
    }

    private static UIElement footer(Screen parent) {
        UIElement footer = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(28);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(6);
            layout.flexShrink(0);
        });
        footer.addChild(footerButton("gui.simukraft.config.save", SimuKraftClientConfigScreen::save));
        footer.addChild(footerButton("gui.simukraft.config.reset", () -> reset(parent)));
        footer.addChild(footerButton("gui.button.back", () -> Minecraft.getInstance().setScreen(SimuKraftConfigSelectionScreen.create(parent))));
        return footer;
    }

    private static UIElement footerButton(String key, Runnable action) {
        return SimuKraftConfigWidgets.button(Component.translatable(key), action, true).layout(layout -> {
            layout.flex(1);
            layout.height(24);
        });
    }

    private static List<ClientHUDConfig.Anchor> anchorValues() {
        return Arrays.asList(ClientHUDConfig.Anchor.values());
    }

    private static Component anchorText(ClientHUDConfig.Anchor anchor) {
        String name = anchor == null ? ClientConfig.DEFAULT_HUD_ANCHOR : anchor.name();
        return Component.translatable("gui.simukraft.config.hud_anchor." + name.toLowerCase(Locale.ROOT));
    }

    /** save: 保存客户端配置并清理 HUD 缓存。 */
    private static void save() {
        ClientConfig.SPEC.save();
        ClientHUDOverlay.resetCache();
    }

    /** reset: 恢复客户端配置默认值。 */
    private static void reset(Screen parent) {
        ClientConfig.HUD_ENABLED.set(true);
        ClientConfig.HUD_ANCHOR.set(ClientConfig.DEFAULT_HUD_ANCHOR);
        ClientConfig.HUD_POS_X.set(ClientConfig.DEFAULT_HUD_POS_X);
        ClientConfig.HUD_POS_Y.set(ClientConfig.DEFAULT_HUD_POS_Y);
        ClientConfig.PATH_DEBUG_REQUEST_ON_TOGGLE.set(true);
        save();
        Minecraft.getInstance().setScreen(create(parent));
    }
}
