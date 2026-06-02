package client.cn.kafei.simukraft.client.industrial;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.building.BuildingIntegrityUi;
import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import common.cn.kafei.simukraft.industrial.IndustrialConstants;
import common.cn.kafei.simukraft.industrial.IndustrialItemStackSpec;
import common.cn.kafei.simukraft.network.industrial.IndustrialControlBoxActionPacket;
import common.cn.kafei.simukraft.network.industrial.IndustrialControlBoxDemolishPacket;
import common.cn.kafei.simukraft.network.industrial.IndustrialControlBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.industrial.IndustrialControlBoxOpenResponsePacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@SuppressWarnings("null")
public final class IndustrialControlBoxScreenOpener {
    private static final int MAX_PANEL_WIDTH = 480;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int MIN_PANEL_HEIGHT = 190;
    private static final int ROW_BUTTON_BASE = 0xFFE0E0E0;
    private static final int ROW_BUTTON_HOVER = 0xFFF0F0F0;
    private static final int ROW_BUTTON_SELECTED = 0xFFD8EAD8;
    private static final int ROW_BUTTON_BORDER = 0xFF1E1E1E;
    private static final float TEXT_ROLL_SPEED = 0.25F;
    private static BlockPos openedBoxPos;

    private IndustrialControlBoxScreenOpener() {
    }

    public static void request(BlockPos pos) {
        PacketDistributor.sendToServer(new IndustrialControlBoxOpenRequestPacket(pos));
    }

    public static void open(IndustrialControlBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        openedBoxPos = packet.boxPos().immutable();
        syncDisplayedBounds(packet);
        minecraft.execute(() -> minecraft.setScreen(new IndustrialControlBoxScreen(createUi(packet), Component.empty())));
    }

