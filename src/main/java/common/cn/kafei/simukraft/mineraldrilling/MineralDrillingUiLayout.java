package common.cn.kafei.simukraft.mineraldrilling;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import common.cn.kafei.simukraft.SimuKraft;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.function.Supplier;

/** MineralDrillingUiLayout: 组合钻井控制区、矿脉信息、操作按钮和玩家背包。 */
@SuppressWarnings("null")
public final class MineralDrillingUiLayout {
    private static final ResourceLocation ORE_STYLESHEET = StylesheetManager.ORE;
    private static final ResourceLocation SIMUKRAFT_ORE_STYLESHEET =
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "lss/ore.lss");
    private static final int OVERLAY = 0x78000000;
    private static final int FRAME_OUTER = 0xFF171919;
    private static final int FRAME_INNER = 0xFF767A7A;
    private static final int PANEL_LIGHT = 0xFFE0E0DB;
    private static final int PANEL_RECESSED = 0xFF969A99;
    private static final int PANEL_SLOT = 0xFF5C6466;
    private static final int CARD_PAPER = 0xFFF5F0E1;
    private static final int CARD_ACCENT = 0xFFC8A260;
    private static final int TEXT_PRIMARY = 0xFF242727;
    private static final int TEXT_ON_DARK = 0xFFF1F1ED;
    /** NPC 信息界面护甲条的蓝灰填充色。 */
    private static final int INTEGRITY_BLUE = 0xFF8294A2;
    private static final int BUTTON_HOVER = 0xFFF2F2ED;
    private static final int BUTTON_PRESSED = 0xFFD2B478;
    private static final int DANGER_BASE = 0xFFC77970;
    private static final int DANGER_HOVER = 0xFFD98B81;

    private MineralDrillingUiLayout() {
    }

    /** createModularUi: 创建两端同序的容器元素树并启用 Esc 关闭。 */
    public static ModularUI createModularUi(MineralDrillingMenuHolder holder,
                                            Player player,
                                            MineralDrillingUiMetrics metrics,
                                            ClientActions clientActions) {
        return createModularUi(holder, player, metrics, clientActions, ProductTextResolver.IDENTIFIER);
    }

    /** createModularUi: 创建容器元素树，并将服务端产物 ID 的显示解析委托给物理客户端。 */
    public static ModularUI createModularUi(MineralDrillingMenuHolder holder,
                                            Player player,
                                            MineralDrillingUiMetrics metrics,
                                            ClientActions clientActions,
                                            ProductTextResolver productTextResolver) {
        UIElement root = createRoot(holder, player, metrics,
                clientActions != null ? clientActions : ClientActions.NONE,
                productTextResolver != null ? productTextResolver : ProductTextResolver.IDENTIFIER);
        return ModularUI.of(UI.of(root, ORE_STYLESHEET, SIMUKRAFT_ORE_STYLESHEET), player)
                .shouldCloseOnEsc(true);
    }

    private static UIElement createRoot(MineralDrillingMenuHolder holder,
                                        Player player,
                                        MineralDrillingUiMetrics metrics,
                                        ClientActions clientActions,
                                        ProductTextResolver productTextResolver) {
        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        root.style(style -> style.backgroundTexture(new ColorRectTexture(OVERLAY)));

        UIElement workspace = new UIElement().layout(layout -> {
            layout.width(metrics.width());
            layout.height(metrics.height());
            layout.flexShrink(0);
        });
        workspace.style(style -> style.backgroundTexture(new GuiTextureGroup(
                new ColorRectTexture(FRAME_OUTER), new ColorBorderTexture(1, FRAME_INNER))));
        workspace.setOverflowVisible(false);
        root.addChild(workspace);

        addMachinePanel(workspace, holder, metrics);
        addInformationPanel(workspace, holder, player, metrics, productTextResolver);
        addDepthPanel(workspace, holder, player, metrics);
        addActionPanel(workspace, holder, player, metrics, clientActions);
        addInventoryPanel(workspace, metrics);
        workspace.addChild(MineralDrillingSlotLayout.create(holder.inventory(), metrics)
                .style(style -> style.zIndex(20)));
        return root;
    }

    private static void addMachinePanel(UIElement workspace,
                                        MineralDrillingMenuHolder holder,
                                        MineralDrillingUiMetrics metrics) {
        int padding = metrics.panelPadding();
        int height = metrics.topHeight() - padding * 2;
        UIElement panel = panel(padding, padding, metrics.leftPanelWidth(), height, PANEL_LIGHT);
        workspace.addChild(panel);

        panel.addChild(label(Component.translatable("gui.simukraft.mineral_drilling.components"),
                4, 4, metrics.leftPanelWidth() - 8, 12, Horizontal.CENTER, TEXT_PRIMARY));

        int slotX = metrics.machineSlotX() - padding;
        int firstSlotY = metrics.firstMachineSlotY() - padding;
        panel.addChild(panel(slotX - 2, firstSlotY - 2, 22, 22, PANEL_RECESSED));
        panel.addChild(panel(slotX - 2, firstSlotY + 30, 22, 22, PANEL_RECESSED));
        panel.addChild(label(Component.translatable("gui.simukraft.mineral_drilling.drill_rod_slot"),
                slotX + 26, firstSlotY, metrics.leftPanelWidth() - slotX - 31, 18,
                Horizontal.LEFT, TEXT_PRIMARY));
        panel.addChild(label(Component.translatable("gui.simukraft.mineral_drilling.drill_bit_slot"),
                slotX + 26, firstSlotY + 32, metrics.leftPanelWidth() - slotX - 31, 18,
                Horizontal.LEFT, TEXT_PRIMARY));

        int integrityY = Math.min(height - 27, Math.max(firstSlotY + 54, height - 34));
        panel.addChild(boundLabel(holder::integrityText, 6, integrityY,
                metrics.leftPanelWidth() - 12, 11, Horizontal.LEFT, TEXT_PRIMARY));
        ProgressBar integrity = new ProgressBar();
        integrity.setRange(0.0F, 1.0F);
        integrity.setAllowHitTest(false);
        integrity.layout(layout -> absoluteLayout(layout, 6, integrityY + 12,
                metrics.leftPanelWidth() - 12, 14));
        integrity.progressBarStyle(style -> style.fillDirection(FillDirection.LEFT_TO_RIGHT).interpolate(true));
        integrity.barContainer.style(style -> style.backgroundTexture(new GuiTextureGroup(
                new ColorRectTexture(PANEL_SLOT), new ColorBorderTexture(1, FRAME_INNER))));
        integrity.bar.style(style -> style.backgroundTexture(new ColorRectTexture(INTEGRITY_BLUE)));
        integrity.label.setText(Component.empty());
        integrity.bind(DataBindingBuilder.floatValS2C(holder::integrityProgress).build());
        panel.addChild(integrity);
    }

    private static void addInformationPanel(UIElement workspace,
                                            MineralDrillingMenuHolder holder,
                                            Player player,
                                            MineralDrillingUiMetrics metrics,
                                            ProductTextResolver productTextResolver) {
        int padding = metrics.panelPadding();
        int x = padding + metrics.leftPanelWidth() + metrics.contentGap();
        int height = metrics.topHeight() - padding * 2;
        int width = metrics.middlePanelWidth();
        UIElement panel = panel(x, padding, width, height, CARD_PAPER);
        workspace.addChild(panel);

        UIElement accent = absolute(2, 2, width - 4, 3);
        accent.setAllowHitTest(false);
        accent.style(style -> style.backgroundTexture(new ColorRectTexture(CARD_ACCENT)));
        panel.addChild(accent);
        panel.addChild(label(Component.translatable("gui.simukraft.mineral_drilling.title"),
                5, 6, width - 10, 12, Horizontal.CENTER, TEXT_PRIMARY));

        int lineHeight = height >= 170 ? 14 : 11;
        int y = 18;
        panel.addChild(boundLabel(holder::buildingText, 6, y, width - 12, lineHeight,
                Horizontal.LEFT, TEXT_PRIMARY));
        y += lineHeight;
        panel.addChild(boundLabel(holder::workerText, 6, y, width - 12, lineHeight,
                Horizontal.LEFT, TEXT_PRIMARY));
        y += lineHeight + 2;
        panel.addChild(boundLabel(holder::mineralText, 6, y, width - 12, lineHeight,
                Horizontal.LEFT, TEXT_PRIMARY));
        y += lineHeight;
        boolean isClient = player != null && player.level().isClientSide();
        panel.addChild(boundProductLabel(holder::productId, productTextResolver, isClient, 6, y,
                width - 12, lineHeight, Horizontal.LEFT, TEXT_PRIMARY));
        y += lineHeight + 2;
        panel.addChild(boundLabel(holder::statusText, 6, y, width - 12, lineHeight,
                Horizontal.LEFT, TEXT_PRIMARY));
        y += lineHeight + 2;

        for (int index = 0; index < 2; index++) {
            int markerIndex = index;
            Label marker = boundLabel(() -> holder.markerText(markerIndex), 6, y,
                    width - 12, lineHeight, Horizontal.LEFT, index == 0 ? 0xFF8F302D : 0xFF80691D);
            marker.setDisplay(holder.hasMarker(index));
            panel.addChild(marker);
            y += lineHeight;
        }
    }

    private static void addDepthPanel(UIElement workspace,
                                      MineralDrillingMenuHolder holder,
                                      Player player,
                                      MineralDrillingUiMetrics metrics) {
        int padding = metrics.panelPadding();
        int x = metrics.width() - padding - metrics.depthPanelWidth();
        int height = metrics.topHeight() - padding * 2;
        workspace.addChild(MineralDrillingDepthPanel.create(
                holder, player, x, padding, metrics.depthPanelWidth(), height));
    }

    private static void addActionPanel(UIElement workspace,
                                       MineralDrillingMenuHolder holder,
                                       Player player,
                                       MineralDrillingUiMetrics metrics,
                                       ClientActions clientActions) {
        int padding = metrics.panelPadding();
        int width = metrics.actionWidth();
        int height = metrics.bottomHeight() - padding;
        UIElement panel = panel(padding, metrics.bottomY(), width, height, PANEL_RECESSED);
        workspace.addChild(panel);

        int gap = 2;
        int top = 4;
        int buttonHeight = Math.max(16, (height - top * 2 - gap * 4) / 5);
        int buttonWidth = width - 8;
        boolean hasWorker = holder.snapshot().hasWorker();
        Button hireButton = clientButton(Component.translatable("gui.simukraft.mineral_drilling.hire"),
                4, top, buttonWidth, buttonHeight, !hasWorker,
                () -> clientActions.requestHire(holder.boxPos()));
        bindActive(hireButton, () -> !holder.snapshot().hasWorker());
        panel.addChild(hireButton);
        top += buttonHeight + gap;
        Button fireButton = serverButton(Component.translatable("gui.simukraft.mineral_drilling.fire"),
                4, top, buttonWidth, buttonHeight, hasWorker,
                () -> holder.fireWorker(player), false);
        bindActive(fireButton, () -> holder.snapshot().hasWorker());
        panel.addChild(fireButton);
        top += buttonHeight + gap;
        panel.addChild(boundServerButton(holder::toggleText, 4, top, buttonWidth, buttonHeight,
                true, () -> holder.toggleRunning(player), false));
        top += buttonHeight + gap;
        panel.addChild(clientButton(holder.boundsText(), 4, top, buttonWidth, buttonHeight,
                holder.snapshot().hasBounds(), () -> clientActions.toggleBounds(holder.snapshot())));
        top += buttonHeight + gap;
        Button demolishButton = serverButton(Component.translatable("gui.simukraft.mineral_drilling.demolish"),
                4, top, buttonWidth, buttonHeight, holder.snapshot().hasBuilding(),
                () -> holder.demolish(player), true);
        // 客户端先清掉本地边界，服务端成功后会关闭容器；拒绝拆除时仍可再次显示边界。
        demolishButton.setOnClick(event -> clientActions.clearBounds(holder.snapshot()));
        panel.addChild(demolishButton);
    }

    private static void addInventoryPanel(UIElement workspace, MineralDrillingUiMetrics metrics) {
        int padding = metrics.panelPadding();
        int height = metrics.bottomHeight() - padding;
        UIElement panel = panel(metrics.inventoryPanelX(), metrics.bottomY(),
                metrics.inventoryPanelWidth(), height, PANEL_RECESSED);
        workspace.addChild(panel);
    }

    private static Button clientButton(Component text,
                                       int x,
                                       int y,
                                       int width,
                                       int height,
                                       boolean active,
                                       Runnable action) {
        Button button = styledButton(x, y, width, height, active, false);
        button.addChild(label(text, 2, 1, width - 4, height - 2,
                Horizontal.CENTER, active ? TEXT_PRIMARY : 0xFF707575));
        button.setOnClick(event -> action.run());
        return button;
    }

    private static Button serverButton(Component text,
                                       int x,
                                       int y,
                                       int width,
                                       int height,
                                       boolean active,
                                       Runnable action,
                                       boolean danger) {
        Button button = styledButton(x, y, width, height, active, danger);
        button.addChild(label(text, 2, 1, width - 4, height - 2,
                Horizontal.CENTER, active ? TEXT_PRIMARY : 0xFF707575));
        button.setOnServerClick(event -> action.run());
        return button;
    }

    private static Button boundServerButton(Supplier<Component> text,
                                            int x,
                                            int y,
                                            int width,
                                            int height,
                                            boolean active,
                                            Runnable action,
                                            boolean danger) {
        Button button = styledButton(x, y, width, height, active, danger);
        button.addChild(boundLabel(text, 2, 1, width - 4, height - 2,
                Horizontal.CENTER, active ? TEXT_PRIMARY : 0xFF707575));
        button.setOnServerClick(event -> action.run());
        return button;
    }

    private static Button styledButton(int x,
                                       int y,
                                       int width,
                                       int height,
                                       boolean active,
                                       boolean danger) {
        Button button = new Button().noText();
        IGuiTexture base = buttonTexture(danger ? DANGER_BASE : PANEL_LIGHT);
        IGuiTexture hover = buttonTexture(danger ? DANGER_HOVER : BUTTON_HOVER);
        IGuiTexture pressed = buttonTexture(BUTTON_PRESSED);
        button.buttonStyle(style -> style.baseTexture(base).hoverTexture(hover).pressedTexture(pressed));
        button.setActive(active);
        button.layout(layout -> absoluteLayout(layout, x, y, width, height));
        return button;
    }

    private static IGuiTexture buttonTexture(int color) {
        return new GuiTextureGroup(new ColorRectTexture(color), new ColorBorderTexture(1, FRAME_OUTER));
    }

    /** bindActive: 将服务端权威的可用状态单向同步到客户端按钮。 */
    private static void bindActive(Button button, Supplier<Boolean> activeSupplier) {
        button.addSyncValue(DataBindingBuilder.boolS2C(activeSupplier)
                .remoteSetter(button::setActive)
                .build()
                .getSyncValue());
    }

    private static Label boundLabel(Supplier<Component> supplier,
                                    int x,
                                    int y,
                                    int width,
                                    int height,
                                    Horizontal horizontal,
                                    int color) {
        Label label = label(Component.empty(), x, y, width, height, horizontal, color);
        label.bind(DataBindingBuilder.componentS2C(supplier).build());
        return label;
    }

    /** boundProductLabel: 服务端只同步资源 ID，由客户端的数据接收端解析物品本地化名称。 */
    private static Label boundProductLabel(Supplier<String> productIdSupplier,
                                           ProductTextResolver productTextResolver,
                                           boolean isClient,
                                           int x,
                                           int y,
                                           int width,
                                           int height,
                                           Horizontal horizontal,
                                           int color) {
        Label label = label(Component.empty(), x, y, width, height, horizontal, color);
        String initialProductId = productIdSupplier.get();
        if (isClient) {
            label.setText(productTextResolver.resolve(initialProductId));
        }
        label.bind(DataBindingBuilder.componentS2C(
                        () -> Component.literal(safeProductId(productIdSupplier.get())))
                .remoteSetter(productId -> label.setText(productTextResolver.resolve(productId.getString())))
                .build());
        return label;
    }

    /** safeProductId: 规范化同步前的产物资源 ID，避免空值写入组件。 */
    private static String safeProductId(String productId) {
        return productId == null ? "" : productId;
    }

    private static Label label(Component text,
                               int x,
                               int y,
                               int width,
                               int height,
                               Horizontal horizontal,
                               int color) {
        Label label = new Label();
        label.setText(text);
        label.setAllowHitTest(false);
        label.setOverflowVisible(false);
        label.layout(layout -> absoluteLayout(layout, x, y, width, height));
        label.textStyle(style -> style
                .textColor(color)
                .textShadow(color == TEXT_ON_DARK)
                .textWrap(TextWrap.ROLL)
                .rollSpeed(0.22F)
                .textAlignHorizontal(horizontal)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static UIElement panel(int x, int y, int width, int height, int color) {
        UIElement panel = absolute(x, y, width, height);
        panel.style(style -> style.backgroundTexture(new GuiTextureGroup(
                new ColorRectTexture(color), new ColorBorderTexture(1, FRAME_INNER))));
        return panel;
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

    /** ClientActions: 封装只允许物理客户端执行的雇佣和边界显示操作。 */
    public interface ClientActions {
        ClientActions NONE = new ClientActions() {
        };

        /** requestHire: 打开钻井工雇佣候选界面。 */
        default void requestHire(net.minecraft.core.BlockPos boxPos) {
        }

        /** toggleBounds: 切换当前建筑边界渲染。 */
        default void toggleBounds(MineralDrillingMenuSnapshot snapshot) {
        }

        /** clearBounds: 清理拆除请求对应的客户端建筑边界。 */
        default void clearBounds(MineralDrillingMenuSnapshot snapshot) {
        }
    }

    @FunctionalInterface
    public interface ProductTextResolver {
        /** resolve: 将服务端同步的物品资源 ID 转为当前物理客户端可见的文本。 */
        Component resolve(String productId);

        ProductTextResolver IDENTIFIER = productId -> Component.translatable(
                "gui.simukraft.mineral_drilling.product",
                productId == null || productId.isBlank()
                        ? Component.translatable("gui.simukraft.mineral_drilling.none")
                        : Component.literal(productId));
    }
}
