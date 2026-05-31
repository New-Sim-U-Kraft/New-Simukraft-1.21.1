package client.cn.kafei.simukraft.client.buildbox;

import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import common.cn.kafei.simukraft.network.planner.CreatePlanningTaskPacket;
import common.cn.kafei.simukraft.planner.PlanOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 建筑盒"规划区域"界面：键盘操作的自由视角，自由飞行观察并框选 3D 区域，选择操作类型(清除/填充/替换)，
 * 确认后由服务端按体积预扣城市资金并派给规划师执行。填充/替换的方块取自玩家手持(主手=填充/目标方块，副手=替换的源方块)。
 */
@SuppressWarnings("null")
public final class PlanningAreaScreen extends Screen implements FreeCameraScreen {
    private static final int MAX_LEN = 64;
    private static final int MAX_HALF_WIDTH = 32;
    private static final int MAX_HEIGHT = 64;
    private static final int MAX_FAR = 32;
    private static final int MAX_SIDE = 32;
    private static final int MAX_VERTICAL = 32;

    private static BlockPos boxPos;
    private static Direction facing = Direction.NORTH;
    private static PlanOperation operation = PlanOperation.REMOVE;
    private static int length = 5;
    private static int halfWidth = 2;
    private static int height = 1;
    private static int forward = 1;
    private static int side;
    private static int vertical;

    private PlanningAreaScreen() {
        super(Component.translatable("gui.simukraft.plan_area.title"));
    }

    public static void open(BlockPos buildBoxPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        boxPos = buildBoxPos.immutable();
        facing = minecraft.player != null ? minecraft.player.getDirection() : Direction.NORTH;
        operation = PlanOperation.REMOVE;
        length = 5;
        halfWidth = 2;
        height = 1;
        forward = 1;
        side = 0;
        vertical = 0;
        minecraft.execute(() -> minecraft.setScreen(new PlanningAreaScreen()));
    }

