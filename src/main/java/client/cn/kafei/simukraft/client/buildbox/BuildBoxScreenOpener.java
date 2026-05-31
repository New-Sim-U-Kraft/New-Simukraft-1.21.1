package client.cn.kafei.simukraft.client.buildbox;

import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import client.cn.kafei.simukraft.client.ui.SimuKraftFlexLayout;
import common.cn.kafei.simukraft.network.npc.hire.NpcHireFirePacket;
import common.cn.kafei.simukraft.network.npc.state.EmploymentStateRequestPacket;
import common.cn.kafei.simukraft.network.npc.state.EmploymentStateResponsePacket;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("null")
public class BuildBoxScreenOpener {
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_SPACING = 2;
    private static final int MAIN_PANEL_WIDTH = 430;
    private static final int MAIN_PANEL_HEIGHT = 200;
    private static final int CATEGORY_BUTTON_WIDTH = 110;
    private static final int CATEGORY_BUTTON_HEIGHT = 20;
    private static final int CATEGORY_BUTTON_GAP = 4;
    private static final float CATEGORY_TITLE_LEFT_RATIO = 0.30F;
    private static final float CATEGORY_TITLE_TOP_RATIO = 0.29F;
    private static final float CATEGORY_TITLE_WIDTH_RATIO = 0.40F;
    private static final float CATEGORY_TITLE_HEIGHT_RATIO = 0.20F;
    private static final float CATEGORY_GRID_LEFT_RATIO = 0.11F;
    private static final float CATEGORY_GRID_TOP_RATIO = 0.625F;
    private static final float CATEGORY_GRID_WIDTH_RATIO = 0.78F;
    private static final float CATEGORY_GRID_HEIGHT_RATIO = 0.32F;

    public static void open(BlockPos buildBoxPos) {
        PacketDistributor.sendToServer(new EmploymentStateRequestPacket(buildBoxPos, "build_box"));
    }

