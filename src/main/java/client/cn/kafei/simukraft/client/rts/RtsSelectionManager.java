package client.cn.kafei.simukraft.client.rts;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.input.SimuKraftKeyMappings;
import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import client.cn.kafei.simukraft.mixin.MixinGameRenderer;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.network.rts.RtsBuildingBoundsRequestPacket;
import common.cn.kafei.simukraft.network.rts.RtsCitizenActionPacket;
import common.cn.kafei.simukraft.network.rts.RtsDemolishPacket;
import common.cn.kafei.simukraft.network.rts.RtsMovePacket;
import common.cn.kafei.simukraft.network.rts.RtsOpenTargetPacket;
import common.cn.kafei.simukraft.network.rts.RtsPlaceBlockPacket;
import common.cn.kafei.simukraft.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** RTS 鼠标目标状态：只负责光标射线、选择状态和鼠标捕获状态。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class RtsSelectionManager {
    private static final double MAX_RAY_DISTANCE = 128.0D;
    private static final long HOLD_PROGRESS_DISPLAY_DELAY_NANOS = 200_000_000L;
    private static final int HOLD_RING_FRAME_SIZE = 24;
    private static final int HOLD_RING_FRAME_COLUMNS = 8;
    private static final int HOLD_RING_FRAME_COUNT = 64;
    private static final ResourceLocation HOLD_RING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SimuKraft.MOD_ID, "textures/gui/rts_hold_ring.png");
    private static volatile boolean active;
    private static volatile BlockPos targetPos;
    private static volatile BlockPos targetPlacementPos;
    private static volatile BlockPos placementClickedPos;
    private static volatile Direction placementFace;
    private static volatile BlockPos selectedPos;
    private static volatile UUID targetCitizenId;
    private static volatile Component targetCitizenName = Component.empty();
    private static volatile BlockPos citizenMoveTarget;
    private static final Set<UUID> selectedCitizenIds = new LinkedHashSet<>();
    private static long lastLeftClickNanos;
    private static BlockPos lastLeftClickPos;
    private static long lastCitizenClickNanos;
    private static UUID lastCitizenClickId;
    private static boolean leftPressed;
    private static boolean moveHoldCompleted;
    private static BlockPos pressedPos;
    private static UUID pressedCitizenId;
    private static long leftPressedAtNanos;

    private RtsSelectionManager() {
    }

    /** isActive: 返回 RTS 鼠标选择模式是否开启。 */
    public static boolean isActive() {
        return active;
    }

    /** beginPreviewSession: 暂停 RTS 方块高亮，仅保留预览界面所需的城市边界。 */
    public static void beginPreviewSession() {
        if (!active) {
            return;
        }
        targetPos = null;
        targetPlacementPos = null;
        clearPlacementTarget();
        selectedPos = null;
        setTargetCitizen(null);
        clearCitizenSelection();
        lastLeftClickPos = null;
        lastLeftClickNanos = 0L;
        clearCitizenDoubleClick();
        clearMoveState();
        BuildingBoundsRenderer.setRtsTarget(null);
        BuildingBoundsRenderer.setRtsSelection(null);
        BuildingBoundsRenderer.setRtsBuildingBounds(null);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
    }

    /** endPreviewSession: 退出 RTS 预览界面后重新获取建筑边界快照。 */
    public static void endPreviewSession() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.player == null || minecraft.level == null) {
            return;
        }
        PacketDistributor.sendToServer(new RtsBuildingBoundsRequestPacket());
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
    }

    /** cursorPlacementPos: 返回 RTS 光标 X/Z 对应最高地表上方的默认落点。 */
    public static BlockPos cursorPlacementPos() {
        Minecraft minecraft = Minecraft.getInstance();
        BlockHitResult hit = rayTraceCursor(minecraft);
        return surfacePlacementPos(minecraft, hit);
    }

    /** cursorTargetPos: 返回 RTS 系统光标命中的原始方块坐标。 */
    public static BlockPos cursorTargetPos() {
        BlockHitResult hit = rayTraceCursor(Minecraft.getInstance());
        return hit != null && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().immutable() : null;
    }

    /** toggle: 切换 RTS 鼠标选择模式。 */
    public static void toggle() {
        if (active) {
            deactivate();
        } else {
            activate();
        }
    }

    /** activate: 开启模式并释放鼠标，让系统光标保持可见。 */
    public static void activate() {
        Minecraft minecraft = Minecraft.getInstance();
        if (active || minecraft.player == null || minecraft.level == null) {
            return;
        }
        active = true;
        selectedPos = null;
        clearPlacementTarget();
        setTargetCitizen(null);
        clearCitizenSelection();
        lastLeftClickPos = null;
        lastLeftClickNanos = 0L;
        clearCitizenDoubleClick();
        clearMoveState();
        FreeCameraManager.activateRts();
        minecraft.mouseHandler.releaseMouse();
    }

    /** deactivate: 关闭模式并清理客户端高亮状态。 */
    public static void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        targetPos = null;
        targetPlacementPos = null;
        clearPlacementTarget();
        selectedPos = null;
        setTargetCitizen(null);
        clearCitizenSelection();
        lastLeftClickPos = null;
        lastLeftClickNanos = 0L;
        clearCitizenDoubleClick();
        clearMoveState();
        FreeCameraManager.deactivate();
        BuildingBoundsRenderer.setRtsTarget(null);
        BuildingBoundsRenderer.setRtsSelection(null);
        BuildingBoundsRenderer.setRtsBuildingBounds(null);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.player != null) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    /** clear: 断线或世界切换时无条件清理状态。 */
    public static void clear() {
        active = false;
        targetPos = null;
        targetPlacementPos = null;
        clearPlacementTarget();
        selectedPos = null;
        setTargetCitizen(null);
        clearCitizenSelection();
        clearCitizenDoubleClick();
        clearMoveState();
        FreeCameraManager.deactivate();
        BuildingBoundsRenderer.setRtsTarget(null);
        BuildingBoundsRenderer.setRtsSelection(null);
        BuildingBoundsRenderer.setRtsBuildingBounds(null);
    }

    /** onClientTick: 处理可修改按键并按帧更新鼠标光标目标。 */
    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (SimuKraftKeyMappings.RTS_TOGGLE.consumeClick() && minecraft.screen == null) {
            toggle();
        }
        if (!active) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()) {
            deactivate();
            return;
        }
        if (minecraft.screen == null && SimuKraftKeyMappings.RTS_DELETE.consumeClick() && selectedPos != null) {
            clearMoveState();
            PacketDistributor.sendToServer(new RtsDemolishPacket(selectedPos));
            selectedPos = null;
            BuildingBoundsRenderer.setRtsSelection(null);
        }
        if (minecraft.screen != null) {
            setTarget(null);
            clearPlacementTarget();
            setTargetCitizen(null);
            return;
        }
        updateTarget(minecraft);
        RtsMovePreviewManager.update(targetPlacementPos);
        updateMoveHold(minecraft);
        // Vanilla may try to grab the mouse after a click; RTS always needs a visible cursor.
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
    }

    /** onMouseButton: 拦截游戏世界中的原版攻击/使用，避免 RTS 光标操作误触发。 */
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!active) {
            return;
        }
        if (RtsMiniMapRenderer.handleMouseButton(event)) {
            event.setCanceled(true);
            return;
        }
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        int button = event.getButton();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }
        event.setCanceled(true);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                handleLeftPress();
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                finishLeftPress();
            }
        } else if (event.getAction() == GLFW.GLFW_PRESS) {
            if ((event.getModifiers() & GLFW.GLFW_MOD_ALT) != 0 || isCameraRotationActive()) {
                return;
            }
            boolean cancelledMovePreview = RtsMovePreviewManager.isActive();
            clearMoveState();
            if (cancelledMovePreview) {
                return;
            }
            if (targetCitizenId != null) {
                selectCitizen(targetCitizenId, false);
                RtsCitizenContextMenuScreen.open(targetCitizenId, targetCitizenName);
                return;
            }
            if (!cancelledMovePreview && targetPos != null) {
                selectedPos = targetPos.immutable();
                clearCitizenSelection();
                BuildingBoundsRenderer.setRtsSelection(selectedPos);
                RtsContextMenuScreen.open(targetPos);
            }
        }
    }

    /** onMouseScrolling: 按住 Alt 滚动时缩放 RTS 相机，阻止原版切换物品栏。 */
    public static void onMouseScrolling(InputEvent.MouseScrollingEvent event) {
        if (handleRtsCameraScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    /** handleRtsCameraScroll: 在 RTS 世界或预览界面中处理 Alt 滚轮缩放。 */
    public static boolean handleRtsCameraScroll(double scrollDelta) {
        if (!canUseRtsCameraControls() || !isAltDown()) {
            return false;
        }
        FreeCameraManager.adjustZoom(scrollDelta, isControlDown());
        return true;
    }

    /** canUseRtsCameraControls: 判断当前是否为可接收 RTS 相机输入的世界或 RTS 预览界面。 */
    public static boolean canUseRtsCameraControls() {
        Minecraft minecraft = Minecraft.getInstance();
        return active && FreeCameraManager.isRtsActive()
                && (minecraft.screen == null || minecraft.screen instanceof FreeCameraScreen);
    }

    /** isCameraRotationActive: 返回 Alt 与右键是否共同处于按下状态。 */
    public static boolean isCameraRotationActive() {
        if (!canUseRtsCameraControls()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS && isAltDown();
    }

    /** handleEscapeKey: 无界面时按 ESC 退出 RTS 并恢复原版鼠标捕获。 */
    public static boolean handleEscapeKey(int keyCode, int action) {
        if (!active || keyCode != GLFW.GLFW_KEY_ESCAPE || action != GLFW.GLFW_PRESS
                || Minecraft.getInstance().screen != null) {
            return false;
        }
        deactivate();
        return true;
    }

    /** handlePreviewMovementKey: 处理抓取预览复用的方向键与高度键。 */
    public static boolean handlePreviewMovementKey(int keyCode, int scanCode, int action) {
        if (!active || !RtsMovePreviewManager.isActive() || action != GLFW.GLFW_PRESS) {
            return false;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_FORWARD, keyCode, scanCode)) {
            RtsMovePreviewManager.moveRelativeToCamera(0, 1);
        } else if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_BACKWARD, keyCode, scanCode)) {
            RtsMovePreviewManager.moveRelativeToCamera(0, -1);
        } else if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_LEFT, keyCode, scanCode)) {
            RtsMovePreviewManager.moveRelativeToCamera(-1, 0);
        } else if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_RIGHT, keyCode, scanCode)) {
            RtsMovePreviewManager.moveRelativeToCamera(1, 0);
        } else if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_UP, keyCode, scanCode)) {
            RtsMovePreviewManager.moveVertical(1);
        } else if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_DOWN, keyCode, scanCode)) {
            RtsMovePreviewManager.moveVertical(-1);
        } else if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_ROTATE, keyCode, scanCode)) {
            RtsMovePreviewManager.rotatePreview();
        } else {
            return false;
        }
        return true;
    }

    /** beginMove: 从右键菜单进入落点选择状态。 */
    public static void beginMove(BlockPos source) {
        if (!active || source == null) {
            return;
        }
        updateTarget(Minecraft.getInstance());
        if (RtsMovePreviewManager.start(source, targetPlacementPos)) {
            lastLeftClickPos = null;
            lastLeftClickNanos = 0L;
            selectedPos = source.immutable();
            BuildingBoundsRenderer.setRtsSelection(selectedPos);
        }
    }

    /** beginCitizenMove: 让右键菜单中的市民进入下一次左键指定落点的状态。 */
    public static void beginCitizenMove(UUID citizenId) {
        if (!active || citizenId == null) {
            return;
        }
        clearMoveState();
        selectedPos = null;
        BuildingBoundsRenderer.setRtsSelection(null);
        selectCitizen(citizenId, false);
    }

    /** renderHoldProgress: 在系统光标旁绘制长按移动的圆形进度。 */
    public static void renderHoldProgress(GuiGraphics graphics) {
        if (!active || !leftPressed || moveHoldCompleted || pressedPos == null || Minecraft.getInstance().screen != null) {
            return;
        }
        long holdNanos = moveHoldNanos();
        long elapsedNanos = System.nanoTime() - leftPressedAtNanos;
        if (elapsedNanos < HOLD_PROGRESS_DISPLAY_DELAY_NANOS) {
            return;
        }
        long visibleHoldNanos = Math.max(1L, holdNanos - HOLD_PROGRESS_DISPLAY_DELAY_NANOS);
        double progress = Math.min(1.0D,
                (elapsedNanos - HOLD_PROGRESS_DISPLAY_DELAY_NANOS) / (double) visibleHoldNanos);
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getScreenWidth();
        int screenHeight = minecraft.getWindow().getScreenHeight();
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }
        int cursorX = (int) (minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / screenWidth);
        int cursorY = (int) (minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / screenHeight);
        int frame = Mth.clamp((int) Math.round(progress * (HOLD_RING_FRAME_COUNT - 1)),
                0, HOLD_RING_FRAME_COUNT - 1);
        int sourceX = frame % HOLD_RING_FRAME_COLUMNS * HOLD_RING_FRAME_SIZE;
        int sourceY = frame / HOLD_RING_FRAME_COLUMNS * HOLD_RING_FRAME_SIZE;
        graphics.blit(HOLD_RING_TEXTURE, cursorX - HOLD_RING_FRAME_SIZE / 2,
                cursorY - HOLD_RING_FRAME_SIZE / 2, sourceX, sourceY,
                HOLD_RING_FRAME_SIZE, HOLD_RING_FRAME_SIZE,
                HOLD_RING_FRAME_SIZE * HOLD_RING_FRAME_COLUMNS,
                HOLD_RING_FRAME_SIZE * HOLD_RING_FRAME_COLUMNS);
    }

    /** targetPos: 返回当前光标命中的方块，供操作层读取。 */
    public static BlockPos targetPos() {
        return targetPos;
    }

    /** selectedPos: 返回当前左键选中的方块，供后续菜单/操作层读取。 */
    public static BlockPos selectedPos() {
        return selectedPos;
    }

    /** hasCitizenSelection: 返回当前是否有可供 RTS 市民标记渲染的选择。 */
    static boolean hasCitizenSelection() {
        return !selectedCitizenIds.isEmpty();
    }

    /** isCitizenSelected: 判断指定市民是否属于当前 RTS 多选集合。 */
    static boolean isCitizenSelected(UUID citizenId) {
        return citizenId != null && selectedCitizenIds.contains(citizenId);
    }

    /** citizenMoveTarget: 返回最近一次 RTS 市民移动命令的地表目标。 */
    static BlockPos citizenMoveTarget() {
        return citizenMoveTarget;
    }

    private static void updateTarget(Minecraft minecraft) {
        CursorRay ray = cursorRay(minecraft);
        BlockHitResult hit = rayTraceCursor(minecraft, ray);
        CitizenEntity citizen = rayTraceCitizen(minecraft, ray, hit);
        setPlacementTarget(hit);
        if (citizen != null) {
            setTargetCitizen(citizen);
            setTarget(null, surfacePlacementPos(minecraft, hit));
            return;
        }
        setTargetCitizen(null);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            setTarget(null);
            return;
        }
        BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
        setTarget(ClientConfig.isRtsTargetBlockEnabled(state) ? hit.getBlockPos() : null,
                surfacePlacementPos(minecraft, hit));
    }

    /** setPlacementTarget: 保存所有命中的方块面，独立于 RTS 高亮方块筛选。 */
    private static void setPlacementTarget(BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            clearPlacementTarget();
            return;
        }
        placementClickedPos = hit.getBlockPos().immutable();
        placementFace = hit.getDirection();
    }

    /** clearPlacementTarget: 清理已失效的远程方块放置目标。 */
    private static void clearPlacementTarget() {
        placementClickedPos = null;
        placementFace = null;
    }

    /** surfacePlacementPos: 将射线命中转换为同列最高可阻挡地表的上方落点。 */
    private static BlockPos surfacePlacementPos(Minecraft minecraft, BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !(minecraft.level instanceof ClientLevel level)) {
            return null;
        }
        BlockPos hitPos = hit.getBlockPos();
        int surfaceY = RtsSurfaceHeightResolver.resolveSurfaceY(level, hitPos.getX(), hitPos.getZ());
        return new BlockPos(hitPos.getX(), surfaceY, hitPos.getZ());
    }

    /** rayTraceCursor: 将系统光标转换为与当前投影一致的世界射线。 */
    private static BlockHitResult rayTraceCursor(Minecraft minecraft) {
        return rayTraceCursor(minecraft, cursorRay(minecraft));
    }

    /** rayTraceCursor: 使用已经计算好的光标射线查找最先命中的方块。 */
    private static BlockHitResult rayTraceCursor(Minecraft minecraft, CursorRay ray) {
        if (!(minecraft.level instanceof ClientLevel level) || ray == null) {
            return null;
        }
        return level.clip(new ClipContext(ray.from(), ray.to(), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                minecraft.player));
    }

    /** cursorRay: 将系统光标转换为与当前透视或正交投影一致的世界射线。 */
    private static CursorRay cursorRay(Minecraft minecraft) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized() || !(minecraft.level instanceof ClientLevel)) {
            return null;
        }
        int screenWidth = minecraft.getWindow().getScreenWidth();
        int screenHeight = minecraft.getWindow().getScreenHeight();
        if (screenWidth <= 0 || screenHeight <= 0) {
            return null;
        }
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        Camera.NearPlane nearPlane = camera.getNearPlane();
        if (FreeCameraManager.isRtsActive()) {
            Vec3 center = nearPlane.getPointOnPlane(0.0F, 0.0F);
            Vec3 forward = center.normalize();
            Vec3 right = nearPlane.getPointOnPlane(1.0F, 0.0F).subtract(center).normalize();
            Vec3 up = nearPlane.getPointOnPlane(0.0F, 1.0F).subtract(center).normalize();
            double aspect = (double) screenWidth / screenHeight;
            double offsetX = (mouseX / screenWidth - 0.5D) * FreeCameraManager.rtsZoom() * aspect;
            double offsetY = (0.5D - mouseY / screenHeight) * FreeCameraManager.rtsZoom();
            Vec3 from = camera.getPosition().add(right.scale(offsetX)).add(up.scale(offsetY));
            Vec3 to = from.add(forward.scale(MAX_RAY_DISTANCE));
            return new CursorRay(from, to);
        }
        float rayScale = cursorFovScale(minecraft);
        float planeX = (float) (mouseX / screenWidth * 2.0D - 1.0D) * rayScale;
        float planeY = (float) (1.0D - mouseY / screenHeight * 2.0D) * rayScale;
        Vec3 direction = nearPlane.getPointOnPlane(planeX, planeY).normalize();
        Vec3 from = camera.getPosition();
        Vec3 to = from.add(direction.scale(MAX_RAY_DISTANCE));
        return new CursorRay(from, to);
    }

    /** rayTraceCitizen: 命中市民且其命中点在方块命中点之前时才返回，避免隔墙选中。 */
    private static CitizenEntity rayTraceCitizen(Minecraft minecraft, CursorRay ray, BlockHitResult blockHit) {
        if (ray == null || minecraft.player == null || !(minecraft.level instanceof ClientLevel)) {
            return null;
        }
        AABB searchBox = new AABB(ray.from(), ray.to()).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(minecraft.player, ray.from(), ray.to(), searchBox,
                entity -> entity instanceof CitizenEntity citizen && citizen.isAlive() && !citizen.isRemoved(),
                MAX_RAY_DISTANCE * MAX_RAY_DISTANCE);
        if (hit == null || !(hit.getEntity() instanceof CitizenEntity citizen)) {
            return null;
        }
        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getLocation().distanceToSqr(ray.from()) <= hit.getLocation().distanceToSqr(ray.from())) {
            return null;
        }
        return citizen;
    }

    private static void setTarget(BlockPos newTarget) {
        setTarget(newTarget, null);
    }

    /** cursorFovScale: 按实际渲染 FOV 校正 NearPlane 横纵偏移，避免边缘射线偏离光标。 */
    private static float cursorFovScale(Minecraft minecraft) {
        MixinGameRenderer renderer = (MixinGameRenderer) minecraft.gameRenderer;
        float modifier = renderer.simukraft$getFovModifier();
        double configuredFov = minecraft.options.fov().get();
        if (configuredFov <= 0.0D || modifier <= 0.0F) {
            return 1.0F;
        }
        double configuredTangent = Math.tan(Math.toRadians(configuredFov * 0.5D));
        if (configuredTangent <= 0.0D) {
            return 1.0F;
        }
        double actualTangent = Math.tan(Math.toRadians(configuredFov * modifier * 0.5D));
        return (float) Mth.clamp(actualTangent / configuredTangent, 0.1D, 4.0D);
    }

    private static void setTarget(BlockPos newTarget, BlockPos newPlacementTarget) {
        BlockPos immutable = newTarget == null ? null : newTarget.immutable();
        BlockPos placement = newPlacementTarget == null ? null : newPlacementTarget.immutable();
        if ((immutable == null ? targetPos == null : immutable.equals(targetPos))
                && (placement == null ? targetPlacementPos == null : placement.equals(targetPlacementPos))) {
            return;
        }
        targetPos = immutable;
        targetPlacementPos = placement;
        BuildingBoundsRenderer.setRtsTarget(immutable);
    }

    /** setTargetCitizen: 同步当前光标命中的市民，命中市民时不显示方块预选框。 */
    private static void setTargetCitizen(CitizenEntity citizen) {
        targetCitizenId = citizen == null ? null : citizen.getUUID();
        targetCitizenName = citizen == null ? Component.empty() : citizen.getDisplayName().copy();
    }

    private static void handleLeftPress() {
        if (isShiftDown() && !RtsMovePreviewManager.isActive() && placementClickedPos != null && placementFace != null) {
            PacketDistributor.sendToServer(new RtsPlaceBlockPacket(placementClickedPos, placementFace));
            lastLeftClickPos = null;
            lastLeftClickNanos = 0L;
            clearMoveState();
            return;
        }
        if (RtsMovePreviewManager.isActive()) {
            if (targetPlacementPos == null) {
                updateTarget(Minecraft.getInstance());
            }
            RtsMovePreviewManager.update(targetPlacementPos);
            if (!RtsMovePreviewManager.isSurfaceReady()) {
                ClientInfoToast.show(
                        Component.translatable("toast.simukraft.title"),
                        Component.translatable("message.simukraft.rts.surface_loading"),
                        "warning"
                );
                return;
            }
            if (ServerConfig.claimProtectionEnabled() && !RtsMovePreviewManager.isDestinationInCurrentCityTerritory()) {
                ClientInfoToast.show(
                        Component.translatable("toast.simukraft.title"),
                        Component.translatable("message.simukraft.construction.outside_city"),
                        "warning"
                );
                return;
            }
            sendMove(RtsMovePreviewManager.sourcePos(), RtsMovePreviewManager.destinationPos(),
                    RtsMovePreviewManager.manualVerticalOffset(), RtsMovePreviewManager.rotationDegrees());
            clearMoveState();
            return;
        }
        if (targetCitizenId != null) {
            pressedCitizenId = targetCitizenId;
            return;
        }
        if (!selectedCitizenIds.isEmpty() && targetPlacementPos != null) {
            sendCitizenMove(targetPlacementPos);
            return;
        }
        pressedPos = targetPos == null ? null : targetPos.immutable();
        leftPressedAtNanos = System.nanoTime();
        leftPressed = pressedPos != null;
        moveHoldCompleted = false;
    }

    private static void updateMoveHold(Minecraft minecraft) {
        if (!leftPressed) {
            return;
        }
        if (GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            finishLeftPress();
            return;
        }
        if (!moveHoldCompleted && hasCompletedMoveHold()) {
            moveHoldCompleted = true;
            lastLeftClickPos = null;
            lastLeftClickNanos = 0L;
        }
    }

    private static void finishLeftPress() {
        if (pressedCitizenId != null) {
            UUID citizenId = pressedCitizenId;
            pressedCitizenId = null;
            finishCitizenClick(citizenId);
            return;
        }
        if (!leftPressed) {
            return;
        }
        BlockPos clicked = pressedPos;
        boolean shouldStartMovePreview = moveHoldCompleted || hasCompletedMoveHold();
        clearHoldState();
        if (shouldStartMovePreview) {
            RtsMovePreviewManager.start(clicked, targetPlacementPos);
            return;
        }
        if (clicked == null) {
            return;
        }
        selectedPos = clicked;
        clearCitizenSelection();
        BuildingBoundsRenderer.setRtsSelection(selectedPos);
        long now = System.nanoTime();
        if (clicked.equals(lastLeftClickPos) && now - lastLeftClickNanos <= 350_000_000L) {
            PacketDistributor.sendToServer(new RtsOpenTargetPacket(clicked));
            lastLeftClickPos = null;
            lastLeftClickNanos = 0L;
            return;
        }
        lastLeftClickPos = clicked;
        lastLeftClickNanos = now;
    }

    private static void sendMove(BlockPos source, BlockPos destination, int manualVerticalOffset, int rotationDegrees) {
        if (source != null && destination != null && (!source.equals(destination) || rotationDegrees != 0)) {
            BlockPos focus = BlockPos.containing(FreeCameraManager.rtsFocus());
            PacketDistributor.sendToServer(new RtsMovePacket(source, destination, manualVerticalOffset, rotationDegrees,
                    focus.getX() >> 4, focus.getZ() >> 4));
        }
    }

    private static long moveHoldNanos() {
        return ClientConfig.rtsMoveHoldSeconds() * 1_000_000_000L;
    }

    /** hasCompletedMoveHold: 根据真实按住时长判定本次点击是否应触发移动。 */
    private static boolean hasCompletedMoveHold() {
        return leftPressedAtNanos > 0L && System.nanoTime() - leftPressedAtNanos >= moveHoldNanos();
    }

    private static void clearMoveState() {
        clearHoldState();
        pressedCitizenId = null;
        RtsMovePreviewManager.clear();
    }

    private static void clearHoldState() {
        leftPressed = false;
        moveHoldCompleted = false;
        pressedPos = null;
        leftPressedAtNanos = 0L;
    }

    /** finishCitizenClick: 处理单选、反选、多选，以及同一市民的双击打开操作。 */
    private static void finishCitizenClick(UUID citizenId) {
        if (citizenId == null) {
            return;
        }
        if (isControlDown()) {
            selectCitizen(citizenId, true);
            clearCitizenDoubleClick();
            return;
        }
        long now = System.nanoTime();
        if (citizenId.equals(lastCitizenClickId) && now - lastCitizenClickNanos <= 350_000_000L) {
            PacketDistributor.sendToServer(new RtsCitizenActionPacket(
                    isShiftDown() ? RtsCitizenActionPacket.Action.OPEN_SHOP : RtsCitizenActionPacket.Action.OPEN_INFO,
                    java.util.List.of(citizenId), BlockPos.ZERO));
            clearCitizenDoubleClick();
            return;
        }
        if (selectedCitizenIds.remove(citizenId)) {
            citizenMoveTarget = null;
            clearCitizenDoubleClick();
            return;
        }
        selectCitizen(citizenId, false);
        lastCitizenClickId = citizenId;
        lastCitizenClickNanos = now;
    }

    /** selectCitizen: 普通点击替换选择，Ctrl 点击切换市民是否加入选择集合。 */
    private static void selectCitizen(UUID citizenId, boolean toggleMembership) {
        if (citizenId == null) {
            return;
        }
        if (toggleMembership) {
            if (!selectedCitizenIds.remove(citizenId)) {
                selectedCitizenIds.add(citizenId);
            }
        } else {
            selectedCitizenIds.clear();
            selectedCitizenIds.add(citizenId);
        }
        citizenMoveTarget = null;
        clearCitizenDoubleClick();
        selectedPos = null;
        BuildingBoundsRenderer.setRtsSelection(null);
    }

    /** clearCitizenSelection: 退出 RTS 或切换回方块选择时释放市民选择集合。 */
    private static void clearCitizenSelection() {
        selectedCitizenIds.clear();
        citizenMoveTarget = null;
        clearCitizenDoubleClick();
    }

    /** clearCitizenDoubleClick: 重置市民双击计时，防止切换目标后误触发打开。 */
    private static void clearCitizenDoubleClick() {
        lastCitizenClickId = null;
        lastCitizenClickNanos = 0L;
    }

    /** sendCitizenMove: 将当前全部已选市民移动至光标命中的地表位置。 */
    private static void sendCitizenMove(BlockPos destination) {
        if (destination != null && !selectedCitizenIds.isEmpty()) {
            citizenMoveTarget = destination.immutable();
            clearCitizenDoubleClick();
            PacketDistributor.sendToServer(new RtsCitizenActionPacket(RtsCitizenActionPacket.Action.MOVE,
                    java.util.List.copyOf(selectedCitizenIds), destination));
        }
    }

    private static boolean isAltDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    /** isControlDown: 判断 Ctrl 是否按下以启用 RTS 快速缩放。 */
    private static boolean isControlDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    /** isShiftDown: 判断 Shift 是否按下以选择市民的商店打开动作。 */
    private static boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    /** CursorRay: 保存已按当前投影换算的光标世界射线端点。 */
    private record CursorRay(Vec3 from, Vec3 to) {
    }
}
