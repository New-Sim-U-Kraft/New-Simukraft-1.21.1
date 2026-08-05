package common.cn.kafei.simukraft.mineraldrilling;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** MineralDrillingDepthPanel: 绘制双向深度滚动条与最多两个只读矿脉标记。 */
@SuppressWarnings("null")
public final class MineralDrillingDepthPanel {
    private static final int FRAME_INNER = 0xFF767A7A;
    private static final int PANEL_RECESSED = 0xFF969A99;
    private static final int TEXT_PRIMARY = 0xFF242727;
    private static final int TRACK_BRASS = 0xFFC8A260;
    private static final int DEPTH_GREEN = 0xFF4FAE67;
    private static final int VEIN_RED = 0xFFC84F49;
    private static final int VEIN_BLUE = 0xFF159CF0;
    private static final int HANDLE_HEIGHT = 9;
    private static final String DEPTH_CHANGE_MESSAGE = "mineral_drilling_depth";
    private static final String DEPTH_VALUE_TAG = "depth";

    private MineralDrillingDepthPanel() {
    }

    /** create: 创建范围翻转后的纵向深度控制，使世界高处显示在轨道顶部。 */
    public static UIElement create(MineralDrillingMenuHolder holder,
                                   Player player,
                                   int x,
                                   int y,
                                   int width,
                                   int height) {
        UIElement panel = absolute(x, y, width, height);
        panel.style(style -> style.backgroundTexture(new GuiTextureGroup(
                new ColorRectTexture(PANEL_RECESSED), new ColorBorderTexture(1, FRAME_INNER))));

        panel.addChild(label(Component.translatable("gui.simukraft.mineral_drilling.depth_control"),
                3, 3, width - 6, 11, Horizontal.CENTER));

        int scrollerTop = 25;
        int scrollerHeight = Math.max(54, height - 48);
        int scrollerX = Math.max(6, width / 2 - 6);
        float rangeMin = -holder.maxDepth();
        float rangeMax = -holder.minDepth();
        if (rangeMax <= rangeMin) {
            rangeMax = rangeMin + 1.0F;
        }

        InteractiveDepthScroller depthScroller = scroller(scrollerX, scrollerTop, 12, scrollerHeight,
                rangeMin, rangeMax, TRACK_BRASS, DEPTH_GREEN, false);
        depthScroller.setScrollBarSize(HANDLE_HEIGHT);
        depthScroller.scrollerStyle(style -> style.scrollBarSize(HANDLE_HEIGHT));
        float finalRangeMax = rangeMax;
        depthScroller.setClampNormalizedValue(normalized -> snapNormalized(
                normalized, rangeMin, finalRangeMax));
        // 深度只由服务端下行同步；拖动期间只更新本地预览，避免每个像素触发持久化。
        depthScroller.bind(DataBindingBuilder.floatValS2C(
                () -> -holder.drillDepthValue()).build());
        panel.addChild(depthScroller);

        UIElement[] veinBars = new UIElement[2];
        for (int index = 0; index < 2; index++) {
            int markerColor = index == 0 ? VEIN_RED : VEIN_BLUE;
            int markerX = index == 0 ? scrollerX - 7 : scrollerX + 14;
            UIElement marker = absolute(markerX, scrollerTop, 5, 2);
            marker.setAllowHitTest(false);
            marker.style(style -> style.backgroundTexture(new GuiTextureGroup(
                    new ColorRectTexture(markerColor),
                    new ColorBorderTexture(1, FRAME_INNER))));
            marker.setDisplay(holder.hasMarker(index));
            veinBars[index] = marker;
            panel.addChild(marker);
        }

        // 隐藏 Scroller 自带手柄，保留其拖动命中区域，使用独立元素绘制带阻尼的视觉手柄。
        depthScroller.scrollBar.buttonStyle(style -> style
                .baseTexture(IGuiTexture.EMPTY)
                .hoverTexture(IGuiTexture.EMPTY)
                .pressedTexture(IGuiTexture.EMPTY));
        UIElement smoothHandle = absolute(scrollerX + 1, scrollerTop, 10, HANDLE_HEIGHT);
        smoothHandle.style(style -> style.backgroundTexture(new GuiTextureGroup(
                new ColorRectTexture(DEPTH_GREEN), new ColorBorderTexture(1, 0xFF303535))));
        smoothHandle.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0) {
                return;
            }
            smoothHandle.startDrag(depthScroller.getValue(), IGuiTexture.EMPTY);
            event.stopPropagation();
        });
        smoothHandle.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, depthScroller::updateFromVisualDrag);
        smoothHandle.addEventListener(UIEvents.DRAG_END, event -> {
            int selectedDepth = selectedDepth(depthScroller.getValue(), holder.minDepth(), holder.maxDepth());
            net.minecraft.nbt.CompoundTag request = new net.minecraft.nbt.CompoundTag();
            request.putInt(DEPTH_VALUE_TAG, selectedDepth);
            smoothHandle.sendMessage(DEPTH_CHANGE_MESSAGE, request);
        });
        smoothHandle.onMessage(DEPTH_CHANGE_MESSAGE, request -> {
            if (request.contains(DEPTH_VALUE_TAG, Tag.TAG_INT)) {
                holder.setDrillDepth(player, request.getInt(DEPTH_VALUE_TAG));
            }
        });
        panel.addChild(smoothHandle);

        float initialNormalized = normalizedForValue(-holder.drillDepthValue(), rangeMin, rangeMax);
        float[] displayedNormalized = {initialNormalized};
        depthScroller.addEventListener(UIEvents.TICK, event -> {
            float target = Math.clamp(depthScroller.getNormalizedValue(), 0.0F, 1.0F);
            float difference = target - displayedNormalized[0];
            displayedNormalized[0] += difference * 0.2F;
            if (Math.abs(difference) < 0.002F) {
                displayedNormalized[0] = target;
            }
            float handleY = scrollerTop + displayedNormalized[0]
                    * Math.max(0, scrollerHeight - HANDLE_HEIGHT);
            smoothHandle.layout(layout -> absoluteLayout(
                    layout, scrollerX + 1, handleY, 10, HANDLE_HEIGHT));
        });
        panel.addEventListener(UIEvents.TICK, event -> {
            for (int index = 0; index < veinBars.length; index++) {
                UIElement marker = veinBars[index];
                boolean visible = holder.hasMarker(index);
                marker.setDisplay(visible);
                if (visible) {
                    float top = normalizedForDepth(holder.markerMaxDepthValue(index),
                            rangeMin, finalRangeMax) * scrollerHeight;
                    float bottom = normalizedForDepth(holder.markerMinDepthValue(index),
                            rangeMin, finalRangeMax) * scrollerHeight;
                    float markerHeight = Math.max(2.0F, bottom - top);
                    int markerX = index == 0 ? scrollerX - 7 : scrollerX + 14;
                    marker.layout(layout -> absoluteLayout(
                            layout, markerX, scrollerTop + top, 5, markerHeight));
                }
            }
        });

        panel.addChild(label(Component.literal(Integer.toString(holder.maxDepth())),
                2, 15, width - 4, 9, Horizontal.CENTER));
        panel.addChild(label(Component.literal(Integer.toString(holder.minDepth())),
                2, height - 11, width - 4, 9, Horizontal.CENTER));
        panel.addChild(boundLabel(holder, 2, height - 23, width - 4, 10));
        return panel;
    }

    private static InteractiveDepthScroller scroller(int x,
                                                      int y,
                                                      int width,
                                                      int height,
                                                      float min,
                                                      float max,
                                                      int trackColor,
                                                      int handleColor,
                                                      boolean arrows) {
        InteractiveDepthScroller scroller = new InteractiveDepthScroller();
        scroller.setRange(min, max);
        scroller.setScrollBarSize(arrows ? 8.0F : 4.0F);
        scroller.scrollerStyle(style -> style.scrollDelta(1.0F).scrollBarSize(arrows ? 8.0F : 4.0F));
        scroller.layout(layout -> absoluteLayout(layout, x, y, width, height));
        scroller.headButton.setDisplay(arrows);
        scroller.tailButton.setDisplay(arrows);
        scroller.headButton.layout(layout -> layout.width(width).height(arrows ? 8 : 0));
        scroller.tailButton.layout(layout -> layout.width(width).height(arrows ? 8 : 0));
        scroller.scrollContainer.layout(layout -> layout.width(width).flex(1));
        scroller.scrollContainer.style(style -> style.backgroundTexture(
                trackColor == 0 ? IGuiTexture.EMPTY : new GuiTextureGroup(
                        new ColorRectTexture(trackColor), new ColorBorderTexture(1, FRAME_INNER))));
        IGuiTexture handle = new GuiTextureGroup(
                new ColorRectTexture(handleColor), new ColorBorderTexture(1, 0xFF303535));
        scroller.scrollBar.buttonStyle(style -> style
                .baseTexture(handle)
                .hoverTexture(handle)
                .pressedTexture(handle));
        return scroller;
    }

    /** InteractiveDepthScroller: 让独立阻尼手柄复用 LDLib2 原生纵向拖拽计算。 */
    private static final class InteractiveDepthScroller extends Scroller.Vertical {
        /** updateFromVisualDrag: 将绿色视觉手柄的拖拽事件转交给原生滚动器。 */
        private void updateFromVisualDrag(UIEvent event) {
            onDraggingScrollBar(event);
        }
    }

    private static float snapNormalized(float normalized, float min, float max) {
        float clamped = Math.clamp(normalized, 0.0F, 1.0F);
        float range = max - min;
        if (range <= 0.0F) {
            return 0.0F;
        }
        float snapped = Math.round(min + clamped * range);
        return Math.clamp((snapped - min) / range, 0.0F, 1.0F);
    }

    /** selectedDepth: 将滚动器中的反向数值还原为经过边界限制的世界 Y 深度。 */
    static int selectedDepth(float scrollerValue, int minDepth, int maxDepth) {
        return Math.clamp(Math.round(-scrollerValue), Math.min(minDepth, maxDepth), Math.max(minDepth, maxDepth));
    }

    /** normalizedForValue: 将滚动器值映射到轨道比例并限制到安全范围。 */
    private static float normalizedForValue(float value, float min, float max) {
        return Math.clamp((value - min) / Math.max(1.0F, max - min), 0.0F, 1.0F);
    }

    /** normalizedForDepth: 将世界 Y 映射到顶部为高处、底部为低处的轨道比例。 */
    private static float normalizedForDepth(float depth, float rangeMin, float rangeMax) {
        return normalizedForValue(-depth, rangeMin, rangeMax);
    }

    private static Label boundLabel(MineralDrillingMenuHolder holder,
                                    int x,
                                    int y,
                                    int width,
                                    int height) {
        Label label = label(Component.empty(), x, y, width, height, Horizontal.CENTER);
        label.bind(DataBindingBuilder.componentS2C(holder::depthText).build());
        return label;
    }

    private static Label label(Component text,
                               int x,
                               int y,
                               int width,
                               int height,
                               Horizontal horizontal) {
        Label label = new Label();
        label.setText(text);
        label.setAllowHitTest(false);
        label.setOverflowVisible(false);
        label.layout(layout -> absoluteLayout(layout, x, y, width, height));
        label.textStyle(style -> style
                .textColor(TEXT_PRIMARY)
                .textShadow(false)
                .textWrap(TextWrap.HIDE)
                .textAlignHorizontal(horizontal)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static UIElement absolute(int x, int y, int width, int height) {
        return new UIElement().layout(layout -> absoluteLayout(layout, x, y, width, height));
    }

    private static void absoluteLayout(com.lowdragmc.lowdraglib2.gui.ui.style.LayoutStyle layout,
                                       float x,
                                       float y,
                                       float width,
                                       float height) {
        layout.positionType(TaffyPosition.ABSOLUTE);
        layout.left(x);
        layout.top(y);
        layout.width(Math.max(1.0F, width));
        layout.height(Math.max(1.0F, height));
    }
}
