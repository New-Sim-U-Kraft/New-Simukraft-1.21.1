package client.cn.kafei.simukraft.client.rts;

import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.input.SimuKraftKeyMappings;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/** RTS 小地图：复用本地地图缓存，绘制相机视口并处理相机跳转。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class RtsMiniMapRenderer {
    private static final int SMALL_MAP_SIZE = 112;
    private static final int SMALL_WORLD_SPAN = 256;
    private static final int LARGE_WORLD_SPAN = 1024;
    private static final int MAP_MARGIN = 12;
    private static final int FRAME_SIZE = 3;
    private static final int REFRESH_INTERVAL_TICKS = 5;
    private static final int MAP_DRAG_THRESHOLD = 2;
    private static final int COLOR_FRAME = 0xEE111815;
    private static final int COLOR_BORDER = 0xFF8DAA8A;
    private static final int COLOR_VIEWPORT = 0xFFECF77A;

    private static boolean expanded;
    private static boolean textureRefreshRequested = true;
    private static boolean mapClickCaptured;
    private static boolean mapDragActive;
    private static int ticksUntilRefresh;
    private static int territoryDataVersion = -1;
    private static int mapDragStartX;
    private static int mapDragStartY;
    private static int mapDragLastX;
    private static int mapDragLastY;

    private RtsMiniMapRenderer() {
    }

    /** onClientTick: 切换地图尺寸并维持已有地图缓存的活跃扫描。 */
    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean togglePressed = SimuKraftKeyMappings.RTS_MINIMAP_TOGGLE.consumeClick();
        if (!isVisible()) {
            FreeCameraManager.setRtsEdgePanBlocked(false);
            RtsMiniMapTexture.releaseConsumer();
            resetMapInteraction();
            textureRefreshRequested = true;
            return;
        }
        FreeCameraManager.setRtsEdgePanBlocked(mapClickCaptured || isMouseOverMap(minecraft));
        RtsMiniMapTexture.acquireConsumer();
        updateMapDrag(minecraft);
        int dataVersion = ClientCityChunkCache.getInstance().getDataVersion();
        if (territoryDataVersion != dataVersion) {
            territoryDataVersion = dataVersion;
            textureRefreshRequested = true;
        }
        if (togglePressed && RtsSelectionManager.canUseRtsCameraControls()) {
            expanded = !expanded;
            textureRefreshRequested = true;
        }
        if (++ticksUntilRefresh >= REFRESH_INTERVAL_TICKS) {
            ticksUntilRefresh = 0;
            textureRefreshRequested = true;
        }
    }

    /** render: 在 RTS HUD 中绘制地图和当前相机可见范围。 */
    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isVisible() || !RtsSelectionManager.canUseRtsCameraControls()) {
            return;
        }
        ResourceLocation texture = RtsMiniMapTexture.location();
        if (textureRefreshRequested) {
            texture = RtsMiniMapTexture.refresh(FreeCameraManager.rtsFocus(), worldSpan());
            textureRefreshRequested = texture == null;
        }
        if (texture == null) {
            return;
        }
        MapLayout layout = layout(minecraft);
        graphics.fill(layout.left() - FRAME_SIZE, layout.top() - FRAME_SIZE,
                layout.right() + FRAME_SIZE, layout.bottom() + FRAME_SIZE, COLOR_FRAME);
        int textureSize = RtsMiniMapTexture.size();
        graphics.blit(texture, layout.left(), layout.top(), layout.size(), layout.size(),
                0.0F, 0.0F, textureSize, textureSize, textureSize, textureSize);
        drawBorder(graphics, layout);
        drawViewport(graphics, layout);
    }

    /** handleMouseButton: 捕获地图区域的左键，防止同时触发 RTS 方块操作。 */
    public static boolean handleMouseButton(InputEvent.MouseButton.Pre event) {
        if (!isVisible() || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (event.getAction() == GLFW.GLFW_RELEASE && mapClickCaptured) {
            finishMapInteraction(Minecraft.getInstance());
            return true;
        }
        if (event.getAction() != GLFW.GLFW_PRESS
                || !RtsSelectionManager.canUseRtsCameraControls()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        MapLayout layout = layout(minecraft);
        int mouseX = guiMouseX(minecraft);
        int mouseY = guiMouseY(minecraft);
        if (!layout.contains(mouseX, mouseY)) {
            return false;
        }
        beginMapInteraction(mouseX, mouseY);
        return true;
    }

    /** clear: 断开连接时释放动态纹理和地图消费者引用。 */
    public static void clear() {
        RtsMiniMapTexture.clear();
        expanded = false;
        textureRefreshRequested = true;
        resetMapInteraction();
        ticksUntilRefresh = 0;
        territoryDataVersion = -1;
    }

    private static boolean isVisible() {
        return !Minecraft.getInstance().options.hideGui
                && RtsSelectionManager.isActive() && FreeCameraManager.isRtsActive();
    }

    private static void jumpCamera(MapLayout layout, int mouseX, int mouseY) {
        Vec3 focus = FreeCameraManager.rtsFocus();
        double span = worldSpan();
        double x = focus.x + ((mouseX - layout.left()) / (double) layout.size() - 0.5D) * span;
        double z = focus.z + ((mouseY - layout.top()) / (double) layout.size() - 0.5D) * span;
        FreeCameraManager.setRtsFocus(x, z);
        textureRefreshRequested = true;
    }

    /** beginMapInteraction: 记录小地图按下点，以便区分单击跳转与视图框拖拽。 */
    private static void beginMapInteraction(int mouseX, int mouseY) {
        mapClickCaptured = true;
        mapDragActive = false;
        mapDragStartX = mouseX;
        mapDragStartY = mouseY;
        mapDragLastX = mouseX;
        mapDragLastY = mouseY;
    }

    /** updateMapDrag: 按当前鼠标位移平移 RTS 摄像机，鼠标移出小地图时限制在边界。 */
    private static void updateMapDrag(Minecraft minecraft) {
        if (!mapClickCaptured) {
            return;
        }
        if (!RtsSelectionManager.canUseRtsCameraControls()) {
            resetMapInteraction();
            return;
        }
        if (GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                != GLFW.GLFW_PRESS) {
            finishMapInteraction(minecraft);
            return;
        }
        MapLayout layout = layout(minecraft);
        int mouseX = Mth.clamp(guiMouseX(minecraft), layout.left(), layout.right() - 1);
        int mouseY = Mth.clamp(guiMouseY(minecraft), layout.top(), layout.bottom() - 1);
        if (!mapDragActive && Math.abs(mouseX - mapDragStartX) < MAP_DRAG_THRESHOLD
                && Math.abs(mouseY - mapDragStartY) < MAP_DRAG_THRESHOLD) {
            return;
        }
        mapDragActive = true;
        int dragX = mouseX - mapDragLastX;
        int dragY = mouseY - mapDragLastY;
        mapDragLastX = mouseX;
        mapDragLastY = mouseY;
        if (dragX == 0 && dragY == 0) {
            return;
        }
        double worldBlocksPerPixel = worldSpan() / (double) layout.size();
        Vec3 focus = FreeCameraManager.rtsFocus();
        FreeCameraManager.setRtsFocus(
                focus.x + dragX * worldBlocksPerPixel,
                focus.z + dragY * worldBlocksPerPixel);
        textureRefreshRequested = true;
    }

    /** finishMapInteraction: 拖拽未开始时执行既有单击跳转，并清理小地图输入状态。 */
    private static void finishMapInteraction(Minecraft minecraft) {
        if (mapClickCaptured && !mapDragActive && RtsSelectionManager.canUseRtsCameraControls()) {
            MapLayout layout = layout(minecraft);
            int mouseX = Mth.clamp(guiMouseX(minecraft), layout.left(), layout.right() - 1);
            int mouseY = Mth.clamp(guiMouseY(minecraft), layout.top(), layout.bottom() - 1);
            jumpCamera(layout, mouseX, mouseY);
        }
        resetMapInteraction();
    }

    /** resetMapInteraction: 重置小地图单击和拖拽的临时输入状态。 */
    private static void resetMapInteraction() {
        mapClickCaptured = false;
        mapDragActive = false;
        mapDragStartX = 0;
        mapDragStartY = 0;
        mapDragLastX = 0;
        mapDragLastY = 0;
    }

    private static void drawViewport(GuiGraphics graphics, MapLayout layout) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        Camera.NearPlane nearPlane = camera.getNearPlane();
        Vec3 center = nearPlane.getPointOnPlane(0.0F, 0.0F);
        Vec3 forward = center.normalize();
        if (Math.abs(forward.y) < 0.0001D) {
            return;
        }
        Vec3 right = nearPlane.getPointOnPlane(1.0F, 0.0F).subtract(center).normalize();
        Vec3 up = nearPlane.getPointOnPlane(0.0F, 1.0F).subtract(center).normalize();
        Vec3 focus = FreeCameraManager.rtsFocus();
        double aspect = (double) minecraft.getWindow().getScreenWidth() / minecraft.getWindow().getScreenHeight();
        Vec3[] corners = new Vec3[4];
        double[] xOffsets = {-0.5D, 0.5D, 0.5D, -0.5D};
        double[] yOffsets = {0.5D, 0.5D, -0.5D, -0.5D};
        for (int index = 0; index < corners.length; index++) {
            Vec3 from = camera.getPosition()
                    .add(right.scale(xOffsets[index] * FreeCameraManager.rtsZoom() * aspect))
                    .add(up.scale(yOffsets[index] * FreeCameraManager.rtsZoom()));
            double distance = (focus.y - from.y) / forward.y;
            corners[index] = from.add(forward.scale(distance));
        }
        for (int index = 0; index < corners.length; index++) {
            Vec3 start = corners[index];
            Vec3 end = corners[(index + 1) % corners.length];
            drawLine(graphics, layout, worldToMapX(layout, focus, start.x), worldToMapY(layout, focus, start.z),
                    worldToMapX(layout, focus, end.x), worldToMapY(layout, focus, end.z));
        }
    }

    private static void drawBorder(GuiGraphics graphics, MapLayout layout) {
        graphics.fill(layout.left(), layout.top(), layout.right(), layout.top() + 1, COLOR_BORDER);
        graphics.fill(layout.left(), layout.bottom() - 1, layout.right(), layout.bottom(), COLOR_BORDER);
        graphics.fill(layout.left(), layout.top(), layout.left() + 1, layout.bottom(), COLOR_BORDER);
        graphics.fill(layout.right() - 1, layout.top(), layout.right(), layout.bottom(), COLOR_BORDER);
    }

    private static void drawLine(GuiGraphics graphics, MapLayout layout, int x0, int y0, int x1, int y1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) {
            drawViewportPixel(graphics, layout, x0, y0);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            double progress = step / (double) steps;
            int x = Mth.floor(x0 + (x1 - x0) * progress);
            int y = Mth.floor(y0 + (y1 - y0) * progress);
            drawViewportPixel(graphics, layout, x, y);
        }
    }

    private static void drawViewportPixel(GuiGraphics graphics, MapLayout layout, int x, int y) {
        if (x >= layout.left() && x < layout.right() && y >= layout.top() && y < layout.bottom()) {
            graphics.fill(x, y, x + 1, y + 1, COLOR_VIEWPORT);
        }
    }

    private static int worldToMapX(MapLayout layout, Vec3 focus, double worldX) {
        return Mth.floor(layout.left() + layout.size() * (0.5D + (worldX - focus.x) / worldSpan()));
    }

    private static int worldToMapY(MapLayout layout, Vec3 focus, double worldZ) {
        return Mth.floor(layout.top() + layout.size() * (0.5D + (worldZ - focus.z) / worldSpan()));
    }

    private static int worldSpan() {
        return expanded ? LARGE_WORLD_SPAN : SMALL_WORLD_SPAN;
    }

    private static MapLayout layout(Minecraft minecraft) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int size = expanded ? Math.max(96, Math.min(320, Math.min(width - MAP_MARGIN * 2, height - MAP_MARGIN * 2))) : SMALL_MAP_SIZE;
        int left = expanded ? (width - size) / 2 : width - size - MAP_MARGIN;
        int top = expanded ? (height - size) / 2 : MAP_MARGIN;
        return new MapLayout(left, top, size);
    }

    private static int guiMouseX(Minecraft minecraft) {
        int screenWidth = minecraft.getWindow().getScreenWidth();
        return screenWidth <= 0 ? 0 : (int) (minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / screenWidth);
    }

    private static int guiMouseY(Minecraft minecraft) {
        int screenHeight = minecraft.getWindow().getScreenHeight();
        return screenHeight <= 0 ? 0 : (int) (minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / screenHeight);
    }

    /** isMouseOverMap: 判断当前系统鼠标是否位于小地图区域。 */
    private static boolean isMouseOverMap(Minecraft minecraft) {
        MapLayout layout = layout(minecraft);
        return layout.contains(guiMouseX(minecraft), guiMouseY(minecraft));
    }

    private record MapLayout(int left, int top, int size) {
        private int right() {
            return left + size;
        }

        private int bottom() {
            return top + size;
        }

        private boolean contains(int x, int y) {
            return x >= left && x < right() && y >= top && y < bottom();
        }
    }
}
