package client.cn.kafei.simukraft.client.logistics;

import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.selection.TwoPointSelectionScreen;
import common.cn.kafei.simukraft.logistics.LogisticsConstants;
import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
import common.cn.kafei.simukraft.logistics.LogisticsDirection;
import common.cn.kafei.simukraft.network.logistics.LogisticsBoxActionPacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsServerBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsServerBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsWarehouseGridOpenRequestPacket;
import common.cn.kafei.simukraft.network.npc.hire.NpcHireFirePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class LogisticsServerBoxScreenOpener {
    private static ActiveTab activeTab = ActiveTab.OVERVIEW;

    private LogisticsServerBoxScreenOpener() {
    }

    /** pushWarehouseItems: 把服务端仓库快照推给当前旧版仓库页。 */
    public static void pushWarehouseItems(BlockPos pos, List<net.minecraft.world.item.ItemStack> items, List<Integer> counts) {
        LogisticsWarehouseGridScreen.receiveIfOpen(pos, items, counts);
    }

    /** request: 请求打开旧版服务端主界面。 */
    public static void request(BlockPos pos) {
        activeTab = ActiveTab.OVERVIEW;
        PacketDistributor.sendToServer(new LogisticsServerBoxOpenRequestPacket(pos));
    }

    /** requestMap: 请求打开旧版地图 Tab。 */
    public static void requestMap(BlockPos pos) {
        activeTab = ActiveTab.MAP;
        PacketDistributor.sendToServer(new LogisticsServerBoxOpenRequestPacket(pos));
    }

    /** requestManage: 请求打开旧版仓库总览 Tab。 */
    public static void requestManage(BlockPos pos) {
        activeTab = ActiveTab.OVERVIEW;
        PacketDistributor.sendToServer(new LogisticsServerBoxOpenRequestPacket(pos));
    }

    /** open: 接收服务端快照并打开原生 Screen。 */
    public static void open(LogisticsServerBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> minecraft.setScreen(new LogisticsServerBoxScreen(packet, activeTab)));
        }
    }

    private enum ActiveTab {
        OVERVIEW,
        MAP,
        ROUTES
    }

    private static final class LogisticsServerBoxScreen extends Screen {
        private static final int TAB_WIDTH = 86;
        private static final int TAB_HEIGHT = 24;
        private static final int TAB_X = 5;

        private static final int ROUTE_ROW_H = 48;
        private static final int SCROLLBAR_W = 6;
        /** savedRouteScrollRow: 静态保存路由列表滚动行，服务端刷新重建界面后仍保持位置。 */
        private static int savedRouteScrollRow = 0;

        private final LogisticsServerBoxOpenResponsePacket packet;
        private ActiveTab currentTab;
        private int routeScrollRow;

        private LogisticsServerBoxScreen(LogisticsServerBoxOpenResponsePacket packet, ActiveTab currentTab) {
            super(Component.translatable("gui.simukraft.logistics.server.title"));
            this.packet = packet;
            this.currentTab = currentTab;
            this.routeScrollRow = savedRouteScrollRow;
        }

        /** routeViewportTop: 路由列表首行顶部 Y。 */
        private int routeViewportTop() {
            return 60;
        }

        /** routeViewportBottom: 路由列表可视区底部 Y。 */
        private int routeViewportBottom() {
            return this.height - 8;
        }

        /** routeVisibleRows: 可视区能完整容纳的频道行数。 */
        private int routeVisibleRows() {
            return Math.max(1, (routeViewportBottom() - routeViewportTop()) / ROUTE_ROW_H);
        }

        /** maxRouteScroll: 最大滚动行（顶端行索引上限）。 */
        private int maxRouteScroll() {
            return Math.max(0, packet.channels().size() - routeVisibleRows());
        }

        /** clampRouteScroll: 约束滚动行并同步到静态保存值。 */
        private void clampRouteScroll() {
            routeScrollRow = Math.max(0, Math.min(maxRouteScroll(), routeScrollRow));
            savedRouteScrollRow = routeScrollRow;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (currentTab == ActiveTab.ROUTES && maxRouteScroll() > 0) {
                int panelR = this.width - 6;
                int sbX = panelR - SCROLLBAR_W - 1;
                int panelT = routeViewportTop() - 6;
                int panelB = routeViewportBottom() + 4;
                if (mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= panelT + 1 && mouseY < panelB - 1) {
                    int trackH = panelB - 2 - (panelT + 1);
                    int thumbH = Math.max(8, trackH * routeVisibleRows() / Math.max(1, packet.channels().size()));
                    double rel = (mouseY - panelT - 1 - thumbH / 2.0) / Math.max(1, trackH - thumbH);
                    routeScrollRow = (int) Math.round(rel * maxRouteScroll());
                    clampRouteScroll();
                    rebuildUI();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (currentTab == ActiveTab.ROUTES && verticalAmount != 0 && maxRouteScroll() > 0) {
                routeScrollRow -= (int) Math.signum(verticalAmount);
                clampRouteScroll();
                rebuildUI();
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        /** init: 重建左侧 Tab 和当前内容页按钮。 */
        @Override
        protected void init() {
            rebuildUI();
        }

        /** renderBackground: 绘制旧版深色背景。 */
        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            LogisticsNativeStyle.drawBackdrop(graphics, this.width, this.height);
        }

        /** render: 绘制标题、左侧栏、分隔线和当前 Tab 文本。 */
        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.server.title"), TAB_X, 10, LogisticsNativeStyle.TEXT, true);
            LogisticsNativeStyle.drawPanel(graphics, TAB_X - 2, 26, TAB_WIDTH + 4, this.height - 31);
            int lineX = TAB_X + TAB_WIDTH + 6;
            graphics.fill(lineX, 26, lineX + 1, this.height - 5, LogisticsNativeStyle.PANEL_LINE);
            int contentX = lineX + 8;
            int contentY = 30;
            switch (currentTab) {
                case OVERVIEW -> renderOverview(graphics, contentX + 164, contentY);
                case MAP -> renderMapTab(graphics, contentX, contentY);
                case ROUTES -> renderRoutes(graphics, contentX, contentY);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        /** rebuildUI: 清空并重建所有按钮，防止刷新后重复绑定。 */
        private void rebuildUI() {
            clearWidgets();
            int tabY = 30;
            addTab(Component.translatable("gui.simukraft.logistics.server.tab.overview"), ActiveTab.OVERVIEW, tabY);
            addTab(Component.translatable("gui.simukraft.logistics.server.tab.map"), ActiveTab.MAP, tabY + TAB_HEIGHT + 2);
            addTab(Component.translatable("gui.simukraft.logistics.server.tab.routes"), ActiveTab.ROUTES, tabY + (TAB_HEIGHT + 2) * 2);
            int contentX = TAB_X + TAB_WIDTH + 15;
            int contentY = 30;
            switch (currentTab) {
                case OVERVIEW -> buildOverviewButtons(contentX, contentY);
                case MAP -> buildMapButtons(contentX, contentY);
                case ROUTES -> buildRouteButtons(contentX, contentY);
            }
        }

        /** addTab: 创建左侧旧版 Tab 按钮。 */
        private void addTab(Component label, ActiveTab tab, int y) {
            String prefix = currentTab == tab ? "> " : "";
            Button button = addRenderableWidget(LogisticsNativeStyle.button(Component.literal(prefix).append(label),
                    TAB_X, y, TAB_WIDTH, TAB_HEIGHT, () -> {
                        currentTab = tab;
                        activeTab = tab;
                        rebuildUI();
                    }));
            button.active = currentTab != tab;
        }

        /** buildOverviewButtons: 创建仓库总览页按钮。 */
        private void buildOverviewButtons(int x, int y) {
            int buttonWidth = 150;
            int buttonHeight = 20;
            int gap = 24;
            boolean hasWarehouse = hasWarehouse();
            Button hire = addRenderableWidget(LogisticsNativeStyle.button(packet.hasWorker()
                    ? Component.translatable("gui.simukraft.logistics.worker_line", packet.workerName())
                    : Component.translatable("gui.simukraft.logistics.hire_storage"), x, y, buttonWidth, buttonHeight,
                    () -> NpcHireScreen.request(packet.boxPos(), LogisticsConstants.SERVER_SOURCE_TYPE, LogisticsConstants.STORAGE_ROLE)));
            hire.active = packet.hasCity() && !packet.hasWorker();
            Button fire = addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.fire_storage"),
                    x, y + gap, buttonWidth, buttonHeight, this::fireWorker));
            fire.active = packet.hasWorker() && packet.workerId() != null;
            Button create = addRenderableWidget(LogisticsNativeStyle.button(hasWarehouse
                    ? Component.translatable("gui.simukraft.logistics.create_warehouse.count", packet.containers().size())
                    : Component.translatable("gui.simukraft.logistics.create_warehouse"), x, y + gap * 2, buttonWidth, buttonHeight,
                    () -> TwoPointSelectionScreen.openLogistics(packet.boxPos(), LogisticsBoxActionPacket.Action.BIND_WAREHOUSE_AREA)));
            create.active = packet.hasCity() && !hasWarehouse;
            Button delete = addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.delete_warehouse"),
                    x, y + gap * 3, buttonWidth, buttonHeight,
                    () -> send(LogisticsBoxActionPacket.Action.DELETE_WAREHOUSE, null, null, BlockPos.ZERO, "", LogisticsDirection.WAREHOUSE_TO_CLIENT, List.of())));
            delete.active = hasWarehouse;
            Button manage = addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.inventory"),
                    x, y + gap * 4, buttonWidth, buttonHeight,
                    () -> PacketDistributor.sendToServer(new LogisticsWarehouseGridOpenRequestPacket(packet.boxPos()))));
            manage.active = hasWarehouse;
        }

        /** buildMapButtons: 创建地图页打开按钮。 */
        private void buildMapButtons(int x, int y) {
            Button openMap = addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.server.tab.map"),
                    x, y + 44, 120, 20, () -> LogisticsNetworkMapScreen.open(packet)));
            openMap.active = hasWarehouse();
        }

        /** buildRouteButtons: 创建路径管理按钮（支持滚动 + 发送/接收双端保有量）。 */
        private void buildRouteButtons(int x, int y) {
            Button addRoute = addRenderableWidget(LogisticsNativeStyle.button(Component.literal("+ ").append(Component.translatable("gui.simukraft.logistics.channel.create")),
                    x + 120, y - 2, 112, 18, () -> LogisticsChannelCreateScreenOpener.open(packet, null)));
            addRoute.active = hasWarehouse() && !packet.clients().isEmpty();
            clampRouteScroll();
            List<LogisticsControlBoxService.ChannelEntry> channels = packet.channels();
            int top = routeViewportTop();
            int last = Math.min(channels.size(), routeScrollRow + routeVisibleRows());
            for (int i = routeScrollRow; i < last; i++) {
                LogisticsControlBoxService.ChannelEntry channel = channels.get(i);
                UUID channelId = channel.channelId();
                int rowY = top + (i - routeScrollRow) * ROUTE_ROW_H;
                addRenderableWidget(LogisticsNativeStyle.button(Component.translatable(channel.enabled()
                                ? "gui.simukraft.logistics.channel.disable"
                                : "gui.simukraft.logistics.channel.enable"),
                        x + 240, rowY, 36, 16,
                        () -> send(LogisticsBoxActionPacket.Action.TOGGLE_CHANNEL, null, channelId, BlockPos.ZERO, "", LogisticsDirection.WAREHOUSE_TO_CLIENT, List.of())));
                addRenderableWidget(LogisticsNativeStyle.button(Component.literal("x"),
                        x + 280, rowY, 24, 16,
                        () -> send(LogisticsBoxActionPacket.Action.DELETE_CHANNEL, null, channelId, BlockPos.ZERO, "", LogisticsDirection.WAREHOUSE_TO_CLIENT, List.of())));
                final net.minecraft.client.gui.components.EditBox srcBox = keepBox(x + 64, rowY + 26, channel.keepSourceQuantity());
                final net.minecraft.client.gui.components.EditBox tgtBox = keepBox(x + 170, rowY + 26, channel.keepTargetQuantity());
                addRenderableWidget(srcBox);
                addRenderableWidget(tgtBox);
                addRenderableWidget(LogisticsNativeStyle.button(
                        Component.translatable("gui.simukraft.logistics.channel.keep_apply"),
                        x + 216, rowY + 26, 60, 12,
                        () -> send(LogisticsBoxActionPacket.Action.SET_CHANNEL_KEEP_QUANTITY, null, channelId, BlockPos.ZERO,
                                keepValue(srcBox) + "|" + keepValue(tgtBox), LogisticsDirection.WAREHOUSE_TO_CLIENT, List.of())));
            }
        }

        /** keepBox: 创建只接受数字的保有量输入框。 */
        private net.minecraft.client.gui.components.EditBox keepBox(int x, int y, int value) {
            net.minecraft.client.gui.components.EditBox box = new net.minecraft.client.gui.components.EditBox(this.font, x, y, 38, 12, Component.empty());
            box.setValue(String.valueOf(Math.max(0, value)));
            box.setMaxLength(7);
            box.setFilter(s -> s.matches("\\d*"));
            return box;
        }

        /** keepValue: 读取保有量输入框的非负整数值，空白按 0。 */
        private int keepValue(net.minecraft.client.gui.components.EditBox box) {
            String val = box.getValue();
            if (val == null || val.isBlank()) {
                return 0;
            }
            try {
                return Math.max(0, Integer.parseInt(val));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        /** renderOverview: 绘制仓库状态和费用说明。 */
        private void renderOverview(GuiGraphics graphics, int x, int y) {
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.server.tab.overview"), x, y, LogisticsNativeStyle.TEXT_WARN);
            y += 14;
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.city_line", packet.hasCity() ? packet.cityName() : "-"), x, y, LogisticsNativeStyle.TEXT_DIM);
            y += 11;
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.funds_line", String.format(Locale.ROOT, "%.2f", packet.cityBalance())), x, y, LogisticsNativeStyle.TEXT_DIM);
            y += 11;
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.worker_line", packet.hasWorker() ? packet.workerName() : "-"), x, y, LogisticsNativeStyle.TEXT_DIM);
            y += 11;
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.container_count", packet.containers().size()), x, y, LogisticsNativeStyle.TEXT_DIM);
            y += 11;
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel_count", packet.channels().size()), x, y, LogisticsNativeStyle.TEXT_DIM);
            y += 22;
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.transfer_cost_hint"), x, y, LogisticsNativeStyle.TEXT_WARN);
            y += 14;
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.containers"), x, y, LogisticsNativeStyle.TEXT);
            y += 12;
            if (packet.containers().isEmpty()) {
                graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.empty"), x, y, LogisticsNativeStyle.TEXT_MUTED);
            } else {
                for (BlockPos container : packet.containers()) {
                    graphics.drawString(this.font, LogisticsNativeStyle.posText(container), x + 8, y, LogisticsNativeStyle.TEXT_DIM);
                    y += 10;
                    if (y > this.height - 12) {
                        break;
                    }
                }
            }
        }

        /** renderMapTab: 绘制旧版地图页提示。 */
        private void renderMapTab(GuiGraphics graphics, int x, int y) {
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.server.tab.map"), x, y, LogisticsNativeStyle.TEXT_WARN);
            y += 16;
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.map.hint"), x, y, LogisticsNativeStyle.TEXT_DIM);
        }

        /** renderRoutes: 绘制路径列表（与 buildRouteButtons 共用滚动窗口）。 */
        private void renderRoutes(GuiGraphics graphics, int x, int y) {
            List<LogisticsControlBoxService.ChannelEntry> channels = packet.channels();
            Component header = Component.translatable("gui.simukraft.logistics.server.tab.routes")
                    .append(Component.literal(" (" + channels.size() + ")"));
            graphics.drawString(this.font, header, x, y, LogisticsNativeStyle.TEXT_WARN);
            int panelL = x - 6;
            int panelT = routeViewportTop() - 6;
            int panelR = this.width - 6;
            int panelB = routeViewportBottom() + 4;
            if (channels.isEmpty()) {
                graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.empty"), x, y + 30, LogisticsNativeStyle.TEXT_MUTED);
                return;
            }
            int top = routeViewportTop();
            int visible = routeVisibleRows();
            int last = Math.min(channels.size(), routeScrollRow + visible);
            graphics.enableScissor(panelL + 2, panelT + 1, panelR - SCROLLBAR_W - 2, panelB - 1);
            for (int i = routeScrollRow; i < last; i++) {
                LogisticsControlBoxService.ChannelEntry channel = channels.get(i);
                int rowY = top + (i - routeScrollRow) * ROUTE_ROW_H;
                String direction = channel.direction() == LogisticsDirection.CLIENT_TO_WAREHOUSE ? "<-" : "->";
                LogisticsNativeStyle.drawStatusBadge(graphics, this.font, channel.enabled(), x, rowY - 1);
                LogisticsNativeStyle.drawFitString(graphics, this.font, direction + " " + LogisticsItemDisplayName.channelName(channel.name(), channel.filters()),
                        x + 31, rowY, 198, LogisticsNativeStyle.TEXT);
                LogisticsNativeStyle.drawFitString(graphics, this.font, clientName(channel.clientId()) + " | " + LogisticsItemDisplayName.filterText(channel.filters()),
                        x + 10, rowY + 11, 225, LogisticsNativeStyle.TEXT_DIM);
                graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.keep_source"), x + 10, rowY + 28, LogisticsNativeStyle.TEXT_DIM);
                graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.channel.keep_target"), x + 116, rowY + 28, LogisticsNativeStyle.TEXT_DIM);
            }
            graphics.disableScissor();
            int sbX = panelR - SCROLLBAR_W - 1;
            int sbTop = panelT + 1;
            int sbBot = panelB - 1;
            int trackH = sbBot - sbTop;
            int total = channels.size();
            int thumbH = Math.max(8, trackH * visible / Math.max(1, total));
            int thumbY = sbTop + (maxRouteScroll() > 0 ? (trackH - thumbH) * routeScrollRow / maxRouteScroll() : 0);
            graphics.fill(sbX, sbTop, sbX + SCROLLBAR_W, sbBot, 0xAA222244);
            graphics.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, 0xCC6666AA);
        }

        /** send: 发送物流服务端盒动作包。 */
        private void send(LogisticsBoxActionPacket.Action action, UUID clientId, UUID channelId, BlockPos targetPos,
                          String value, LogisticsDirection direction, List<String> filters) {
            PacketDistributor.sendToServer(new LogisticsBoxActionPacket(packet.boxPos(), action, clientId, channelId, targetPos, value, direction,
                    BlockPos.ZERO, BlockPos.ZERO, filters));
        }

        /** fireWorker: 解雇当前仓储管理员。 */
        private void fireWorker() {
            if (packet.hasWorker() && packet.workerId() != null) {
                PacketDistributor.sendToServer(new NpcHireFirePacket(packet.boxPos(), LogisticsConstants.SERVER_SOURCE_TYPE,
                        LogisticsConstants.STORAGE_ROLE, packet.workerId()));
            }
        }

        /** hasWarehouse: 判断当前服务端盒是否已绑定仓库容器。 */
        private boolean hasWarehouse() {
            return !packet.containers().isEmpty();
        }

        /** clientName: 按客户端 ID 获取显示名。 */
        private String clientName(UUID clientId) {
            return packet.clients().stream()
                    .filter(client -> client.clientId().equals(clientId))
                    .map(LogisticsControlBoxService.ClientEntry::name)
                    .findFirst()
                    .orElse("-");
        }

        /** filterText: 格式化频道物品过滤器。 */
    }
}
