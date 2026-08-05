package client.cn.kafei.simukraft.client.selection;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.buildbox.PlannerOperationScreenOpener;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.input.SimuKraftKeyMappings;
import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import client.cn.kafei.simukraft.client.ui.SlidingInfoPanel;
import common.cn.kafei.simukraft.logistics.LogisticsDirection;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxSetAreaPacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsBoxActionPacket;
import common.cn.kafei.simukraft.planner.PlanOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class TwoPointSelectionScreen extends Screen implements FreeCameraScreen {
    private static final double REACH_DISTANCE = 128.0D;

    private final TwoPointSelectionManager.SelectionMode mode;
    private final BlockPos ownerPos;
    @Nullable private final PlanOperation operation;
    @Nullable private final LogisticsBoxActionPacket.Action logisticsAction;

    private static boolean sPanelVisible = true;
    private final SlidingInfoPanel panel = new SlidingInfoPanel();

    private TwoPointSelectionScreen(TwoPointSelectionManager.SelectionMode mode, BlockPos ownerPos,
                                    @Nullable PlanOperation operation,
                                    @Nullable LogisticsBoxActionPacket.Action logisticsAction) {
        super(Component.translatable("gui.simukraft.area_selection.title"));
        this.mode           = mode;
        this.ownerPos       = ownerPos.immutable();
        this.operation      = operation;
        this.logisticsAction = logisticsAction;
        panel.setVisible(sPanelVisible);
    }

    /** openPlanning: 打开规划区域两点选区。 */
    public static void openPlanning(BlockPos buildBoxPos, PlanOperation operation) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> mc.setScreen(new TwoPointSelectionScreen(
                TwoPointSelectionManager.SelectionMode.PLANNING, buildBoxPos, operation, null)));
    }

    /** openFarmland: 打开农田区域两点选区。 */
    public static void openFarmland(FarmlandBoxOpenResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> mc.setScreen(new TwoPointSelectionScreen(
                TwoPointSelectionManager.SelectionMode.FARMLAND, packet.boxPos(), null, null)));
    }

    /** openLogistics: 打开物流批量绑定两点选区。 */
    public static void openLogistics(BlockPos boxPos, LogisticsBoxActionPacket.Action action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> mc.setScreen(new TwoPointSelectionScreen(
                TwoPointSelectionManager.SelectionMode.LOGISTICS, boxPos, null, action)));
    }

    @Override
    protected void init() {
        super.init();
        TwoPointSelectionManager.start(mode, ownerPos, operation);
        FreeCameraManager.activate();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 不绘制暗底，保持世界可见
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int sw = this.width, sh = this.height;
        panel.beginRender(g, font, sw, sh, 18);
        // ── 顶部标题条 ──
        g.fill(0, 0, sw, 18, 0xAA000000);
        g.fill(0, 17, sw, 18, 0x55AAAACC);
        g.drawCenteredString(font, modeTitle(), sw / 2, 5, SimuKraftUiTheme.TEXT_WARNING_COLOR);
        // ── 面板内容 ──
        int kh = 10, iX = panel.getInnerX();
        int curY = 18 + 7;
        // ── 选区操作 ──
        panel.drawSectionTitle(g, Component.translatable("gui.simukraft.area_selection.section.select"), curY);
        curY += font.lineHeight + 8;
        curY = panel.drawMouseAction(g, SlidingInfoPanel.MOUSE_LEFT, iX, curY,
                Component.translatable("gui.simukraft.area_selection.point1.label"), SimuKraftUiTheme.TEXT_INFO_COLOR);
        curY = panel.drawMouseAction(g, SlidingInfoPanel.MOUSE_RIGHT, iX, curY,
                Component.translatable("gui.simukraft.area_selection.point2.label"), SimuKraftUiTheme.TEXT_INFO_COLOR);
        panel.drawSeparator(g, curY); curY += 8;
        // ── 摄像机 ──
        panel.drawSectionTitle(g, Component.translatable("gui.simukraft.area_selection.section.camera"), curY);
        curY += font.lineHeight + 8;
        var mc = this.minecraft;
        if (mc != null) {
            int step = 11, kw = 10, wasdCX = panel.getPanelX() + 26;
            panel.drawKeyCap(g, mc.options.keyUp.getTranslatedKeyMessage(),    wasdCX,        curY,        kw, kh);
            panel.drawKeyCap(g, mc.options.keyLeft.getTranslatedKeyMessage(),  wasdCX - step, curY + step, kw, kh);
            panel.drawKeyCap(g, mc.options.keyDown.getTranslatedKeyMessage(),  wasdCX,        curY + step, kw, kh);
            panel.drawKeyCap(g, mc.options.keyRight.getTranslatedKeyMessage(), wasdCX + step, curY + step, kw, kh);
            int modX = panel.getPanelX() + 58;
            int shiftW = Math.max(14, font.width(mc.options.keyShift.getTranslatedKeyMessage()) + 6);
            int spaceW = panel.getPanelW() - 16 - (modX - iX);
            panel.drawKeyCapAt(g, mc.options.keyShift.getTranslatedKeyMessage(),  modX, curY,            shiftW, kh);
            panel.drawKeyCapAt(g, mc.options.keyJump.getTranslatedKeyMessage(),   modX, curY + step,     spaceW, kh);
            int ctrlCapW = Math.max(kw, font.width(Component.literal("Ctrl")) + 6);
            panel.drawKeyCapAt(g, Component.literal("Ctrl"), modX, curY + step * 2 + 2, ctrlCapW, kh);
            g.drawString(font, Component.translatable("gui.building_preview.label.boost"),
                    modX + ctrlCapW + 3, curY + step * 2 + 2 + (kh - font.lineHeight) / 2,
                    SimuKraftUiTheme.TEXT_MUTED_COLOR, false);
            curY += step * 2 + font.lineHeight + 5;
        }
        panel.drawSeparator(g, curY); curY += 8;
        // ── 数据 ──
        panel.drawSectionTitle(g, Component.translatable("gui.simukraft.area_selection.section.data"), curY);
        curY += font.lineHeight + 8;
        TwoPointSelectionManager.SelectionState state = TwoPointSelectionManager.state();
        g.drawString(font, pointLine("gui.simukraft.area_selection.point1", state.point1()),
                iX, curY, SimuKraftUiTheme.TEXT_SECONDARY_COLOR, false); curY += font.lineHeight + 2;
        g.drawString(font, pointLine("gui.simukraft.area_selection.point2", state.point2()),
                iX, curY, SimuKraftUiTheme.TEXT_SECONDARY_COLOR, false); curY += font.lineHeight + 2;
        if (state.point1() != null && state.point2() != null) {
            BlockPos min = TwoPointSelectionManager.min(state.point1(), state.point2());
            BlockPos max = TwoPointSelectionManager.max(state.point1(), state.point2());
            int vol = (max.getX()-min.getX()+1)*(max.getY()-min.getY()+1)*(max.getZ()-min.getZ()+1);
            g.drawString(font, Component.translatable("gui.simukraft.area_selection.volume", vol),
                    iX, curY, SimuKraftUiTheme.TEXT_SUCCESS_COLOR, false);
            curY += font.lineHeight + 2;
        }
        panel.drawSeparator(g, curY); curY += 8;
        // ── 操作键 ──
        curY = panel.drawKeyAction(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.SELECTION_CONFIRM), iX, curY, kh,
                Component.translatable("gui.simukraft.area_selection.action.confirm"), SimuKraftUiTheme.TEXT_WARNING_COLOR);
        curY = panel.drawKeyAction(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.SELECTION_CANCEL), iX, curY, kh,
                Component.translatable("gui.simukraft.area_selection.action.cancel"), SimuKraftUiTheme.TEXT_WARNING_COLOR);
        panel.drawKeyAction(g, Component.literal("Tab"), iX, curY, kh,
                Component.translatable("gui.simukraft.area_selection.action.hide"), SimuKraftUiTheme.TEXT_MUTED_COLOR);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            panel.toggle(); sPanelVisible = panel.isVisible(); return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.SELECTION_POINT_1, keyCode, scanCode)) {
            setPoint(true); return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.SELECTION_POINT_2, keyCode, scanCode)) {
            setPoint(false); return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.SELECTION_CONFIRM, keyCode, scanCode)) {
            confirm(); return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.SELECTION_CANCEL, keyCode, scanCode)) {
            cancel(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (SimuKraftKeyMappings.matchesMouse(SimuKraftKeyMappings.SELECTION_POINT_1, button)) {
            setPoint(true); return true;
        }
        if (SimuKraftKeyMappings.matchesMouse(SimuKraftKeyMappings.SELECTION_POINT_2, button)) {
            setPoint(false); return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        super.removed();
        FreeCameraManager.deactivate();
        TwoPointSelectionManager.clear();
        if (this.minecraft != null && this.minecraft.mouseHandler.isMouseGrabbed()) {
            this.minecraft.mouseHandler.releaseMouse();
        }
    }

    private Component modeTitle() {
        return switch (mode) {
            case FARMLAND  -> Component.translatable("gui.simukraft.area_selection.mode.farmland");
            case LOGISTICS -> Component.translatable("gui.simukraft.area_selection.title_mode",
                    Component.translatable(logisticsAction != null
                            ? "gui.simukraft.logistics.action." + logisticsAction.name().toLowerCase()
                            : "gui.simukraft.area_selection.title"));
            default        -> Component.translatable("gui.simukraft.area_selection.title_mode",
                    Component.translatable(operation != null
                            ? operation.translationKey() : "gui.simukraft.plan_area.op.remove"));
        };
    }

    private void setPoint(boolean first) {
        BlockPos hit = raycastBlock();
        if (hit == null) {
            ClientInfoToast.show(Component.translatable("toast.simukraft.title"),
                    Component.translatable("gui.simukraft.area_selection.no_target"), "warning");
            return;
        }
        if (first) TwoPointSelectionManager.setPoint1(hit);
        else        TwoPointSelectionManager.setPoint2(hit);
    }

    @Nullable
    private BlockPos raycastBlock() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) return null;
        Vec3 cameraPos = FreeCameraManager.getPosition();
        double yawRad   = Math.toRadians(FreeCameraManager.getYaw());
        double pitchRad = Math.toRadians(FreeCameraManager.getPitch());
        Vec3 look = new Vec3(-Math.sin(yawRad)*Math.cos(pitchRad), -Math.sin(pitchRad),
                Math.cos(yawRad)*Math.cos(pitchRad)).normalize();
        BlockHitResult result = this.minecraft.level.clip(new ClipContext(
                cameraPos, cameraPos.add(look.scale(REACH_DISTANCE)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.minecraft.player));
        return result.getType() == HitResult.Type.BLOCK ? result.getBlockPos() : null;
    }

    private void confirm() {
        TwoPointSelectionManager.SelectionState state = TwoPointSelectionManager.state();
        if (state.point1() == null || state.point2() == null) {
            ClientInfoToast.show(Component.translatable("toast.simukraft.title"),
                    Component.translatable("gui.simukraft.area_selection.need_points"), "warning");
            return;
        }
        BlockPos min = TwoPointSelectionManager.min(state.point1(), state.point2());
        BlockPos max = TwoPointSelectionManager.max(state.point1(), state.point2());
        if (mode == TwoPointSelectionManager.SelectionMode.FARMLAND) {
            PacketDistributor.sendToServer(new FarmlandBoxSetAreaPacket(ownerPos, min, max));
            closeSelection(null);
            return;
        }
        if (mode == TwoPointSelectionManager.SelectionMode.LOGISTICS && logisticsAction != null) {
            TwoPointSelectionManager.clear();
            PacketDistributor.sendToServer(new LogisticsBoxActionPacket(
                    ownerPos, logisticsAction, new UUID(0, 0), new UUID(0, 0),
                    BlockPos.ZERO, "", LogisticsDirection.WAREHOUSE_TO_CLIENT, min, max, List.of()));
            if (this.minecraft != null) this.minecraft.setScreen(null);
            return;
        }
        if (operation != null) {
            closeSelection(() -> PlannerOperationScreenOpener.afterAreaSelected(ownerPos, operation, min, max));
        }
    }

    private void cancel() {
        if (mode == TwoPointSelectionManager.SelectionMode.FARMLAND) {
            PacketDistributor.sendToServer(new FarmlandBoxOpenRequestPacket(ownerPos));
            closeSelection(null);
            return;
        }
        if (mode == TwoPointSelectionManager.SelectionMode.LOGISTICS) {
            TwoPointSelectionManager.clear();
            if (this.minecraft != null) this.minecraft.setScreen(null);
            return;
        }
        closeSelection(() -> PlannerOperationScreenOpener.open(ownerPos));
    }

    private void closeSelection(@Nullable Runnable next) {
        Minecraft minecraft = this.minecraft;
        TwoPointSelectionManager.clear();
        FreeCameraManager.deactivate();
        if (minecraft != null && minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
        if (minecraft != null) {
            minecraft.setScreen(null);
            if (next != null) minecraft.execute(next);
        }
    }

    private Component pointLine(String key, @Nullable BlockPos pos) {
        return pos == null
            ? Component.translatable(key + ".empty")
            : Component.translatable(key + ".set", pos.getX(), pos.getY(), pos.getZ());
    }
}
