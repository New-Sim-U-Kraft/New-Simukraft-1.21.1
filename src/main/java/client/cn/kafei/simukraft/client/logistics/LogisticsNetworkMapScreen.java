package client.cn.kafei.simukraft.client.logistics;

import client.cn.kafei.simukraft.client.city.map.SimuMapManager;
import client.cn.kafei.simukraft.client.city.map.SimuMapRegion;
import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
import common.cn.kafei.simukraft.logistics.LogisticsDirection;
import common.cn.kafei.simukraft.network.logistics.LogisticsBoxActionPacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsServerBoxOpenResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
final class LogisticsNetworkMapScreen extends Screen {
    private static final double MIN_ZOOM = 0.1D;
    private static final double MAX_ZOOM = 10.0D;

    private final LogisticsServerBoxOpenResponsePacket packet;
    private final SimuMapManager mapManager = SimuMapManager.getInstance();
    private UUID hoveredClientId;
    private boolean hoveredWarehouse;
    private List<HoveredRoute> hoveredRoutes = List.of();
    private UUID selectedClientId;
    private QuickEndpoint quickReceiver;
    private QuickEndpoint quickSender;
    private double zoomLevel = 1.0D;
    private double offsetX;
    private double offsetY;
    private double lastMouseX;
    private double lastMouseY;
    private boolean dragging;
    private boolean mapConsumerAcquired;

    private LogisticsNetworkMapScreen(LogisticsServerBoxOpenResponsePacket packet) {
        super(Component.translatable("gui.simukraft.logistics.server.tab.map"));
        this.packet = packet;
    }

