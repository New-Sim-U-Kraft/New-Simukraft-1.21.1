package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import client.cn.kafei.simukraft.client.ui.SimuKraftFlexLayout;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import common.cn.kafei.simukraft.building.BuildingPackageCatalog;
import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import common.cn.kafei.simukraft.ui.RecipeBookSearchUi;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
@SuppressWarnings("null")
@EventBusSubscriber(value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public final class BuildingListScreenOpener {
    private static final int CARD_TEXT_COLOR = SimuKraftUiTheme.CARD_TEXT_COLOR;
    private static final int MAX_BUILDINGS_PER_PAGE = 12;
    private static final int CARD_GAP = 6;
    private static final int MIN_BUTTON_WIDTH = 50;
    private static final int MIN_BUTTON_HEIGHT = 20;
    private static final float TITLE_LEFT_RATIO = 0.153F;
    private static final float TITLE_TOP_RATIO = 0.038F;
    private static final float TITLE_WIDTH_RATIO = 0.70F;
    private static final float TITLE_HEIGHT_RATIO = 0.124F;
    private static final float CARD_LEFT_RATIO = 0.02F;
    private static final float CARD_TOP_RATIO = 0.168F;
    private static final float CARD_WIDTH_RATIO = 0.96F;
    private static final float CARD_HEIGHT_RATIO = 0.728F;
    private static final float SELECTED_INFO_HEIGHT_RATIO = 0.04F;
    private static final float PAGER_LEFT_RATIO = 0.19F;
    private static final float PAGER_TOP_RATIO = 0.874F;
    private static final float PAGER_WIDTH_RATIO = 0.61F;
    private static final float PAGER_HEIGHT_RATIO = 0.07F;
    private static final float CONFIRM_LEFT_RATIO = 0.79F;
    private static final float CONFIRM_TOP_RATIO = 0.874F;
    private static final float CONFIRM_WIDTH_RATIO = 0.16F;
    private static final float CONFIRM_HEIGHT_RATIO = 0.07F;
    private static final int PREFERRED_CARD_WIDTH = 180;
    private static final int MIN_CARD_WIDTH = 130;
    private static final int PREFERRED_CARD_HEIGHT = 70;
    private static final int MIN_CARD_HEIGHT = 62;
    private static final int TOOLBAR_GAP = 6;
    private static final int FAVORITE_BUTTON_SIZE = 18;
    private static final int FAVORITE_BUTTON_TOP = 8;
    private static final int FAVORITE_BUTTON_RIGHT_INSET = 4;
    private static final int FAVORITE_STAR_COLOR  = 0xFFFFD700;
    private static final int ITEM_ICON_SIZE        = 10; // 来源图标尺寸（书/书架）
    private static int currentPage;
    private static String currentCategory;
    private static BlockPos currentBuildBoxPos;
    private static String selectedBuildingFileName;
    private static String searchText = "";
    private static boolean showFavoritesOnly;
    private static boolean sortDescending;
    private static SortMode sortMode = SortMode.NAME;
    private static PendingPreview pendingPreview;

    private BuildingListScreenOpener() {
    }

    public static void open(String category, BlockPos buildBoxPos) {
        boolean newSession = !Objects.equals(currentCategory, category) || !Objects.equals(currentBuildBoxPos, buildBoxPos);
        if (newSession) {
            currentPage = 0;
            selectedBuildingFileName = null;
            searchText = "";
            showFavoritesOnly = false;
        }
        currentCategory = category;
        currentBuildBoxPos = buildBoxPos;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(createUi(category, buildBoxPos), Component.empty())));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (pendingPreview == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            pendingPreview = null;
            return;
        }
        if (minecraft.screen == null || minecraft.screen instanceof com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen) {
            minecraft.setScreen(new BuildingConfirmScreen(new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(createUi(currentCategory, currentBuildBoxPos), Component.empty()), pendingPreview.building(), pendingPreview.buildBoxPos(), pendingPreview.structure()));
            pendingPreview = null;
        }
    }

    private static ModularUI createUi(String category, BlockPos buildBoxPos) {
        SimuKraftFlexLayout.ScreenSize screenSize = SimuKraftFlexLayout.screenSize();
        int screenWidth = screenSize.width();
        int screenHeight = screenSize.height();
        RegionMetrics regions = resolveRegions(screenWidth, screenHeight);
        GridMetrics grid = resolveGridMetrics(regions.cardRegion().width(), regions.cardRegion().height());
        List<BuildingCacheService.BuildingMeta> buildings = BuildingCacheService.getBuildings(category);

        UIElement root = SimuKraftFlexLayout.root(screenSize);

        SimuKraftFlexLayout.addTopChrome(root, screenSize, Component.translatable("gui.button.back"), () -> BuildBoxScreenOpener.open(buildBoxPos));

        UIElement titleRegion = absoluteRegion(regions.titleRegion());
        titleRegion.layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.justifyContent(AlignContent.CENTER);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(Math.max(4, regions.titleRegion().height() / 12));
        });
        titleRegion.addChild(textElement(Component.translatable("gui.building_list.title", categoryName(category)), regions.titleRegion().width(), SimuKraftUiTheme.TEXT_PRIMARY_COLOR, TextTexture.TextType.NORMAL).layout(layout -> {
            layout.widthPercent(100);
            layout.height(18);
        }));
        UIElement statusSlot = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(18);
        });
        titleRegion.addChild(statusSlot);
        root.addChild(titleRegion);

        UIElement cardRegion = absoluteRegion(regions.cardRegion());
        cardRegion.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.flexWrap(FlexWrap.WRAP);
            layout.alignContent(AlignContent.CENTER);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(CARD_GAP);
            layout.paddingAll(4);
        });
        root.addChild(cardRegion);

        UIElement infoRegion = absoluteRegion(regions.selectedInfoRegion());
        infoRegion.setAllowHitTest(false);
        infoRegion.layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        root.addChild(infoRegion);

        UIElement pagerRegion = absoluteRegion(regions.pagerRegion());
        pagerRegion.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(Math.max(8, regions.pagerRegion().width() / 28));
        });
        root.addChild(pagerRegion);

        UIElement confirmRegion = absoluteRegion(regions.confirmRegion());
        confirmRegion.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        root.addChild(confirmRegion);

        root.addChild(RecipeBookSearchUi.frameElement(regions.searchRegion().left(), regions.searchRegion().top(),
                regions.searchRegion().width(), regions.searchRegion().height()));
        UIElement toolbarButtonsRegion = absoluteRegion(regions.toolbarButtonsRegion());
        toolbarButtonsRegion.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(TOOLBAR_GAP);
        });
        root.addChild(toolbarButtonsRegion);

        BuildingUiController controller = new BuildingUiController(category, buildings, regions, grid, statusSlot, cardRegion,
                infoRegion, pagerRegion, confirmRegion, toolbarButtonsRegion);
        root.addChild(RecipeBookSearchUi.createField(regions.searchRegion().left() + RecipeBookSearchUi.TEXT_OFFSET_X,
                regions.searchRegion().top() + searchTextOffsetY(regions.searchRegion().height()),
                Math.max(RecipeBookSearchUi.TEXT_WIDTH, regions.searchRegion().width() - RecipeBookSearchUi.TEXT_OFFSET_X - 4),
                RecipeBookSearchUi.TEXT_HEIGHT,
                searchText, controller::onSearchChanged));
        controller.refresh();
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static UIElement createBuildingCard(BuildingCacheService.BuildingMeta building, int width, int height, int accentColor, Runnable refreshAction) {
        boolean selected = building.structureFileName().equals(selectedBuildingFileName);
        int buttonInset = 2;
        int buttonWidth = Math.max(1, width - buttonInset * 2);
        int buttonHeight = Math.max(1, height - buttonInset * 2);
        UIElement wrapper = new UIElement().layout(layout -> {
            layout.width(width);
            layout.height(height);
            layout.flexShrink(0);
        });
        wrapper.addChild(SimuKraftUiTheme.createDecorationLayer(3, 4, width - 4, height - 4, "simukraft_card_shadow"));

        Button card = new Button().noText();
        card.addClass("simukraft_large_button");
        card.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(buttonInset);
            layout.top(buttonInset);
            layout.width(buttonWidth);
            layout.height(buttonHeight);
            layout.paddingAll(6);
        });
        card.addClass("simukraft_card_button");
        card.setOnClick(event -> {
            if (event.button == 0) {
                selectedBuildingFileName = building.structureFileName();
                refreshAction.run();
            }
        });
        card.addChild(SimuKraftUiTheme.createDecorationLayer(6, 7, buttonWidth - 12, buttonHeight - 14, "simukraft_card_content_panel"));

        card.addChild(new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(8);
            layout.top(3);
            layout.width(buttonWidth - 16);
            layout.height(2);
        }).style(style -> style.backgroundTexture(new GuiTextureGroup(
                new ColorRectTexture(0x66000000),
                new ColorRectTexture(accentColor)
        ))));

        // 来源图标：官方建筑=书，非官方=书架；层级在文字之下、卡片背景之上
        ItemStack sourceStack = new ItemStack(
                BuildingPackageCatalog.OFFICIAL_PACKAGE_NAME.equals(building.packageName())
                        ? Items.BOOK : Items.BOOKSHELF);
        int srcIconLeft = Math.max(4, buttonWidth - FAVORITE_BUTTON_SIZE - FAVORITE_BUTTON_RIGHT_INSET) - ITEM_ICON_SIZE - 1;
        int srcIconTop  = FAVORITE_BUTTON_TOP + (FAVORITE_BUTTON_SIZE - ITEM_ICON_SIZE) / 2 - 2;
        card.addChild(new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(srcIconLeft);
            layout.top(srcIconTop);
            layout.width(ITEM_ICON_SIZE);
            layout.height(ITEM_ICON_SIZE);
        }).style(style -> style.backgroundTexture(new ItemStackTexture(sourceStack))));

        card.addChild(textElement(Component.literal(trim(building.name(), Math.max(10, buttonWidth / 10))), buttonWidth, CARD_TEXT_COLOR, TextTexture.TextType.NORMAL, false).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(8);
            layout.width(buttonWidth);
            layout.height(12);
        }));
        card.addChild(favoriteButton(building, refreshAction).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(Math.max(4, buttonWidth - FAVORITE_BUTTON_SIZE - FAVORITE_BUTTON_RIGHT_INSET));
            layout.top(FAVORITE_BUTTON_TOP);
            layout.width(FAVORITE_BUTTON_SIZE);
            layout.height(FAVORITE_BUTTON_SIZE);
        }));

        int infoLeft = 14;
        int infoWidth = buttonWidth - infoLeft - 8;
        int infoY = 20;
        card.addChild(infoLine(Component.translatable("gui.building.size", building.size()), infoWidth, CARD_TEXT_COLOR).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(infoLeft);
            layout.top(infoY);
            layout.width(infoWidth);
            layout.height(10);
        }));
        card.addChild(infoLine(Component.translatable("gui.building.price", building.amount()), infoWidth, CARD_TEXT_COLOR).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(infoLeft);
            layout.top(infoY + 10);
            layout.width(infoWidth);
            layout.height(10);
        }));
        card.addChild(infoLine(Component.translatable("gui.building.author", building.author()), infoWidth, CARD_TEXT_COLOR).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(infoLeft);
            layout.top(infoY + 20);
            layout.width(infoWidth);
            layout.height(10);
        }));
        wrapper.addChild(card);
        if (selected) {
            wrapper.addChild(SimuKraftUiTheme.createSelectionBorder(width, height));
        }

        return wrapper;
    }

    /** favoriteButton: 在建筑卡片右上角绘制独立收藏按钮。 */
    private static UIElement favoriteButton(BuildingCacheService.BuildingMeta building, Runnable refreshAction) {
        boolean favorite = isFavorite(building);
        UIElement root = new UIElement();
        root.addChild(textElement(Component.literal(favorite ? "★" : "☆"), FAVORITE_BUTTON_SIZE, FAVORITE_STAR_COLOR, TextTexture.TextType.NORMAL, false).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(FAVORITE_BUTTON_SIZE);
            layout.height(FAVORITE_BUTTON_SIZE);
        }).setAllowHitTest(false));

        Button hitArea = new Button().noText();
        hitArea.buttonStyle(style -> style
                .baseTexture(IGuiTexture.EMPTY)
                .hoverTexture(IGuiTexture.EMPTY)
                .pressedTexture(IGuiTexture.EMPTY));
        hitArea.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(FAVORITE_BUTTON_SIZE);
            layout.height(FAVORITE_BUTTON_SIZE);
        });
        hitArea.setOnClick(event -> {
            if (event.button == 0) {
                toggleFavorite(building);
                refreshAction.run();
                event.stopPropagation();
            }
        });
        root.addChild(hitArea);
        return root;
    }

    private static UIElement infoLine(Component text, int width, int color) {
        UIElement element = new UIElement();
        element.style(style -> style.backgroundTexture(new TextTexture(text.getString())
                .setWidth(width)
                .setType(TextTexture.TextType.LEFT)
                .setColor(color)
                .setDropShadow(false)));
        return element;
    }

    private static UIElement textElement(Component text, int width, int color, TextTexture.TextType type) {
        return textElement(text, width, color, type, true);
    }

    private static UIElement textElement(Component text, int width, int color, TextTexture.TextType type, boolean dropShadow) {
        UIElement element = new UIElement();
        element.style(style -> style.backgroundTexture(new TextTexture(text.getString())
                .setWidth(width)
                .setType(type)
                .setColor(color)
                .setDropShadow(dropShadow)));
        return element;
    }

    private static RegionMetrics resolveRegions(int screenWidth, int screenHeight) {
        RegionBox titleRegion = relativeBox(screenWidth, screenHeight, TITLE_LEFT_RATIO, TITLE_TOP_RATIO, TITLE_WIDTH_RATIO, TITLE_HEIGHT_RATIO);
        RegionBox pagerRegion = relativeBox(screenWidth, screenHeight, PAGER_LEFT_RATIO, PAGER_TOP_RATIO, PAGER_WIDTH_RATIO, PAGER_HEIGHT_RATIO);
        RegionBox confirmRegion = relativeBox(screenWidth, screenHeight, CONFIRM_LEFT_RATIO, CONFIRM_TOP_RATIO, CONFIRM_WIDTH_RATIO, CONFIRM_HEIGHT_RATIO);
        int toolbarTop = clamp(Math.max(titleRegion.bottom() + 6, Math.round(screenHeight * 0.18F)), 0,
                Math.max(0, pagerRegion.top() - Math.max(22, RecipeBookSearchUi.FRAME_HEIGHT) - MIN_CARD_HEIGHT - 12));
        int toolbarLeft = clamp(Math.round(screenWidth * 0.036F), 0, Math.max(0, screenWidth - 1));
        int toolbarWidth = clamp(Math.round(screenWidth * 0.928F), 1, Math.max(1, screenWidth - toolbarLeft));
        int toolbarHeight = Math.max(22, RecipeBookSearchUi.FRAME_HEIGHT + 4);
        int toolbarButtonHeight = Math.max(MIN_BUTTON_HEIGHT, toolbarHeight - 2);
        int controlsWidth = clamp(Math.round(toolbarWidth * 0.34F), Math.min(220, toolbarWidth), Math.min(430, toolbarWidth));
        int searchWidth = Math.max(RecipeBookSearchUi.FRAME_WIDTH, toolbarWidth - controlsWidth - TOOLBAR_GAP);
        RegionBox searchRegion = new RegionBox(
                toolbarLeft,
                toolbarTop + Math.max(0, (toolbarHeight - toolbarButtonHeight) / 2),
                searchWidth,
                toolbarButtonHeight
        );
        RegionBox toolbarButtonsRegion = new RegionBox(
                searchRegion.right() + TOOLBAR_GAP,
                toolbarTop,
                Math.max(1, toolbarLeft + toolbarWidth - searchRegion.right() - TOOLBAR_GAP),
                toolbarHeight
        );
        RegionBox rawCardRegion = relativeBox(screenWidth, screenHeight, CARD_LEFT_RATIO, CARD_TOP_RATIO, CARD_WIDTH_RATIO, CARD_HEIGHT_RATIO);
        int cardTop = clamp(Math.max(rawCardRegion.top(), toolbarTop + toolbarHeight + 8), 0, Math.max(0, pagerRegion.top() - MIN_CARD_HEIGHT - 4));
        int cardBottomLimit = Math.max(rawCardRegion.top() + MIN_CARD_HEIGHT, Math.min(rawCardRegion.bottom(), pagerRegion.top() - 4));
        RegionBox cardRegion = new RegionBox(
                rawCardRegion.left(),
                cardTop,
                rawCardRegion.width(),
                Math.max(MIN_CARD_HEIGHT, cardBottomLimit - cardTop)
        );
        int selectedInfoHeight = Math.max(16, Math.round(screenHeight * SELECTED_INFO_HEIGHT_RATIO));
        RegionBox selectedInfoRegion = new RegionBox(
                cardRegion.left(),
                Math.max(cardRegion.top(), cardRegion.bottom() - selectedInfoHeight),
                cardRegion.width(),
                selectedInfoHeight
        );
        return new RegionMetrics(
                titleRegion,
                searchRegion,
                toolbarButtonsRegion,
                cardRegion,
                selectedInfoRegion,
                pagerRegion,
                confirmRegion
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** searchTextOffsetY: 根据搜索框高度计算输入文字的垂直居中偏移。 */
    private static int searchTextOffsetY(int frameHeight) {
        return Math.max(1, (frameHeight - RecipeBookSearchUi.TEXT_HEIGHT) / 2);
    }

    private static UIElement absoluteRegion(RegionBox region) {
        return SimuKraftFlexLayout.absoluteRegion(region.left(), region.top(), region.width(), region.height());
    }

    private static RegionBox relativeBox(int screenWidth, int screenHeight, float leftRatio, float topRatio, float widthRatio, float heightRatio) {
        int left = clamp(Math.round(screenWidth * leftRatio), 0, screenWidth - 1);
        int top = clamp(Math.round(screenHeight * topRatio), 0, screenHeight - 1);
        int width = clamp(Math.round(screenWidth * widthRatio), 1, screenWidth - left);
        int height = clamp(Math.round(screenHeight * heightRatio), 1, screenHeight - top);
        return new RegionBox(left, top, width, height);
    }

    private static void layoutButtonInRegion(Button button, RegionBox region, float widthRatio, float heightRatio) {
        int width = clamp(Math.round(region.width() * widthRatio), Math.min(MIN_BUTTON_WIDTH, region.width()), region.width());
        int height = clamp(Math.round(region.height() * heightRatio), Math.min(MIN_BUTTON_HEIGHT, region.height()), region.height());
        button.layout(layout -> {
            layout.width(width);
            layout.height(height);
        });
    }

    private static GridMetrics resolveGridMetrics(int regionWidth, int regionHeight) {
        int availableWidth = Math.max(MIN_CARD_WIDTH, regionWidth - 8);
        int columns = Math.max(1, (availableWidth + CARD_GAP) / (MIN_CARD_WIDTH + CARD_GAP));
        int cardWidth = Math.max(MIN_CARD_WIDTH, Math.min(PREFERRED_CARD_WIDTH, (availableWidth - (columns - 1) * CARD_GAP) / columns));
        int availableHeight = Math.max(MIN_CARD_HEIGHT, regionHeight - 8);
        int rows = Math.max(1, (availableHeight + CARD_GAP) / (MIN_CARD_HEIGHT + CARD_GAP));
        int cardHeight = Math.max(MIN_CARD_HEIGHT, Math.min(PREFERRED_CARD_HEIGHT, (availableHeight - (rows - 1) * CARD_GAP) / rows));
        int perPage = Math.max(6, Math.min(MAX_BUILDINGS_PER_PAGE, columns * rows));
        return new GridMetrics(columns, rows, perPage, cardWidth, cardHeight);
    }

    private static List<BuildingCacheService.BuildingMeta> pageBuildings(List<BuildingCacheService.BuildingMeta> buildings, int page, int perPage) {
        int start = Math.max(0, page) * perPage;
        int end = Math.min(start + perPage, buildings.size());
        if (start >= end) {
            return new ArrayList<>();
        }
        return new ArrayList<>(buildings.subList(start, end));
    }

    private static int totalPages(List<BuildingCacheService.BuildingMeta> buildings, int perPage) {
        return Math.max(1, (int) Math.ceil(Math.max(1, buildings.size()) / (double) Math.max(1, perPage)));
    }

    private static List<BuildingCacheService.BuildingMeta> filteredBuildings(List<BuildingCacheService.BuildingMeta> buildings, String category) {
        String query = normalizedSearchText();
        return buildings.stream()
                .filter(building -> !showFavoritesOnly || isFavorite(building))
                .filter(building -> query.isBlank() || matchesSearch(building, category, query))
                .sorted(currentComparator())
                .toList();
    }

    /** currentComparator: 根据当前工具栏排序状态生成稳定排序规则。 */
    private static Comparator<BuildingCacheService.BuildingMeta> currentComparator() {
        Comparator<BuildingCacheService.BuildingMeta> comparator = switch (sortMode) {
            case NAME -> Comparator.comparing(BuildingCacheService.BuildingMeta::name, String.CASE_INSENSITIVE_ORDER);
            case PRICE -> Comparator.comparingDouble(building -> firstNumber(building.amount()));
            case SIZE -> Comparator.comparingInt(building -> sizeVolume(building.size()));
            case AUTHOR -> Comparator.comparing(BuildingCacheService.BuildingMeta::author, String.CASE_INSENSITIVE_ORDER);
        };
        comparator = comparator.thenComparing(BuildingCacheService.BuildingMeta::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(BuildingCacheService.BuildingMeta::structureFileName, String.CASE_INSENSITIVE_ORDER);
        return sortDescending ? comparator.reversed() : comparator;
    }

    private static boolean matchesSearch(BuildingCacheService.BuildingMeta building, String category, String query) {
        return containsSearch(building.name(), query)
                || containsSearch(building.size(), query)
                || containsSearch(building.amount(), query)
                || containsSearch(building.author(), query)
                || containsSearch(building.metaFileName(), query)
                || containsSearch(building.structureFileName(), query)
                || containsSearch(building.category(), query)
                || containsSearch(category, query)
                || containsSearch(categoryName(category), query);
    }

    private static boolean containsSearch(String text, String query) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String normalizedSearchText() {
        return searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
    }

    /** toggleFavorite: 切换建筑收藏并处理仅看收藏时的选中状态。 */
    private static void toggleFavorite(BuildingCacheService.BuildingMeta building) {
        BuildingFavoriteStore.toggleFavorite(building);
        if (selectedBuildingFileName != null && selectedBuildingFileName.equals(building.structureFileName()) && showFavoritesOnly && !isFavorite(building)) {
            selectedBuildingFileName = null;
        }
    }

    /** isFavorite: 查询建筑是否已收藏。 */
    private static boolean isFavorite(BuildingCacheService.BuildingMeta building) {
        return BuildingFavoriteStore.isFavorite(building);
    }

    /** firstNumber: 从造价文本中提取排序用的首个数字。 */
    private static double firstNumber(String text) {
        if (text == null || text.isBlank()) {
            return 0.0D;
        }
        StringBuilder builder = new StringBuilder();
        boolean started = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-') {
                builder.append(c);
                started = true;
            } else if (started) {
                break;
            }
        }
        try {
            return builder.isEmpty() ? 0.0D : Double.parseDouble(builder.toString());
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    /** sizeVolume: 从尺寸文本中提取长宽高并计算体积排序值。 */
    private static int sizeVolume(String size) {
        if (size == null || size.isBlank()) {
            return 0;
        }
        int[] values = new int[3];
        int index = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i <= size.length() && index < values.length; i++) {
            char c = i < size.length() ? size.charAt(i) : ' ';
            if (c >= '0' && c <= '9') {
                current.append(c);
            } else if (!current.isEmpty()) {
                try {
                    values[index++] = Integer.parseInt(current.toString());
                } catch (NumberFormatException ignored) {
                    values[index++] = 0;
                }
                current.setLength(0);
            }
        }
        return Math.max(0, values[0]) * Math.max(0, values[1]) * Math.max(0, values[2]);
    }

    private static void confirmSelectedBuilding() {
        if (selectedBuildingFileName == null || currentCategory == null || currentBuildBoxPos == null) {
            return;
        }
        Optional<BuildingCacheService.BuildingMeta> selectedBuilding = BuildingCacheService.getBuildings(currentCategory).stream()
                .filter(building -> selectedBuildingFileName.equals(building.structureFileName()))
                .findFirst();
        if (selectedBuilding.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        BuildingCacheService.BuildingMeta building = selectedBuilding.get();
        Optional<BuildingStructure> structure = BuildingStructureService.loadStructure(building.category(), building.metaFileName());
        if (structure.isPresent()) {
            pendingPreview = new PendingPreview(building, currentBuildBoxPos, structure.get());
            minecraft.setScreen(null);
        } else {
            ClientInfoToast.show(
                    Component.translatable("toast.simukraft.title"),
                    Component.translatable("message.simukraft.building_preview.open_failed", building.name()),
                    "error"
            );
        }
    }

    private static String categoryName(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "residential" -> Component.translatable("gui.category.residential").getString();
            case "commercial" -> Component.translatable("gui.category.commercial").getString();
            case "industry" -> Component.translatable("gui.category.industrial").getString();
            case "public" -> Component.translatable("gui.category.public").getString();
            case "other" -> Component.translatable("gui.category.other").getString();
            default -> category;
        };
    }

    private static int categoryColor(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "residential" -> 0xFF90EE90;
            case "commercial" -> 0xFFADD8E6;
            case "industry" -> 0xFFC8A260;
            case "public" -> 0xFFB39DDB;
            case "other" -> 0xFFFFFFFF;
            default -> 0xFFC8A260;
        };
    }

    private static String trim(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxChars - 2)) + "..";
    }

    private static final class BuildingUiController {
        private final String category;
        private final List<BuildingCacheService.BuildingMeta> buildings;
        private final RegionMetrics regions;
        private final GridMetrics grid;
        private final UIElement statusSlot;
        private final UIElement cardRegion;
        private final UIElement infoRegion;
        private final UIElement pagerRegion;
        private final UIElement confirmRegion;
        private final UIElement toolbarButtonsRegion;

        private BuildingUiController(String category, List<BuildingCacheService.BuildingMeta> buildings, RegionMetrics regions,
                                     GridMetrics grid, UIElement statusSlot, UIElement cardRegion, UIElement infoRegion,
                                     UIElement pagerRegion, UIElement confirmRegion, UIElement toolbarButtonsRegion) {
            this.category = category;
            this.buildings = buildings;
            this.regions = regions;
            this.grid = grid;
            this.statusSlot = statusSlot;
            this.cardRegion = cardRegion;
            this.infoRegion = infoRegion;
            this.pagerRegion = pagerRegion;
            this.confirmRegion = confirmRegion;
            this.toolbarButtonsRegion = toolbarButtonsRegion;
        }

        /** onSearchChanged: 搜索变化时只刷新建筑列表，保留输入框焦点。 */
        private void onSearchChanged(String text) {
            searchText = text == null ? "" : text;
            currentPage = 0;
            selectedBuildingFileName = null;
            refresh();
        }

        /** refresh: 根据当前搜索、分页和选中状态重建可变区域。 */
        private void refresh() {
            List<BuildingCacheService.BuildingMeta> filteredBuildings = filteredBuildings(buildings, category);
            int pageCount = totalPages(filteredBuildings, grid.perPage());
            currentPage = Math.max(0, Math.min(currentPage, pageCount - 1));
            refreshStatus(filteredBuildings);
            refreshCards(filteredBuildings);
            refreshSelectedInfo();
            refreshPager(pageCount);
            refreshConfirm();
            refreshToolbar();
        }

        private void refreshStatus(List<BuildingCacheService.BuildingMeta> filteredBuildings) {
            statusSlot.clearAllChildren();
            Component status = buildings.isEmpty()
                    ? Component.translatable("gui.building_list.empty_dir", BuildingCacheService.rootDirectoryText())
                    : Component.translatable("gui.building_list.status", filteredBuildings.size());
            int statusColor = buildings.isEmpty()
                    ? SimuKraftUiTheme.TEXT_ERROR_COLOR
                    : filteredBuildings.isEmpty() ? SimuKraftUiTheme.TEXT_WARNING_COLOR : SimuKraftUiTheme.TEXT_SUCCESS_COLOR;
            statusSlot.addChild(textElement(status, regions.titleRegion().width(), statusColor, TextTexture.TextType.NORMAL).layout(layout -> {
                layout.widthPercent(100);
                layout.height(18);
            }));
        }

        private void refreshCards(List<BuildingCacheService.BuildingMeta> filteredBuildings) {
            cardRegion.clearAllChildren();
            List<BuildingCacheService.BuildingMeta> pageBuildings = pageBuildings(filteredBuildings, currentPage, grid.perPage());
            for (BuildingCacheService.BuildingMeta building : pageBuildings) {
                cardRegion.addChild(createBuildingCard(building, grid.cardWidth(), grid.cardHeight(), categoryColor(category), this::refresh));
            }
        }

        private void refreshSelectedInfo() {
            infoRegion.clearAllChildren();
            if (selectedBuildingFileName == null) {
                return;
            }
            infoRegion.addChild(textElement(Component.translatable("gui.building_list.selected", selectedBuildingFileName),
                    regions.selectedInfoRegion().width(), SimuKraftUiTheme.TEXT_WARNING_COLOR, TextTexture.TextType.NORMAL).layout(layout -> {
                layout.widthPercent(100);
                layout.height(12);
            }));
        }

        private void refreshPager(int pageCount) {
            pagerRegion.clearAllChildren();
            Button prevButton = new Button();
            prevButton.setText(Component.translatable("gui.pagination.previous"));
            layoutButtonInRegion(prevButton, regions.pagerRegion(), 0.25F, 0.82F);
            if (currentPage > 0) {
                prevButton.setOnClick(event -> {
                    currentPage--;
                    refresh();
                });
            } else {
                prevButton.setActive(false);
            }

            UIElement pageLabel = textElement(Component.translatable("gui.pagination.info", currentPage + 1, pageCount),
                    regions.pagerRegion().width() / 3, SimuKraftUiTheme.TEXT_SECONDARY_COLOR, TextTexture.TextType.NORMAL);
            pageLabel.layout(layout -> {
                layout.width(Math.max(80, regions.pagerRegion().width() / 3));
                layout.height(14);
            });

            Button nextButton = new Button();
            nextButton.setText(Component.translatable("gui.pagination.next"));
            layoutButtonInRegion(nextButton, regions.pagerRegion(), 0.25F, 0.82F);
            if (currentPage < pageCount - 1) {
                nextButton.setOnClick(event -> {
                    currentPage++;
                    refresh();
                });
            } else {
                nextButton.setActive(false);
            }
            pagerRegion.addChild(prevButton);
            pagerRegion.addChild(pageLabel);
            pagerRegion.addChild(nextButton);
        }

        private void refreshConfirm() {
            confirmRegion.clearAllChildren();
            Button confirmButton = new Button();
            confirmButton.setText(Component.translatable("gui.button.select"));
            layoutButtonInRegion(confirmButton, regions.confirmRegion(), 0.88F, 0.82F);
            if (selectedBuildingFileName != null) {
                confirmButton.setOnClick(event -> confirmSelectedBuilding());
            } else {
                confirmButton.setActive(false);
            }
            confirmRegion.addChild(confirmButton);
        }

        /** refreshToolbar: 重建搜索栏右侧的收藏与排序按钮。 */
        private void refreshToolbar() {
            toolbarButtonsRegion.clearAllChildren();
            int width = Math.max(MIN_BUTTON_WIDTH, (regions.toolbarButtonsRegion().width() - TOOLBAR_GAP * 3) / 4);
            int height = Math.max(MIN_BUTTON_HEIGHT, regions.toolbarButtonsRegion().height() - 2);

            Button favoriteButton = toolbarButton(Component.translatable(showFavoritesOnly
                    ? "gui.building_list.favorites_only"
                    : "gui.building_list.show_favorites"), width, height);
            favoriteButton.setOnClick(event -> {
                if (event.button == 0) {
                    showFavoritesOnly = !showFavoritesOnly;
                    currentPage = 0;
                    selectedBuildingFileName = null;
                    refresh();
                }
            });

            Button sortModeButton = toolbarButton(Component.translatable("gui.building_list.sort_button", sortMode.label()), width, height);
            sortModeButton.setOnClick(event -> {
                if (event.button == 0) {
                    sortMode = sortMode.next();
                    currentPage = 0;
                    refresh();
                }
            });

            Button sortDirectionButton = toolbarButton(Component.translatable(sortDescending
                    ? "gui.building_list.sort_desc"
                    : "gui.building_list.sort_asc"), width, height);
            sortDirectionButton.setOnClick(event -> {
                if (event.button == 0) {
                    sortDescending = !sortDescending;
                    currentPage = 0;
                    refresh();
                }
            });

            Button resetButton = toolbarButton(Component.translatable("gui.building_list.reset_filters"), width, height);
            resetButton.setOnClick(event -> {
                if (event.button == 0) {
                    searchText = "";
                    showFavoritesOnly = false;
                    sortDescending = false;
                    sortMode = SortMode.NAME;
                    currentPage = 0;
                    selectedBuildingFileName = null;
                    open(category, currentBuildBoxPos);
                }
            });

            toolbarButtonsRegion.addChild(favoriteButton);
            toolbarButtonsRegion.addChild(sortModeButton);
            toolbarButtonsRegion.addChild(sortDirectionButton);
            toolbarButtonsRegion.addChild(resetButton);
        }

        /** toolbarButton: 创建建筑列表工具栏的固定尺寸按钮。 */
        private Button toolbarButton(Component text, int width, int height) {
            Button button = new Button();
            button.setText(text);
            button.layout(layout -> {
                layout.width(width);
                layout.height(height);
                layout.flexShrink(0);
            });
            return button;
        }
    }

    private record PendingPreview(BuildingCacheService.BuildingMeta building, BlockPos buildBoxPos, BuildingStructure structure) {
    }

    private record GridMetrics(int columns, int rows, int perPage, int cardWidth, int cardHeight) {
    }

    private record RegionMetrics(RegionBox titleRegion,
                                 RegionBox searchRegion,
                                 RegionBox toolbarButtonsRegion,
                                 RegionBox cardRegion,
                                 RegionBox selectedInfoRegion,
                                 RegionBox pagerRegion,
                                 RegionBox confirmRegion) {
    }

    private record RegionBox(int left, int top, int width, int height) {
        private int right() {
            return left + width;
        }

        private int bottom() {
            return top + height;
        }
    }

    private enum SortMode {
        NAME("gui.building_list.sort.name"),
        PRICE("gui.building_list.sort.price"),
        SIZE("gui.building_list.sort.size"),
        AUTHOR("gui.building_list.sort.author");

        private final String labelKey;

        SortMode(String labelKey) {
            this.labelKey = labelKey;
        }

        private Component label() {
            return Component.translatable(labelKey);
        }

        private SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
