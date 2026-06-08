package client.cn.kafei.simukraft.client.config;

import client.cn.kafei.simukraft.client.ui.SimuKraftClientUiPreferences;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.SplitView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

final class SimuKraftConfigWidgets {
    static final int WINDOW_BG = 0xE6202020;
    static final int PANEL_BG = 0xCC2A2A2A;
    static final int HEADER_BG = 0xFF2D5A6B;
    static final int CARD_BG = 0xFF353535;
    static final int BUTTON_BG = 0xFF3A5A6A;
    static final int BUTTON_HOVER = 0xFF4A7A8A;
    static final int BORDER = 0xFF4A90A4;
    static final int TEXT = 0xFFE0E0E0;
    static final int TEXT_TITLE = 0xFFFFFFFF;
    static final int TEXT_MUTED = 0xFFAAAAAA;
    static final int TEXT_HIGHLIGHT = 0xFF88CCFF;
    static final float TEXT_ROLL_SPEED = 0.35F;

    private SimuKraftConfigWidgets() {
    }

    /** screenUi: 创建使用 Ore 主题的根 UI。 */
    static ModularUI screenUi(UIElement root) {
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    /** screenRoot: 把窗口面板放入居中根节点。 */
    static UIElement screenRoot(UIElement window) {
        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        var mcWindow = net.minecraft.client.Minecraft.getInstance().getWindow();
        root.addChild(SimuKraftUiTheme.createShellPanel(mcWindow.getGuiScaledWidth(), mcWindow.getGuiScaledHeight()));
        root.addChild(window);
        return root;
    }

    /** window: 创建可填充内容的旧版窗口面板。 */
    static UIElement window(int width, int height) {
        return panel(width, height, WINDOW_BG, BORDER).setOverflowVisible(false).layout(layout -> {
            layout.width(width);
            layout.height(height);
            layout.paddingAll(2);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
    }

    /** panel: 创建旧版深色边框面板。 */
    static UIElement panel(int width, int height, int color, int border) {
        return new UIElement()
                .style(style -> style.backgroundTexture(new GuiTextureGroup(new ColorRectTexture(color), new ColorBorderTexture(2, border))))
                .layout(layout -> {
                    layout.width(width);
                    layout.height(height);
                });
    }

    /** header: 创建标题栏。 */
    static UIElement header(Component title, int height) {
        UIElement header = panel(1, height, HEADER_BG, HEADER_BG);
        header.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.flexShrink(0);
        });
        header.addChild(label(title, Horizontal.CENTER, TEXT_TITLE, height - 4, TextWrap.HIDE));
        return header;
    }

    /** label: 创建统一文本标签。 */
    static Label label(Component text, Horizontal align, int color, int height, TextWrap wrap) {
        Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style.textColor(color)
                .textShadow(false)
                .fontSize(9.0F)
                .textWrap(wrap)
                .textAlignHorizontal(align)
                .textAlignVertical(Vertical.CENTER));
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
            layout.flexShrink(0);
        });
        return label;
    }

    /** button: 创建旧版颜色按钮。 */
    static Button button(Component text, Runnable action, boolean active) {
        Button button = new Button();
        button.setText(text);
        button.setActive(active);
        button.textStyle(style -> style.textColor(active ? TEXT : TEXT_MUTED)
                .textShadow(false)
                .fontSize(9.0F)
                .textWrap(TextWrap.HOVER_ROLL)
                .rollSpeed(TEXT_ROLL_SPEED));
        button.buttonStyle(style -> style
                .baseTexture(new GuiTextureGroup(new ColorRectTexture(active ? BUTTON_BG : 0xFF404040), new ColorBorderTexture(1, BORDER)))
                .hoverTexture(new GuiTextureGroup(new ColorRectTexture(active ? BUTTON_HOVER : 0xFF404040), new ColorBorderTexture(1, active ? TEXT_HIGHLIGHT : BORDER)))
                .pressedTexture(new GuiTextureGroup(new ColorRectTexture(BUTTON_HOVER), new ColorBorderTexture(1, TEXT_HIGHLIGHT))));
        if (active) {
            button.setOnClick(event -> action.run());
        }
        return button;
    }

    /** row: 创建横向设置行。 */
    static UIElement row(Component label, UIElement control) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(30);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(8);
            layout.flexShrink(0);
        });
        row.addChild(label(label, Horizontal.LEFT, TEXT, 24, TextWrap.HIDE).layout(layout -> {
            layout.flex(1);
            layout.height(24);
        }));
        row.addChild(control);
        return row;
    }

    /** section: 创建分组标题。 */
    static UIElement section(Component title) {
        UIElement group = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(24);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.flexShrink(0);
        });
        group.addChild(label(title, Horizontal.LEFT, TEXT_HIGHLIGHT, 16, TextWrap.HIDE));
        group.addChild(new UIElement().style(style -> style.backgroundTexture(new ColorRectTexture(0xFF4A5A6A))).layout(layout -> {
            layout.widthPercent(100);
            layout.height(1);
        }));
        return group;
    }

    /** column: 创建通用竖向列。 */
    static UIElement column(int padding, int gap) {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.paddingAll(padding);
            layout.gapAll(gap);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
    }

    /** scrollColumn: 创建按内容高度展开的滚动列。 */
    static UIElement scrollColumn(int padding, int gap) {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.paddingAll(padding);
            layout.gapAll(gap);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
    }

    /** scroller: 创建垂直滚动区域。 */
    static UIElement scroller(UIElement child) {
        UIElement viewport = new UIElement().setOverflowVisible(false).layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flex(1);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
        ScrollerView scroller = new ScrollerView();
        scroller.setOverflowVisible(false);
        scroller.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.ALWAYS)
                .horizontalScrollDisplay(ScrollDisplay.NEVER));
        scroller.viewContainer(container -> container.layout(layout -> layout.widthPercent(100)));
        scroller.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        });
        scroller.addScrollViewChild(child);
        viewport.addChild(scroller);
        return viewport;
    }

    /** textField: 创建文本输入框。 */
    static TextField textField(String value, Consumer<String> responder) {
        TextField field = new TextField();
        field.setAnyString();
        field.setText(value == null ? "" : value, false);
        field.setTextResponder(responder);
        field.layout(layout -> {
            layout.width(92);
            layout.height(22);
            layout.paddingAll(2);
            layout.flexShrink(0);
        });
        field.style(style -> style.backgroundTexture(new GuiTextureGroup(new ColorRectTexture(0xFF101010), new ColorBorderTexture(1, 0xFF808080))));
        field.textFieldStyle(style -> style.textColor(TEXT_TITLE)
                .cursorColor(TEXT_TITLE)
                .textShadow(false)
                .fontSize(9.0F)
                .focusOverlay(IGuiTexture.EMPTY));
        return field;
    }

    /** intField: 创建整数输入框。 */
    static TextField intField(int value, int min, int max, Consumer<Integer> responder) {
        TextField field = textField(String.valueOf(value), text -> {
            try {
                responder.accept(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
            }
        });
        field.setNumbersOnlyInt(min, max);
        return field;
    }

    /** doubleField: 创建小数输入框。 */
    static TextField doubleField(double value, double min, double max, Consumer<Double> responder) {
        TextField field = textField(String.format(java.util.Locale.ROOT, "%.3f", value), text -> {
            try {
                responder.accept(Double.parseDouble(text));
            } catch (NumberFormatException ignored) {
            }
        });
        field.setNumbersOnlyDouble(min, max);
        return field;
    }

    /** switchControl: 创建 LDLib2 Switch 开关。 */
    static Switch switchControl(boolean value, Consumer<Boolean> responder) {
        Switch control = new Switch();
        control.setOn(value, false);
        control.setOnSwitchChanged(responder::accept);
        control.layout(layout -> {
            layout.width(26);
            layout.height(14);
            layout.flexShrink(0);
        });
        return control;
    }

    /** selector: 创建 LDLib2 Selector 下拉选择器。 */
    static <T> Selector<T> selector(List<T> values, T selected, Function<T, Component> labeler, Consumer<T> responder) {
        Selector<T> selector = new Selector<>();
        T safeSelected = selected != null || values.isEmpty() ? selected : values.get(0);
        selector.setCandidates(values);
        selector.setCandidateUIProvider(UIElementProvider.text(value -> {
            if (value == null) {
                return Component.empty();
            }
            Component label = labeler.apply(value);
            return label == null ? Component.empty() : label;
        }));
        selector.setSelected(safeSelected, false);
        selector.setOnValueChanged(value -> {
            if (value != null) {
                responder.accept(value);
            }
        });
        selector.selectorStyle(style -> style.maxItemCount(Math.min(6, Math.max(1, values.size()))).closeAfterSelect(true));
        selector.layout(layout -> {
            layout.width(132);
            layout.height(22);
            layout.flexShrink(0);
        });
        return selector;
    }

    /** split: 创建带持久化比例的水平分栏。 */
    static UIElement split(String key, float fallback, float min, float max, UIElement left, UIElement right) {
        PersistentHorizontalSplitView split = new PersistentHorizontalSplitView(key, min, max);
        split.setBorderSize(5F);
        split.setMinPercentage(min);
        split.setMaxPercentage(max);
        split.left(left);
        split.right(right);
        split.applySavedPercentage(fallback);
        split.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flex(1);
        });
        return split;
    }

    static final class PersistentHorizontalSplitView extends SplitView.Horizontal {
        private final String preferenceKey;
        private final float min;
        private final float max;
        private float currentPercentage;
        private float lastSavedPercentage;

        /** PersistentHorizontalSplitView: 记录分栏比例并在拖动后保存。 */
        private PersistentHorizontalSplitView(String preferenceKey, float min, float max) {
            this.preferenceKey = preferenceKey;
            this.min = min;
            this.max = max;
            addEventListener(UIEvents.MOUSE_UP, event -> savePreference());
            addEventListener(UIEvents.DRAG_END, event -> savePreference());
            addEventListener(UIEvents.REMOVED, event -> savePreference());
        }

        /** applySavedPercentage: 应用已保存分栏比例。 */
        private void applySavedPercentage(float fallback) {
            float percentage = SimuKraftClientUiPreferences.getFloat(preferenceKey, fallback, min, max);
            super.setPercentage(percentage);
            currentPercentage = percentage;
            lastSavedPercentage = currentPercentage;
        }

        @Override
        public SplitView.Horizontal setPercentage(float percentage) {
            currentPercentage = clampPercentage(percentage);
            super.setPercentage(currentPercentage);
            savePreference();
            return this;
        }

        /** savePreference: 保存当前分栏比例。 */
        private void savePreference() {
            float current = currentPercentage;
            if (Math.abs(current - lastSavedPercentage) < 0.1F) {
                return;
            }
            SimuKraftClientUiPreferences.setFloat(preferenceKey, current, min, max);
            lastSavedPercentage = current;
        }

        private float clampPercentage(float percentage) {
            return Math.max(min, Math.min(max, percentage));
        }
    }
}