    /** open: 打开旧版全屏物流地图。 */
    static void open(LogisticsServerBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> minecraft.setScreen(new LogisticsNetworkMapScreen(packet)));
        }
    }

    /** init: 初始化地图扫描和底部按钮。 */
    @Override
    protected void init() {
        ensureMapReady();
        centerOnWarehouse();
        rebuildButtons();
    }

    /** renderBackground: 地图已自行绘制全屏底色，禁用原版菜单模糊背景。 */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /** render: 绘制全屏地图、路线、节点、侧边栏和按钮。 */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF0A0A0A);
        int mapWidth = mapWidth();
        updateHover(mouseX, mouseY, mapWidth);
        renderTerrain(graphics, 0, 0, mapWidth, this.height);
        renderChannelLines(graphics, mapWidth);
        renderMarkers(graphics, mapWidth);
        if (selectedClientId != null) {
            renderSidePanel(graphics, mapWidth);
        }
        graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.server.tab.map").getString()
                + " - " + LogisticsNativeStyle.posText(packet.boxPos()), 5, 5, LogisticsNativeStyle.TEXT);
        graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.transfer_cost_hint"), 5, 18, LogisticsNativeStyle.TEXT_DIM);
        LogisticsNativeStyle.drawFitString(graphics, this.font, Component.translatable("gui.simukraft.logistics.map.quick_status",
                endpointLabel(quickReceiver), endpointLabel(quickSender)), 5, 31, mapWidth - 10, LogisticsNativeStyle.TEXT_WARN);
        graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.map.hint"), 5, this.height - 12, LogisticsNativeStyle.TEXT_MUTED);
        renderHoverTooltip(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** mouseClicked: 左键选接收端，右键选发送端，空白处拖拽地图。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 || button == 1) {
            QuickEndpoint clicked = endpointAt(mouseX, mouseY);
            if (clicked != null) {
                if (button == 0) {
                    quickReceiver = clicked;
                    selectedClientId = null;
                } else {
                    quickSender = clicked;
                }
                dragging = false;
                if (!tryOpenQuickCreate()) {
                    rebuildButtons();
                }
                return true;
            }
        }
        if (button != 0) {
            return false;
        }
        if (selectedClientId != null && mouseX < mapWidth()) {
            selectedClientId = null;
            rebuildButtons();
        }
        dragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    /** mouseDragged: 拖拽平移地图。 */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            offsetX += mouseX - lastMouseX;
            offsetY += mouseY - lastMouseY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** mouseReleased: 停止地图拖拽。 */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** mouseScrolled: 以鼠标为中心缩放地图。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double oldZoom = zoomLevel;
        zoomLevel = verticalAmount > 0 ? Math.min(MAX_ZOOM, zoomLevel * 1.2D) : Math.max(MIN_ZOOM, zoomLevel / 1.2D);
        int mapWidth = mapWidth();
        double mouseOffsetX = mouseX - mapWidth / 2.0D;
        double mouseOffsetY = mouseY - this.height / 2.0D;
        double scale = zoomLevel / oldZoom;
        offsetX = offsetX * scale - mouseOffsetX * (scale - 1.0D);
        offsetY = offsetY * scale - mouseOffsetY * (scale - 1.0D);
        return true;
    }

    /** keyPressed: ESC 优先关闭侧边栏，再返回主界面。 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && (quickReceiver != null || quickSender != null)) {
            quickReceiver = null;
            quickSender = null;
            rebuildButtons();
            return true;
        }
        if (keyCode == 256 && selectedClientId != null) {
            selectedClientId = null;
            rebuildButtons();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** removed: 释放 SimuMap 消费者，避免地图纹理长期占用。 */
    @Override
    public void removed() {
        if (mapConsumerAcquired && SimuMapManager.isAvailable()) {
            mapManager.releaseConsumer();
            mapConsumerAcquired = false;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** rebuildButtons: 重建底部按钮和选中节点的路线操作按钮。 */
    private void rebuildButtons() {
        clearWidgets();
        addRenderableWidget(LogisticsNativeStyle.button(Component.literal("+ ").append(Component.translatable("gui.simukraft.logistics.channel.create")),
                5, this.height - 25, 110, 20, () -> LogisticsChannelCreateScreenOpener.open(packet, preferredClientId(), preferredDirection())));
        addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.map.center"),
                120, this.height - 25, 82, 20, this::centerOnWarehouse));
        addRenderableWidget(LogisticsNativeStyle.button(Component.translatable("gui.simukraft.logistics.channel.cancel"),
                207, this.height - 25, 62, 20, () -> LogisticsServerBoxScreenOpener.open(packet)));
        if (selectedClientId == null) {
            return;
        }
        int x = this.width - 180;
        int y = 76;
        for (LogisticsControlBoxService.ChannelEntry channel : selectedChannels()) {
            UUID channelId = channel.channelId();
            addRenderableWidget(LogisticsNativeStyle.button(Component.translatable(channel.enabled()
                    ? "gui.simukraft.logistics.channel.disable"
                    : "gui.simukraft.logistics.channel.enable"), x + 105, y + 8, 32, 14,
                    () -> sendChannelAction(LogisticsBoxActionPacket.Action.TOGGLE_CHANNEL, channelId)));
            addRenderableWidget(LogisticsNativeStyle.button(Component.literal("x"), x + 140, y + 8, 20, 14,
                    () -> sendChannelAction(LogisticsBoxActionPacket.Action.DELETE_CHANNEL, channelId)));
            y += 36;
            if (y > this.height - 45) {
                break;
            }
        }
    }

    /** ensureMapReady: 初始化地图管理器并触发小范围扫描。 */
    private void ensureMapReady() {
        if (!SimuMapManager.isAvailable()) {
            return;
        }
        if (!mapConsumerAcquired) {
            mapManager.init();
            mapManager.acquireConsumer();
            mapConsumerAcquired = true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            int chunkX = minecraft.player.chunkPosition().x;
            int chunkZ = minecraft.player.chunkPosition().z;
            mapManager.forceScanArea(chunkX, chunkZ, Math.min(12, mapManager.getEffectiveScanRadius()));
            mapManager.forceRenderAll();
        }
    }

    /** renderTerrain: 绘制 SimuMap 地形纹理。 */
    private void renderTerrain(GuiGraphics graphics, int startX, int startY, int width, int height) {
        graphics.fill(startX, startY, startX + width, startY + height, 0xFF1A2028);
        if (!SimuMapManager.isAvailable()) {
            return;
        }
        double centerX = startX + width / 2.0D;
        double centerY = startY + height / 2.0D;
        for (SimuMapRegion region : mapManager.getAllRegions()) {
            if (!region.isImageLoaded() && !region.hasData()) {
                continue;
            }
            int textureId = region.getTextureId();
            if (textureId == -1) {
                continue;
            }
            double screenX = centerX + offsetX + region.regionX * 512.0D * zoomLevel;
            double screenY = centerY + offsetY + region.regionZ * 512.0D * zoomLevel;
            double regionSize = 512.0D * zoomLevel;
            if (screenX + regionSize < startX || screenX > startX + width || screenY + regionSize < startY || screenY > startY + height) {
                continue;
            }
            drawRegionTexture(graphics, textureId, screenX, screenY, regionSize);
        }
    }

    /** drawRegionTexture: 把地图区域纹理绘制到屏幕坐标。 */
    private void drawRegionTexture(GuiGraphics graphics, int textureId, double screenX, double screenY, double regionSize) {
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float x0 = Math.round((float) screenX);
        float y0 = Math.round((float) screenY);
        float x1 = Math.round((float) (screenX + regionSize));
        float y1 = Math.round((float) (screenY + regionSize));
        buffer.addVertex(matrix, x0, y1, 0).setUv(0, 1);
        buffer.addVertex(matrix, x1, y1, 0).setUv(1, 1);
        buffer.addVertex(matrix, x1, y0, 0).setUv(1, 0);
        buffer.addVertex(matrix, x0, y0, 0).setUv(0, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /** renderChannelLines: 单向画方向箭头，同一对端点双向时合并成对顶角。 */
    private void renderChannelLines(GuiGraphics graphics, int mapWidth) {
        int centerX = mapWidth / 2;
        int centerY = this.height / 2;
        int[] warehouse = worldToScreen(packet.boxPos(), centerX, centerY);
        Set<UUID> drawnBidirectional = new HashSet<>();
        for (LogisticsControlBoxService.ChannelEntry channel : packet.channels()) {
            LogisticsControlBoxService.ClientEntry client = client(channel.clientId());
            if (client == null) {
                continue;
            }
            int[] clientPoint = worldToScreen(client.boxPos(), centerX, centerY);
            if (hasBothDirections(channel.clientId())) {
                if (!drawnBidirectional.add(channel.clientId())) {
                    continue;
                }
                LogisticsMapOverlay.drawRoute(graphics, warehouse[0], warehouse[1],
                        clientPoint[0], clientPoint[1], anyChannelEnabled(channel.clientId()), 0.0F,
                        anyChannelHovered(channel.clientId()), true);
                continue;
            }
            float lane = LogisticsMapOverlay.laneOffset(channelLaneSlot(channel), channelLaneCount(channel.clientId()));
            boolean highlighted = isHoveredRoute(channel.channelId());
            if (channel.direction() == LogisticsDirection.CLIENT_TO_WAREHOUSE) {
                LogisticsMapOverlay.drawRoute(graphics, clientPoint[0], clientPoint[1],
                        warehouse[0], warehouse[1], channel.enabled(), lane, highlighted, false);
            } else {
                LogisticsMapOverlay.drawRoute(graphics, warehouse[0], warehouse[1],
                        clientPoint[0], clientPoint[1], channel.enabled(), lane, highlighted, false);
            }
        }
    }

    /** renderMarkers: 绘制仓库仓房图标和客户端菱形节点。 */
    private void renderMarkers(GuiGraphics graphics, int mapWidth) {
        int centerX = mapWidth / 2;
        int centerY = this.height / 2;
        int[] warehouse = worldToScreen(packet.boxPos(), centerX, centerY);
        QuickEndpoint warehouseEndpoint = warehouseEndpoint();
        LogisticsMapOverlay.drawWarehouseMarker(graphics, this.font, warehouse[0], warehouse[1],
                Component.translatable("gui.simukraft.logistics.node.warehouse").getString(),
                hoveredWarehouse, warehouseEndpoint.equals(quickReceiver), warehouseEndpoint.equals(quickSender));
        for (LogisticsControlBoxService.ClientEntry client : packet.clients()) {
            int[] screen = worldToScreen(client.boxPos(), centerX, centerY);
            boolean selected = client.clientId().equals(selectedClientId) || client.clientId().equals(hoveredClientId);
            QuickEndpoint endpoint = clientEndpoint(client);
            LogisticsMapOverlay.drawClientMarker(graphics, this.font, screen[0], screen[1], client.name(),
                    selected, endpoint.equals(quickReceiver), endpoint.equals(quickSender));
        }
    }

    /** renderSidePanel: 绘制选中客户端的旧版侧边信息面板。 */
    private void renderSidePanel(GuiGraphics graphics, int x) {
        int panelWidth = 180;
        LogisticsNativeStyle.drawPanel(graphics, x, 0, panelWidth, this.height);
        LogisticsControlBoxService.ClientEntry selected = client(selectedClientId);
        int y = 8;
        if (selected != null) {
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.map.marker.client", selected.name()), x + 6, y, LogisticsNativeStyle.TEXT);
            y += 13;
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.map.marker.ports", selected.portCount()), x + 6, y, LogisticsNativeStyle.TEXT_DIM);
            y += 12;
            graphics.drawString(this.font, LogisticsNativeStyle.posText(selected.boxPos()), x + 6, y, LogisticsNativeStyle.TEXT_DIM);
        }
        y += 18;
        graphics.fill(x + 5, y, x + panelWidth - 5, y + 1, LogisticsNativeStyle.PANEL_LINE);
        y += 10;
        graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.routes"), x + 6, y, LogisticsNativeStyle.TEXT_WARN);
        y += 16;
        List<LogisticsControlBoxService.ChannelEntry> channels = selectedChannels();
        if (channels.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.simukraft.logistics.empty"), x + 6, y, LogisticsNativeStyle.TEXT_MUTED);
            return;
        }
        for (LogisticsControlBoxService.ChannelEntry channel : channels) {
            String dir = channel.direction() == LogisticsDirection.CLIENT_TO_WAREHOUSE ? "<-" : "->";
            LogisticsNativeStyle.drawStatusBadge(graphics, this.font, channel.enabled(), x + 6, y - 1);
            LogisticsNativeStyle.drawFitString(graphics, this.font, dir + " " + LogisticsItemDisplayName.channelName(channel.name(), channel.filters()), x + 37, y, 66, LogisticsNativeStyle.TEXT);
            LogisticsNativeStyle.drawFitString(graphics, this.font, LogisticsItemDisplayName.filterText(channel.filters()), x + 12, y + 11, 150, LogisticsNativeStyle.TEXT_MUTED);
            y += 36;
            if (y > this.height - 45) {
                break;
            }
        }
    }

    /** renderHoverTooltip: 显示端点或鼠标附近路线的悬浮框。 */
    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> lines = hoverTooltipLines();
        if (lines.isEmpty()) {
            return;
        }
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    /** hoverTooltipLines: 组装端点信息和附近路线文本。 */
    private List<Component> hoverTooltipLines() {
        List<Component> lines = new ArrayList<>();
        LogisticsControlBoxService.ClientEntry hoveredClient = client(hoveredClientId);
        if (hoveredClient != null) {
            lines.add(Component.translatable("gui.simukraft.logistics.map.marker.client", hoveredClient.name()));
            lines.add(Component.translatable("gui.simukraft.logistics.map.marker.ports", hoveredClient.portCount()));
            lines.add(Component.literal(LogisticsNativeStyle.posText(hoveredClient.boxPos())));
            lines.add(Component.translatable("gui.simukraft.logistics.map.quick_tooltip"));
        } else if (hoveredWarehouse) {
            lines.add(Component.translatable("gui.simukraft.logistics.map.marker.warehouse"));
            lines.add(Component.literal(LogisticsNativeStyle.posText(packet.boxPos())));
            lines.add(Component.translatable("gui.simukraft.logistics.map.quick_tooltip"));
        }
        if (hoveredRoutes.isEmpty()) {
            return lines;
        }
        if (!lines.isEmpty()) {
            lines.add(Component.empty());
        }
        lines.add(Component.translatable("gui.simukraft.logistics.map.nearby_routes"));
        for (HoveredRoute hovered : hoveredRoutes) {
            String status = Component.translatable(hovered.channel().enabled()
                    ? "gui.simukraft.logistics.map.route_on"
                    : "gui.simukraft.logistics.map.route_off").getString();
            String direction = Component.translatable(hovered.channel().direction() == LogisticsDirection.WAREHOUSE_TO_CLIENT
                    ? "gui.simukraft.logistics.route_in"
                    : "gui.simukraft.logistics.route_out").getString();
            String name = LogisticsItemDisplayName.channelName(hovered.channel().name(), hovered.channel().filters());
            String clientName = hovered.client() == null ? "-" : hovered.client().name();
            lines.add(Component.translatable("gui.simukraft.logistics.map.route_entry",
                    status, direction, clientName + " · " + name));
            lines.add(Component.translatable("gui.simukraft.logistics.map.route_items",
                    LogisticsItemDisplayName.filterText(hovered.channel().filters())));
        }
        return lines;
    }

    /** updateHover: 根据鼠标位置找出端点和附近路线。 */
    private void updateHover(int mouseX, int mouseY, int mapWidth) {
        hoveredClientId = null;
        hoveredWarehouse = false;
        hoveredRoutes = List.of();
        if (dragging || mouseY >= this.height - 25 || mouseX >= mapWidth) {
            return;
        }
        int centerX = mapWidth / 2;
        int centerY = this.height / 2;
        int[] warehouse = worldToScreen(packet.boxPos(), centerX, centerY);
        if (LogisticsMapOverlay.containsMarker(mouseX, mouseY, warehouse[0], warehouse[1])) {
            hoveredWarehouse = true;
        }
        for (LogisticsControlBoxService.ClientEntry clientEntry : packet.clients()) {
            int[] screen = worldToScreen(clientEntry.boxPos(), centerX, centerY);
            if (LogisticsMapOverlay.containsMarker(mouseX, mouseY, screen[0], screen[1])) {
                hoveredClientId = clientEntry.clientId();
                break;
            }
        }
        hoveredRoutes = nearbyRoutes(mouseX, mouseY, warehouse, centerX, centerY);
    }

    /** nearbyRoutes: 收集鼠标附近的路线，端点上会带上该节点相关线路。 */
    private List<HoveredRoute> nearbyRoutes(int mouseX, int mouseY, int[] warehouse, int centerX, int centerY) {
        List<HoveredRoute> nearby = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (LogisticsControlBoxService.ChannelEntry channel : packet.channels()) {
            LogisticsControlBoxService.ClientEntry clientEntry = client(channel.clientId());
            if (clientEntry == null) {
                continue;
            }
            int[] clientPoint = worldToScreen(clientEntry.boxPos(), centerX, centerY);
            float lane = hasBothDirections(channel.clientId())
                    ? 0.0F
                    : LogisticsMapOverlay.laneOffset(channelLaneSlot(channel), channelLaneCount(channel.clientId()));
            double distance = LogisticsMapOverlay.routeDistance(
                    mouseX, mouseY, warehouse[0], warehouse[1], clientPoint[0], clientPoint[1], lane);
            boolean onEndpoint = hoveredWarehouse || channel.clientId().equals(hoveredClientId);
            if (!onEndpoint && distance > LogisticsMapOverlay.ROUTE_HOVER_DISTANCE) {
                continue;
            }
            if (!seen.add(channel.channelId())) {
                continue;
            }
            nearby.add(new HoveredRoute(channel, clientEntry, distance));
        }
        nearby.sort(Comparator.comparingDouble(HoveredRoute::distance));
        if (nearby.size() > 8) {
            return List.copyOf(nearby.subList(0, 8));
        }
        return List.copyOf(nearby);
    }

    /** hasBothDirections: 同一客户端同时存在补货和回收时视为双向。 */
    private boolean hasBothDirections(UUID clientId) {
        boolean toClient = false;
        boolean toWarehouse = false;
        for (LogisticsControlBoxService.ChannelEntry channel : packet.channels()) {
            if (!clientId.equals(channel.clientId()) || channel.direction() == null) {
                continue;
            }
            if (channel.direction() == LogisticsDirection.WAREHOUSE_TO_CLIENT) {
                toClient = true;
            } else if (channel.direction() == LogisticsDirection.CLIENT_TO_WAREHOUSE) {
                toWarehouse = true;
            }
            if (toClient && toWarehouse) {
                return true;
            }
        }
        return false;
    }

    private boolean anyChannelEnabled(UUID clientId) {
        for (LogisticsControlBoxService.ChannelEntry channel : packet.channels()) {
            if (clientId.equals(channel.clientId()) && channel.enabled()) {
                return true;
            }
        }
        return false;
    }

    private boolean anyChannelHovered(UUID clientId) {
        for (HoveredRoute hovered : hoveredRoutes) {
            if (clientId.equals(hovered.channel().clientId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isHoveredRoute(UUID channelId) {
        for (HoveredRoute hovered : hoveredRoutes) {
            if (hovered.channel().channelId().equals(channelId)) {
                return true;
            }
        }
        return false;
    }

    /** endpointAt: 查询鼠标下的仓库或客户端节点。 */
    private QuickEndpoint endpointAt(double mouseX, double mouseY) {
        int centerX = mapWidth() / 2;
        int centerY = this.height / 2;
        int[] warehouse = worldToScreen(packet.boxPos(), centerX, centerY);
        if (LogisticsMapOverlay.containsMarker(mouseX, mouseY, warehouse[0], warehouse[1])) {
            return warehouseEndpoint();
        }
        for (LogisticsControlBoxService.ClientEntry client : packet.clients()) {
            int[] screen = worldToScreen(client.boxPos(), centerX, centerY);
            if (LogisticsMapOverlay.containsMarker(mouseX, mouseY, screen[0], screen[1])) {
                return clientEndpoint(client);
            }
        }
        return null;
    }

    /** worldToScreen: 将世界坐标转换为地图屏幕坐标。 */
    private int[] worldToScreen(BlockPos pos, int centerX, int centerY) {
        return new int[]{
                (int) Math.round(centerX + offsetX + pos.getX() * zoomLevel),
                (int) Math.round(centerY + offsetY + pos.getZ() * zoomLevel)
        };
    }

    /** selectedChannels: 返回当前客户端关联的频道。 */
    private List<LogisticsControlBoxService.ChannelEntry> selectedChannels() {
        if (selectedClientId == null) {
            return List.of();
        }
        return packet.channels().stream()
                .filter(channel -> selectedClientId.equals(channel.clientId()))
                .toList();
    }

    /** client: 按 ID 查找客户端数据。 */
    private LogisticsControlBoxService.ClientEntry client(UUID clientId) {
        if (clientId == null) {
            return null;
        }
        for (var client : packet.clients()) {
            if (clientId.equals(client.clientId())) return client;
        }
        return null;
    }

    /** preferredClientId: 根据快速端点为普通创建按钮预选客户端。 */
    private UUID preferredClientId() {
        if (quickReceiver != null && !quickReceiver.warehouse()) {
            return quickReceiver.clientId();
        }
        if (quickSender != null && !quickSender.warehouse()) {
            return quickSender.clientId();
        }
        return selectedClientId;
    }

    /** preferredDirection: 根据快速端点为普通创建按钮预选方向。 */
    private LogisticsDirection preferredDirection() {
        if (quickSender != null && !quickSender.warehouse()) {
            return LogisticsDirection.CLIENT_TO_WAREHOUSE;
        }
        return LogisticsDirection.WAREHOUSE_TO_CLIENT;
    }

    /** tryOpenQuickCreate: 双端点有效时打开创建线路弹窗。 */
    private boolean tryOpenQuickCreate() {
        if (quickReceiver == null || quickSender == null) {
            return false;
        }
        if (quickReceiver.equals(quickSender)) {
            ClientInfoToast.show(Component.translatable("toast.simukraft.title"), Component.translatable("gui.simukraft.logistics.map.quick_same"), "warning");
            return false;
        }
        if (quickSender.warehouse() && !quickReceiver.warehouse()) {
            LogisticsChannelCreateScreenOpener.open(packet, quickReceiver.clientId(), LogisticsDirection.WAREHOUSE_TO_CLIENT);
            return true;
        }
        if (!quickSender.warehouse() && quickReceiver.warehouse()) {
            LogisticsChannelCreateScreenOpener.open(packet, quickSender.clientId(), LogisticsDirection.CLIENT_TO_WAREHOUSE);
            return true;
        }
        ClientInfoToast.show(Component.translatable("toast.simukraft.title"), Component.translatable("gui.simukraft.logistics.map.quick_invalid"), "warning");
        return false;
    }

    /** endpointLabel: 返回快速创建端点的紧凑显示名。 */
    private String endpointLabel(QuickEndpoint endpoint) {
        if (endpoint == null) {
            return "-";
        }
        if (endpoint.warehouse()) {
            return Component.translatable("gui.simukraft.logistics.node.warehouse").getString();
        }
        LogisticsControlBoxService.ClientEntry client = client(endpoint.clientId());
        return client != null ? client.name() : LogisticsNativeStyle.posText(endpoint.pos());
    }

    private QuickEndpoint warehouseEndpoint() {
        return new QuickEndpoint(true, null, packet.boxPos().immutable());
    }

    private QuickEndpoint clientEndpoint(LogisticsControlBoxService.ClientEntry client) {
        return new QuickEndpoint(false, client.clientId(), client.boxPos().immutable());
    }

    /** sendChannelAction: 发送启停或删除频道请求。 */
    private void sendChannelAction(LogisticsBoxActionPacket.Action action, UUID channelId) {
        PacketDistributor.sendToServer(new LogisticsBoxActionPacket(packet.boxPos(), action, null, channelId, BlockPos.ZERO, "", LogisticsDirection.WAREHOUSE_TO_CLIENT));
        selectedClientId = null;
    }

    /** centerOnWarehouse: 将地图中心移动到仓库盒。 */
    private void centerOnWarehouse() {
        offsetX = -packet.boxPos().getX() * zoomLevel;
        offsetY = -packet.boxPos().getZ() * zoomLevel;
    }

    /** mapWidth: 根据侧边栏状态计算地图宽度。 */
    private int mapWidth() {
        return selectedClientId == null ? this.width : this.width - 180;
    }

    /** channelLaneSlot: 同一客户端多条线路时的错开序号。 */
    private int channelLaneSlot(LogisticsControlBoxService.ChannelEntry channel) {
        int slot = 0;
        for (LogisticsControlBoxService.ChannelEntry other : packet.channels()) {
            if (!channel.clientId().equals(other.clientId())) {
                continue;
            }
            if (channel.channelId().equals(other.channelId())) {
                return slot;
            }
            slot++;
        }
        return 0;
    }

    /** channelLaneCount: 统计指向同一客户端的线路数量。 */
    private int channelLaneCount(UUID clientId) {
        int count = 0;
        for (LogisticsControlBoxService.ChannelEntry channel : packet.channels()) {
            if (clientId.equals(channel.clientId())) {
                count++;
            }
        }
        return count;
    }

    private record QuickEndpoint(boolean warehouse, UUID clientId, BlockPos pos) {
    }

    private record HoveredRoute(LogisticsControlBoxService.ChannelEntry channel,
                                LogisticsControlBoxService.ClientEntry client,
                                double distance) {
    }
}
