package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.selection.TwoPointSelectionScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftFlexLayout;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import common.cn.kafei.simukraft.network.planner.CreatePlanningTaskPacket;
import common.cn.kafei.simukraft.network.planner.PlannerMaterialScanRequestPacket;
import common.cn.kafei.simukraft.planner.PlanOperation;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class PlannerOperationScreenOpener {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 24;
    private static final float TITLE_LEFT_RATIO = 0.30F;
    private static final float TITLE_TOP_RATIO = 0.21F;
    private static final float TITLE_WIDTH_RATIO = 0.40F;
    private static final float TITLE_HEIGHT_RATIO = 0.20F;
    private static final float GRID_LEFT_RATIO = 0.18F;
    private static final float GRID_TOP_RATIO = 0.48F;
    private static final float GRID_WIDTH_RATIO = 0.64F;
    private static final float GRID_HEIGHT_RATIO = 0.28F;

    private PlannerOperationScreenOpener() {
    }

    public static void open(BlockPos buildBoxPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new ModularUIScreen(createOperationUi(buildBoxPos), Component.empty())));
    }

    public static void afterAreaSelected(BlockPos buildBoxPos, PlanOperation operation, BlockPos min, BlockPos max) {
        if (operation == PlanOperation.REMOVE) {
            openRemoveConfirm(buildBoxPos, min, max);
            return;
        }
        PacketDistributor.sendToServer(new PlannerMaterialScanRequestPacket(buildBoxPos, min, max, operation));
        openLoading(buildBoxPos, operation);
    }

    private static ModularUI createOperationUi(BlockPos buildBoxPos) {
        SimuKraftFlexLayout.ScreenSize screenSize = SimuKraftFlexLayout.screenSize();
        UIElement root = SimuKraftFlexLayout.root(screenSize);
        SimuKraftFlexLayout.addTopChrome(root, screenSize, Component.translatable("gui.button.back"), () -> BuildBoxScreenOpener.open(buildBoxPos));

        UIElement titleRegion = titleRegion(screenSize);
        titleRegion.addChild(text(Component.translatable("gui.simukraft.plan_area.choose_operation"), titleWidth(screenSize), SimuKraftUiTheme.TEXT_PRIMARY_COLOR, 20));
        titleRegion.addChild(text(Component.translatable("gui.simukraft.plan_area.choose_operation_hint"), titleWidth(screenSize), SimuKraftUiTheme.TEXT_INFO_COLOR, 16));
        root.addChild(titleRegion);

        UIElement gridRegion = gridRegion(screenSize);
        gridRegion.addChild(actionButton(Component.translatable("gui.simukraft.plan_area.op.remove"), () -> TwoPointSelectionScreen.openPlanning(buildBoxPos, PlanOperation.REMOVE), true));
        gridRegion.addChild(actionButton(Component.translatable("gui.simukraft.plan_area.op.fill"), () -> TwoPointSelectionScreen.openPlanning(buildBoxPos, PlanOperation.FILL), true));
        gridRegion.addChild(actionButton(Component.translatable("gui.simukraft.plan_area.op.replace"), () -> TwoPointSelectionScreen.openPlanning(buildBoxPos, PlanOperation.REPLACE), true));
        root.addChild(gridRegion);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static void openRemoveConfirm(BlockPos buildBoxPos, BlockPos min, BlockPos max) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new ModularUIScreen(createRemoveConfirmUi(buildBoxPos, min, max), Component.empty())));
    }

    private static ModularUI createRemoveConfirmUi(BlockPos buildBoxPos, BlockPos min, BlockPos max) {
        SimuKraftFlexLayout.ScreenSize screenSize = SimuKraftFlexLayout.screenSize();
        UIElement root = SimuKraftFlexLayout.root(screenSize);
        SimuKraftFlexLayout.addTopChrome(root, screenSize, Component.translatable("gui.button.back"), () -> open(buildBoxPos));

        UIElement titleRegion = titleRegion(screenSize);
        titleRegion.addChild(text(Component.translatable("gui.simukraft.plan_area.confirm_remove_title"), titleWidth(screenSize), SimuKraftUiTheme.TEXT_PRIMARY_COLOR, 20));
        titleRegion.addChild(text(Component.translatable("gui.simukraft.area_selection.bounds",
                min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(), volume(min, max)), titleWidth(screenSize), SimuKraftUiTheme.TEXT_WARNING_COLOR, 18));
        root.addChild(titleRegion);

        UIElement gridRegion = gridRegion(screenSize);
        gridRegion.addChild(actionButton(Component.translatable("gui.simukraft.plan_area.confirm_start"), () -> {
            PacketDistributor.sendToServer(new CreatePlanningTaskPacket(buildBoxPos, min, max, PlanOperation.REMOVE, "", "", null, Map.of()));
            Minecraft.getInstance().setScreen(null);
        }, true));
        gridRegion.addChild(actionButton(Component.translatable("gui.button.back"), () -> open(buildBoxPos), true));
        root.addChild(gridRegion);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static void openLoading(BlockPos buildBoxPos, PlanOperation operation) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new ModularUIScreen(createLoadingUi(buildBoxPos, operation), Component.empty())));
    }

    private static ModularUI createLoadingUi(BlockPos buildBoxPos, PlanOperation operation) {
        SimuKraftFlexLayout.ScreenSize screenSize = SimuKraftFlexLayout.screenSize();
        UIElement root = SimuKraftFlexLayout.root(screenSize);
        SimuKraftFlexLayout.addTopChrome(root, screenSize, Component.translatable("gui.button.back"), () -> open(buildBoxPos));
        UIElement titleRegion = titleRegion(screenSize);
        titleRegion.addChild(text(Component.translatable("gui.simukraft.plan_area.material_loading", Component.translatable(operation.translationKey())), titleWidth(screenSize), SimuKraftUiTheme.TEXT_INFO_COLOR, 20));
        root.addChild(titleRegion);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static UIElement actionButton(Component text, Runnable action, boolean active) {
        Button button = new Button();
        button.setText(text);
        button.setActive(active);
        if (active) {
            button.setOnClick(event -> action.run());
        }
        button.layout(layout -> {
            layout.width(BUTTON_WIDTH);
            layout.height(BUTTON_HEIGHT);
        });
        return button;
    }

    private static UIElement titleRegion(SimuKraftFlexLayout.ScreenSize screenSize) {
        UIElement region = SimuKraftFlexLayout.absoluteRegion(
                clamp(Math.round(screenSize.width() * TITLE_LEFT_RATIO), 0, screenSize.width() - 1),
                clamp(Math.round(screenSize.height() * TITLE_TOP_RATIO), 0, screenSize.height() - 1),
                titleWidth(screenSize),
                clamp(Math.round(screenSize.height() * TITLE_HEIGHT_RATIO), 1, screenSize.height()));
        region.layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(6);
        });
        return region;
    }

    private static UIElement gridRegion(SimuKraftFlexLayout.ScreenSize screenSize) {
        UIElement region = SimuKraftFlexLayout.absoluteRegion(
                clamp(Math.round(screenSize.width() * GRID_LEFT_RATIO), 0, screenSize.width() - 1),
                clamp(Math.round(screenSize.height() * GRID_TOP_RATIO), 0, screenSize.height() - 1),
                clamp(Math.round(screenSize.width() * GRID_WIDTH_RATIO), 1, screenSize.width()),
                clamp(Math.round(screenSize.height() * GRID_HEIGHT_RATIO), 1, screenSize.height()));
        region.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP);
            layout.gapAll(8);
        });
        return region;
    }

    private static int titleWidth(SimuKraftFlexLayout.ScreenSize screenSize) {
        return clamp(Math.round(screenSize.width() * TITLE_WIDTH_RATIO), 1, screenSize.width());
    }

    private static UIElement text(Component text, int width, int color, int height) {
        return SimuKraftFlexLayout.text(text, width, color, TextTexture.TextType.NORMAL, true).layout(layout -> {
            layout.width(width);
            layout.height(height);
        });
    }

    private static int volume(BlockPos min, BlockPos max) {
        return Math.max(0, (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