    public static void applyEmploymentState(EmploymentStateResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(createUi(packet.sourcePos(), packet), Component.empty())));
    }

    private static ModularUI createUi(BlockPos buildBoxPos, EmploymentStateResponsePacket state) {
        SimuKraftFlexLayout.ScreenSize screenSize = SimuKraftFlexLayout.screenSize();
        UIElement root = SimuKraftFlexLayout.root(screenSize);
        UIElement page = SimuKraftFlexLayout.pageColumn();
        root.addChild(page);

        SimuKraftFlexLayout.addTopChrome(root, screenSize, Component.translatable("gui.button.done"), BuildBoxScreenOpener::close);
        page.addChild(SimuKraftFlexLayout.spacer(1));

        int panelWidth = Math.min(MAIN_PANEL_WIDTH, Math.max(280, screenSize.width() - 24));
        UIElement centerContainer = SimuKraftFlexLayout.centeredColumn(panelWidth, MAIN_PANEL_WIDTH, MAIN_PANEL_HEIGHT, 0);

        int textWidth = Math.max(240, panelWidth - 28);
        centerContainer.addChild(textElement(Component.translatable("gui.build_box.title"), textWidth, TextTexture.TextType.NORMAL, SimuKraftUiTheme.TEXT_PRIMARY_COLOR).layout(layout -> {
            layout.width(textWidth);
            layout.height(20);
            layout.marginBottom(4);
        }));
        centerContainer.addChild(textElement(Component.translatable(state.statusKey()), textWidth, TextTexture.TextType.NORMAL, SimuKraftUiTheme.TEXT_INFO_COLOR).layout(layout -> {
            layout.width(textWidth);
            layout.height(16);
            layout.marginBottom(4);
        }));
        centerContainer.addChild(textElement(Component.translatable("gui.build_box.instruction"), textWidth, TextTexture.TextType.NORMAL, SimuKraftUiTheme.TEXT_WARNING_COLOR).layout(layout -> {
            layout.width(textWidth);
            layout.height(16);
            layout.marginBottom(Math.max(12, Math.min(44, screenSize.height() / 6)));
        }));

        UIElement row1 = SimuKraftFlexLayout.row(BUTTON_SPACING);
        row1.addChild(createButtonSlot("gui.build_box.hire_builder", () -> handleHireBuilder(buildBoxPos), !state.hasAnyEmployee()));
        row1.addChild(createButtonSlot("gui.build_box.select_building", () -> handleSelectBuilding(buildBoxPos), state.builderCitizenId() != null));
        row1.addChild(createButtonSlot("gui.build_box.fire_employee", () -> handleFireEmployee(buildBoxPos, state), state.hasAnyEmployee()));
        centerContainer.addChild(row1);

        UIElement row2 = SimuKraftFlexLayout.row(BUTTON_SPACING);
        row2.addChild(createButtonSlot("gui.build_box.hire_planner", () -> handleHirePlanner(buildBoxPos), !state.hasAnyEmployee()));
        row2.addChild(createButtonSlot("gui.build_box.plan_area", () -> handlePlanArea(buildBoxPos), state.plannerCitizenId() != null));
        row2.addChild(createButtonSlot("gui.build_box.employee_info", BuildBoxScreenOpener::handleEmployeeInfo, true));
        centerContainer.addChild(row2);

        page.addChild(centerContainer);
        page.addChild(SimuKraftFlexLayout.spacer(1));

        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement textElement(Component text, int width, TextTexture.TextType type, int color) {
        return SimuKraftFlexLayout.text(text, width, color, type, true);
    }

    private static UIElement createButtonSlot(String translationKey, Runnable action, boolean active) {
        Button button = new Button();
        button.setText(Component.translatable(translationKey));
        if (active) {
            button.setOnClick(event -> action.run());
        }
        button.layout(layout -> {
            layout.width(BUTTON_WIDTH);
            layout.height(BUTTON_HEIGHT);
        });
        button.setActive(active);
        return button;
    }

    private static UIElement createCategoryButtonSlot(String translationKey, BlockPos buildBoxPos) {
        Button button = new Button();
        button.setText(Component.translatable(translationKey));
        button.setOnClick(event -> handleBuildingCategory(buildBoxPos, translationKey));
        button.layout(layout -> {
            layout.width(CATEGORY_BUTTON_WIDTH);
            layout.height(CATEGORY_BUTTON_HEIGHT);
            layout.flexShrink(0);
        });
        return button;
    }

    private static void close() {
        Minecraft.getInstance().setScreen(null);
    }

    private static void handleHireBuilder(BlockPos pos) {
        client.cn.kafei.simukraft.client.hire.NpcHireScreen.request(pos, "build_box", "builder");
    }

    private static void handleHirePlanner(BlockPos pos) {
        client.cn.kafei.simukraft.client.hire.NpcHireScreen.request(pos, "build_box", "planner");
    }

    private static void handleSelectBuilding(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(createSelectBuildingUi(pos), Component.empty())));
    }

    private static ModularUI createSelectBuildingUi(BlockPos buildBoxPos) {
        SimuKraftFlexLayout.ScreenSize screenSize = SimuKraftFlexLayout.screenSize();
        UIElement root = SimuKraftFlexLayout.root(screenSize);
        int screenWidth = screenSize.width();
        int screenHeight = screenSize.height();
        CategoryRegions regions = resolveCategoryRegions(screenWidth, screenHeight);

        SimuKraftFlexLayout.addTopChrome(root, screenSize, Component.translatable("gui.button.done"), BuildBoxScreenOpener::close);

        UIElement titleRegion = SimuKraftFlexLayout.absoluteRegion(regions.titleLeft(), regions.titleTop(), regions.titleWidth(), regions.titleHeight());
        titleRegion.layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(Math.max(6, regions.titleHeight() / 12));
        });
        titleRegion.addChild(textElement(Component.translatable("gui.select_building.title"), regions.titleWidth(), TextTexture.TextType.NORMAL, SimuKraftUiTheme.TEXT_PRIMARY_COLOR).layout(layout -> {
            layout.widthPercent(100);
            layout.height(20);
        }));
        titleRegion.addChild(textElement(Component.translatable("gui.select_building.status_working"), regions.titleWidth(), TextTexture.TextType.NORMAL, SimuKraftUiTheme.TEXT_INFO_COLOR).layout(layout -> {
            layout.widthPercent(100);
            layout.height(16);
        }));
        titleRegion.addChild(textElement(Component.translatable("gui.select_building.instruction"), regions.titleWidth(), TextTexture.TextType.NORMAL, SimuKraftUiTheme.TEXT_WARNING_COLOR).layout(layout -> {
            layout.widthPercent(100);
            layout.height(16);
        }));
        root.addChild(titleRegion);

        UIElement gridRegion = SimuKraftFlexLayout.absoluteRegion(regions.gridLeft(), regions.gridTop(), regions.gridWidth(), regions.gridHeight());
        gridRegion.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.flexWrap(FlexWrap.WRAP);
            layout.alignContent(AlignContent.FLEX_START);
            layout.alignItems(AlignItems.FLEX_START);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(CATEGORY_BUTTON_GAP);
        });
        gridRegion.addChild(createCategoryButtonSlot("gui.category.residential", buildBoxPos));
        gridRegion.addChild(createCategoryButtonSlot("gui.category.commercial", buildBoxPos));
        gridRegion.addChild(createCategoryButtonSlot("gui.category.industrial", buildBoxPos));
        gridRegion.addChild(createCategoryButtonSlot("gui.category.other", buildBoxPos));
        gridRegion.addChild(createCategoryButtonSlot("gui.category.public", buildBoxPos));
        root.addChild(gridRegion);

        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static void handlePlanArea(BlockPos pos) {
        PlanningAreaScreen.open(pos);
    }

    private static void handleBuildingCategory(BlockPos buildBoxPos, String translationKey) {
        String category = switch (translationKey) {
            case "gui.category.residential" -> "residential";
            case "gui.category.commercial" -> "commercial";
            case "gui.category.industrial" -> "industry";
            case "gui.category.public" -> "public";
            case "gui.category.other" -> "other";
            default -> "other";
        };
        BuildingListScreenOpener.open(category, buildBoxPos);
    }

    private static void handleEmployeeInfo() {
    }

    private static void handleFireEmployee(BlockPos pos, EmploymentStateResponsePacket state) {
        if (state.builderCitizenId() != null) {
            PacketDistributor.sendToServer(new NpcHireFirePacket(pos, "build_box", "builder", state.builderCitizenId()));
            close();
            return;
        }
        if (state.plannerCitizenId() != null) {
            PacketDistributor.sendToServer(new NpcHireFirePacket(pos, "build_box", "planner", state.plannerCitizenId()));
            close();
        }
    }

    private static CategoryRegions resolveCategoryRegions(int screenWidth, int screenHeight) {
        return new CategoryRegions(
                clamp(Math.round(screenWidth * CATEGORY_TITLE_LEFT_RATIO), 0, screenWidth - 1),
                clamp(Math.round(screenHeight * CATEGORY_TITLE_TOP_RATIO), 0, screenHeight - 1),
                clamp(Math.round(screenWidth * CATEGORY_TITLE_WIDTH_RATIO), 1, screenWidth),
                clamp(Math.round(screenHeight * CATEGORY_TITLE_HEIGHT_RATIO), 1, screenHeight),
                clamp(Math.round(screenWidth * CATEGORY_GRID_LEFT_RATIO), 0, screenWidth - 1),
                clamp(Math.round(screenHeight * CATEGORY_GRID_TOP_RATIO), 0, screenHeight - 1),
                clamp(Math.round(screenWidth * CATEGORY_GRID_WIDTH_RATIO), 1, screenWidth),
                clamp(Math.round(screenHeight * CATEGORY_GRID_HEIGHT_RATIO), 1, screenHeight)
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CategoryRegions(int titleLeft,
                                   int titleTop,
                                   int titleWidth,
                                   int titleHeight,
                                   int gridLeft,
                                   int gridTop,
                                   int gridWidth,
                                   int gridHeight) {
    }
}


