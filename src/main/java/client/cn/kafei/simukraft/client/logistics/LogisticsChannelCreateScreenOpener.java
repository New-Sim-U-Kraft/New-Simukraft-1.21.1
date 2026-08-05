package client.cn.kafei.simukraft.client.logistics;

import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
import common.cn.kafei.simukraft.logistics.LogisticsDirection;
import common.cn.kafei.simukraft.logistics.LogisticsInventoryEntry;
import common.cn.kafei.simukraft.network.logistics.LogisticsBoxActionPacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsServerBoxOpenResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class LogisticsChannelCreateScreenOpener {
    private LogisticsChannelCreateScreenOpener() {
    }

    /** open: 打开旧版物流线路创建界面。 */
    public static void open(LogisticsServerBoxOpenResponsePacket packet, UUID preselectedClientId) {
        open(packet, preselectedClientId, LogisticsDirection.WAREHOUSE_TO_CLIENT);
    }

    /** open: 打开线路创建界面并预选客户端与方向。 */
    public static void open(LogisticsServerBoxOpenResponsePacket packet, UUID preselectedClientId, LogisticsDirection direction) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> minecraft.setScreen(new LogisticsChannelCreateScreen(packet, preselectedClientId, direction)));
        }
    }

    private static final class LogisticsChannelCreateScreen extends Screen {
        private final LogisticsServerBoxOpenResponsePacket packet;
        private final LogisticsItemFilterGrid itemGrid;
        private UUID selectedClientId;
        private LogisticsDirection direction;
        private EditBox nameField;
        private String lastAutoName = "";
        private int clientScrollOffset = 0;
        private static final int CLIENT_LIST_TOP = 52;
        private static final int CLIENT_LIST_BOTTOM = 196;
        private static final int CLIENT_ITEM_HEIGHT = 22;
        private static final int CLIENT_LIST_VISIBLE = (CLIENT_LIST_BOTTOM - CLIENT_LIST_TOP) / CLIENT_ITEM_HEIGHT;

        private LogisticsChannelCreateScreen(LogisticsServerBoxOpenResponsePacket packet, UUID preselectedClientId, LogisticsDirection direction) {
            super(Component.translatable("gui.simukraft.logistics.channel.create_title"));
            this.packet = packet;
            this.selectedClientId = preselectedClientId;
            this.direction = direction != null ? direction : LogisticsDirection.WAREHOUSE_TO_CLIENT;
            this.itemGrid = new LogisticsItemFilterGrid(filterItems());
        }

        /** init: 创建名称输入框、客户端选择和方向按钮。 */
        @Override
        protected void init() {
            rebuildWidgets("");
        }

        /** renderBackground: 绘制旧版半透明背景。 */
        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            LogisticsNativeStyle.drawBackdrop(graphics, this.width, this.height);
        }

        /** render: 绘制创建线路表单和物品过滤网格。 */
        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            Layout layout = layout();
            LogisticsNativeStyle.drawPanel(graphics, layout.x(), layout.y(), layout.width(), layout.height());
            graphics.drawCenteredString(this.font, this.title, layout.x() + layout.width() / 2, layout.y() + 10, LogisticsNativeStyle.TEXT);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.client"), layout.x() + 10, layout.y() + 34, LogisticsNativeStyle.TEXT_WARN);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.name"), layout.x() + 176, layout.y() + 34, LogisticsNativeStyle.TEXT_WARN);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.direction"), layout.x() + 176, layout.y() + 76, LogisticsNativeStyle.TEXT_WARN);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.items"), layout.x() + 176, layout.y() + 116, LogisticsNativeStyle.TEXT_WARN);
            renderSelectedClientHint(graphics, layout.x() + 10, layout.y() + 206);
            renderClientScrollbar(graphics, layout);
            itemGrid.render(graphics, this.font, layout.gridX(), layout.gridY(), mouseX, mouseY);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        /** mouseClicked: 优先处理过滤网格点击。 */
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            Layout layout = layout();
            if (itemGrid.mouseClicked(mouseX, mouseY, button, layout.gridX(), layout.gridY())) {
                autoFillNameIfUntouched();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        /** mouseScrolled: 处理客户端列表和过滤网格滚动。 */
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            Layout layout = layout();
            if (mouseX >= layout.x() && mouseX <= layout.x() + 166
                    && mouseY >= layout.y() + CLIENT_LIST_TOP && mouseY <= layout.y() + CLIENT_LIST_BOTTOM) {
                int maxOffset = Math.max(0, packet.clients().size() - CLIENT_LIST_VISIBLE);
                clientScrollOffset = Math.clamp((int) (clientScrollOffset - verticalAmount), 0, maxOffset);
                rebuildWidgets(nameField != null ? nameField.getValue() : "");
                return true;
            }
            if (itemGrid.mouseScrolled(mouseX, mouseY, verticalAmount, layout.gridX(), layout.gridY())) {
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        /** rebuildWidgets: 重建按钮并保留名称输入内容。 */
        private void rebuildWidgets(String previousName) {
            clearWidgets();
            Layout layout = layout();
            boolean autoName = canAutoReplaceName(previousName);
            String name = autoName ? suggestedChannelName() : previousName;
            nameField = new EditBox(this.font, layout.x() + 176, layout.y() + 50, 190, 20, Component.empty());
            nameField.setMaxLength(64);
            nameField.setValue(name);
            if (autoName) {
                lastAutoName = name;
            }
            addRenderableWidget(nameField);
            addClientButtons(layout);
            addDirectionButtons(layout);
            int buttonY = layout.y() + layout.height() - 24;
            addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.channel.confirm"),
                    layout.x() + layout.width() - 174, buttonY, 78, 20, this::confirm));
            addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.channel.cancel"),
                    layout.x() + layout.width() - 90, buttonY, 78, 20,
                    () -> LogisticsServerBoxScreenOpener.open(packet)));
        }

        /** addClientButtons: 创建目标客户端选择按钮（支持滚动）。 */
        private void addClientButtons(Layout layout) {
            List<LogisticsControlBoxService.ClientEntry> clients = packet.clients();
            int maxOffset = Math.max(0, clients.size() - CLIENT_LIST_VISIBLE);
            clientScrollOffset = Math.clamp(clientScrollOffset, 0, maxOffset);
            int x = layout.x() + 10;
            int y = layout.y() + CLIENT_LIST_TOP;
            for (int i = clientScrollOffset; i < clients.size() && y <= layout.y() + CLIENT_LIST_BOTTOM - CLIENT_ITEM_HEIGHT; i++) {
                LogisticsControlBoxService.ClientEntry client = clients.get(i);
                UUID clientId = client.clientId();
                String prefix = clientId.equals(selectedClientId) ? "> " : "";
                addRenderableWidget(LogisticsNativeStyle.button(Component.literal(prefix + client.name() + " [" + client.portCount() + "]"),
                        x, y, 150, 20, () -> {
                            selectedClientId = clientId;
                            refreshFilterItems();
                            rebuildWidgets(nameField != null ? nameField.getValue() : "");
                        }));
                y += CLIENT_ITEM_HEIGHT;
            }
        }

        /** addDirectionButtons: 创建发送/接收方向按钮。 */
        private void addDirectionButtons(Layout layout) {
            addRenderableWidget(LogisticsNativeStyle.button(direction == LogisticsDirection.WAREHOUSE_TO_CLIENT
                            ? Component.literal("> ").append(Component.translatable("gui.simukraft.logistics.channel.direction.send"))
                            : Component.translatable("gui.simukraft.logistics.channel.direction.send"),
                    layout.x() + 176, layout.y() + 92, 88, 20, () -> setDirection(LogisticsDirection.WAREHOUSE_TO_CLIENT)));
            addRenderableWidget(LogisticsNativeStyle.button(direction == LogisticsDirection.CLIENT_TO_WAREHOUSE
                            ? Component.literal("> ").append(Component.translatable("gui.simukraft.logistics.channel.direction.receive"))
                            : Component.translatable("gui.simukraft.logistics.channel.direction.receive"),
                    layout.x() + 270, layout.y() + 92, 88, 20, () -> setDirection(LogisticsDirection.CLIENT_TO_WAREHOUSE)));
        }

        /** setDirection: 切换线路传输方向。 */
        private void setDirection(LogisticsDirection direction) {
            this.direction = direction;
            refreshFilterItems();
            rebuildWidgets(nameField != null ? nameField.getValue() : "");
        }

        /** renderSelectedClientHint: 绘制当前选择的客户端提示。 */
        private void renderSelectedClientHint(GuiGraphics graphics, int x, int y) {
            LogisticsControlBoxService.ClientEntry client = selectedClient();
            Component text = client == null
                    ? Component.translatable("gui.simukraft.logistics.channel.need_client")
                    : Component.literal(client.name() + " " + LogisticsNativeStyle.posText(client.boxPos()));
            LogisticsNativeStyle.drawFitString(graphics, this.font, text, x, y, 150, client == null ? LogisticsNativeStyle.TEXT_BAD : LogisticsNativeStyle.TEXT_GOOD);
        }

        /** renderClientScrollbar: 当客户端列表超出可见区时绘制滚动条。 */
        private void renderClientScrollbar(GuiGraphics graphics, Layout layout) {
            int total = packet.clients().size();
            if (total <= CLIENT_LIST_VISIBLE) return;
            int sbX = layout.x() + 163;
            int sbTop = layout.y() + CLIENT_LIST_TOP;
            int sbHeight = CLIENT_LIST_BOTTOM - CLIENT_LIST_TOP;
            int thumbH = Math.max(10, sbHeight * CLIENT_LIST_VISIBLE / total);
            int maxOffset = total - CLIENT_LIST_VISIBLE;
            int thumbY = sbTop + (sbHeight - thumbH) * clientScrollOffset / maxOffset;
            graphics.fill(sbX, sbTop, sbX + 3, sbTop + sbHeight, 0xFF444444);
            graphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFFAAAAAA);
        }

        /** confirm: 校验客户端和过滤物品后发送创建频道请求。 */
        private void confirm() {
            if (selectedClientId == null) {
                ClientInfoToast.show(Component.translatable("toast.simukraft.title"), Component.translatable("gui.simukraft.logistics.channel.need_client"), "warning");
                return;
            }
            List<String> filters = itemGrid.selectedItemIds();
            if (filters.isEmpty()) {
                ClientInfoToast.show(Component.translatable("toast.simukraft.title"), Component.translatable("gui.simukraft.logistics.channel.need_items"), "warning");
                return;
            }
            String name = nameField != null ? nameField.getValue() : "";
            if (canAutoReplaceName(name)) {
                name = suggestedChannelName();
            }
            PacketDistributor.sendToServer(new LogisticsBoxActionPacket(packet.boxPos(),
                    LogisticsBoxActionPacket.Action.ADD_CHANNEL,
                    selectedClientId,
                    null,
                    BlockPos.ZERO,
                    name,
                    direction,
                    BlockPos.ZERO,
                    BlockPos.ZERO,
                    filters));
            Minecraft.getInstance().setScreen(null);
        }

        /** selectedClient: 返回当前选中的客户端数据。 */
        private LogisticsControlBoxService.ClientEntry selectedClient() {
            if (selectedClientId == null) {
                return null;
            }
            return packet.clients().stream()
                    .filter(client -> selectedClientId.equals(client.clientId()))
                    .findFirst()
                    .orElse(null);
        }

        /** refreshFilterItems: 按当前方向和客户端刷新过滤物品来源。 */
        private void refreshFilterItems() {
            itemGrid.setItems(filterItems());
        }

        /** filterItems: 发送时显示仓库库存，接收时显示选中客户端端口库存。 */
        private List<LogisticsInventoryEntry> filterItems() {
            if (direction == LogisticsDirection.CLIENT_TO_WAREHOUSE) {
                return selectedClientInventory();
            }
            return packet.inventory();
        }

        /** autoFillNameIfUntouched: 物品选择变化时自动填入客户端翻译名。 */
        private void autoFillNameIfUntouched() {
            if (nameField == null || !canAutoReplaceName(nameField.getValue())) {
                return;
            }
            lastAutoName = suggestedChannelName();
            nameField.setValue(lastAutoName);
        }

        /** suggestedChannelName: 根据已选过滤物品生成默认线路名。 */
        private String suggestedChannelName() {
            List<String> filters = itemGrid.selectedItemIds();
            if (filters.isEmpty()) {
                return Component.translatable("gui.simukraft.logistics.channel.default_name").getString();
            }
            return LogisticsItemDisplayName.filterText(filters);
        }

        /** canAutoReplaceName: 判断当前名称是否仍属于自动生成内容。 */
        private boolean canAutoReplaceName(String name) {
            String defaultName = Component.translatable("gui.simukraft.logistics.channel.default_name").getString();
            return name == null || name.isBlank() || name.equals(defaultName)
                    || name.equals("物流线路") || name.equals("Logistics Route") || name.equals(lastAutoName);
        }

        /** selectedClientInventory: 查找当前选中客户端的端口库存快照。 */
        private List<LogisticsInventoryEntry> selectedClientInventory() {
            if (selectedClientId == null) {
                return List.of();
            }
            return packet.clientInventories().stream()
                    .filter(entry -> selectedClientId.equals(entry.clientId()))
                    .findFirst()
                    .map(LogisticsControlBoxService.ClientInventoryEntry::inventory)
                    .orElse(List.of());
        }

        /** layout: 计算表单位置和尺寸。 */
        private Layout layout() {
            int width = Math.min(410, this.width - 20);
            int height = Math.min(270, this.height - 20);
            int x = (this.width - width) / 2;
            int y = (this.height - height) / 2;
            return new Layout(x, y, width, height, x + 176, y + 132);
        }

        private record Layout(int x, int y, int width, int height, int gridX, int gridY) {
        }
    }
}
