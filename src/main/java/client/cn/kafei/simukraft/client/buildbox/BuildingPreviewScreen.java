package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.input.SimuKraftKeyMappings;
import client.cn.kafei.simukraft.client.rts.RtsSelectionManager;
import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import client.cn.kafei.simukraft.client.ui.SlidingInfoPanel;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.BuildingMetadataReader;
import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import common.cn.kafei.simukraft.network.building.BuildBoxStartConstructionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class BuildingPreviewScreen extends Screen implements FreeCameraScreen {
    private final Screen parent;
    private final BuildingCacheService.BuildingMeta building;
    private final BlockPos buildBoxPos;
    private final BuildingStructure structure;
    private final boolean rtsPreviewMode;

    private static boolean sPanelVisible = true; // Tab 隐藏状态持久化

    private final SlidingInfoPanel panel = new SlidingInfoPanel();
    private final int bedCount;
    private final int doorCount;
    private boolean replaceWithAir = false;
    private boolean rtsPreviewSession;

    public BuildingPreviewScreen(Screen parent, BuildingCacheService.BuildingMeta building, BlockPos buildBoxPos, BuildingStructure structure) {
        super(Component.translatable("gui.building_preview.title"));
        this.parent = parent;
        this.building = building;
        this.buildBoxPos = buildBoxPos;
        this.structure = structure;
        this.rtsPreviewMode = RtsSelectionManager.isActive();
        this.bedCount = (int) structure.blocks().stream()
                .filter(b -> b.state().getBlock() instanceof BedBlock
                        && b.state().getValue(BedBlock.PART) == BedPart.HEAD)
                .count();
        this.doorCount = BuildingCatalog.findBuilding(building.category(), building.metaFileName())
                .map(BuildingMetadataReader::householdCount)
                .orElse(1);
        panel.setVisible(sPanelVisible);
    }

    @Override
    protected void init() {
        super.init();
        BlockPos rtsPlacement = rtsPreviewMode ? RtsSelectionManager.cursorPlacementPos() : null;
        BlockPos previewOrigin = rtsPlacement != null ? rtsPlacement : buildBoxPos.offset(1, 0, 1);
        BuildingPreviewManager.startPreview(structure, previewOrigin);
        if (!BuildingPreviewManager.isPreviewActive()) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
            return;
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            BuildingBoundsRenderer.setPreviewPlayerId(this.minecraft.player.getUUID());
        }
        if (rtsPreviewMode) {
            if (!FreeCameraManager.isActive()) {
                FreeCameraManager.activateRts();
            }
            RtsSelectionManager.beginPreviewSession();
            BuildingPreviewManager.beginRtsPreview(rtsPlacement);
            rtsPreviewSession = true;
            releasePreviewMouse();
        } else {
            FreeCameraManager.activate();
        }
    }

    @Override
    public void removed() {
        super.removed();
        // Screen 被任何原因关闭时（含死亡）均需清理预览状态
        clearPreviewState();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (rtsPreviewMode) {
            return;
        }
        int sw = this.width, sh = this.height;
        // beginRender 必须先调用以初始化 panel.font，panel 从 y=18 绘制不影响标题栏
        panel.beginRender(g, font, sw, sh, 18);
        // ── 顶部标题条 ──
        g.fill(0, 0, sw, 18, 0xAA000000);
        g.fill(0, 17, sw, 18, 0x55AAAACC);
        g.pose().pushPose();
        g.pose().translate(2.0F, 3.0F, 0.0F);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.renderItem(new ItemStack(Items.BARRIER), 0, 0);
        g.pose().popPose();
        Component modeText = Component.translatable(replaceWithAir
                ? "gui.building_preview.mode.replace_air" : "gui.building_preview.mode.keep_obstacles");
        int modeColor = replaceWithAir ? SimuKraftUiTheme.TEXT_SUCCESS_COLOR : SimuKraftUiTheme.TEXT_MUTED_COLOR;
        Component eKey = Component.literal("E");
        int eCapW = Math.max(10, font.width(eKey) + 6);
        panel.drawKeyCapAt(g, eKey, 22, 4, eCapW, 10);
        g.drawString(font, modeText, 22 + eCapW + 3, 4 + (10 - font.lineHeight) / 2, modeColor, false);
        g.drawCenteredString(font,
                Component.translatable("gui.building_preview.title_with_name", building.name()),
                sw / 2, 5, SimuKraftUiTheme.TEXT_PRIMARY_COLOR);
        // ── 住宅信息（床位+户数，仅住宅建筑显示，标题栏模式文字右侧）──
        if (bedCount > 0) {
            String bedStr = String.valueOf(bedCount);
            int bedX = 22 + eCapW + 3 + font.width(modeText) + 6;
            g.pose().pushPose();
            g.pose().translate(bedX, 3.0F, 0.0F);
            g.pose().scale(0.75F, 0.75F, 1.0F);
            g.renderItem(new ItemStack(Items.RED_BED), 0, 0);
            g.pose().popPose();
            g.drawString(font, bedStr, bedX + 14, 1 + (16 - font.lineHeight) / 2, SimuKraftUiTheme.TEXT_PRIMARY_COLOR, false);
            int doorX = bedX + 14 + font.width(bedStr) + 6;
            g.pose().pushPose();
            g.pose().translate(doorX, 3.0F, 0.0F);
            g.pose().scale(0.75F, 0.75F, 1.0F);
            g.renderItem(new ItemStack(Items.OAK_DOOR), 0, 0);
            g.pose().popPose();
            g.drawString(font, String.valueOf(doorCount), doorX + 14, 1 + (16 - font.lineHeight) / 2, SimuKraftUiTheme.TEXT_PRIMARY_COLOR, false);
        }
        int kw = 10, kh = 10, step = 11;
        int pX = panel.getPanelX(), pW = panel.getPanelW(), iX = panel.getInnerX();
        int curY = 18 + 7;
        // ── 预览移动（方向键）──
        panel.drawSectionTitle(g, Component.translatable("gui.building_preview.section.preview_move"), curY);
        curY += font.lineHeight + 8;
        int arrowCX = pX + 30;
        panel.drawKeyCap(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_MOVE_FORWARD),  arrowCX,        curY,        kw, kh);
        panel.drawKeyCap(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_MOVE_LEFT),    arrowCX - step, curY + step, kw, kh);
        panel.drawKeyCap(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_MOVE_BACKWARD),arrowCX,        curY + step, kw, kh);
        panel.drawKeyCap(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_MOVE_RIGHT),   arrowCX + step, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.building_preview.label.move"),
                arrowCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        int heightCX = pX + 62;
        panel.drawKeyCap(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_MOVE_UP),   heightCX, curY,        kw, kh);
        panel.drawKeyCap(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_MOVE_DOWN), heightCX, curY + step, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.building_preview.label.height"),
                heightCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        int rotateCX = pX + 86;
        panel.drawKeyCap(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_ROTATE),
                rotateCX, curY + step / 2, kw, kh);
        g.drawCenteredString(font, Component.translatable("gui.building_preview.label.rotate"),
                rotateCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        curY += step * 2 + font.lineHeight + 5;
        panel.drawSeparator(g, curY); curY += 8;
        // ── 摄像机移动 ──
        panel.drawSectionTitle(g, Component.translatable("gui.building_preview.section.camera"), curY);
        curY += font.lineHeight + 8;
        var mc = this.minecraft;
        if (mc != null) {
            int wasdCX = pX + 30;
            panel.drawKeyCap(g, mc.options.keyUp.getTranslatedKeyMessage(),    wasdCX,        curY,        kw, kh);
            panel.drawKeyCap(g, mc.options.keyLeft.getTranslatedKeyMessage(),  wasdCX - step, curY + step, kw, kh);
            panel.drawKeyCap(g, mc.options.keyDown.getTranslatedKeyMessage(),  wasdCX,        curY + step, kw, kh);
            panel.drawKeyCap(g, mc.options.keyRight.getTranslatedKeyMessage(), wasdCX + step, curY + step, kw, kh);
            g.drawCenteredString(font, Component.translatable("gui.building_preview.label.move"),
                    wasdCX, curY + step * 2 + 2, SimuKraftUiTheme.TEXT_MUTED_COLOR);
            int modX = pX + 58;
            int shiftCapW = Math.max(14, font.width(mc.options.keyShift.getTranslatedKeyMessage()) + 6);
            int spaceCapW = pW - 16 - (modX - iX);
            panel.drawKeyCapAt(g, mc.options.keyShift.getTranslatedKeyMessage(), modX, curY,        shiftCapW, kh);
            panel.drawKeyCapAt(g, mc.options.keyJump.getTranslatedKeyMessage(),  modX, curY + step, spaceCapW, kh);
            int ctrlCapW = Math.max(kw, font.width(Component.literal("Ctrl")) + 6);
            panel.drawKeyCapAt(g, Component.literal("Ctrl"), modX, curY + step * 2 + 2, ctrlCapW, kh);
            g.drawString(font, Component.translatable("gui.building_preview.label.boost"),
                    modX + ctrlCapW + 3, curY + step * 2 + 2 + (kh - font.lineHeight) / 2,
                    SimuKraftUiTheme.TEXT_MUTED_COLOR, false);
        }
        curY += step * 2 + font.lineHeight + 5;
        panel.drawSeparator(g, curY); curY += 8;
        // ── 坐标与方块数 ──
        panel.drawSectionTitle(g, Component.translatable("gui.building_preview.section.data"), curY);
        curY += font.lineHeight + 8;
        BlockPos origin = BuildingPreviewManager.getPreviewOrigin();
        Component originText = Component.translatable("gui.building_preview.origin_info",
                origin.getX(), origin.getY(), origin.getZ(), BuildingPreviewManager.getRotationDegrees());
        Component blockText = Component.translatable("gui.building_preview.block_count", structure.blockCount());
        for (FormattedCharSequence line : font.split(originText, pW - 16)) {
            g.drawString(font, line, iX, curY, SimuKraftUiTheme.TEXT_SUCCESS_COLOR, false);
            curY += font.lineHeight + 2;
        }
        for (FormattedCharSequence line : font.split(blockText, pW - 16)) {
            g.drawString(font, line, iX, curY, SimuKraftUiTheme.TEXT_INFO_COLOR, false);
            curY += font.lineHeight + 2;
        }
        panel.drawSeparator(g, curY); curY += 8;
        // ── 操作键 ──
        curY = panel.drawKeyAction(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_CONFIRM),
                iX, curY, kh, Component.translatable("gui.building_preview.action.start"),
                SimuKraftUiTheme.TEXT_WARNING_COLOR);
        curY = panel.drawKeyAction(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_CANCEL),
                iX, curY, kh, Component.translatable("gui.building_preview.action.cancel"),
                SimuKraftUiTheme.TEXT_WARNING_COLOR);
        panel.drawKeyAction(g, SimuKraftKeyMappings.display(SimuKraftKeyMappings.PREVIEW_TOGGLE_HUD),
                iX, curY, kh, Component.translatable("gui.building_preview.action.hide"),
                SimuKraftUiTheme.TEXT_MUTED_COLOR);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_E) {
            replaceWithAir = !replaceWithAir;
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_TOGGLE_HUD, keyCode, scanCode)) {
            if (!rtsPreviewMode) {
                panel.toggle();
                sPanelVisible = panel.isVisible();
            }
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_CANCEL, keyCode, scanCode)) {
            exitPreview();
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_FORWARD, keyCode, scanCode)) {
            movePreviewRelativeToCamera(0, 1);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_BACKWARD, keyCode, scanCode)) {
            movePreviewRelativeToCamera(0, -1);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_LEFT, keyCode, scanCode)) {
            movePreviewRelativeToCamera(-1, 0);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_RIGHT, keyCode, scanCode)) {
            movePreviewRelativeToCamera(1, 0);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_UP, keyCode, scanCode)) {
            movePreviewVertical(1);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_MOVE_DOWN, keyCode, scanCode)) {
            movePreviewVertical(-1);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_ROTATE, keyCode, scanCode)) {
            BuildingPreviewManager.rotatePreview(structure);
            return true;
        }
        if (SimuKraftKeyMappings.matches(SimuKraftKeyMappings.PREVIEW_CONFIRM, keyCode, scanCode)) {
            confirmPreview();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** tick: RTS 预览中按每帧光标落点更新已抓取建筑的位置。 */
    @Override
    public void tick() {
        super.tick();
        if (rtsPreviewMode) {
            BuildingPreviewManager.updateRtsPreview(RtsSelectionManager.cursorPlacementPos());
        }
    }

    /** mouseClicked: RTS 预览中左键确认建造，右键取消当前抓取预览。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!rtsPreviewMode) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            confirmPreview();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (RtsSelectionManager.isCameraRotationActive()) {
                return true;
            }
            exitPreview();
            return true;
        }
        return true;
    }

    /** mouseScrolled: 在 RTS 建筑预览中将 Alt 滚轮交给俯视相机缩放。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (rtsPreviewMode && RtsSelectionManager.handleRtsCameraScroll(verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** movePreviewRelativeToCamera: 按当前模式移动预览建筑的手动偏移。 */
    private void movePreviewRelativeToCamera(int right, int forward) {
        if (rtsPreviewMode) {
            BuildingPreviewManager.moveRtsPreviewRelativeToCamera(right, forward);
        } else {
            BuildingPreviewManager.movePreviewRelativeToCamera(right, forward);
        }
    }

    /** movePreviewVertical: 按当前模式调整预览建筑的高度偏移。 */
    private void movePreviewVertical(int dy) {
        if (rtsPreviewMode) {
            BuildingPreviewManager.moveRtsPreviewVertical(dy);
        } else {
            BuildingPreviewManager.movePreviewVertical(dy);
        }
    }

    /** confirmPreview: 校验领地后确认当前预览建筑的位置。 */
    private void confirmPreview() {
        if (rtsPreviewMode && !BuildingPreviewManager.isRtsSurfaceReady()) {
            ClientInfoToast.show(
                    Component.translatable("toast.simukraft.title"),
                    Component.translatable("message.simukraft.rts.surface_loading"),
                    "warning"
            );
            return;
        }
        if (ServerConfig.claimProtectionEnabled() && !BuildingBoundsRenderer.isEntireBuildingInCityTerritory()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                ClientInfoToast.show(
                        Component.translatable("toast.simukraft.title"),
                        Component.translatable("message.simukraft.construction.outside_city"),
                        "warning"
                );
            }
            return;
        }
        startConstruction();
    }

    private void exitPreview() {
        var minecraft = this.minecraft;
        clearPreviewState();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void startConstruction() {
        var minecraft = this.minecraft;
        if (minecraft == null) {
            return;
        }
        PacketDistributor.sendToServer(new BuildBoxStartConstructionPacket(buildBoxPos, building.category(), stripExtension(building.metaFileName()), BuildingPreviewManager.getPreviewOrigin(), BuildingPreviewManager.getRotationDegrees(), replaceWithAir));
        clearPreviewState();
        minecraft.setScreen(null);
    }

    /** clearPreviewState: 清理建筑预览，RTS 中保留已启用的俯视相机。 */
    private void clearPreviewState() {
        BuildingPreviewManager.clearPreview();
        if (rtsPreviewSession) {
            RtsSelectionManager.endPreviewSession();
            rtsPreviewSession = false;
        }
        if (!rtsPreviewMode) {
            FreeCameraManager.deactivate();
        }
        BuildingBoundsRenderer.setPreviewPlayerId(null);
        releasePreviewMouse();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private void releasePreviewMouse() {
        if (this.minecraft != null && this.minecraft.mouseHandler.isMouseGrabbed()) {
            this.minecraft.mouseHandler.releaseMouse();
        }
    }
}
