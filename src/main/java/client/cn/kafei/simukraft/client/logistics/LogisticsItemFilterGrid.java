package client.cn.kafei.simukraft.client.logistics;

import common.cn.kafei.simukraft.logistics.LogisticsInventoryEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("null")
final class LogisticsItemFilterGrid {
    private static final int COLS = 9;
    private static final int ROWS = 4;
    private static final int SLOT = 18;
    private static final float COUNT_SCALE = 0.65F;

    private List<LogisticsInventoryEntry> items;
    private final Set<String> selectedItemIds = new LinkedHashSet<>();
    private int scrollRow;

    LogisticsItemFilterGrid(List<LogisticsInventoryEntry> items) {
        this.items = items != null ? List.copyOf(items) : List.of();
    }

    /** width: 返回过滤网格的固定宽度。 */
    int width() {
        return COLS * SLOT + 4;
    }

    /** height: 返回过滤网格的固定高度。 */
    int height() {
        return ROWS * SLOT + 14;
    }

    /** selectedItemIds: 返回当前选中的物品 ID。 */
    List<String> selectedItemIds() {
        return List.copyOf(selectedItemIds);
    }

    /** setItems: 替换当前可选物品，并移除不再可见的旧选择。 */
    void setItems(List<LogisticsInventoryEntry> nextItems) {
        items = nextItems != null ? List.copyOf(nextItems) : List.of();
        Set<String> visibleItemIds = items.stream()
                .map(LogisticsInventoryEntry::itemId)
                .collect(java.util.stream.Collectors.toSet());
        selectedItemIds.removeIf(itemId -> !visibleItemIds.contains(itemId));
        scrollRow = LogisticsNativeStyle.clamp(scrollRow, 0, maxScrollRow());
    }

    /** render: 绘制物品过滤网格、选中框和悬浮提示。 */
    void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        graphics.fill(x - 2, y - 2, x + COLS * SLOT + 2, y + ROWS * SLOT + 2, 0xFF10101C);
        int start = scrollRow * COLS;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slotX = x + col * SLOT;
                int slotY = y + row * SLOT;
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF2A2A4A);
                int index = start + row * COLS + col;
                if (index >= items.size()) {
                    continue;
                }
                LogisticsInventoryEntry entry = items.get(index);
                ItemStack stack = LogisticsItemDisplayName.stackFor(entry.itemId());
                graphics.renderItem(stack, slotX, slotY);
                drawCount(graphics, font, slotX, slotY, entry.count());
                if (selectedItemIds.contains(entry.itemId())) {
                    drawSelection(graphics, slotX, slotY);
                }
            }
        }
        graphics.drawString(font, Component.translatable("gui.simukraft.logistics.channel.selected_count", selectedItemIds.size()),
                x, y + ROWS * SLOT + 4, LogisticsNativeStyle.TEXT, true);
        renderTooltip(graphics, font, x, y, mouseX, mouseY);
    }

    /** mouseClicked: 点击物品格时切换过滤选中状态。 */
    boolean mouseClicked(double mouseX, double mouseY, int button, int x, int y) {
        if (button != 0 || !contains(mouseX, mouseY, x, y)) {
            return false;
        }
        int index = hoveredIndex(mouseX, mouseY, x, y);
        if (index >= 0 && index < items.size()) {
            String itemId = items.get(index).itemId();
            if (!selectedItemIds.remove(itemId)) {
                selectedItemIds.add(itemId);
            }
            return true;
        }
        return false;
    }

    /** mouseScrolled: 鼠标滚轮按行滚动物品过滤网格。 */
    boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount, int x, int y) {
        if (!contains(mouseX, mouseY, x, y)) {
            return false;
        }
        int max = maxScrollRow();
        if (max > 0) {
            scrollRow = LogisticsNativeStyle.clamp(scrollRow + (verticalAmount > 0 ? -1 : 1), 0, max);
        }
        return true;
    }

    /** contains: 判断鼠标是否位于过滤网格内部。 */
    private boolean contains(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + COLS * SLOT && mouseY >= y && mouseY < y + ROWS * SLOT;
    }

    /** hoveredIndex: 计算当前悬浮的过滤物品索引。 */
    private int hoveredIndex(double mouseX, double mouseY, int x, int y) {
        if (!contains(mouseX, mouseY, x, y)) {
            return -1;
        }
        int col = (int) ((mouseX - x) / SLOT);
        int row = (int) ((mouseY - y) / SLOT);
        return scrollRow * COLS + row * COLS + col;
    }

    /** renderTooltip: 显示物品名称和 ID，便于区分同名物品。 */
    private void renderTooltip(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int index = hoveredIndex(mouseX, mouseY, x, y);
        if (index < 0 || index >= items.size()) {
            return;
        }
        LogisticsInventoryEntry entry = items.get(index);
        graphics.renderComponentTooltip(font, List.of(
                LogisticsItemDisplayName.stackFor(entry.itemId()).getHoverName(),
                Component.translatable("gui.simukraft.logistics.grid.count", Math.max(1, entry.count())),
                Component.literal(entry.itemId())
        ), mouseX, mouseY);
    }

    /** maxScrollRow: 计算过滤网格最大滚动行。 */
    private int maxScrollRow() {
        int rows = Math.max(0, (int) Math.ceil(items.size() / (double) COLS));
        return Math.max(0, rows - ROWS);
    }

    /** drawSelection: 绘制旧版橙色选中边框。 */
    private static void drawSelection(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y, 0xFFFF8800);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, 0xFFFF8800);
        graphics.fill(x - 1, y, x, y + 16, 0xFFFF8800);
        graphics.fill(x + 16, y, x + 17, y + 16, 0xFFFF8800);
    }

    /** drawCount: 在图标右下角上层绘制小号数量。 */
    private static void drawCount(GuiGraphics graphics, Font font, int x, int y, int count) {
        if (count <= 1) {
            return;
        }
        String text = formatCount(count);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.pose().scale(COUNT_SCALE, COUNT_SCALE, 1.0F);
        int textX = Math.round((x + 17 - font.width(text) * COUNT_SCALE) / COUNT_SCALE);
        int textY = Math.round((y + 10) / COUNT_SCALE);
        graphics.drawString(font, text, textX, textY, LogisticsNativeStyle.TEXT, true);
        graphics.pose().popPose();
    }

    /** formatCount: 将库存数量压缩成过滤格可读的短文本。 */
    private static String formatCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        return count < 1_000_000 ? (count / 1000) + "k" : (count / 1_000_000) + "M";
    }

}
