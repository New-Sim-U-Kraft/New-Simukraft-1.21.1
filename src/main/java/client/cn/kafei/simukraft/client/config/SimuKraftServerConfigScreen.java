package client.cn.kafei.simukraft.client.config;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

@SuppressWarnings("null")
public final class SimuKraftServerConfigScreen {
    private static final int WINDOW_WIDTH = 600;
    private static final int MIN_WINDOW_WIDTH = 360;
    private static final int WINDOW_HEIGHT = 520;
    private static final int MIN_WINDOW_HEIGHT = 300;
    private static final int HEADER_HEIGHT = 36;
    private static final int FOOTER_HEIGHT = 42;
    private static final int WRAPPED_FOOTER_HEIGHT = 70;
    private static final int TAB_HEADER_HEIGHT = 24;
    private static final int TAB_COUNT = 5;

    private SimuKraftServerConfigScreen() {
    }

    /** create: 创建服务端配置页。 */
    public static Screen create(Screen parent) {
        return create(parent, SimuKraftServerConfigDraft.live(), "gui.simukraft.config.tab.general");
    }

    /** create: 使用现有草稿重建服务端配置页。 */
    static Screen create(Screen parent, SimuKraftServerConfigDraft draft) {
        return create(parent, draft, "gui.simukraft.config.tab.general");
    }

    /** createMaterialsTab: 返回服务器配置并定位到材料页。 */
    static Screen createMaterialsTab(Screen parent, SimuKraftServerConfigDraft draft) {
        return create(parent, draft, "gui.simukraft.config.tab.materials");
    }

    private static Screen create(Screen parent, SimuKraftServerConfigDraft draft, String selectedTabKey) {
        return new ModularUIScreen(SimuKraftConfigWidgets.screenUi(createUi(parent, draft, selectedTabKey)), Component.translatable("gui.simukraft.config.server"));
    }

    /** createUi: 组装旧版五 Tab 服务端配置页。 */
    private static UIElement createUi(Screen parent, SimuKraftServerConfigDraft draft, String selectedTabKey) {
        int windowWidth = SimuKraftConfigWidgets.windowWidth(WINDOW_WIDTH, MIN_WINDOW_WIDTH);
        int windowHeight = SimuKraftConfigWidgets.windowHeight(WINDOW_HEIGHT, MIN_WINDOW_HEIGHT);
        int footerHeight = footerHeight(windowWidth);
        UIElement window = SimuKraftConfigWidgets.window(windowWidth, windowHeight);
        window.addChild(SimuKraftConfigWidgets.header(Component.translatable("gui.simukraft.config.server"), HEADER_HEIGHT));
        window.addChild(tabs(parent, draft, windowWidth, windowHeight, footerHeight, selectedTabKey));
        window.addChild(footer(parent, draft, footerHeight));
        return SimuKraftConfigWidgets.screenRoot(window);
    }

    /** tabs: 创建旧版五页签。 */
    private static TabView tabs(Screen parent, SimuKraftServerConfigDraft draft, int windowWidth, int windowHeight, int footerHeight, String selectedTabKey) {
        int tabViewHeight = tabViewHeight(windowHeight, footerHeight);
        int tabContentHeight = tabContentHeight(tabViewHeight);
        int tabWidth = tabWidth(windowWidth);
        TabView tabs = new TabView();
        tabs.layout(layout -> {
            layout.widthPercent(100);
            layout.height(tabViewHeight);
            layout.flexShrink(0);
        });
        tabs.tabHeaderContainer(container -> {
            container.setOverflowVisible(false);
            container.layout(layout -> {
                layout.height(TAB_HEADER_HEIGHT);
                layout.flexShrink(0);
            });
        });
        tabs.tabContentContainer(container -> {
            container.setOverflowVisible(false);
            container.layout(layout -> {
                layout.height(tabContentHeight);
                layout.flexGrow(0);
                layout.flexShrink(0);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.alignItems(AlignItems.STRETCH);
            });
        });
        Tab general = tab("gui.simukraft.config.tab.general", tabWidth);
        tabs.addTab(general, generalPage(draft));
        tabs.addTab(tab("gui.simukraft.config.tab.npc", tabWidth), npcPage(draft));
        tabs.addTab(tab("gui.simukraft.config.tab.planner", tabWidth), plannerPage(draft));
        tabs.addTab(tab("gui.simukraft.config.tab.builder", tabWidth), builderPage(draft));
        Tab materials = tab("gui.simukraft.config.tab.materials", tabWidth);
        tabs.addTab(materials, materialsPage(parent, draft));
        tabs.selectTab("gui.simukraft.config.tab.materials".equals(selectedTabKey) ? materials : general);
        return tabs;
    }

