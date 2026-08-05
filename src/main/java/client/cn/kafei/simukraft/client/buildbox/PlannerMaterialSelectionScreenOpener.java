package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.ui.SimuKraftFlexLayout;
import client.cn.kafei.simukraft.client.ui.SimuKraftClientUiPreferences;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.SplitView;
import common.cn.kafei.simukraft.network.planner.CreatePlanningTaskPacket;
import common.cn.kafei.simukraft.network.planner.PlannerMaterialScanResponsePacket;
import common.cn.kafei.simukraft.planner.PlanOperation;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class PlannerMaterialSelectionScreenOpener {
    private static final int MAX_CONTENT_WIDTH = 760;
    private static final int MIN_CONTENT_WIDTH = 300;
    private static final float TITLE_LEFT_RATIO = 0.12F;
    private static final float TITLE_TOP_RATIO = 0.055F;
    private static final float TITLE_WIDTH_RATIO = 0.76F;
    private static final float TITLE_HEIGHT_RATIO = 0.08F;
    private static final float CONTENT_LEFT_RATIO = 0.04F;
    private static final float CONTENT_TOP_RATIO = 0.17F;
    private static final float CONTENT_WIDTH_RATIO = 0.92F;
    private static final float CONTENT_HEIGHT_RATIO = 0.68F;
    private static final float FOOTER_TOP_RATIO = 0.875F;
    private static final float FOOTER_HEIGHT_RATIO = 0.08F;
    private static final int ROW_BUTTON_BASE = 0xFFE0E0E0;
    private static final int ROW_BUTTON_HOVER = 0xFFF0F0F0;
    private static final int ROW_BUTTON_SELECTED = 0xFFD8EAD8;
    private static final int ROW_BUTTON_BORDER = 0xFF1E1E1E;
    private static final float TEXT_ROLL_SPEED = 0.35F;
    private static final String PREF_FILL_CONTAINER = "planner.material.fill.container";
    private static final String PREF_REPLACE_CONTAINER = "planner.material.replace.container";
    private static final String PREF_REPLACE_SOURCE = "planner.material.replace.source";
    private static final String PREF_REPLACE_TARGET = "planner.material.replace.target";
    private static PlannerMaterialScanResponsePacket currentPacket;
    private static BlockPos selectedChest;
    private static String selectedFillBlock = "";
    private static String selectedSourceBlock = "";
    private static String selectedTargetBlock = "";
    private static final Map<String, String> REPLACEMENT_MAP = new LinkedHashMap<>();
    private static final List<PersistentHorizontalSplitView> ACTIVE_SPLITS = new CopyOnWriteArrayList<>();
    private static UIElement activeFooterRegion;
    private static UIElement activeContainerColumn;
    private static UIElement activeFillBlocksColumn;
    private static UIElement activeSourceColumn;
    private static UIElement activeTargetColumn;
    private static UIElement activeMapColumn;
    private static MaterialLayoutMetrics activeMetrics;

    private PlannerMaterialSelectionScreenOpener() {
    }

    public static void open(PlannerMaterialScanResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        currentPacket = packet;
        selectedChest = packet.containers().isEmpty() ? null : packet.containers().getFirst().pos();
        selectedFillBlock = "";
        selectedSourceBlock = "";
        selectedTargetBlock = "";
        REPLACEMENT_MAP.clear();
        minecraft.execute(() -> {
            saveActiveSplitPreferences();
            ACTIVE_SPLITS.clear();
            clearActiveRegions();
            reopen();
        });
    }

    private static void reopen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || currentPacket == null) {
            return;
        }
        minecraft.setScreen(new ModularUIScreen(createUi(), Component.empty()));
    }

    private static void refreshPreservingLayout() {
        refreshActiveRegions();
    }

    private static void saveActiveSplitPreferences() {
        for (PersistentHorizontalSplitView splitView : ACTIVE_SPLITS) {
            splitView.savePreference();
        }
    }

    private static void refreshActiveRegions() {
        PlannerMaterialScanResponsePacket packet = currentPacket;
        if (packet == null || activeFooterRegion == null || activeMetrics == null) {
            reopen();
            return;
        }
        if (packet.operation() == PlanOperation.FILL) {
            if (activeContainerColumn == null || activeFillBlocksColumn == null) {
                reopen();
                return;
            }
            int listHeight = fillListHeight(activeMetrics);
            populateContainerColumn(activeContainerColumn, packet, listHeight, activeMetrics);
            populateFillBlocksColumn(activeFillBlocksColumn, listHeight, activeMetrics);
        } else {
            if (activeContainerColumn == null || activeSourceColumn == null || activeTargetColumn == null || activeMapColumn == null) {
                reopen();
                return;
            }
            int listHeight = replaceListHeight(activeMetrics);
            int mapHeight = replaceMapHeight(activeMetrics);
            populateContainerColumn(activeContainerColumn, packet, listHeight, activeMetrics);
            populateSourceColumn(activeSourceColumn, packet, listHeight, activeMetrics);
            populateTargetColumn(activeTargetColumn, listHeight, activeMetrics);
            populateMapColumn(activeMapColumn, mapHeight, activeMetrics);
        }
        activeFooterRegion.clearAllChildren();
        populateFooter(activeFooterRegion, packet, activeMetrics);
    }

    private static void clearActiveRegions() {
        activeFooterRegion = null;
        activeContainerColumn = null;
        activeFillBlocksColumn = null;
        activeSourceColumn = null;
        activeTargetColumn = null;
        activeMapColumn = null;
        activeMetrics = null;
    }

    private static ModularUI createUi() {
        PlannerMaterialScanResponsePacket packet = currentPacket;
        SimuKraftFlexLayout.ScreenSize screenSize = SimuKraftFlexLayout.screenSize();
        UIElement root = SimuKraftFlexLayout.root(screenSize);
        root.addEventListener(UIEvents.REMOVED, event -> saveActiveSplitPreferences());
        SimuKraftFlexLayout.addTopChrome(root, screenSize, Component.translatable("gui.button.back"), () -> {
            saveActiveSplitPreferences();
            PlannerOperationScreenOpener.open(packet.buildBoxPos());
        });

        UIElement titleRegion = titleRegion(screenSize);
        titleRegion.addChild(label(Component.translatable("gui.simukraft.plan_area.material_title", Component.translatable(packet.operation().translationKey())), Horizontal.CENTER, SimuKraftUiTheme.TEXT_PRIMARY_COLOR, 18, TextWrap.HIDE));
        root.addChild(titleRegion);

        MaterialLayoutMetrics metrics = layoutMetrics(screenSize);
        UIElement contentRegion = contentRegion(screenSize, metrics);
        activeMetrics = metrics;
        populateContent(contentRegion, packet, metrics);

        root.addChild(contentRegion);
        UIElement footerRegion = footerRegion(screenSize, metrics);
        activeFooterRegion = footerRegion;
        populateFooter(footerRegion, packet, metrics);
        root.addChild(footerRegion);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static void populateContent(UIElement contentRegion, PlannerMaterialScanResponsePacket packet, MaterialLayoutMetrics metrics) {
        if (packet.containers().isEmpty()) {
            contentRegion.layout(layout -> {
                layout.flexDirection(FlexDirection.COLUMN);
                layout.alignItems(AlignItems.CENTER);
                layout.justifyContent(AlignContent.CENTER);
            });
            contentRegion.addChild(label(Component.translatable("gui.simukraft.plan_area.no_containers"), Horizontal.CENTER, SimuKraftUiTheme.TEXT_ERROR_COLOR, 40, TextWrap.HIDE));
        } else if (packet.operation() == PlanOperation.FILL) {
            contentRegion.addChild(fillContent(packet, metrics));
        } else {
            contentRegion.addChild(replaceContent(packet, metrics));
        }
    }

    private static UIElement titleRegion(SimuKraftFlexLayout.ScreenSize screenSize) {
        UIElement region = SimuKraftFlexLayout.absoluteRegion(
                clamp(Math.round(screenSize.width() * TITLE_LEFT_RATIO), 0, screenSize.width() - 1),
                clamp(Math.round(screenSize.height() * TITLE_TOP_RATIO), 0, screenSize.height() - 1),
                clamp(Math.round(screenSize.width() * TITLE_WIDTH_RATIO), 1, screenSize.width()),
                clamp(Math.round(screenSize.height() * TITLE_HEIGHT_RATIO), 1, screenSize.height()));
        region.layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(6);
        });
        return region;
    }

    private static UIElement footerRegion(SimuKraftFlexLayout.ScreenSize screenSize, MaterialLayoutMetrics metrics) {
        int left = screenSize.width() > metrics.contentWidth()
                ? (screenSize.width() - metrics.contentWidth()) / 2
                : clamp(Math.round(screenSize.width() * CONTENT_LEFT_RATIO), 0, screenSize.width() - 1);
        UIElement footer = SimuKraftFlexLayout.absoluteRegion(
                left,
                clamp(Math.round(screenSize.height() * FOOTER_TOP_RATIO), 0, screenSize.height() - 1),
                metrics.contentWidth(),
                clamp(Math.round(screenSize.height() * FOOTER_HEIGHT_RATIO), 1, screenSize.height()));
        footer.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.gapAll(metrics.gap());
        });
        return footer;
    }

    private static void populateFooter(UIElement footer, PlannerMaterialScanResponsePacket packet, MaterialLayoutMetrics metrics) {
        UIElement bounds = label(boundsText(packet), Horizontal.LEFT, SimuKraftUiTheme.TEXT_INFO_COLOR, metrics.footerTextHeight(), TextWrap.WRAP).layout(layout -> {
            layout.width(Math.max(1, metrics.contentWidth() - metrics.footerActionsWidth() - metrics.gap()));
            layout.height(metrics.footerTextHeight());
            layout.flexShrink(1);
        });
        footer.addChild(bounds);
        footer.addChild(footerActions(packet, metrics));
    }

    private static UIElement footerActions(PlannerMaterialScanResponsePacket packet, MaterialLayoutMetrics metrics) {
        UIElement actions = new UIElement().layout(layout -> {
            layout.width(metrics.footerActionsWidth());
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.FLEX_END);
            layout.gapAll(metrics.gap());
            layout.flexShrink(0);
        });
        if (packet.operation() == PlanOperation.FILL) {
            actions.addChild(label(selectedFillBlock.isBlank()
                    ? Component.translatable("gui.simukraft.plan_area.no_block_selected")
                    : Component.translatable("gui.simukraft.plan_area.fill_selected", blockName(selectedFillBlock)), Horizontal.RIGHT, SimuKraftUiTheme.TEXT_SUCCESS_COLOR, metrics.actionHeight(), TextWrap.HOVER_ROLL).layout(layout -> {
                layout.width(Math.max(1, metrics.footerActionsWidth() - metrics.actionWidth() - metrics.gap()));
                layout.height(metrics.actionHeight());
                layout.flexShrink(1);
            }));
            actions.addChild(actionButton(Component.translatable("gui.simukraft.plan_area.confirm_start"), PlannerMaterialSelectionScreenOpener::startFill, !selectedFillBlock.isBlank(), metrics.actionWidth(), metrics.actionHeight()));
            return actions;
        }
        actions.addChild(actionButton(Component.translatable("gui.simukraft.plan_area.clear_mapping"), () -> {
            REPLACEMENT_MAP.clear();
            refreshPreservingLayout();
        }, !REPLACEMENT_MAP.isEmpty(), metrics.actionWidth(), metrics.actionHeight()));
        actions.addChild(actionButton(Component.translatable("gui.simukraft.plan_area.confirm_start"), PlannerMaterialSelectionScreenOpener::startReplace, !REPLACEMENT_MAP.isEmpty(), metrics.actionWidth(), metrics.actionHeight()));
        return actions;
    }

    private static UIElement contentRegion(SimuKraftFlexLayout.ScreenSize screenSize, MaterialLayoutMetrics metrics) {
        int left = screenSize.width() > metrics.contentWidth()
                ? (screenSize.width() - metrics.contentWidth()) / 2
                : clamp(Math.round(screenSize.width() * CONTENT_LEFT_RATIO), 0, screenSize.width() - 1);
        UIElement region = SimuKraftFlexLayout.absoluteRegion(
                left,
                clamp(Math.round(screenSize.height() * CONTENT_TOP_RATIO), 0, screenSize.height() - 1),
                metrics.contentWidth(),
                metrics.contentHeight());
        region.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        return region;
    }

    private static UIElement fillContent(PlannerMaterialScanResponsePacket packet, MaterialLayoutMetrics metrics) {
        int listHeight = fillListHeight(metrics);
        UIElement body = bodyRow(metrics);
        activeContainerColumn = column(metrics);
        activeFillBlocksColumn = column(metrics);
        populateContainerColumn(activeContainerColumn, packet, listHeight, metrics);
        populateFillBlocksColumn(activeFillBlocksColumn, listHeight, metrics);
        body.addChild(splitView(PREF_FILL_CONTAINER, 31F, 16F, 55F, activeContainerColumn, activeFillBlocksColumn));
        return body;
    }

    private static UIElement replaceContent(PlannerMaterialScanResponsePacket packet, MaterialLayoutMetrics metrics) {
        int listHeight = replaceListHeight(metrics);
        int mapHeight = replaceMapHeight(metrics);
        UIElement body = bodyRow(metrics);

        activeContainerColumn = column(metrics);
        activeSourceColumn = column(metrics);
        activeTargetColumn = column(metrics);
        activeMapColumn = column(metrics);
        populateContainerColumn(activeContainerColumn, packet, listHeight, metrics);
        populateSourceColumn(activeSourceColumn, packet, listHeight, metrics);
        populateTargetColumn(activeTargetColumn, listHeight, metrics);
        populateMapColumn(activeMapColumn, mapHeight, metrics);

        UIElement targetAndMap = splitView(PREF_REPLACE_TARGET, 50F, 28F, 72F, activeTargetColumn, activeMapColumn);
        UIElement sourceAndRest = splitView(PREF_REPLACE_SOURCE, 33F, 22F, 58F, activeSourceColumn, targetAndMap);
        body.addChild(splitView(PREF_REPLACE_CONTAINER, 22F, 12F, 42F, activeContainerColumn, sourceAndRest));
        return body;
    }

    private static void populateFillBlocksColumn(UIElement blocks, int listHeight, MaterialLayoutMetrics metrics) {
        blocks.clearAllChildren();
        blocks.addChild(label(Component.translatable("gui.simukraft.plan_area.choose_fill_block"), Horizontal.LEFT, SimuKraftUiTheme.TEXT_WARNING_COLOR, metrics.labelHeight(), TextWrap.HIDE));
        blocks.addChild(scrollBlockList(currentChestBlocks(), selectedFillBlock, blockId -> {
            selectedFillBlock = blockId;
            refreshPreservingLayout();
        }, listHeight, metrics));
    }

    private static void populateSourceColumn(UIElement sourceColumn, PlannerMaterialScanResponsePacket packet, int listHeight, MaterialLayoutMetrics metrics) {
        sourceColumn.clearAllChildren();
        sourceColumn.addChild(label(Component.translatable("gui.simukraft.plan_area.replace_sources"), Horizontal.LEFT, SimuKraftUiTheme.TEXT_WARNING_COLOR, metrics.labelHeight(), TextWrap.HIDE));
        sourceColumn.addChild(scrollBlockList(packet.sourceBlocks(), selectedSourceBlock, blockId -> {
            selectedSourceBlock = blockId;
            if (!selectedTargetBlock.isBlank()) {
                REPLACEMENT_MAP.put(selectedSourceBlock, selectedTargetBlock);
            }
            refreshPreservingLayout();
        }, listHeight, metrics));
    }

    private static void populateTargetColumn(UIElement targetColumn, int listHeight, MaterialLayoutMetrics metrics) {
        targetColumn.clearAllChildren();
        targetColumn.addChild(label(Component.translatable("gui.simukraft.plan_area.replace_targets"), Horizontal.LEFT, SimuKraftUiTheme.TEXT_WARNING_COLOR, metrics.labelHeight(), TextWrap.HIDE));
        targetColumn.addChild(scrollBlockList(currentChestBlocks(), selectedTargetBlock, blockId -> {
            selectedTargetBlock = blockId;
            if (!selectedSourceBlock.isBlank()) {
                REPLACEMENT_MAP.put(selectedSourceBlock, selectedTargetBlock);
            }
            refreshPreservingLayout();
        }, listHeight, metrics));
    }

    private static void populateMapColumn(UIElement mapColumn, int mapHeight, MaterialLayoutMetrics metrics) {
        mapColumn.clearAllChildren();
        mapColumn.addChild(label(Component.translatable("gui.simukraft.plan_area.replacement_map", REPLACEMENT_MAP.size()), Horizontal.LEFT, SimuKraftUiTheme.TEXT_SUCCESS_COLOR, metrics.labelHeight(), TextWrap.HIDE));
        mapColumn.addChild(scrollMappings(mapHeight, metrics));
    }

    private static void populateContainerColumn(UIElement column, PlannerMaterialScanResponsePacket packet, int listHeight, MaterialLayoutMetrics metrics) {
        column.clearAllChildren();
        column.addChild(label(Component.translatable("gui.simukraft.plan_area.choose_container"), Horizontal.LEFT, SimuKraftUiTheme.TEXT_WARNING_COLOR, metrics.labelHeight(), TextWrap.HIDE));
        UIElement list = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(metrics.innerGap());
        });
        for (PlannerMaterialScanResponsePacket.ContainerBlocks container : packet.containers()) {
            Component text = Component.translatable("gui.simukraft.plan_area.container_entry",
                    container.pos().getX(), container.pos().getY(), container.pos().getZ(), container.blocks().size());
            boolean selected = container.pos().equals(selectedChest);
            list.addChild(containerButton(text, selected, () -> {
                selectedChest = container.pos();
                selectedFillBlock = "";
                selectedTargetBlock = "";
                REPLACEMENT_MAP.clear();
                refreshPreservingLayout();
            }, metrics));
        }
        column.addChild(scrollable(list, listHeight));
    }

    private static ScrollerView scrollBlockList(Map<String, Integer> blocks, String selectedBlock, BlockSelectAction action, int height, MaterialLayoutMetrics metrics) {
        UIElement list = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(metrics.innerGap());
        });
        if (blocks.isEmpty()) {
            list.addChild(label(Component.translatable("gui.simukraft.plan_area.no_blocks"), Horizontal.LEFT, SimuKraftUiTheme.TEXT_ERROR_COLOR, 18, TextWrap.HIDE));
        } else {
            blocks.forEach((blockId, count) -> list.addChild(blockButton(blockId, count, blockId.equals(selectedBlock), () -> action.select(blockId), metrics)));
        }
        return scrollable(list, height);
    }

    private static ScrollerView scrollMappings(int height, MaterialLayoutMetrics metrics) {
        UIElement list = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(metrics.innerGap());
        });
        if (REPLACEMENT_MAP.isEmpty()) {
            list.addChild(label(Component.translatable("gui.simukraft.plan_area.no_mapping"), Horizontal.LEFT, SimuKraftUiTheme.TEXT_MUTED_COLOR, 18, TextWrap.HIDE));
        } else {
            REPLACEMENT_MAP.forEach((source, target) -> list.addChild(mappingButton(source, target, () -> {
                REPLACEMENT_MAP.remove(source);
                refreshPreservingLayout();
            }, metrics)));
        }
        return scrollable(list, height);
    }

    private static ScrollerView scrollable(UIElement child, int height) {
        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        scroller.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
        scroller.addScrollViewChild(child);
        return scroller;
    }

    private static UIElement bodyRow(MaterialLayoutMetrics metrics) {
        return new UIElement().layout(layout -> {
            layout.width(metrics.contentWidth());
            layout.height(metrics.contentHeight());
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.STRETCH);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(metrics.gap());
        });
    }

    private static UIElement splitView(String key, float fallback, float min, float max, UIElement first, UIElement second) {
        PersistentHorizontalSplitView splitView = new PersistentHorizontalSplitView(key, min, max);
        splitView.setBorderSize(5F);
        splitView.setMinPercentage(min);
        splitView.setMaxPercentage(max);
        splitView.left(first);
        splitView.right(second);
        splitView.applySavedPercentage(fallback);
        ACTIVE_SPLITS.add(splitView);
        splitView.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flex(1);
        });
        return splitView;
    }

    private static UIElement column(MaterialLayoutMetrics metrics) {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(metrics.gap());
            layout.flex(1);
        });
    }

    private static final class PersistentHorizontalSplitView extends SplitView.Horizontal {
        private final String preferenceKey;
        private final float min;
        private final float max;
        private float currentPercentage;
        private float lastSavedPercentage;

        private PersistentHorizontalSplitView(String preferenceKey, float min, float max) {
            this.preferenceKey = preferenceKey;
            this.min = min;
            this.max = max;
            addEventListener(UIEvents.MOUSE_UP, event -> savePreference());
            addEventListener(UIEvents.DRAG_END, event -> savePreference());
            addEventListener(UIEvents.REMOVED, event -> savePreference());
        }

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
            SimuKraftClientUiPreferences.setFloat(preferenceKey, currentPercentage, min, max);
            lastSavedPercentage = currentPercentage;
            return this;
        }

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

    private static Button actionButton(Component text, Runnable action, boolean active, int width, int height) {
        Button button = new Button();
        button.setText(text);
        button.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED));
        button.setActive(active);
        if (active) {
            button.setOnClick(event -> action.run());
        }
        button.layout(layout -> {
            layout.width(width);
            layout.height(height);
            layout.flexShrink(0);
        });
        return button;
    }

    private static Button containerButton(Component text, boolean selected, Runnable action, MaterialLayoutMetrics metrics) {
        Button button = rowButton(action, true, metrics.rowHeight(), selected);
        button.addChild(iconElement(new ItemStack(Items.CHEST), metrics.iconSize()));
        button.addChild(textColumn(selected ? Component.translatable("gui.simukraft.plan_area.selected_entry", text) : text,
                Component.translatable("gui.simukraft.plan_area.container_icon_hint"), selected, metrics));
        return button;
    }

    private static Button blockButton(String blockId, int count, boolean selected, Runnable action, MaterialLayoutMetrics metrics) {
        Button button = rowButton(action, true, metrics.rowHeight(), selected);
        button.addChild(iconElement(blockStack(blockId), metrics.iconSize()));
        button.addChild(textColumn(blockName(blockId), Component.literal("x" + count), selected, metrics));
        return button;
    }

    private static Button mappingButton(String sourceBlockId, String targetBlockId, Runnable action, MaterialLayoutMetrics metrics) {
        Button button = rowButton(action, true, metrics.rowHeight(), true);
        button.addChild(iconElement(blockStack(sourceBlockId), metrics.iconSize()));
        button.addChild(label(Component.literal(">"), Horizontal.CENTER, SimuKraftUiTheme.TEXT_WARNING_COLOR, metrics.iconSize(), TextWrap.HIDE).layout(layout -> {
            layout.width(Math.max(12, metrics.iconSize() - 2));
            layout.height(metrics.iconSize());
            layout.flexShrink(0);
        }));
        button.addChild(iconElement(blockStack(targetBlockId), metrics.iconSize()));
        button.addChild(textColumn(blockName(sourceBlockId), blockName(targetBlockId), true, metrics));
        return button;
    }

    private static Button rowButton(Runnable action, boolean active, int height, boolean selected) {
        Button button = new Button().noText();
        button.buttonStyle(style -> style
                .baseTexture(rowButtonTexture(selected, false))
                .hoverTexture(rowButtonTexture(selected, true))
                .pressedTexture(rowButtonTexture(true, true)));
        button.setActive(active);
        if (active) {
            button.setOnClick(event -> action.run());
        }
        button.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.FLEX_START);
            layout.gapAll(4);
            layout.paddingAll(3);
            layout.flexShrink(0);
        });
        return button;
    }

    private static IGuiTexture rowButtonTexture(boolean selected, boolean hover) {
        int color = selected ? ROW_BUTTON_SELECTED : hover ? ROW_BUTTON_HOVER : ROW_BUTTON_BASE;
        return new GuiTextureGroup(new ColorRectTexture(color), new ColorBorderTexture(-1, ROW_BUTTON_BORDER));
    }

    private static UIElement iconElement(ItemStack stack, int size) {
        return new UIElement().layout(layout -> {
            layout.width(size);
            layout.height(size);
            layout.flexShrink(0);
        }).style(style -> style.backgroundTexture(new ItemStackTexture(stack.copyWithCount(1))));
    }

    private static UIElement textColumn(Component primary, Component secondary, boolean selected, MaterialLayoutMetrics metrics) {
        UIElement column = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.height(metrics.rowHeight() - 8);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.flexShrink(1);
        });
        column.setOverflowVisible(false);
        column.addChild(label(primary, Horizontal.LEFT, selected ? SimuKraftUiTheme.TEXT_SUCCESS_COLOR : SimuKraftUiTheme.TEXT_PRIMARY_COLOR, metrics.textLineHeight(), TextWrap.HOVER_ROLL));
        column.addChild(label(secondary, Horizontal.LEFT, SimuKraftUiTheme.TEXT_MUTED_COLOR, metrics.textLineHeight(), TextWrap.HOVER_ROLL));
        return column;
    }

    private static Label label(Component text, Horizontal horizontal, int color, int height, TextWrap wrap) {
        Label label = new Label();
        label.setText(text);
        label.setOverflowVisible(false);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
        label.textStyle(style -> style
                .textColor(color)
                .textShadow(true)
                .textWrap(wrap)
                .rollSpeed(TEXT_ROLL_SPEED)
                .textAlignHorizontal(horizontal)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static Component boundsText(PlannerMaterialScanResponsePacket packet) {
        return Component.translatable("gui.simukraft.area_selection.bounds",
                packet.min().getX(), packet.min().getY(), packet.min().getZ(),
                packet.max().getX(), packet.max().getY(), packet.max().getZ(),
                (packet.max().getX() - packet.min().getX() + 1) * (packet.max().getY() - packet.min().getY() + 1) * (packet.max().getZ() - packet.min().getZ() + 1));
    }

    private static Map<String, Integer> currentChestBlocks() {
        PlannerMaterialScanResponsePacket packet = currentPacket;
        if (packet == null || selectedChest == null) {
            return Map.of();
        }
        return packet.containers().stream()
                .filter(container -> selectedChest.equals(container.pos()))
                .findFirst()
                .map(PlannerMaterialScanResponsePacket.ContainerBlocks::blocks)
                .orElse(Map.of());
    }

    private static void startFill() {
        PlannerMaterialScanResponsePacket packet = currentPacket;
        if (packet == null || selectedChest == null || selectedFillBlock.isBlank()) {
            return;
        }
        saveActiveSplitPreferences();
        PacketDistributor.sendToServer(new CreatePlanningTaskPacket(packet.buildBoxPos(), packet.min(), packet.max(), PlanOperation.FILL, selectedFillBlock, "", selectedChest, Map.of()));
        Minecraft.getInstance().setScreen(null);
    }

    private static void startReplace() {
        PlannerMaterialScanResponsePacket packet = currentPacket;
        if (packet == null || selectedChest == null || REPLACEMENT_MAP.isEmpty()) {
            return;
        }
        saveActiveSplitPreferences();
        Map.Entry<String, String> first = REPLACEMENT_MAP.entrySet().iterator().next();
        PacketDistributor.sendToServer(new CreatePlanningTaskPacket(packet.buildBoxPos(), packet.min(), packet.max(), PlanOperation.REPLACE, first.getValue(), first.getKey(), selectedChest, Map.copyOf(REPLACEMENT_MAP)));
        Minecraft.getInstance().setScreen(null);
    }

    private static Component blockName(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return Component.literal(blockId);
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block.asItem() == Items.AIR) {
            return block.getName();
        }
        return new ItemStack(block.asItem()).getHoverName();
    }

    private static ItemStack blockStack(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return new ItemStack(Items.BARRIER);
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block.asItem() == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(block.asItem());
    }

    private static int fillListHeight(MaterialLayoutMetrics metrics) {
        return Math.max(metrics.rowHeight() * 3, metrics.contentHeight() - metrics.labelHeight() - 10);
    }

    private static int replaceListHeight(MaterialLayoutMetrics metrics) {
        return Math.max(metrics.rowHeight() * 3, metrics.contentHeight() - metrics.labelHeight() - 20);
    }

    private static int replaceMapHeight(MaterialLayoutMetrics metrics) {
        return Math.max(metrics.rowHeight() * 2, metrics.contentHeight() - metrics.labelHeight() - 20);
    }

    private static MaterialLayoutMetrics layoutMetrics(SimuKraftFlexLayout.ScreenSize screenSize) {
        int contentWidth = clamp(Math.min(MAX_CONTENT_WIDTH, Math.round(screenSize.width() * CONTENT_WIDTH_RATIO)), MIN_CONTENT_WIDTH, screenSize.width());
        int footerTop = clamp(Math.round(screenSize.height() * FOOTER_TOP_RATIO), 0, screenSize.height() - 1);
        int contentTop = clamp(Math.round(screenSize.height() * CONTENT_TOP_RATIO), 0, screenSize.height() - 1);
        int contentHeight = clamp(Math.round(screenSize.height() * CONTENT_HEIGHT_RATIO), 140, Math.max(140, footerTop - contentTop - 8));
        int rowHeight = clamp(Math.round(screenSize.height() * 0.070F), 30, 38);
        int iconSize = clamp(rowHeight - 14, 16, 22);
        int textLineHeight = clamp((rowHeight - 10) / 2, 10, 14);
        int actionHeight = clamp(Math.round(screenSize.height() * 0.048F), 20, 26);
        int footerTextHeight = clamp(Math.round(screenSize.height() * 0.060F), 24, 34);
        int labelHeight = clamp(Math.round(screenSize.height() * 0.036F), 12, 18);
        int gap = clamp(Math.round(contentWidth * 0.014F), 4, 10);
        int innerGap = clamp(Math.round(contentWidth * 0.008F), 2, 5);
        int actionWidth = clamp(Math.round(contentWidth * 0.18F), 118, 150);
        int footerActionsWidth = Math.min(contentWidth, actionWidth * 2 + gap);
        return new MaterialLayoutMetrics(contentWidth, contentHeight, rowHeight, iconSize, textLineHeight, actionHeight, footerTextHeight, labelHeight, gap, innerGap, actionWidth, footerActionsWidth);
    }

    private interface BlockSelectAction {
        void select(String blockId);
    }

    private record MaterialLayoutMetrics(int contentWidth,
                                         int contentHeight,
                                         int rowHeight,
                                         int iconSize,
                                         int textLineHeight,
                                         int actionHeight,
                                         int footerTextHeight,
                                         int labelHeight,
                                         int gap,
                                         int innerGap,
                                         int actionWidth,
                                         int footerActionsWidth) {
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
