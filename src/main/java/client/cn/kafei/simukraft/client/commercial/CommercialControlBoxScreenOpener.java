package client.cn.kafei.simukraft.client.commercial;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.building.BuildingIntegrityUi;
import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import common.cn.kafei.simukraft.commercial.CommercialConstants;
import common.cn.kafei.simukraft.network.commercial.CommercialControlBoxActionPacket;
import common.cn.kafei.simukraft.network.commercial.CommercialControlBoxDemolishPacket;
import common.cn.kafei.simukraft.network.commercial.CommercialControlBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.commercial.CommercialControlBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.npc.hire.NpcHireFirePacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class CommercialControlBoxScreenOpener {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 208;
    private static final int ACTION_WIDTH = 132;
    private static final int ACTION_HEIGHT = 22;
    private static final int INTEGRITY_HEIGHT = 18;
    private static final float TEXT_ROLL_SPEED = 0.25F;
    private CommercialControlBoxScreenOpener() {
    }

    /** request: 请求服务端打开商业控制箱管理界面。 */
    public static void request(BlockPos pos) {
        PacketDistributor.sendToServer(new CommercialControlBoxOpenRequestPacket(pos));
    }

    /** open: 打开或刷新商业控制箱管理界面。 */
    public static void open(CommercialControlBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        packet.boxPos().immutable();
        syncDisplayedBounds(packet);
        minecraft.execute(() -> minecraft.setScreen(new CommercialControlBoxScreen(createUi(packet), Component.empty())));
    }

    private static ModularUI createUi(CommercialControlBoxOpenResponsePacket packet) {
        int screenWidth = Math.max(320, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(240, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.paddingAll(8);
        });
        root.addChild(SimuKraftUiTheme.createShellPanel(screenWidth, screenHeight));
        root.addChild(topButton("gui.button.done", 5, 5, 50, CommercialControlBoxScreenOpener::close));
        root.addChild(topButton("gui.button.demolish", -5, 5, 60, () -> demolish(packet)));

        UIElement panel = new UIElement().layout(layout -> {
            layout.widthPercent(92);
            layout.maxWidth(PANEL_WIDTH);
            layout.height(PANEL_HEIGHT);
            layout.maxHeight(PANEL_HEIGHT);
            layout.paddingAll(10);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(6);
        }).addClass("simukraft_panel");

        panel.addChild(label(Component.translatable("gui.simukraft.commercial.title"), Horizontal.CENTER, 0xFFFFFF, 16));
        panel.addChild(label(buildingLine(packet), Horizontal.LEFT, 0xFFF5F5A0, 13));
        panel.addChild(label(definitionLine(packet), Horizontal.LEFT, packet.definitionValid() ? 0xFFF5F5A0 : 0xFFFF7070, 13));
        panel.addChild(label(workerLine(packet), Horizontal.LEFT, 0xFFF5F5A0, 13));
        panel.addChild(BuildingIntegrityUi.progressBar(packet.integrityAvailable(), packet.integrityPercent(), INTEGRITY_HEIGHT));

        UIElement managementRow = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(6);
            layout.marginTop(2);
        });
        managementRow.addChild(actionButton(Component.translatable("gui.simukraft.commercial.hire"), () -> hire(packet), packet.hasBuilding() && packet.definitionValid() && !packet.hasWorker()));
        managementRow.addChild(actionButton(Component.translatable("gui.simukraft.building_integrity.repair_free"), () -> repair(packet), packet.integrityAvailable() && (packet.integrityRepairableBlocks() > 0 || packet.integrityManualRepairBlocks() > 0)));
        panel.addChild(managementRow);

        UIElement toolRow = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(6);
        });
        toolRow.addChild(actionButton(Component.translatable("gui.simukraft.commercial.fire"), () -> fire(packet), packet.hasWorker()));
        toolRow.addChild(actionButton(boundsText(packet), () -> toggleBounds(packet), packet.hasBuildingBounds()));
        panel.addChild(toolRow);

        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static Button topButton(String key, int x, int y, int width, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            if (x >= 0) {
                layout.left(x);
            } else {
                layout.right(-x);
            }
            layout.top(y);
            layout.width(width);
            layout.height(22);
        });
        return button;
    }

    private static UIElement actionButton(Component text, Runnable action, boolean active) {
        UIElement slot = new UIElement().layout(layout -> {
            layout.width(ACTION_WIDTH);
            layout.height(ACTION_HEIGHT);
        });
        Button button = new Button();
        button.setText(text);
        button.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).rollSpeed(TEXT_ROLL_SPEED));
        if (active) {
            button.setOnClick(event -> action.run());
        }
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(ACTION_WIDTH);
            layout.height(ACTION_HEIGHT);
        });
        button.setActive(active);
        slot.addChild(button);
        return slot;
    }

    private static UIElement label(Component text, Horizontal horizontal, int color, int height) {
        Label label = new Label();
        label.setText(text);
        label.setOverflowVisible(false);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
        label.textStyle(style -> style
                .textColor(color)
                .textShadow(true)
                .textWrap(TextWrap.HOVER_ROLL)
                .rollSpeed(TEXT_ROLL_SPEED)
                .textAlignHorizontal(horizontal)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static Component buildingLine(CommercialControlBoxOpenResponsePacket packet) {
        Component value = packet.hasBuilding() ? Component.literal(packet.buildingName()) : Component.translatable("gui.simukraft.commercial.none");
        return Component.translatable("gui.simukraft.commercial.building_line", value);
    }

    private static Component definitionLine(CommercialControlBoxOpenResponsePacket packet) {
        Component value = packet.definitionValid() ? Component.literal(packet.definitionName()) : Component.translatable("gui.simukraft.commercial.definition_missing");
        return Component.translatable("gui.simukraft.commercial.definition_line", value);
    }

    private static Component workerLine(CommercialControlBoxOpenResponsePacket packet) {
        Component value = packet.hasWorker() ? Component.literal(packet.workerName()) : Component.translatable("gui.simukraft.commercial.none");
        Component status = Component.translatable(packet.statusKey());
        if (!packet.statusText().isBlank()) {
            status = status.copy().append(Component.literal(" " + packet.statusText()));
        }
        return Component.translatable("gui.simukraft.commercial.worker_line", value)
                .append(Component.literal(" ("))
                .append(status)
                .append(Component.literal(")"));
    }

    private static Component boundsText(CommercialControlBoxOpenResponsePacket packet) {
        return Component.translatable("gui.simukraft.commercial.show_building_bounds", onOffText(BuildingBoundsRenderer.isBuildingBoundsVisible(packet.boxPos())));
    }

    private static Component onOffText(boolean enabled) {
        return Component.translatable(enabled ? "gui.switch.on" : "gui.switch.off");
    }

    private static void toggleBounds(CommercialControlBoxOpenResponsePacket packet) {
        boolean next = !BuildingBoundsRenderer.isBuildingBoundsVisible(packet.boxPos());
        if (next) {
            showBounds(packet);
        } else {
            BuildingBoundsRenderer.setBuildingBoundsVisible(packet.boxPos(), null, false);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new CommercialControlBoxScreen(createUi(packet), Component.empty()));
        }
    }

    private static void syncDisplayedBounds(CommercialControlBoxOpenResponsePacket packet) {
        if (!BuildingBoundsRenderer.isBuildingBoundsVisible(packet.boxPos())) {
            return;
        }
        if (packet.hasBuildingBounds()) {
            showBounds(packet);
        } else {
            BuildingBoundsRenderer.setBuildingBoundsVisible(packet.boxPos(), null, false);
        }
    }

    private static void showBounds(CommercialControlBoxOpenResponsePacket packet) {
        if (!packet.hasBuildingBounds()) {
            return;
        }
        AABB bounds = new AABB(
                packet.boundsMin().getX(),
                packet.boundsMin().getY(),
                packet.boundsMin().getZ(),
                packet.boundsMax().getX() + 1,
                packet.boundsMax().getY() + 1,
                packet.boundsMax().getZ() + 1);
        BuildingBoundsRenderer.setBuildingBoundsVisible(packet.boxPos(), bounds, true);
    }

    private static void repair(CommercialControlBoxOpenResponsePacket packet) {
        PacketDistributor.sendToServer(new CommercialControlBoxActionPacket(packet.boxPos(), CommercialControlBoxActionPacket.Action.REPAIR_BUILDING));
    }

    private static void hire(CommercialControlBoxOpenResponsePacket packet) {
        NpcHireScreen.request(packet.boxPos(), CommercialConstants.HIRE_SOURCE_TYPE, CommercialConstants.HIRE_ROLE);
    }

    private static void fire(CommercialControlBoxOpenResponsePacket packet) {
        if (packet.hasWorker() && packet.workerId() != null) {
            PacketDistributor.sendToServer(new NpcHireFirePacket(packet.boxPos(), CommercialConstants.HIRE_SOURCE_TYPE, CommercialConstants.HIRE_ROLE, packet.workerId()));
        }
    }

    private static void demolish(CommercialControlBoxOpenResponsePacket packet) {
        BuildingBoundsRenderer.setBuildingBoundsVisible(packet.boxPos(), null, false);
        Minecraft.getInstance().setScreen(null);
        PacketDistributor.sendToServer(new CommercialControlBoxDemolishPacket(packet.boxPos()));
    }

    private static void close() {
        Minecraft.getInstance().setScreen(null);
    }

    private static final class CommercialControlBoxScreen extends ModularUIScreen {
        private CommercialControlBoxScreen(ModularUI modularUI, Component title) {
            super(modularUI, title);
        }

        @Override
        public void removed() {
            super.removed();
            Minecraft minecraft = Minecraft.getInstance();
            if (!(minecraft.screen instanceof CommercialControlBoxScreen)) {
            }
        }
    }
}
