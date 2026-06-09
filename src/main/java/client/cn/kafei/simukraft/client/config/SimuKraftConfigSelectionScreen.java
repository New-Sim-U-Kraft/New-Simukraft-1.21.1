package client.cn.kafei.simukraft.client.config;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import common.cn.kafei.simukraft.SimuKraft;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@SuppressWarnings("null")
public final class SimuKraftConfigSelectionScreen {
    private static final int WINDOW_WIDTH = 280;
    private static final int WINDOW_HEIGHT = 320;
    private static final int MIN_WINDOW_WIDTH = 220;
    private static final int MIN_WINDOW_HEIGHT = 220;
    private static final int HEADER_HEIGHT = 40;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 24;

    private SimuKraftConfigSelectionScreen() {
    }

    /** create: 创建配置选择菜单。 */
    public static Screen create(Screen parent) {
        return new ModularUIScreen(SimuKraftConfigWidgets.screenUi(createUi(parent)), Component.translatable("gui.simukraft.config.title"));
    }

    /** createUi: 组装旧版配置选择布局。 */
    private static UIElement createUi(Screen parent) {
        int windowWidth = SimuKraftConfigWidgets.windowWidth(WINDOW_WIDTH, MIN_WINDOW_WIDTH);
        int windowHeight = SimuKraftConfigWidgets.windowHeight(WINDOW_HEIGHT, MIN_WINDOW_HEIGHT);
        int buttonWidth = buttonWidth(windowWidth);
        UIElement window = SimuKraftConfigWidgets.window(windowWidth, windowHeight);
        window.addChild(SimuKraftConfigWidgets.header(Component.translatable("gui.simukraft.config.title"), HEADER_HEIGHT));
        UIElement body = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
            layout.paddingTop(SimuKraftConfigWidgets.isNarrowScreen() ? 12 : 20);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.FLEX_START);
            layout.gapAll(8);
        });

        body.addChild(menuButton("gui.simukraft.config.client", () -> open(SimuKraftClientConfigScreen.create(parent)), true, buttonWidth));
        body.addChild(menuButton("gui.simukraft.config.server", () -> open(SimuKraftServerConfigScreen.create(parent)), canEditServerConfig(), buttonWidth));
        body.addChild(linkRow(buttonWidth));
        body.addChild(spacer(10));
        body.addChild(menuButton("gui.button.back", () -> open(parent), true, buttonWidth));
        window.addChild(body);
        return SimuKraftConfigWidgets.screenRoot(window);
    }

    private static UIElement linkRow(int buttonWidth) {
        UIElement row = new UIElement().layout(layout -> {
            layout.width(buttonWidth);
            layout.height(BUTTON_HEIGHT);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(4);
            layout.flexShrink(0);
        });
        int linkButtonWidth = Math.max(1, (buttonWidth - 4) / 2);
        row.addChild(menuButton(Component.literal("Bilibili"), () -> openUrl("https://space.bilibili.com/3546922073721320"), true, linkButtonWidth).layout(layout -> {
            layout.flex(1);
            layout.height(BUTTON_HEIGHT);
        }));
        row.addChild(menuButton(Component.literal("mcmod"), () -> openUrl("https://www.mcmod.cn/class/24995.html"), true, linkButtonWidth).layout(layout -> {
            layout.flex(1);
            layout.height(BUTTON_HEIGHT);
        }));
        return row;
    }

    private static UIElement spacer(int height) {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
            layout.flexShrink(0);
        });
    }

    private static UIElement menuButton(String key, Runnable action, boolean active, int width) {
        return menuButton(Component.translatable(key), action, active, width);
    }

    private static UIElement menuButton(Component text, Runnable action, boolean active, int width) {
        return SimuKraftConfigWidgets.button(text, action, active).layout(layout -> {
            layout.width(width);
            layout.height(BUTTON_HEIGHT);
            layout.flexShrink(0);
        });
    }

    private static int buttonWidth(int windowWidth) {
        int availableWidth = Math.max(1, windowWidth - 32);
        int minWidth = Math.min(120, availableWidth);
        return Math.max(minWidth, Math.min(BUTTON_WIDTH, availableWidth));
    }

    /** canEditServerConfig: 判断当前客户端是否有服务端配置权限。 */
    private static boolean canEditServerConfig() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getSingleplayerServer() != null
                || minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    private static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    private static void openUrl(String url) {
        try {
            Util.getPlatform().openUri(url);
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Failed to open config link: {}", url, exception);
        }
    }
}
