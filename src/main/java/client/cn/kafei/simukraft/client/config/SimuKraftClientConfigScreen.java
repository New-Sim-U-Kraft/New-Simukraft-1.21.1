package client.cn.kafei.simukraft.client.config;

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


@SuppressWarnings("null")
public final class SimuKraftClientConfigScreen {
    private static final int WINDOW_WIDTH = 280;
    private static final int WINDOW_HEIGHT = 320;
    private static final int MIN_WINDOW_WIDTH = 220;
    private static final int MIN_WINDOW_HEIGHT = 260;
    private static final int HEADER_HEIGHT = 36;

    private SimuKraftClientConfigScreen() {
    }

    /** create: 创建客户端配置页。 */
    public static Screen create(Screen parent) {
        return new ModularUIScreen(SimuKraftConfigWidgets.screenUi(createUi(parent)), Component.translatable("gui.simukraft.config.client"));
    }

    /** createUi: 按旧版小窗口样式组装客户端配置。 */
    private static UIElement createUi(Screen parent) {
        UIElement window = SimuKraftConfigWidgets.window(WINDOW_WIDTH, WINDOW_HEIGHT, MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT);
        window.addChild(SimuKraftConfigWidgets.header(Component.translatable("gui.simukraft.config.client"), HEADER_HEIGHT));
        UIElement body = SimuKraftConfigWidgets.column(12, 6);

        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.hud_enabled"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.HUD_ENABLED.get(), ClientConfig.HUD_ENABLED::set)));
        body.addChild(openHudEditorRow());
        body.addChild(openToastEditorRow());
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.path_debug_request"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.PATH_DEBUG_REQUEST_ON_TOGGLE.get(), ClientConfig.PATH_DEBUG_REQUEST_ON_TOGGLE::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.rts.target_simukraft_blocks"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.RTS_TARGET_SIMUKRAFT_BLOCKS.get(), ClientConfig.RTS_TARGET_SIMUKRAFT_BLOCKS::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.rts.target_vanilla_blocks"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.RTS_TARGET_VANILLA_BLOCKS.get(), ClientConfig.RTS_TARGET_VANILLA_BLOCKS::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.rts.target_other_mod_blocks"),
                SimuKraftConfigWidgets.switchControl(ClientConfig.RTS_TARGET_OTHER_MOD_BLOCKS.get(), ClientConfig.RTS_TARGET_OTHER_MOD_BLOCKS::set)));
        body.addChild(SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.rts.move_hold_seconds"),
                SimuKraftConfigWidgets.intField(ClientConfig.RTS_MOVE_HOLD_SECONDS.get(), 1, 10, ClientConfig.RTS_MOVE_HOLD_SECONDS::set)));
        window.addChild(SimuKraftConfigWidgets.scroller(body));
        window.addChild(footer(parent));
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

    /** openHudEditorRow: 提供单一按钮入口，打开旧版拖拽式 HUD 编辑器。 */
    private static UIElement openHudEditorRow() {
        return SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.hud_position"),
                SimuKraftConfigWidgets.button(Component.translatable("gui.simukraft.config.open"),
                        () -> Minecraft.getInstance().setScreen(new HUDPositionEditorScreen(Minecraft.getInstance().screen)), true)
                        .layout(layout -> {
                            layout.width(96);
                            layout.height(24);
                            layout.flexShrink(0);
                        }));
    }

    /** openToastEditorRow: 打开独立通知弹窗编辑器。 */
    private static UIElement openToastEditorRow() {
        return SimuKraftConfigWidgets.row(
                Component.translatable("gui.simukraft.config.client.toast_position"),
                SimuKraftConfigWidgets.button(Component.translatable("gui.simukraft.config.open"),
                        () -> Minecraft.getInstance().setScreen(
                                new ToastPositionEditorScreen(Minecraft.getInstance().screen)),
                        true)
                        .layout(layout -> {
                            layout.width(96);
                            layout.height(24);
                            layout.flexShrink(0);
                        }));
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
        ClientConfig.HUD_MAX_WIDTH.set(ClientConfig.DEFAULT_HUD_MAX_WIDTH);
        ClientConfig.TOAST_ANCHOR.set(ClientConfig.DEFAULT_TOAST_ANCHOR);
        ClientConfig.TOAST_POS_X.set(ClientConfig.DEFAULT_TOAST_POS_X);
        ClientConfig.TOAST_POS_Y.set(ClientConfig.DEFAULT_TOAST_POS_Y);
        ClientConfig.TOAST_WIDTH.set(ClientConfig.DEFAULT_TOAST_WIDTH);
        ClientConfig.TOAST_HEIGHT.set(ClientConfig.DEFAULT_TOAST_HEIGHT);
        ClientConfig.PATH_DEBUG_REQUEST_ON_TOGGLE.set(true);
        ClientConfig.RTS_TARGET_SIMUKRAFT_BLOCKS.set(true);
        ClientConfig.RTS_TARGET_VANILLA_BLOCKS.set(true);
        ClientConfig.RTS_TARGET_OTHER_MOD_BLOCKS.set(true);
        ClientConfig.RTS_MOVE_HOLD_SECONDS.set(ClientConfig.DEFAULT_RTS_MOVE_HOLD_SECONDS);
        save();
        Minecraft.getInstance().setScreen(create(parent));
    }
}
