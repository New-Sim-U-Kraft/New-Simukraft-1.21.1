package client.cn.kafei.simukraft.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** ConfigButtonHandler：在主菜单 Mod 按钮左侧和暂停菜单统计信息按钮右侧注入配置入口按钮。 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public final class ConfigButtonHandler {

    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_SPACING = 5;

    /** onScreenInit：主菜单 / 暂停菜单初始化后注入按钮。 */
    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen titleScreen) {
            injectTitleScreenButton(event, titleScreen);
        } else if (event.getScreen() instanceof PauseScreen pauseScreen) {
            injectPauseScreenButton(event, pauseScreen);
        }
    }

    /** injectTitleScreenButton：在 Mod 按钮左侧添加配置按钮。 */
    private void injectTitleScreenButton(ScreenEvent.Init.Post event, TitleScreen screen) {
        Button modButton = findModButton(screen);
        if (modButton == null) return;

        int buttonY = modButton.getHeight() != BUTTON_SIZE
                ? modButton.getY() + (modButton.getHeight() - BUTTON_SIZE) / 2
                : modButton.getY();
        int buttonX = modButton.getX() - BUTTON_SIZE - BUTTON_SPACING + 1;
        if (buttonX < 5) {
            buttonX = 5;
            modButton.setX(buttonX + BUTTON_SIZE + BUTTON_SPACING - 1);
        }

        AnimatedIconButton btn = new AnimatedIconButton(buttonX, buttonY, BUTTON_SIZE, BUTTON_SIZE,
                b -> Minecraft.getInstance().setScreen(SimuKraftConfigSelectionScreen.create(event.getScreen())));
        btn.setTooltip(Tooltip.create(nn(Component.translatable("gui.simukraft.config_button.title"))));
        event.addListener(nn(btn));
    }

    /** injectPauseScreenButton：在统计信息按钮右侧添加配置按钮。 */
    private void injectPauseScreenButton(ScreenEvent.Init.Post event, PauseScreen screen) {
        Button statsButton = findStatsButton(screen);
        if (statsButton == null) return;

        int buttonY = statsButton.getHeight() != BUTTON_SIZE
                ? statsButton.getY() + (statsButton.getHeight() - BUTTON_SIZE) / 2
                : statsButton.getY();
        int buttonX = statsButton.getX() + statsButton.getWidth() + BUTTON_SPACING;

        AnimatedIconButton btn = new AnimatedIconButton(buttonX, buttonY, BUTTON_SIZE, BUTTON_SIZE,
                b -> Minecraft.getInstance().setScreen(SimuKraftConfigSelectionScreen.create(event.getScreen())));
        btn.setTooltip(Tooltip.create(nn(Component.translatable("gui.simukraft.config_button.title"))));
        event.addListener(nn(btn));
    }

    /** findModButton：在主菜单查找 Mods 按钮（位于屏幕左半侧，Y ≈ height/4+96）。 */
    @Nullable
    private Button findModButton(TitleScreen screen) {
        List<? extends GuiEventListener> children = screen.children();
        for (GuiEventListener listener : children) {
            if (listener instanceof Button button && button.getX() < screen.width / 2) {
                if (Math.abs(button.getY() - (screen.height / 4 + 48 + 48)) < 10) {
                    return button;
                }
            }
        }
        // 退化：返回左半侧最后一个按钮
        Button fallback = null;
        for (GuiEventListener listener : children) {
            if (listener instanceof Button button && button.getX() < screen.width / 2) {
                fallback = button;
            }
        }
        return fallback;
    }

    /** findStatsButton：通过原版翻译键查找暂停菜单的统计信息按钮。 */
    @Nullable
    private Button findStatsButton(PauseScreen screen) {
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof Button button
                    && button.getMessage().getContents() instanceof TranslatableContents contents
                    && "gui.stats".equals(contents.getKey())) {
                return button;
            }
        }
        return null;
    }

    private static <T> T nn(T value) {
        return Objects.requireNonNull(value);
    }
}