    private static int footerHeight(int windowWidth) {
        return windowWidth < 292 ? WRAPPED_FOOTER_HEIGHT : FOOTER_HEIGHT;
    }

    private static int tabWidth(int windowWidth) {
        int availableWidth = Math.max(1, (windowWidth - 4) / TAB_COUNT);
        int minWidth = Math.min(40, availableWidth);
        return Math.max(minWidth, Math.min(64, availableWidth));
    }

    private static int tabViewHeight(int windowHeight, int footerHeight) {
        return Math.max(1, windowHeight - HEADER_HEIGHT - footerHeight - 4);
    }

    private static int tabContentHeight(int tabViewHeight) {
        return Math.max(1, tabViewHeight - TAB_HEADER_HEIGHT - 8);
    }

    private static Tab tab(String key, int width) {
        Tab tab = new Tab();
        tab.setText(Component.translatable(key));
        tab.textStyle(style -> style.textColor(SimuKraftConfigWidgets.TEXT_TITLE).textShadow(false).textWrap(TextWrap.HIDE));
        tab.layout(layout -> {
            layout.width(width);
            layout.height(24);
            layout.flexShrink(0);
        });
        return tab;
    }

    private static UIElement generalPage(SimuKraftServerConfigDraft draft) {
        UIElement page = pageColumn();
        page.addChild(SimuKraftConfigWidgets.section(Component.translatable("gui.simukraft.config.section.economy")));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("gui.simukraft.config.city_chunk_price"),
                SimuKraftConfigWidgets.doubleField(draft.cityChunkPrice, 0.0D, 1_000_000.0D, value -> draft.cityChunkPrice = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("gui.simukraft.config.population_interval"),
                SimuKraftConfigWidgets.intField(draft.populationGrowthIntervalTicks, 20, 2_400_000, value -> draft.populationGrowthIntervalTicks = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("gui.simukraft.config.population_max"),
                SimuKraftConfigWidgets.intField(draft.populationGrowthMaxPerInterval, 0, 100, value -> draft.populationGrowthMaxPerInterval = value)));
        page.addChild(SimuKraftConfigWidgets.section(Component.translatable("gui.simukraft.config.section.farming")));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.farming.areaRadius"),
                SimuKraftConfigWidgets.intField(draft.farmAreaRadius, 1, 16, value -> draft.farmAreaRadius = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.farming.workIntervalTicks"),
                SimuKraftConfigWidgets.intField(draft.farmWorkIntervalTicks, 1, 1200, value -> draft.farmWorkIntervalTicks = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.farming.actionsPerCycle"),
                SimuKraftConfigWidgets.intField(draft.farmActionsPerCycle, 1, 64, value -> draft.farmActionsPerCycle = value)));

        page.addChild(SimuKraftConfigWidgets.section(Component.translatable("gui.simukraft.config.section.pathfinding")));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.npc_pathfinding.maxLoadedCitizenEntities"),
                SimuKraftConfigWidgets.intField(draft.pathMaxLoadedCitizenEntities, 1, 1000, value -> draft.pathMaxLoadedCitizenEntities = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.npc_pathfinding.maxActivePathingCitizens"),
                SimuKraftConfigWidgets.intField(draft.pathMaxActiveCitizens, 1, 5000, value -> draft.pathMaxActiveCitizens = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.npc_pathfinding.maxNewPathRequestsPerTick"),
                SimuKraftConfigWidgets.intField(draft.pathMaxNewRequestsPerTick, 0, 32, value -> draft.pathMaxNewRequestsPerTick = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.npc_pathfinding.pathWorkerThreads"),
                SimuKraftConfigWidgets.intField(draft.pathWorkerThreads, 1, 16, value -> draft.pathWorkerThreads = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.npc_pathfinding.localPathRadiusBlocks"),
                SimuKraftConfigWidgets.intField(draft.pathLocalRadiusBlocks, 16, 256, value -> draft.pathLocalRadiusBlocks = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.npc_pathfinding.farMovementTeleportDistance"),
                SimuKraftConfigWidgets.intField(draft.pathFarMovementTeleportDistance, 16, 512, value -> draft.pathFarMovementTeleportDistance = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.npc_pathfinding.repathCooldownTicks"),
                SimuKraftConfigWidgets.intField(draft.pathRepathCooldownTicks, 0, 1200, value -> draft.pathRepathCooldownTicks = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.npc_pathfinding.pathCacheTtlTicks"),
                SimuKraftConfigWidgets.intField(draft.pathCacheTtlTicks, 0, 24000, value -> draft.pathCacheTtlTicks = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.npc_pathfinding.debugPathfinding"),
                SimuKraftConfigWidgets.switchControl(draft.pathDebug, value -> draft.pathDebug = value)));
        return SimuKraftConfigWidgets.scroller(page);
    }

    private static UIElement npcPage(SimuKraftServerConfigDraft draft) {
        UIElement page = pageColumn();
        page.addChild(SimuKraftConfigWidgets.section(Component.translatable("gui.simukraft.config.section.npc_level")));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.npc_leveling.maxLevel"),
                SimuKraftConfigWidgets.intField(draft.npcMaxLevel, 1, 20, value -> draft.npcMaxLevel = value)));
        page.addChild(SimuKraftConfigWidgets.label(Component.translatable("gui.simukraft.config.npc_level_hint"), Horizontal.LEFT, SimuKraftConfigWidgets.TEXT_MUTED, 42, TextWrap.WRAP));

        page.addChild(SimuKraftConfigWidgets.section(Component.translatable("gui.simukraft.config.section.xp")));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.npc_leveling.builderEnableXpGain"),
                SimuKraftConfigWidgets.switchControl(draft.builderXpGain, value -> draft.builderXpGain = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.npc_leveling.builderXpPerBlock"),
                SimuKraftConfigWidgets.intField(draft.builderXpPerBlock, 0, 100, value -> draft.builderXpPerBlock = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.planner.enableXpGain"),
                SimuKraftConfigWidgets.switchControl(draft.plannerXpGain, value -> draft.plannerXpGain = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.planner.xpPerBlock"),
                SimuKraftConfigWidgets.intField(draft.plannerXpPerBlock, 0, 100, value -> draft.plannerXpPerBlock = value)));
        return SimuKraftConfigWidgets.scroller(page);
    }

    private static UIElement plannerPage(SimuKraftServerConfigDraft draft) {
        UIElement page = pageColumn();
        page.addChild(SimuKraftConfigWidgets.section(Component.translatable("gui.simukraft.config.section.planner_work")));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.planner.blocksPerSecond"),
                SimuKraftConfigWidgets.doubleField(draft.plannerBlocksPerSecond, 0.1D, 40.0D, value -> draft.plannerBlocksPerSecond = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.planner.maxVolume"),
                SimuKraftConfigWidgets.intField(draft.plannerMaxVolume, 1, 200000, value -> draft.plannerMaxVolume = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.planner.pauseAtNight"),
                SimuKraftConfigWidgets.switchControl(draft.plannerPauseAtNight, value -> draft.plannerPauseAtNight = value)));

        page.addChild(SimuKraftConfigWidgets.section(Component.translatable("gui.simukraft.config.section.planner_cost")));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.planner.moneyPerBlockRemove"),
                SimuKraftConfigWidgets.doubleField(draft.plannerMoneyPerBlockRemove, 0.0D, 1000.0D, value -> draft.plannerMoneyPerBlockRemove = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.planner.moneyPerBlockFill"),
                SimuKraftConfigWidgets.doubleField(draft.plannerMoneyPerBlockFill, 0.0D, 1000.0D, value -> draft.plannerMoneyPerBlockFill = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.planner.moneyPerBlockReplace"),
                SimuKraftConfigWidgets.doubleField(draft.plannerMoneyPerBlockReplace, 0.0D, 1000.0D, value -> draft.plannerMoneyPerBlockReplace = value)));
        return SimuKraftConfigWidgets.scroller(page);
    }

    private static UIElement builderPage(SimuKraftServerConfigDraft draft) {
        UIElement page = pageColumn();
        page.addChild(SimuKraftConfigWidgets.section(Component.translatable("gui.simukraft.config.section.builder_work")));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.construction.builderBlocksPerSecond"),
                SimuKraftConfigWidgets.doubleField(draft.builderBlocksPerSecond, 0.1D, 20.0D, value -> draft.builderBlocksPerSecond = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("gui.simukraft.config.builder_pause_at_night"),
                SimuKraftConfigWidgets.switchControl(draft.builderPauseAtNight, value -> draft.builderPauseAtNight = value)));

        page.addChild(SimuKraftConfigWidgets.section(Component.translatable("gui.simukraft.config.section.building_integrity")));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.building_integrity.autoDemolishThresholdPercent"),
                SimuKraftConfigWidgets.intField(draft.buildingIntegrityAutoDemolishThresholdPercent, 0, 100, value -> draft.buildingIntegrityAutoDemolishThresholdPercent = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.building_integrity.checkIntervalTicks"),
                SimuKraftConfigWidgets.intField(draft.buildingIntegrityCheckIntervalTicks, 20, 24000, value -> draft.buildingIntegrityCheckIntervalTicks = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.building_integrity.repairMoneyPerBlock"),
                SimuKraftConfigWidgets.doubleField(draft.buildingIntegrityRepairMoneyPerBlock, 0.0D, 1000.0D, value -> draft.buildingIntegrityRepairMoneyPerBlock = value)));
        return SimuKraftConfigWidgets.scroller(page);
    }

    private static UIElement materialsPage(Screen parent, SimuKraftServerConfigDraft draft) {
        UIElement page = pageColumn();
        page.addChild(SimuKraftConfigWidgets.section(Component.translatable("gui.simukraft.config.section.material_mode")));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("gui.simukraft.config.work_mode"),
                SimuKraftConfigWidgets.selector(List.of(SimuKraftServerConfigDraft.WorkMode.values()), draft.workMode,
                        mode -> Component.translatable(mode.translationKey()),
                        value -> draft.workMode = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.materials.enableMaterialCategoryMatching"),
                SimuKraftConfigWidgets.switchControl(draft.materialCategoryMatching, value -> draft.materialCategoryMatching = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.materials.warningCooldownSeconds"),
                SimuKraftConfigWidgets.intField(draft.materialWarningCooldownSeconds, 1, 300, value -> draft.materialWarningCooldownSeconds = value)));
        page.addChild(SimuKraftConfigWidgets.label(Component.translatable("gui.simukraft.config.material_mode_hint"), Horizontal.LEFT, SimuKraftConfigWidgets.TEXT_MUTED, 60, TextWrap.WRAP));
        page.addChild(SimuKraftConfigWidgets.section(Component.translatable("gui.simukraft.config.section.block_protection")));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.general.enableBlacklistProtection"),
                SimuKraftConfigWidgets.switchControl(draft.blacklistProtection, value -> draft.blacklistProtection = value)));
        page.addChild(SimuKraftConfigWidgets.row(Component.translatable("config.simukraft.general.logBlacklistSkippedBlocks"),
                SimuKraftConfigWidgets.switchControl(draft.logBlacklistSkippedBlocks, value -> draft.logBlacklistSkippedBlocks = value)));
        page.addChild(SimuKraftConfigWidgets.section(Component.translatable("gui.simukraft.config.section.material_editors")));
        page.addChild(openMaterialEditorRow(Component.translatable("config.simukraft.materials.allModeBlockBlacklist"),
                () -> Minecraft.getInstance().setScreen(SimuKraftMaterialConfigPage.createBlacklist(parent, draft))));
        page.addChild(openMaterialEditorRow(Component.translatable("config.simukraft.materials.basicMaterials"),
                () -> Minecraft.getInstance().setScreen(SimuKraftMaterialConfigPage.createBasic(parent, draft))));
        page.addChild(openMaterialEditorRow(Component.translatable("config.simukraft.materials.materialCategoryGroups"),
                () -> Minecraft.getInstance().setScreen(SimuKraftMaterialConfigPage.createCategory(parent, draft))));
        page.addChild(openMaterialEditorRow(Component.translatable("config.simukraft.materials.expertModeSkipList"),
                () -> Minecraft.getInstance().setScreen(SimuKraftMaterialConfigPage.createExpert(parent, draft))));
        return SimuKraftConfigWidgets.scroller(page);
    }

    private static UIElement openMaterialEditorRow(Component label, Runnable action) {
        return SimuKraftConfigWidgets.row(label, SimuKraftConfigWidgets.button(Component.translatable("gui.simukraft.config.open"), action, true).layout(layout -> {
            layout.width(96);
            layout.height(24);
            layout.flexShrink(0);
        }));
    }

    private static UIElement pageColumn() {
        return SimuKraftConfigWidgets.scrollColumn(8, 6);
    }

    private static UIElement footer(Screen parent, SimuKraftServerConfigDraft draft, int footerHeight) {
        UIElement footer = SimuKraftConfigWidgets.footerRow(footerHeight, 8);
        footer.addChild(footerButton("gui.simukraft.config.save", () -> draft.saveToLive()));
        footer.addChild(footerButton("gui.simukraft.config.reload", () -> {
            draft.reloadFromLive();
            Minecraft.getInstance().setScreen(create(parent, draft));
        }));
        footer.addChild(footerButton("gui.simukraft.config.reset", () -> {
            draft.resetToDefaults();
            Minecraft.getInstance().setScreen(create(parent, draft));
        }));
        footer.addChild(footerButton("gui.simukraft.config.cancel", () -> Minecraft.getInstance().setScreen(SimuKraftConfigSelectionScreen.create(parent))));
        footer.addChild(footerButton("gui.simukraft.config.close", () -> Minecraft.getInstance().setScreen(parent)));
        return footer;
    }

    private static UIElement footerButton(String key, Runnable action) {
        return SimuKraftConfigWidgets.button(Component.translatable(key), action, true).layout(layout -> {
            layout.width(64);
            layout.minWidth(52);
            layout.maxWidth(76);
            layout.height(26);
            layout.flexGrow(1);
            layout.flexShrink(1);
        });
    }
}
