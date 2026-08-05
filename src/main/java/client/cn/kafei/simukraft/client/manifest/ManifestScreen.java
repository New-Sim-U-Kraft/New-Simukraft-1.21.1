package client.cn.kafei.simukraft.client.manifest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import common.cn.kafei.simukraft.item.ManifestItem;
import common.cn.kafei.simukraft.network.manifest.ManifestTogglePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class ManifestScreen extends Screen {
    private static final int MIN_PAGE_WIDTH = 145;
    private static final int MAX_PAGE_WIDTH = 260;
    private static final int MIN_PAGE_HEIGHT = 190;
    private static final int MAX_PAGE_HEIGHT = 350;
    private static final int PAGE_BUTTON_WIDTH = 23;
    private static final int PAGE_BUTTON_HEIGHT = 13;
    private static final int ITEM_ICON_SIZE = 16;
    private static final int CHECKBOX_SIZE = 9;
    private static final int CHECKBOX_GAP = 4;
    private static final int ROW_HEIGHT = 18;
    private static final int MATERIAL_FRAME_TITLE_GAP = 4;
    private static final int BOARD_COLOR = 0xFF594233;
    private static final int BOARD_BORDER_COLOR = 0xFF251810;
    private static final int PAPER_COLOR = 0xFFF7E7C5;
    private static final int PAPER_SHADOW_COLOR = 0x5520130B;
    private static final int PAPER_EDGE_COLOR = 0xFFBAA279;
    private static final int TEXT_COLOR = 0xFF000000;
    private static final int MUTED_TEXT_COLOR = 0xFF5F4A30;
    private static final int CHECKED_TEXT_COLOR = 0xFFB7B7B7;
    private static final int METAL_COLOR = 0xFFC3C7CA;
    private static final int METAL_DARK_COLOR = 0xFF747B82;
    private static final int METAL_LIGHT_COLOR = 0xFFE2E5E8;

    private final ItemStack manifestStack;
    private final InteractionHand hand;
    private final List<RowBounds> rowBounds = new ArrayList<>();
    private List<ManifestItem.MaterialEntry> materials = List.of();
    private List<ManifestItem.ProductGroup> productGroups = List.of();
    private PageButton forwardButton;
    private PageButton backButton;
    private int currentPage;

    private ManifestScreen(ItemStack manifestStack, InteractionHand hand) {
        super(Component.translatable("screen.simukraft.manifest.title"));
        this.manifestStack = manifestStack.copy();
        this.hand = hand;
    }

    public static void open(ItemStack stack, InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new ManifestScreen(stack, hand)));
    }

    @Override
    protected void init() {
        refreshMaterials();
        PageLayout layout = pageLayout();
        forwardButton = addRenderableWidget(new PageButton(layout.forwardButtonX(), layout.buttonY(), true, this::pageForward, true));
        backButton = addRenderableWidget(new PageButton(layout.backButtonX(), layout.buttonY(), false, this::pageBack, true));
        updateButtonVisibility();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        PageLayout layout = pageLayout();
        renderPage(guiGraphics, layout);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        rowBounds.clear();
        renderContent(guiGraphics, layout);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (RowBounds bounds : rowBounds) {
            if (bounds.contains(mouseX, mouseY)) {
                toggleMaterial(bounds.materialIndex());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount < 0.0D && currentPage < pageCount() - 1) {
            currentPage++;
            updateButtonVisibility();
            return true;
        }
        if (verticalAmount > 0.0D && currentPage > 0) {
            currentPage--;
            updateButtonVisibility();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderPage(GuiGraphics guiGraphics, PageLayout layout) {
        guiGraphics.fill(layout.boardX(), layout.boardY(), layout.boardRight(), layout.boardBottom(), BOARD_COLOR);
        guiGraphics.fill(layout.boardX(), layout.boardY(), layout.boardRight(), layout.boardY() + 1, BOARD_BORDER_COLOR);
        guiGraphics.fill(layout.boardX(), layout.boardBottom() - 1, layout.boardRight(), layout.boardBottom(), BOARD_BORDER_COLOR);
        guiGraphics.fill(layout.boardX(), layout.boardY(), layout.boardX() + 1, layout.boardBottom(), BOARD_BORDER_COLOR);
        guiGraphics.fill(layout.boardRight() - 1, layout.boardY(), layout.boardRight(), layout.boardBottom(), BOARD_BORDER_COLOR);

        guiGraphics.fill(layout.pageX() + 3, layout.pageY() + 3, layout.pageRight() + 3, layout.pageBottom() + 3, PAPER_SHADOW_COLOR);
        guiGraphics.fill(layout.pageX(), layout.pageY(), layout.pageRight(), layout.pageBottom(), PAPER_COLOR);
        guiGraphics.fill(layout.pageX(), layout.pageY(), layout.pageRight(), layout.pageY() + 1, PAPER_EDGE_COLOR);
        guiGraphics.fill(layout.pageX(), layout.pageBottom() - 1, layout.pageRight(), layout.pageBottom(), PAPER_EDGE_COLOR);
        guiGraphics.fill(layout.pageX(), layout.pageY(), layout.pageX() + 1, layout.pageBottom(), PAPER_EDGE_COLOR);
        guiGraphics.fill(layout.pageRight() - 1, layout.pageY(), layout.pageRight(), layout.pageBottom(), PAPER_EDGE_COLOR);
        renderClip(guiGraphics, layout);
    }

    private void renderClip(GuiGraphics guiGraphics, PageLayout layout) {
        int clipLeft = layout.clipX();
        int clipTop = layout.clipY();
        int clipRight = clipLeft + layout.clipWidth();
        int clipBottom = clipTop + layout.clipHeight();
        int bodyTop = clipTop + Math.max(1, layout.clipHeight() / 3);
        int tabLeft = clipLeft + layout.clipWidth() / 4;
        int tabRight = clipRight - layout.clipWidth() / 4;
        int tabBottom = clipTop + Math.max(1, layout.clipHeight() / 2);
        guiGraphics.fill(clipLeft, bodyTop, clipRight, clipBottom, METAL_COLOR);
        guiGraphics.fill(clipLeft, bodyTop, clipRight, bodyTop + 1, METAL_DARK_COLOR);
        guiGraphics.fill(clipLeft, clipBottom - 1, clipRight, clipBottom, METAL_DARK_COLOR);
        guiGraphics.fill(tabLeft, clipTop, tabRight, tabBottom, METAL_LIGHT_COLOR);
        guiGraphics.fill(tabLeft, clipTop, tabRight, clipTop + 1, METAL_DARK_COLOR);
        guiGraphics.fill(tabLeft, tabBottom - 1, tabRight, tabBottom, METAL_DARK_COLOR);
        int rivetSize = Math.max(4, layout.clipHeight() / 4);
        int rivetY = bodyTop + (clipBottom - bodyTop - rivetSize) / 2;
        guiGraphics.fill(clipLeft + layout.clipWidth() / 8, rivetY, clipLeft + layout.clipWidth() / 8 + rivetSize, rivetY + rivetSize, METAL_DARK_COLOR);
        guiGraphics.fill(clipRight - layout.clipWidth() / 8 - rivetSize, rivetY, clipRight - layout.clipWidth() / 8, rivetY + rivetSize, METAL_DARK_COLOR);
    }

    private void renderContent(GuiGraphics guiGraphics, PageLayout layout) {
        int titleY = layout.pageY() + layout.titleTop();
        guiGraphics.drawCenteredString(font, title, layout.pageX() + layout.pageWidth() / 2, titleY, TEXT_COLOR);
        guiGraphics.drawString(font, Component.translatable("screen.simukraft.manifest.building", ManifestItem.getBuildingName(manifestStack)),
                layout.contentX(), titleY + 16, MUTED_TEXT_COLOR, false);
        guiGraphics.drawString(font, Component.literal((currentPage + 1) + "/" + pageCount()),
                layout.pageRight() - layout.contentPadding() - 22, layout.pageY() + 10, MUTED_TEXT_COLOR, false);

        if (!productGroups.isEmpty()) {
            renderMaterialFrame(guiGraphics, layout, Component.translatable(productFrameTitleKey()));
            renderProductGroup(guiGraphics, layout, productGroups.get(currentPage));
            return;
        }

        renderMaterialFrame(guiGraphics, layout, Component.translatable("screen.simukraft.manifest.material_header"));
        if (materials.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("screen.simukraft.manifest.empty"),
                    layout.pageX() + layout.pageWidth() / 2, layout.listTop(), CHECKED_TEXT_COLOR);
            return;
        }
        int start = currentPage * layout.itemsPerPage();
        int end = Math.min(materials.size(), start + layout.itemsPerPage());
        int y = layout.listTop();
        for (int i = start; i < end; i++) {
            renderMaterialRow(guiGraphics, layout, materials.get(i), y);
            y += ROW_HEIGHT;
        }
    }

    private void renderMaterialRow(GuiGraphics guiGraphics, PageLayout layout, ManifestItem.MaterialEntry entry, int y) {
        int checkboxX = layout.contentX();
        int checkboxY = y + (ROW_HEIGHT - CHECKBOX_SIZE) / 2;
        int color = entry.checked() ? CHECKED_TEXT_COLOR : TEXT_COLOR;
        renderCheckbox(guiGraphics, checkboxX, checkboxY, entry.checked(), color);
        int iconX = checkboxX + CHECKBOX_SIZE + CHECKBOX_GAP;
        int iconY = y + (ROW_HEIGHT - ITEM_ICON_SIZE) / 2;
        guiGraphics.renderItem(materialStack(entry.itemId()), iconX, iconY);
        int textX = iconX + ITEM_ICON_SIZE + 5;
        String countText = fitText(countText(entry), Math.max(1, layout.contentRight() - textX - 8));
        int countWidth = font.width(countText);
        int countX = layout.contentRight() - countWidth;
        int nameWidth = Math.max(1, countX - textX - 8);
        guiGraphics.drawString(font, fitText(materialName(entry.itemId()).getString(), nameWidth), textX, y + 5, color, false);
        guiGraphics.drawString(font, countText, countX, y + 5, color, false);
        if (entry.checked()) {
            int lineY = y + 10;
            guiGraphics.fill(textX, lineY, layout.contentRight(), lineY + 1, CHECKED_TEXT_COLOR);
        }
        if (entry.index() >= 0) {
            rowBounds.add(new RowBounds(entry.index(), layout.contentX(), y, layout.contentWidth(), ROW_HEIGHT));
        }
    }

    /** renderMaterialFrame: 绘制材料列表区域的小标题和边框。 */
    private void renderMaterialFrame(GuiGraphics guiGraphics, PageLayout layout, Component title) {
        int left = layout.contentX() - 5;
        int right = layout.contentRight() + 5;
        int top = layout.listFrameTop();
        int bottom = layout.listFrameBottom();
        guiGraphics.fill(left, top, right, top + 1, PAPER_EDGE_COLOR);
        guiGraphics.fill(left, bottom - 1, right, bottom, PAPER_EDGE_COLOR);
        guiGraphics.fill(left, top, left + 1, bottom, PAPER_EDGE_COLOR);
        guiGraphics.fill(right - 1, top, right, bottom, PAPER_EDGE_COLOR);

        int titleX = left + 9;
        int titleY = top - 4;
        int titleRight = titleX + font.width(title) + MATERIAL_FRAME_TITLE_GAP * 2;
        guiGraphics.fill(titleX - MATERIAL_FRAME_TITLE_GAP, top, titleRight, top + 1, PAPER_COLOR);
        guiGraphics.drawString(font, title, titleX, titleY, MUTED_TEXT_COLOR, false);
    }

    /** renderProductGroup: 上下动态分栏渲染”需要物品 / 产出商品”。 */
    private void renderProductGroup(GuiGraphics guiGraphics, PageLayout layout, ManifestItem.ProductGroup group) {
        int left = layout.contentX() + 4;
        int right = layout.contentRight() - 4;
        int width = Math.max(1, right - left);
        int top = layout.listTop();
        int bottom = layout.listFrameBottom() - 8;

        // 无标题，仅保留10px分隔区，其余全部给行内容
        int rowsArea = Math.max(2 * ROW_HEIGHT, bottom - top - 10);
        int totalItems = Math.max(1, group.materials().size() + group.products().size());
        int totalRowSlots = Math.max(2, rowsArea / ROW_HEIGHT);
        int materialRows = Math.max(1, Math.min(group.materials().size(),
                (int) Math.round((double) totalRowSlots * group.materials().size() / totalItems)));
        int productRows = Math.max(1, Math.min(group.products().size(), totalRowSlots - materialRows));

        // 上区：需要的物品（无标题，直接列表）
        renderEntryRows(guiGraphics, group.materials(), left + 7, top, width - 14, materialRows, false);

        // 分隔线
        int dividerY = top + materialRows * ROW_HEIGHT + 4;
        guiGraphics.fill(left + 8, dividerY, right - 8, dividerY + 1, PAPER_EDGE_COLOR);

        // 下区：产出 / 售出商品（无标题，直接列表）
        renderEntryRows(guiGraphics, group.products(), left + 7, dividerY + 5, width - 14, productRows, false);
    }

    private void renderEntryRows(GuiGraphics guiGraphics,
                                 List<ManifestItem.MaterialEntry> entries,
                                 int x,
                                 int y,
                                 int width,
                                 int maxRows,
                                 boolean checkable) {
        int rows = Math.min(entries.size(), maxRows);
        for (int i = 0; i < rows; i++) {
            renderEntryRow(guiGraphics, entries.get(i), x, y + i * ROW_HEIGHT, width, checkable);
        }
        if (entries.size() > rows) {
            guiGraphics.drawCenteredString(font, Component.literal("..."), x + width / 2, y + rows * ROW_HEIGHT + 3, MUTED_TEXT_COLOR);
        }
    }

    private void renderEntryRow(GuiGraphics guiGraphics, ManifestItem.MaterialEntry entry, int x, int y, int width, boolean checkable) {
        int currentX = x;
        int color = checkable && entry.checked() ? CHECKED_TEXT_COLOR : TEXT_COLOR;
        if (checkable) {
            int checkboxY = y + (ROW_HEIGHT - CHECKBOX_SIZE) / 2;
            renderCheckbox(guiGraphics, currentX, checkboxY, entry.checked(), color);
            currentX += CHECKBOX_SIZE + CHECKBOX_GAP;
        }
        int iconY = y + (ROW_HEIGHT - ITEM_ICON_SIZE) / 2;
        guiGraphics.renderItem(materialStack(entry.itemId()), currentX, iconY);
        currentX += ITEM_ICON_SIZE + 5;
        String countText = checkable ? countText(entry) : "x" + entry.count();
        int countWidth = font.width(countText);
        int countX = x + width - countWidth;
        int nameWidth = Math.max(1, countX - currentX - 8);
        guiGraphics.drawString(font, fitText(materialName(entry.itemId()).getString(), nameWidth), currentX, y + 5, color, false);
        guiGraphics.drawString(font, countText, countX, y + 5, color, false);
        if (checkable && entry.checked()) {
            guiGraphics.fill(currentX, y + 10, x + width, y + 11, CHECKED_TEXT_COLOR);
        }
        if (checkable && entry.index() >= 0) {
            rowBounds.add(new RowBounds(entry.index(), x, y, width, ROW_HEIGHT));
        }
    }

    private void renderCheckbox(GuiGraphics guiGraphics, int x, int y, boolean checked, int color) {
        guiGraphics.hLine(x, x + CHECKBOX_SIZE - 1, y, color);
        guiGraphics.hLine(x, x + CHECKBOX_SIZE - 1, y + CHECKBOX_SIZE - 1, color);
        guiGraphics.vLine(x, y, y + CHECKBOX_SIZE - 1, color);
        guiGraphics.vLine(x + CHECKBOX_SIZE - 1, y, y + CHECKBOX_SIZE - 1, color);
        if (!checked) {
            return;
        }
        guiGraphics.hLine(x + 2, x + 3, y + 5, color);
        guiGraphics.hLine(x + 3, x + 4, y + 6, color);
        guiGraphics.hLine(x + 4, x + 5, y + 7, color);
        guiGraphics.hLine(x + 5, x + 6, y + 6, color);
        guiGraphics.hLine(x + 6, x + 7, y + 5, color);
        guiGraphics.hLine(x + 7, x + 8, y + 4, color);
    }

    private void toggleMaterial(int materialIndex) {
        if (materialIndex < 0 || materialIndex >= materials.size()) {
            return;
        }
        ManifestItem.MaterialEntry entry = materials.stream()
                .filter(candidate -> candidate.index() == materialIndex)
                .findFirst()
                .orElse(null);
        if (entry == null) {
            return;
        }
        boolean checked = !entry.checked();
        ManifestItem.setChecked(manifestStack, materialIndex, checked);
        PacketDistributor.sendToServer(new ManifestTogglePacket(hand, materialIndex, checked));
        refreshMaterials();
    }

    private void pageForward(Button button) {
        if (currentPage < pageCount() - 1) {
            currentPage++;
        }
        updateButtonVisibility();
    }

    private void pageBack(Button button) {
        if (currentPage > 0) {
            currentPage--;
        }
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        if (forwardButton != null) {
            forwardButton.visible = currentPage < pageCount() - 1;
        }
        if (backButton != null) {
            backButton.visible = currentPage > 0;
        }
    }

    private void refreshMaterials() {
        materials = ManifestItem.getMaterials(manifestStack);
        productGroups = ManifestItem.getProductGroups(manifestStack);
        currentPage = Mth.clamp(currentPage, 0, Math.max(0, pageCount() - 1));
        updateButtonVisibility();
    }

    private int pageCount() {
        if (!productGroups.isEmpty()) {
            return productGroups.size();
        }
        int perPage = pageLayout().itemsPerPage();
        return Math.max(1, (materials.size() + perPage - 1) / perPage);
    }

    private String productFrameTitleKey() {
        return "industrial".equals(ManifestItem.getSourceType(manifestStack))
                ? "screen.simukraft.manifest.recipe_header"
                : "screen.simukraft.manifest.product_header";
    }

    private PageLayout pageLayout() {
        int maxPageHeight = Math.max(MIN_PAGE_HEIGHT, height - 44);
        int pageHeight = clamp(Math.round(height * 0.86F), MIN_PAGE_HEIGHT, Math.min(MAX_PAGE_HEIGHT, maxPageHeight));
        int maxPageWidth = Math.max(MIN_PAGE_WIDTH, width - 44);
        int pageWidth = clamp(Math.round(pageHeight * 0.72F), MIN_PAGE_WIDTH, Math.min(MAX_PAGE_WIDTH, maxPageWidth));
        if (pageHeight <= pageWidth) {
            pageHeight = Math.min(maxPageHeight, pageWidth + 42);
        }
        int pageX = (width - pageWidth) / 2;
        int pageY = Math.max(12, (height - pageHeight) / 2 - 4);
        int boardPadding = clamp(Math.round(pageWidth * 0.060F), 8, 12);
        int boardTopExtra = clamp(Math.round(pageHeight * 0.055F), 10, 16);
        int boardX = pageX - boardPadding;
        int boardY = pageY - boardTopExtra;
        int boardWidth = pageWidth + boardPadding * 2;
        int boardHeight = pageHeight + boardPadding + boardTopExtra;
        int contentPadding = clamp(Math.round(pageWidth * 0.090F), 13, 20);
        int listFrameTop = pageY + clamp(Math.round(pageHeight * 0.255F), 52, 72);
        int listTop = listFrameTop + 14;
        int listBottom = pageY + pageHeight - 32;
        int listFrameBottom = listBottom + 5;
        int itemsPerPage = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int clipWidth = clamp(Math.round(pageWidth * 0.440F), 72, 110);
        int clipHeight = clamp(Math.round(pageHeight * 0.070F), 16, 22);
        int clipX = pageX + (pageWidth - clipWidth) / 2;
        int clipY = boardY + 5;
        int buttonY = pageY + pageHeight - PAGE_BUTTON_HEIGHT - 7;
        return new PageLayout(pageX, pageY, pageWidth, pageHeight, boardX, boardY, boardWidth, boardHeight,
                contentPadding, listFrameTop, listTop, listFrameBottom, itemsPerPage, clipX, clipY, clipWidth, clipHeight, buttonY);
    }

    private Component materialName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return Component.literal(itemId);
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item).getHoverName();
    }

    private ItemStack materialStack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return new ItemStack(Items.BARRIER);
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
    }

    private String countText(ManifestItem.MaterialEntry entry) {
        return Component.translatable("screen.simukraft.manifest.count_progress", entry.available(), entry.count()).getString();
    }

    private String fitText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return font.plainSubstrByWidth(text, Math.max(1, maxWidth));
        }
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth - ellipsisWidth)) + ellipsis;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record RowBounds(int materialIndex, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record PageLayout(int pageX,
                              int pageY,
                              int pageWidth,
                              int pageHeight,
                              int boardX,
                              int boardY,
                              int boardWidth,
                              int boardHeight,
                              int contentPadding,
                              int listFrameTop,
                              int listTop,
                              int listFrameBottom,
                              int itemsPerPage,
                              int clipX,
                              int clipY,
                              int clipWidth,
                              int clipHeight,
                              int buttonY) {
        private int pageRight() {
            return pageX + pageWidth;
        }

        private int pageBottom() {
            return pageY + pageHeight;
        }

        private int boardRight() {
            return boardX + boardWidth;
        }

        private int boardBottom() {
            return boardY + boardHeight;
        }

        private int contentX() {
            return pageX + contentPadding;
        }

        private int contentRight() {
            return pageRight() - contentPadding;
        }

        private int contentWidth() {
            return pageWidth - contentPadding * 2;
        }

        private int titleTop() {
            return 21;
        }

        private int backButtonX() {
            return pageX + 20;
        }

        private int forwardButtonX() {
            return pageRight() - 20 - PAGE_BUTTON_WIDTH;
        }
    }
}