    @Override
    protected void init() {
        super.init();
        refreshPreview();
        FreeCameraManager.activate();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = 8;
        int y = 8;
        guiGraphics.drawString(this.font, Component.translatable("gui.simukraft.plan_area.title"), x, y, SimuKraftUiTheme.TEXT_PRIMARY_COLOR);
        y += 14;
        guiGraphics.drawString(this.font, Component.translatable("gui.simukraft.plan_area.operation", Component.translatable(operation.translationKey())), x, y, SimuKraftUiTheme.TEXT_INFO_COLOR);
        y += 12;
        guiGraphics.drawString(this.font, blocksLine(), x, y, SimuKraftUiTheme.TEXT_SECONDARY_COLOR);
        y += 12;
        guiGraphics.drawString(this.font, Component.translatable("gui.simukraft.plan_area.dims", length, halfWidth * 2 + 1, height), x, y, SimuKraftUiTheme.TEXT_SUCCESS_COLOR);
        y += 12;
        guiGraphics.drawString(this.font, Component.translatable("gui.simukraft.plan_area.volume", volume()), x, y, SimuKraftUiTheme.TEXT_SUCCESS_COLOR);
        y += 14;
        guiGraphics.drawString(this.font, Component.translatable("gui.simukraft.plan_area.fly_hint"), x, y, SimuKraftUiTheme.TEXT_MUTED_COLOR);
        y += 12;
        guiGraphics.drawString(this.font, Component.translatable("gui.simukraft.plan_area.controls1"), x, y, SimuKraftUiTheme.TEXT_WARNING_COLOR);
        y += 12;
        guiGraphics.drawString(this.font, Component.translatable("gui.simukraft.plan_area.controls2"), x, y, SimuKraftUiTheme.TEXT_WARNING_COLOR);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> { adjustLength(1); return true; }
            case GLFW.GLFW_KEY_DOWN -> { adjustLength(-1); return true; }
            case GLFW.GLFW_KEY_LEFT -> { adjustHalfWidth(-1); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { adjustHalfWidth(1); return true; }
            case GLFW.GLFW_KEY_E -> { adjustHeight(1); return true; }
            case GLFW.GLFW_KEY_Q -> { adjustHeight(-1); return true; }
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> { adjustForward(1); return true; }
            case GLFW.GLFW_KEY_LEFT_BRACKET -> { adjustForward(-1); return true; }
            case GLFW.GLFW_KEY_PERIOD -> { adjustSide(1); return true; }
            case GLFW.GLFW_KEY_COMMA -> { adjustSide(-1); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { adjustVertical(1); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { adjustVertical(-1); return true; }
            case GLFW.GLFW_KEY_O -> { operation = next(operation); refreshPreview(); rebuild(); return true; }
            case GLFW.GLFW_KEY_R -> { facing = Direction.fromYRot(FreeCameraManager.getYaw()); refreshPreview(); rebuild(); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { confirm(); return true; }
            case GLFW.GLFW_KEY_ESCAPE -> { cancel(); return true; }
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        FreeCameraManager.deactivate();
        clearPreview();
        if (this.minecraft != null && this.minecraft.mouseHandler.isMouseGrabbed()) {
            this.minecraft.mouseHandler.releaseMouse();
        }
    }

    private void adjustLength(int delta) { length = clamp(length + delta, 1, MAX_LEN); refreshPreview(); rebuild(); }
    private void adjustHalfWidth(int delta) { halfWidth = clamp(halfWidth + delta, 0, MAX_HALF_WIDTH); refreshPreview(); rebuild(); }
    private void adjustHeight(int delta) { height = clamp(height + delta, 1, MAX_HEIGHT); refreshPreview(); rebuild(); }
    private void adjustForward(int delta) { forward = clamp(forward + delta, 1, MAX_FAR); refreshPreview(); rebuild(); }
    private void adjustSide(int delta) { side = clamp(side + delta, -MAX_SIDE, MAX_SIDE); refreshPreview(); rebuild(); }
    private void adjustVertical(int delta) { vertical = clamp(vertical + delta, -MAX_VERTICAL, MAX_VERTICAL); refreshPreview(); rebuild(); }

    private void rebuild() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new PlanningAreaScreen());
        }
    }

    private static int volume() {
        return length * (halfWidth * 2 + 1) * height;
    }

    private static BlockPos[] computeBounds() {
        int ax = facing.getStepX();
        int az = facing.getStepZ();
        int px = -az;
        int pz = ax;
        int boxX = boxPos.getX();
        int boxZ = boxPos.getZ();
        int baseY = boxPos.getY() + vertical;
        int nearX = boxX + ax * forward + px * side;
        int nearZ = boxZ + az * forward + pz * side;
        int farX = nearX + ax * (length - 1);
        int farZ = nearZ + az * (length - 1);
        int[] xs = {nearX + px * halfWidth, nearX - px * halfWidth, farX + px * halfWidth, farX - px * halfWidth};
        int[] zs = {nearZ + pz * halfWidth, nearZ - pz * halfWidth, farZ + pz * halfWidth, farZ - pz * halfWidth};
        int minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        int maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        int minZ = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
        int maxZ = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
        return new BlockPos[]{new BlockPos(minX, baseY, minZ), new BlockPos(maxX, baseY + height - 1, maxZ)};
    }

    private static void refreshPreview() {
        BlockPos[] bounds = computeBounds();
        AABB box = new AABB(bounds[0].getX(), bounds[0].getY(), bounds[0].getZ(),
                bounds[1].getX() + 1, bounds[1].getY() + 1, bounds[1].getZ() + 1);
        BuildingBoundsRenderer.setBuildingBoundsVisible(boxPos, box, true);
    }

    private static void clearPreview() {
        if (boxPos != null) {
            BuildingBoundsRenderer.setBuildingBoundsVisible(boxPos, null, false);
        }
    }

    private Component blocksLine() {
        if (operation == PlanOperation.REMOVE) {
            return Component.translatable("gui.simukraft.plan_area.blocks_none");
        }
        if (operation == PlanOperation.FILL) {
            return Component.translatable("gui.simukraft.plan_area.fill_block", blockName(heldBlockId(mainHand())));
        }
        return Component.translatable("gui.simukraft.plan_area.replace_blocks",
                blockName(heldBlockId(mainHand())), blockName(heldBlockId(offHand())));
    }

    private void confirm() {
        BlockPos targetBox = boxPos;
        BlockPos[] bounds = computeBounds();
        String fillId = "";
        String sourceId = "";
        if (operation == PlanOperation.FILL) {
            fillId = heldBlockId(mainHand());
            if (fillId.isEmpty()) {
                hint("gui.simukraft.plan_area.need_fill_block");
                return;
            }
        } else if (operation == PlanOperation.REPLACE) {
            fillId = heldBlockId(mainHand());
            sourceId = heldBlockId(offHand());
            if (fillId.isEmpty() || sourceId.isEmpty()) {
                hint("gui.simukraft.plan_area.need_replace_blocks");
                return;
            }
        }
        clearPreview();
        PacketDistributor.sendToServer(new CreatePlanningTaskPacket(targetBox, bounds[0], bounds[1], operation, fillId, sourceId));
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private void cancel() {
        clearPreview();
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private void hint(String key) {
        client.cn.kafei.simukraft.client.toast.ClientInfoToast.show(
                Component.translatable("toast.simukraft.title"), Component.translatable(key), "warning");
    }

    private ItemStack mainHand() {
        return this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getMainHandItem() : ItemStack.EMPTY;
    }

    private ItemStack offHand() {
        return this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getOffhandItem() : ItemStack.EMPTY;
    }

    private static String heldBlockId(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof BlockItem blockItem) {
            return BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
        }
        return "";
    }

    private static Component blockName(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return Component.translatable("gui.simukraft.plan_area.blocks_none");
        }
        return Component.literal(blockId);
    }

    private static PlanOperation next(PlanOperation current) {
        PlanOperation[] values = PlanOperation.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
