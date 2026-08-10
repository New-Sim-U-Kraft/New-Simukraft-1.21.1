package client.cn.kafei.simukraft.client.freecamera;

import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import common.cn.kafei.simukraft.network.rts.RtsChunkViewPacket;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("null")
@EventBusSubscriber(value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public final class FreeCameraManager {
    private static final double RTS_CAMERA_HEIGHT = 30.0D;
    private static final float RTS_INITIAL_YAW = -135.0F;
    private static final float RTS_INITIAL_PITCH = 45.0F;
    private static final double RTS_MIN_ZOOM = 10.0D;
    private static final double RTS_DEFAULT_ZOOM = 30.0D;
    private static final double RTS_MAX_ZOOM = 220.0D;
    private static final double RTS_ZOOM_STEP = 1.0D;
    private static final double RTS_CAMERA_DISTANCE = RTS_CAMERA_HEIGHT
            / Math.sin(Math.toRadians(RTS_INITIAL_PITCH));
    private static final double RTS_PAN_MAX_SPEED = 20.0D;
    private static final double RTS_FAST_INPUT_MULTIPLIER = 3.0D;
    private static final double RTS_EDGE_PAN_MARGIN = 28.0D;
    private static final double RTS_EDGE_PAN_MAX_INPUT = 1.0D;
    private static boolean active;
    private static boolean rtsMode;
    private static Vec3 position = Vec3.ZERO;
    private static Vec3 rtsFocus = Vec3.ZERO;
    private static double rtsZoom = RTS_DEFAULT_ZOOM;
    private static float yaw;
    private static float pitch;
    private static float speed = 12.0f;
    private static final float SPRINT_MULTIPLIER = 3.0f;
    private static volatile boolean movingForward;
    private static volatile boolean movingBackward;
    private static volatile boolean movingLeft;
    private static volatile boolean movingRight;
    private static volatile boolean movingUp;
    private static volatile boolean movingDown;
    private static volatile boolean sprinting;
    private static volatile boolean rtsEdgePanBlocked;
    private static int lastRtsViewChunkX = Integer.MIN_VALUE;
    private static int lastRtsViewChunkZ = Integer.MIN_VALUE;
    private static net.minecraft.resources.ResourceKey<Level> lastRtsViewDimension;

    private FreeCameraManager() {
    }

    public static void activate() {
        if (active) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        active = true;
        rtsMode = false;
        position = player.getEyePosition();
        yaw = player.getYRot();
        pitch = player.getXRot();
        normalizeYaw();
        CameraMouseLock.setLocked(true);
    }

    /** activateRts: 启动保持系统光标可见的 RTS 俯视相机。 */
    public static void activateRts() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        active = true;
        rtsMode = true;
        rtsFocus = player.getEyePosition();
        rtsZoom = RTS_DEFAULT_ZOOM;
        yaw = RTS_INITIAL_YAW;
        pitch = RTS_INITIAL_PITCH;
        normalizeYaw();
        updateRtsCameraPosition();
        RtsViewAreaSynchronizer.sync(rtsFocus);
        syncRtsChunkView();
        CameraMouseLock.setLocked(false);
    }

    public static void deactivate() {
        if (rtsMode) {
            RtsViewAreaSynchronizer.restore();
            if (Minecraft.getInstance().getConnection() != null) {
                PacketDistributor.sendToServer(new RtsChunkViewPacket(false, 0, 0));
            }
        }
        active = false;
        rtsMode = false;
        rtsFocus = Vec3.ZERO;
        rtsEdgePanBlocked = false;
        clearRtsChunkViewSync();
        CameraMouseLock.setLocked(false);
        resetMovementState();
        KeyMapping.setAll();
    }

    public static boolean isActive() {
        return active;
    }

    /** isRtsActive: 判断当前相机是否处于正交 RTS 视图。 */
    public static boolean isRtsActive() {
        return active && rtsMode;
    }

    /** rtsZoom: 返回 RTS 正交视图的纵向世界范围。 */
    public static double rtsZoom() {
        return rtsZoom;
    }

    /** rtsFocus: 返回 RTS 相机当前视图中心。 */
    public static Vec3 rtsFocus() {
        return rtsFocus;
    }

    /** setRtsFocus: 在不移动玩家本体的前提下定位 RTS 相机中心。 */
    public static void setRtsFocus(double x, double z) {
        if (!isRtsActive()) {
            return;
        }
        rtsFocus = new Vec3(x, rtsFocus.y, z);
        updateRtsCameraPosition();
        RtsViewAreaSynchronizer.sync(rtsFocus);
        syncRtsChunkView();
    }

    /** rtsProjectionMatrix: 生成与 RTS 缩放值一致的正交投影矩阵。 */
    public static Matrix4f rtsProjectionMatrix() {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getScreenWidth();
        int height = minecraft.getWindow().getScreenHeight();
        if (width <= 0 || height <= 0) {
            return new Matrix4f();
        }
        float halfHeight = (float) rtsZoom * 0.5F;
        float halfWidth = halfHeight * width / height;
        return new Matrix4f().setOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight, -3000.0F, 3000.0F);
    }

    public static Vec3 getPosition() {
        return position;
    }

    public static float getYaw() {
        return yaw;
    }

    public static float getPitch() {
        return pitch;
    }

    public static void setMovingForward(boolean state) {
        movingForward = state;
    }

    public static void setMovingBackward(boolean state) {
        movingBackward = state;
    }

    public static void setMovingLeft(boolean state) {
        movingLeft = state;
    }

    public static void setMovingRight(boolean state) {
        movingRight = state;
    }

    public static void setMovingUp(boolean state) {
        movingUp = state;
    }

    public static void setMovingDown(boolean state) {
        movingDown = state;
    }

    public static void setSprinting(boolean state) {
        sprinting = state;
    }

    public static void handleRotation(float deltaYaw, float deltaPitch) {
        if (!active) {
            return;
        }
        yaw += deltaYaw;
        if (!rtsMode) {
            pitch = Mth.clamp(pitch + deltaPitch, -90.0F, 90.0F);
        }
        normalizeYaw();
        if (rtsMode) {
            updateRtsCameraPosition();
        }
    }

    /** adjustZoom: 沿当前相机视线缩放 RTS 俯视镜头。 */
    public static void adjustZoom(double scrollDelta) {
        adjustZoom(scrollDelta, false);
    }

    /** adjustZoom: 按指定加速状态缩放 RTS 俯视镜头。 */
    public static void adjustZoom(double scrollDelta, boolean fast) {
        if (!active || scrollDelta == 0.0D) {
            return;
        }
        if (rtsMode) {
            double step = fast ? RTS_ZOOM_STEP * RTS_FAST_INPUT_MULTIPLIER : RTS_ZOOM_STEP;
            rtsZoom = Mth.clamp(rtsZoom - Math.signum(scrollDelta) * step, RTS_MIN_ZOOM, RTS_MAX_ZOOM);
            return;
        }
        position = position.add(Vec3.directionFromRotation(pitch, yaw).scale(scrollDelta * RTS_ZOOM_STEP));
    }

    public static void resetMovementState() {
        movingForward = false;
        movingBackward = false;
        movingLeft = false;
        movingRight = false;
        movingUp = false;
        movingDown = false;
        sprinting = false;
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        clearVanillaMovementKeys(minecraft);
        if (minecraft.player == null || minecraft.level == null) {
            deactivate();
            return;
        }
        if (!minecraft.player.isAlive()) {
            deactivate();
            return;
        }
        double xInput = 0.0D;
        double zInput = 0.0D;
        double yInput = 0.0D;
        if (movingForward) zInput += 1.0D;
        if (movingBackward) zInput -= 1.0D;
        if (movingLeft) xInput -= 1.0D;
        if (movingRight) xInput += 1.0D;
        if (!rtsMode) {
            if (movingUp) yInput += 1.0D;
            if (movingDown) yInput -= 1.0D;
        } else if (isRtsEdgePanActive(minecraft)) {
            xInput += rtsEdgePanHorizontal(minecraft);
            zInput += rtsEdgePanVertical(minecraft);
        }
        if (xInput == 0.0D && yInput == 0.0D && zInput == 0.0D) {
            if (rtsMode) {
                RtsViewAreaSynchronizer.sync(rtsFocus);
                syncRtsChunkView();
            }
            return;
        }
        if (xInput != 0.0D && zInput != 0.0D) {
            double length = Math.sqrt(xInput * xInput + zInput * zInput);
            xInput /= length;
            zInput /= length;
        }
        float yawRadians = (float) Math.toRadians(yaw);
        double moveX = -Math.sin(yawRadians) * zInput - Math.cos(yawRadians) * xInput;
        double moveZ = Math.cos(yawRadians) * zInput - Math.sin(yawRadians) * xInput;
        float deltaTicks = Mth.clamp(event.getPartialTick().getRealtimeDeltaTicks(), 0.0F, 2.0F);
        double moveSpeed = rtsMode
                ? RTS_PAN_MAX_SPEED * Math.sqrt(rtsZoom / RTS_MAX_ZOOM)
                * (isRtsFastMoveActive(minecraft) ? RTS_FAST_INPUT_MULTIPLIER : 1.0D)
                : speed * (sprinting ? SPRINT_MULTIPLIER : 1.0F);
        double moveDistance = moveSpeed * (deltaTicks / 20.0F);
        if (rtsMode) {
            rtsFocus = rtsFocus.add(moveX * moveDistance, 0.0D, moveZ * moveDistance);
            updateRtsCameraPosition();
            RtsViewAreaSynchronizer.sync(rtsFocus);
            syncRtsChunkView();
        } else {
            position = position.add(moveX * moveDistance, yInput * moveDistance, moveZ * moveDistance);
        }
    }

    /** onComputeFov: 正交 RTS 视图固定 FOV，保持原版渲染阶段的视锥计算稳定。 */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (isRtsActive()) {
            event.setFOV(180.0D);
        }
    }

    /** updateRtsCameraPosition: 保持固定俯角时围绕 RTS 视图中心旋转相机。 */
    private static void updateRtsCameraPosition() {
        position = rtsFocus.subtract(Vec3.directionFromRotation(pitch, yaw).scale(RTS_CAMERA_DISTANCE));
    }

    /** syncRtsChunkView: 焦点跨区块或维度切换时请求服务端更新摄像机区块视窗。 */
    private static void syncRtsChunkView() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isRtsActive() || minecraft.player == null || minecraft.level == null) {
            return;
        }
        int chunkX = SectionPos.blockToSectionCoord(Mth.floor(rtsFocus.x));
        int chunkZ = SectionPos.blockToSectionCoord(Mth.floor(rtsFocus.z));
        net.minecraft.resources.ResourceKey<Level> dimension = minecraft.level.dimension();
        if (chunkX == lastRtsViewChunkX && chunkZ == lastRtsViewChunkZ && dimension.equals(lastRtsViewDimension)) {
            return;
        }
        PacketDistributor.sendToServer(new RtsChunkViewPacket(true, chunkX, chunkZ));
        lastRtsViewChunkX = chunkX;
        lastRtsViewChunkZ = chunkZ;
        lastRtsViewDimension = dimension;
    }

    /** clearRtsChunkViewSync: 清除客户端发送节流状态，保证下次 RTS 必定同步初始焦点。 */
    private static void clearRtsChunkViewSync() {
        lastRtsViewChunkX = Integer.MIN_VALUE;
        lastRtsViewChunkZ = Integer.MIN_VALUE;
        lastRtsViewDimension = null;
    }

    private static void clearVanillaMovementKeys(Minecraft minecraft) {
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keyLeft.setDown(false);
        minecraft.options.keyRight.setDown(false);
        minecraft.options.keyJump.setDown(false);
        minecraft.options.keyShift.setDown(false);
        minecraft.options.keySprint.setDown(false);
    }

    /** isRtsFastMoveActive: 判断 RTS 中 Ctrl 是否按下以启用快速平移。 */
    private static boolean isRtsFastMoveActive(Minecraft minecraft) {
        if (!rtsMode || minecraft == null) {
            return false;
        }
        long window = minecraft.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    /** setRtsEdgePanBlocked: 设置 RTS 边缘平移是否暂时让给小地图等界面。 */
    public static void setRtsEdgePanBlocked(boolean blocked) {
        rtsEdgePanBlocked = blocked;
    }

    /** isRtsEdgePanActive: 判断当前帧是否允许鼠标边缘平移。 */
    private static boolean isRtsEdgePanActive(Minecraft minecraft) {
        return isRtsActive() && !rtsEdgePanBlocked && (minecraft.screen == null
                || minecraft.screen instanceof FreeCameraScreen) && !isRtsCameraRotationActive(minecraft);
    }

    /** rtsEdgePanHorizontal: 计算鼠标靠近左右边缘时的相机输入。 */
    private static double rtsEdgePanHorizontal(Minecraft minecraft) {
        int width = minecraft.getWindow().getScreenWidth();
        if (width <= 0) {
            return 0.0D;
        }
        double mouseX = minecraft.mouseHandler.xpos();
        double left = edgeStrength(mouseX);
        double right = edgeStrength(width - mouseX);
        return right - left;
    }

    /** rtsEdgePanVertical: 计算鼠标靠近上下边缘时的相机输入。 */
    private static double rtsEdgePanVertical(Minecraft minecraft) {
        int height = minecraft.getWindow().getScreenHeight();
        if (height <= 0) {
            return 0.0D;
        }
        double mouseY = minecraft.mouseHandler.ypos();
        double top = edgeStrength(mouseY);
        double bottom = edgeStrength(height - mouseY);
        return top - bottom;
    }

    /** edgeStrength: 将鼠标到屏幕边缘的距离转换为平滑的 0 到 1 输入。 */
    private static double edgeStrength(double distance) {
        if (distance >= RTS_EDGE_PAN_MARGIN) {
            return 0.0D;
        }
        return Mth.clamp((RTS_EDGE_PAN_MARGIN - Math.max(0.0D, distance)) / RTS_EDGE_PAN_MARGIN,
                0.0D, RTS_EDGE_PAN_MAX_INPUT);
    }

    /** isRtsCameraRotationActive: 判断 Alt+右键旋转期间是否应暂停边缘平移。 */
    private static boolean isRtsCameraRotationActive(Minecraft minecraft) {
        long window = minecraft.getWindow().getWindow();
        boolean alt = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        return alt && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    private static void normalizeYaw() {
        yaw %= 360.0F;
        if (yaw < 0.0F) {
            yaw += 360.0F;
        }
    }
}