    public static void refreshIfOpen(IndustrialControlBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || openedBoxPos == null || !openedBoxPos.equals(packet.boxPos())) {
            return;
        }
        syncDisplayedBounds(packet);
        minecraft.execute(() -> {
            if (openedBoxPos != null && openedBoxPos.equals(packet.boxPos()) && minecraft.screen instanceof IndustrialControlBoxScreen) {
                minecraft.setScreen(new IndustrialControlBoxScreen(createUi(packet), Component.empty()));
            }
        });
    }

    private static ModularUI createUi(IndustrialControlBoxOpenResponsePacket packet) {
        int screenWidth = Math.max(320, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(240, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        LayoutMetrics metrics = layoutMetrics(screenWidth, screenHeight);
        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.paddingAll(metrics.rootPadding());
        });
        root.addChild(SimuKraftUiTheme.createShellPanel(screenWidth, screenHeight));

        UIElement panel = new UIElement().layout(layout -> {
            layout.width(metrics.panelWidth());
            layout.height(metrics.panelHeight());
            layout.paddingAll(metrics.panelPadding());
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(metrics.gap());
        }).addClass("simukraft_panel");

        panel.addChild(titleBar(metrics));
        panel.addChild(header(packet, metrics));
        panel.addChild(recipeList(packet, metrics));
        panel.addChild(actionRow(packet, metrics));

        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement titleBar(LayoutMetrics metrics) {
        UIElement bar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.titleBarHeight());
        });
        bar.addChild(label(Component.translatable("gui.simukraft.industrial.title"), Horizontal.CENTER, 0xFFFFFFFF, metrics.titleBarHeight(), TextWrap.HIDE));
        bar.addChild(panelTopButton("gui.button.done", metrics.doneButtonWidth(), metrics.doneButtonHeight(), IndustrialControlBoxScreenOpener::close));
        return bar;
    }

    private static UIElement header(IndustrialControlBoxOpenResponsePacket packet, LayoutMetrics metrics) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.headerHeight());
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(metrics.gap());
        });
        UIElement info = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(metrics.innerGap());
            layout.flexShrink(1);
        });
        info.setOverflowVisible(false);
        info.addChild(label(buildingLine(packet), Horizontal.LEFT, 0xFFF5F5A0, metrics.infoLineHeight(), TextWrap.HOVER_ROLL));
        info.addChild(label(definitionLine(packet), Horizontal.LEFT, packet.definitionValid() ? 0xFFF5F5A0 : 0xFFFF7070, metrics.infoLineHeight(), TextWrap.HOVER_ROLL));
        info.addChild(label(workerLine(packet), Horizontal.LEFT, 0xFFF5F5A0, metrics.infoLineHeight(), TextWrap.HOVER_ROLL));
        info.addChild(label(statusLine(packet), Horizontal.LEFT, 0xFFE0E0FF, metrics.infoLineHeight(), TextWrap.HOVER_ROLL));
        info.addChild(BuildingIntegrityUi.progressBar(packet.integrityAvailable(), packet.integrityPercent(), metrics.integrityBarHeight()));
        row.addChild(info);

        UIElement tools = new UIElement().layout(layout -> {
            layout.width(metrics.toolWidth());
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(metrics.innerGap());
            layout.flexShrink(0);
        });
        tools.addChild(flatButton(Component.translatable("gui.button.demolish"), () -> demolish(packet), packet.hasBuilding(), metrics.toolWidth(), metrics.toolButtonHeight()));
        tools.addChild(flatButton(boundsText(packet), () -> toggleBounds(packet), packet.hasBuildingBounds(), metrics.toolWidth(), metrics.toolButtonHeight()));
        tools.addChild(flatButton(BuildingIntegrityUi.repairText(packet.integrityRepairCost()), () -> action(packet, IndustrialControlBoxActionPacket.Action.REPAIR_BUILDING, ""), packet.integrityAvailable() && (packet.integrityRepairableBlocks() > 0 || packet.integrityManualRepairBlocks() > 0), metrics.toolWidth(), metrics.toolButtonHeight()));
        row.addChild(tools);
        return row;
    }

    private static UIElement recipeList(IndustrialControlBoxOpenResponsePacket packet, LayoutMetrics metrics) {
        UIElement recipes = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.recipeAreaHeight());
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(metrics.gap());
            layout.marginTop(metrics.innerGap());
        });
        recipes.setOverflowVisible(false);
        int maxRows = Math.max(1, metrics.recipeAreaHeight() / Math.max(1, metrics.recipeRowHeight() + metrics.gap()));
        List<IndustrialControlBoxOpenResponsePacket.RecipeEntry> visibleRecipes = packet.recipes().stream().limit(maxRows).toList();
        if (visibleRecipes.isEmpty()) {
            recipes.addChild(label(Component.translatable("gui.simukraft.industrial.no_recipes"), Horizontal.CENTER, 0xFFFF7070, metrics.recipeRowHeight(), TextWrap.HIDE));
        } else {
            visibleRecipes.forEach(recipe -> recipes.addChild(recipeRow(packet, recipe, metrics)));
        }
        return recipes;
    }

    private static UIElement actionRow(IndustrialControlBoxOpenResponsePacket packet, LayoutMetrics metrics) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.actionHeight());
            layout.flexDirection(FlexDirection.ROW);
            layout.flexWrap(FlexWrap.WRAP);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(metrics.gap());
        });
        row.addChild(flatButton(toggleText(packet), () -> action(packet, IndustrialControlBoxActionPacket.Action.TOGGLE_RUN, ""), packet.definitionValid() && packet.hasWorker(), metrics.actionWidth(), metrics.actionHeight()));
        row.addChild(flatButton(Component.translatable("gui.simukraft.industrial.hire"), () -> hire(packet), !packet.hasWorker(), metrics.actionWidth(), metrics.actionHeight()));
        row.addChild(flatButton(Component.translatable("gui.simukraft.industrial.fire"), () -> action(packet, IndustrialControlBoxActionPacket.Action.FIRE, ""), packet.hasWorker(), metrics.actionWidth(), metrics.actionHeight()));
        return row;
    }

    private static UIElement recipeRow(IndustrialControlBoxOpenResponsePacket packet, IndustrialControlBoxOpenResponsePacket.RecipeEntry recipe, LayoutMetrics metrics) {
        boolean selected = recipe.id().equals(packet.selectedRecipeId());
        Button button = new Button().noText();
        button.buttonStyle(style -> style
                .baseTexture(rowButtonTexture(selected, false))
                .hoverTexture(rowButtonTexture(selected, true))
                .pressedTexture(rowButtonTexture(true, true)));
        button.setOnClick(event -> action(packet, IndustrialControlBoxActionPacket.Action.SELECT_RECIPE, recipe.id()));
        button.layout(layout -> {
            layout.widthPercent(100);
            layout.height(metrics.recipeRowHeight());
            layout.paddingLeft(metrics.rowPadding());
            layout.paddingRight(metrics.rowPadding());
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(metrics.innerGap());
        });
        button.addChild(label(Component.literal(selected ? ">" : ""), Horizontal.CENTER, 0xFF1E1E1E, metrics.recipeRowHeight(), TextWrap.HIDE, false).layout(layout -> {
            layout.width(metrics.selectorWidth());
            layout.flexShrink(0);
        }));
        button.addChild(label(Component.literal(recipe.name()), Horizontal.LEFT, 0xFF222222, metrics.recipeRowHeight(), TextWrap.HOVER_ROLL, false).layout(layout -> {
            layout.flex(1);
            layout.flexShrink(1);
        }));
        button.addChild(itemStrip(recipe.inputs(), metrics.inputStripWidth(), metrics.inputLimit(), metrics));
        button.addChild(label(Component.literal("->"), Horizontal.CENTER, 0xFFFFFFFF, metrics.recipeRowHeight(), TextWrap.HIDE).layout(layout -> {
            layout.width(metrics.arrowWidth());
            layout.flexShrink(0);
        }));
        button.addChild(itemStrip(recipe.outputs(), metrics.outputStripWidth(), metrics.outputLimit(), metrics));
        return button;
    }

    private static UIElement itemStrip(List<IndustrialControlBoxOpenResponsePacket.ItemEntry> items, int width, int limit, LayoutMetrics metrics) {
        UIElement strip = new UIElement().layout(layout -> {
            layout.width(width);
            layout.height(metrics.iconSize());
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(metrics.iconGap());
            layout.flexShrink(0);
        });
        strip.setOverflowVisible(false);
        items.stream().limit(limit).forEach(item -> strip.addChild(icon(item, metrics.iconSize())));
        return strip;
    }

    private static UIElement icon(IndustrialControlBoxOpenResponsePacket.ItemEntry item, int size) {
        return new UIElement().layout(layout -> {
            layout.width(size);
            layout.height(size);
            layout.flexShrink(0);
        }).style(style -> style.backgroundTexture(new ItemStackTexture(stack(item.itemId(), item.potionId(), item.count()))));
    }

    private static Button panelTopButton(String key, int width, int height, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(width);
            layout.height(height);
        });
        return button;
    }

    private static Button flatButton(Component text, Runnable action, boolean active, int width, int height) {
        Button button = new Button();
        button.setText(text);
        button.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED));
        if (active) {
            button.setOnClick(event -> action.run());
        }
        button.setActive(active);
        button.layout(layout -> {
            layout.width(width);
            layout.height(height);
            layout.flexShrink(0);
        });
        return button;
    }

    private static IGuiTexture rowButtonTexture(boolean selected, boolean hover) {
        int color = selected ? ROW_BUTTON_SELECTED : hover ? ROW_BUTTON_HOVER : ROW_BUTTON_BASE;
        return new GuiTextureGroup(new ColorRectTexture(color), new ColorBorderTexture(-1, ROW_BUTTON_BORDER));
    }

    private static Label label(Component text, Horizontal horizontal, int color, int height, TextWrap wrap) {
        return label(text, horizontal, color, height, wrap, true);
    }

    private static Label label(Component text, Horizontal horizontal, int color, int height, TextWrap wrap, boolean shadow) {
        Label label = new Label();
        label.setText(text);
        label.setOverflowVisible(false);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
        label.textStyle(style -> style
                .textColor(color)
                .textShadow(shadow)
                .textWrap(wrap)
                .textAlignHorizontal(horizontal)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static Component buildingLine(IndustrialControlBoxOpenResponsePacket packet) {
        Component value = packet.hasBuilding() ? Component.literal(packet.buildingName()) : Component.translatable("gui.simukraft.industrial.none");
        return Component.translatable("gui.simukraft.industrial.building_line", value);
    }

    private static Component definitionLine(IndustrialControlBoxOpenResponsePacket packet) {
        Component value = packet.definitionValid() ? Component.literal(packet.definitionName()) : Component.translatable("gui.simukraft.industrial.definition_missing");
        return Component.translatable("gui.simukraft.industrial.definition_line", value);
    }

    private static Component workerLine(IndustrialControlBoxOpenResponsePacket packet) {
        Component value = packet.hasWorker() ? Component.literal(packet.workerName()) : Component.translatable("gui.simukraft.industrial.none");
        return Component.translatable("gui.simukraft.industrial.worker_line", value);
    }

    private static Component statusLine(IndustrialControlBoxOpenResponsePacket packet) {
        Component status = Component.translatable(packet.statusKey());
        if (!packet.statusText().isBlank()) {
            status = status.copy().append(Component.literal(" " + packet.statusText()));
        }
        return Component.translatable("gui.simukraft.industrial.status_line", status);
    }

    private static Component toggleText(IndustrialControlBoxOpenResponsePacket packet) {
        return Component.translatable(packet.running() ? "gui.simukraft.industrial.stop" : "gui.simukraft.industrial.start");
    }

    private static Component boundsText(IndustrialControlBoxOpenResponsePacket packet) {
        return Component.translatable("gui.simukraft.industrial.show_building_bounds", onOffText(BuildingBoundsRenderer.isBuildingBoundsVisible(packet.boxPos())));
    }

    private static Component onOffText(boolean enabled) {
        return Component.translatable(enabled ? "gui.switch.on" : "gui.switch.off");
    }

    private static void toggleBounds(IndustrialControlBoxOpenResponsePacket packet) {
        boolean next = !BuildingBoundsRenderer.isBuildingBoundsVisible(packet.boxPos());
        if (next) {
            showBounds(packet);
        } else {
            BuildingBoundsRenderer.setBuildingBoundsVisible(packet.boxPos(), null, false);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new IndustrialControlBoxScreen(createUi(packet), Component.empty()));
        }
    }

    private static void syncDisplayedBounds(IndustrialControlBoxOpenResponsePacket packet) {
        if (!BuildingBoundsRenderer.isBuildingBoundsVisible(packet.boxPos())) {
            return;
        }
        if (packet.hasBuildingBounds()) {
            showBounds(packet);
        } else {
            BuildingBoundsRenderer.setBuildingBoundsVisible(packet.boxPos(), null, false);
        }
    }

    private static void showBounds(IndustrialControlBoxOpenResponsePacket packet) {
        if (!packet.hasBuildingBounds()) {
            return;
        }
        AABB bounds = new AABB(
                packet.boundsMin().getX(),
                packet.boundsMin().getY(),
                packet.boundsMin().getZ(),
                packet.boundsMax().getX() + 1,
                packet.boundsMax().getY() + 1,
                packet.boundsMax().getZ() + 1);
        BuildingBoundsRenderer.setBuildingBoundsVisibleWithMarkers(packet.boxPos(), bounds, markerList(packet), true);
    }

    private static List<BuildingBoundsRenderer.DisplayMarker> markerList(IndustrialControlBoxOpenResponsePacket packet) {
        return packet.pointMarkers().stream()
                .map(marker -> new BuildingBoundsRenderer.DisplayMarker(marker.pos(), marker.color()))
                .toList();
    }

    private static void action(IndustrialControlBoxOpenResponsePacket packet, IndustrialControlBoxActionPacket.Action action, String recipeId) {
        PacketDistributor.sendToServer(new IndustrialControlBoxActionPacket(packet.boxPos(), action, recipeId));
    }

    private static void hire(IndustrialControlBoxOpenResponsePacket packet) {
        NpcHireScreen.request(packet.boxPos(), IndustrialConstants.HIRE_SOURCE_TYPE, IndustrialConstants.HIRE_ROLE);
    }

    private static void demolish(IndustrialControlBoxOpenResponsePacket packet) {
        BuildingBoundsRenderer.setBuildingBoundsVisible(packet.boxPos(), null, false);
        openedBoxPos = null;
        Minecraft.getInstance().setScreen(null);
        PacketDistributor.sendToServer(new IndustrialControlBoxDemolishPacket(packet.boxPos()));
    }

    private static ItemStack stack(String itemId, String potionId, int count) {
        ItemStack stack = IndustrialItemStackSpec.of(itemId, potionId).stack(count);
        return stack.isEmpty() ? IndustrialItemStackSpec.of("minecraft:barrier", "").stack(1) : stack;
    }

    private static void close() {
        openedBoxPos = null;
        Minecraft.getInstance().setScreen(null);
    }

    private static LayoutMetrics layoutMetrics(int screenWidth, int screenHeight) {
        int rootPadding = clamp(Math.round(Math.min(screenWidth, screenHeight) * 0.018F), 4, 10);
        int availableWidth = Math.max(MIN_PANEL_WIDTH, screenWidth - rootPadding * 2);
        int availableHeight = Math.max(MIN_PANEL_HEIGHT, screenHeight - rootPadding * 2 - 24);
        int panelWidth = clamp(Math.min(MAX_PANEL_WIDTH, availableWidth), Math.min(MIN_PANEL_WIDTH, availableWidth), availableWidth);
        int panelHeight = clamp(Math.min(MAX_PANEL_HEIGHT, availableHeight), Math.min(MIN_PANEL_HEIGHT, availableHeight), availableHeight);
        int panelPadding = clamp(Math.round(panelWidth * 0.024F), 6, 12);
        int gap = clamp(Math.round(panelHeight * 0.018F), 3, 6);
        int innerGap = clamp(gap - 1, 2, 5);
        int titleHeight = clamp(Math.round(panelHeight * 0.080F), 14, 20);
        int doneButtonHeight = clamp(Math.round(panelHeight * 0.078F), 18, 24);
        int titleBarHeight = Math.max(titleHeight, doneButtonHeight);
        int infoLineHeight = clamp(Math.round(panelHeight * 0.056F), 11, 16);
        int integrityBarHeight = clamp(Math.round(panelHeight * 0.060F), 12, 18);
        int toolButtonHeight = clamp(Math.round(panelHeight * 0.085F), 18, 24);
        int toolWidth = clamp(Math.round(panelWidth * 0.235F), 86, 112);
        int headerHeight = Math.max(infoLineHeight * 4 + integrityBarHeight + innerGap * 4, toolButtonHeight * 3 + innerGap * 2);
        int actionHeight = clamp(Math.round(panelHeight * 0.078F), 20, 24);
        int actionWidth = clamp((panelWidth - panelPadding * 2 - gap * 2) / 3, 84, 132);
        int recipeAreaHeight = Math.max(28, panelHeight - panelPadding * 2 - titleBarHeight - headerHeight - actionHeight - gap * 4 - innerGap);
        int recipeRowHeight = clamp(Math.round(panelHeight * 0.098F), 24, 32);
        int iconSize = clamp(recipeRowHeight - 10, 14, 20);
        int iconGap = clamp(iconSize / 5, 2, 4);
        int selectorWidth = clamp(Math.round(panelWidth * 0.035F), 12, 18);
        int arrowWidth = clamp(Math.round(panelWidth * 0.050F), 18, 26);
        int inputStripWidth = clamp(Math.round(panelWidth * 0.200F), 64, 98);
        int outputStripWidth = clamp(Math.round(panelWidth * 0.180F), 56, 88);
        int inputLimit = Math.max(1, inputStripWidth / Math.max(1, iconSize + iconGap));
        int outputLimit = Math.max(1, outputStripWidth / Math.max(1, iconSize + iconGap));
        int rowPadding = clamp(Math.round(panelWidth * 0.014F), 4, 8);
        int doneButtonWidth = clamp(Math.round(panelWidth * 0.16F), 50, 76);
        return new LayoutMetrics(rootPadding, panelWidth, panelHeight, panelPadding, gap, innerGap, titleBarHeight, doneButtonHeight, infoLineHeight, integrityBarHeight, headerHeight, toolWidth, toolButtonHeight, recipeAreaHeight, recipeRowHeight, iconSize, iconGap, selectorWidth, arrowWidth, inputStripWidth, outputStripWidth, inputLimit, outputLimit, rowPadding, actionWidth, actionHeight, doneButtonWidth);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record LayoutMetrics(int rootPadding,
                                 int panelWidth,
                                 int panelHeight,
                                 int panelPadding,
                                 int gap,
                                 int innerGap,
                                 int titleBarHeight,
                                 int doneButtonHeight,
                                 int infoLineHeight,
                                 int integrityBarHeight,
                                 int headerHeight,
                                 int toolWidth,
                                 int toolButtonHeight,
                                 int recipeAreaHeight,
                                 int recipeRowHeight,
                                 int iconSize,
                                 int iconGap,
                                 int selectorWidth,
                                 int arrowWidth,
                                 int inputStripWidth,
                                 int outputStripWidth,
                                 int inputLimit,
                                 int outputLimit,
                                 int rowPadding,
                                 int actionWidth,
                                 int actionHeight,
                                 int doneButtonWidth) {
    }

    private static final class IndustrialControlBoxScreen extends ModularUIScreen {
        private IndustrialControlBoxScreen(ModularUI modularUI, Component title) {
            super(modularUI, title);
        }

        @Override
        public void removed() {
            super.removed();
            Minecraft minecraft = Minecraft.getInstance();
            if (!(minecraft.screen instanceof IndustrialControlBoxScreen)) {
                openedBoxPos = null;
            }
        }
    }
}
