package client.cn.kafei.simukraft.client.logistics;

import common.cn.kafei.simukraft.logistics.menu.LogisticsWarehouseGridMenu;
import common.cn.kafei.simukraft.network.logistics.LogisticsWarehouseGridRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class LogisticsWarehouseGridScreen extends AbstractContainerScreen<LogisticsWarehouseGridMenu> {
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/generic_54.png");
    private static final int WAREHOUSE_X = 8;
    private static final int WAREHOUSE_Y = 18;
    private static final int SLOT_SIZE = 18;
    private static final int SEARCH_WIDTH = 100;
    private static final int SEARCH_HEIGHT = 12;
    private static final int SCROLLBAR_X = 170;
    private static final int SCROLLBAR_Y = 18;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int SCROLLBAR_HEIGHT = 108;

    private EditBox searchBox;
    private boolean scrolling;
    private int refreshTimer;

    public LogisticsWarehouseGridScreen(LogisticsWarehouseGridMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    /** receiveIfOpen: 将服务端仓库聚合快照推给当前仓库界面。 */
    static boolean receiveIfOpen(BlockPos pos, List<ItemStack> items, List<Integer> counts) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null
                && minecraft.screen instanceof LogisticsWarehouseGridScreen screen
                && screen.menu.getWarehousePos().equals(pos)) {
            screen.menu.updateClientItems(items, counts);
            return true;
        }
        return false;
    }

    /** init: 初始化搜索框并请求仓库聚合快照。 */
    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        int titleWidth = this.font.width(this.title);
        int searchX = Math.min(this.leftPos + 68, this.leftPos + 8 + titleWidth + 5);
        searchBox = new EditBox(this.font, searchX, this.topPos + 4, SEARCH_WIDTH, SEARCH_HEIGHT, Component.empty());
        searchBox.setMaxLength(50);
        searchBox.setBordered(true);
        searchBox.setFilter(value -> true);
        searchBox.setResponder(value -> menu.setSearchFilter(value));
        addRenderableWidget(searchBox);
        requestItems();
    }

    /** containerTick: 定时刷新仓库快照，保持超级堆叠总数同步。 */
    @Override
    protected void containerTick() {
        super.containerTick();
        refreshTimer++;
        if (refreshTimer >= 20) {
            refreshTimer = 0;
            requestItems();
        }
    }

    /** render: 渲染原版箱子、超级堆叠数量、滚动条和 tooltip。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderWarehouseCounts(graphics);
        renderScrollbar(graphics);
        if (!renderWarehouseTooltip(graphics, mouseX, mouseY)) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    /** renderBackground: 绘制旧版半透明暗底和原版箱子背景。 */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LogisticsNativeStyle.drawBackdrop(graphics, this.width, this.height);
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    /** renderBg: 绘制 Minecraft 原版 54 格箱子背景。 */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    /** renderLabels: 绘制标题和玩家背包标题。 */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    /** mouseClicked: 支持搜索框聚焦和滚动条拖动。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOverScrollbar(mouseX, mouseY)) {
            scrolling = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** mouseDragged: 拖动超级堆叠滚动条。 */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrolling) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** mouseReleased: 结束滚动条拖动。 */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** mouseScrolled: 鼠标滚轮按聚合行滚动。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOverWarehouse(mouseX, mouseY) || isMouseOverScrollbar(mouseX, mouseY)) {
            menu.setScrollOffset(menu.scrollOffset() + (verticalAmount > 0.0D ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** keyPressed: 搜索框聚焦时优先处理文本按键。 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == 256 || keyCode == 257) {
                searchBox.setFocused(false);
                return true;
            }
            return searchBox.keyPressed(keyCode, scanCode, modifiers) || searchBox.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** charTyped: 搜索框聚焦时接收可打印字符。 */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.isFocused() && codePoint >= 32) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    /** requestItems: 请求服务端仓库聚合快照。 */
    private void requestItems() {
        PacketDistributor.sendToServer(new LogisticsWarehouseGridRequestPacket(menu.getWarehousePos()));
    }

    /** renderWarehouseCounts: 覆盖绘制超级堆叠真实数量。 */
    private void renderWarehouseCounts(GuiGraphics graphics) {
        for (int slot = 0; slot < LogisticsWarehouseGridMenu.WAREHOUSE_SLOTS; slot++) {
            int count = menu.actualCountAtVisibleSlot(slot);
            if (count <= 1) {
                continue;
            }
            int x = this.leftPos + WAREHOUSE_X + slot % LogisticsWarehouseGridMenu.GRID_COLS * SLOT_SIZE;
            int y = this.topPos + WAREHOUSE_Y + slot / LogisticsWarehouseGridMenu.GRID_COLS * SLOT_SIZE;
            renderQuantity(graphics, x, y, formatCount(count));
        }
    }

    /** renderScrollbar: 绘制右侧超级堆叠滚动条。 */
    private void renderScrollbar(GuiGraphics graphics) {
        int x = this.leftPos + SCROLLBAR_X;
        int y = this.topPos + SCROLLBAR_Y;
        int maxScroll = menu.maxScroll();
        graphics.fill(x, y, x + SCROLLBAR_WIDTH, y + SCROLLBAR_HEIGHT, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + SCROLLBAR_WIDTH - 1, y + SCROLLBAR_HEIGHT - 1, 0xFF8B8B8B);
        int sliderHeight = Math.max(15, (int) (SCROLLBAR_HEIGHT * (LogisticsWarehouseGridMenu.GRID_ROWS / (float) menu.totalRows())));
        int sliderY = y;
        if (maxScroll > 0) {
            sliderY = y + Math.round((SCROLLBAR_HEIGHT - sliderHeight) * (menu.scrollOffset() / (float) maxScroll));
        }
        graphics.fill(x, sliderY, x + SCROLLBAR_WIDTH, sliderY + sliderHeight, scrolling ? 0xFF808080 : 0xFF505050);
    }

    /** renderWarehouseTooltip: 为超级堆叠槽补充真实总数 tooltip。 */
    private boolean renderWarehouseTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int slot = warehouseSlotAt(mouseX, mouseY);
        if (slot < 0) {
            return false;
        }
        ItemStack stack = menu.targetStackAtVisibleSlot(slot);
        int count = menu.actualCountAtVisibleSlot(slot);
        if (stack.isEmpty() || count <= 0) {
            return false;
        }
        graphics.renderTooltip(this.font, warehouseTooltip(stack, count), stack.getTooltipImage(), stack, mouseX, mouseY);
        return true;
    }

    /** warehouseTooltip: 使用原版物品提示并追加仓库真实总数。 */
    private List<Component> warehouseTooltip(ItemStack stack, int count) {
        Minecraft minecraft = this.minecraft;
        Item.TooltipContext context = minecraft != null && minecraft.level != null
                ? Item.TooltipContext.of(minecraft.level)
                : Item.TooltipContext.EMPTY;
        TooltipFlag flag = minecraft != null && minecraft.options.advancedItemTooltips
                ? TooltipFlag.ADVANCED
                : TooltipFlag.NORMAL;
        List<Component> lines = new ArrayList<>(stack.getTooltipLines(context, minecraft != null ? minecraft.player : null, flag));
        lines.add(Component.translatable("gui.simukraft.logistics.grid.count", count));
        return lines;
    }

    /** renderQuantity: 在物品右下角绘制紧凑数量文本。 */
    private void renderQuantity(GuiGraphics graphics, int x, int y, String text) {
        int textX = x + 17 - this.font.width(text);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        graphics.drawString(this.font, text, textX, y + 7, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }

    /** formatCount: 将大数量压缩为 k/M 显示。 */
    private static String formatCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        if (count < 1_000_000) {
            return count < 10_000 ? String.format(Locale.ROOT, "%.1fk", count / 1000.0D) : (count / 1000) + "k";
        }
        return count < 10_000_000 ? String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0D) : (count / 1_000_000) + "M";
    }

    /** warehouseSlotAt: 计算鼠标下的仓库可见槽位。 */
    private int warehouseSlotAt(double mouseX, double mouseY) {
        if (!isMouseOverWarehouse(mouseX, mouseY)) {
            return -1;
        }
        int col = (int) ((mouseX - (leftPos + WAREHOUSE_X)) / SLOT_SIZE);
        int row = (int) ((mouseY - (topPos + WAREHOUSE_Y)) / SLOT_SIZE);
        return row * LogisticsWarehouseGridMenu.GRID_COLS + col;
    }

    /** isMouseOverWarehouse: 判断鼠标是否在仓库 54 格区域。 */
    private boolean isMouseOverWarehouse(double mouseX, double mouseY) {
        int x = this.leftPos + WAREHOUSE_X;
        int y = this.topPos + WAREHOUSE_Y;
        return mouseX >= x
                && mouseX < x + LogisticsWarehouseGridMenu.GRID_COLS * SLOT_SIZE
                && mouseY >= y
                && mouseY < y + LogisticsWarehouseGridMenu.GRID_ROWS * SLOT_SIZE;
    }

    /** isMouseOverScrollbar: 判断鼠标是否在滚动条区域。 */
    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        int x = this.leftPos + SCROLLBAR_X;
        int y = this.topPos + SCROLLBAR_Y;
        return mouseX >= x && mouseX < x + SCROLLBAR_WIDTH && mouseY >= y && mouseY < y + SCROLLBAR_HEIGHT;
    }

    /** updateScrollFromMouse: 根据鼠标位置更新聚合列表滚动行。 */
    private void updateScrollFromMouse(double mouseY) {
        int maxScroll = menu.maxScroll();
        if (maxScroll <= 0) {
            menu.setScrollOffset(0);
            return;
        }
        double ratio = (mouseY - (topPos + SCROLLBAR_Y)) / SCROLLBAR_HEIGHT;
        menu.setScrollOffset((int) Math.round(ratio * maxScroll));
    }
}
