package client.cn.kafei.simukraft.client.controlbox;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.building.BuildingIntegrityUi;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxDemolishPacket;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxOccupancyPacket;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxOpenResponsePacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.stream.Collectors;

@SuppressWarnings("null")
public final class ResidentialControlBoxScreenOpener {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 208;
    private static final int ACTION_WIDTH = 132;
    private static final int ACTION_HEIGHT = 22;
    private static final int INTEGRITY_HEIGHT = 18;
    private static final float TEXT_ROLL_SPEED = 0.25F;
    private static BlockPos openedControlBoxPos;

    private ResidentialControlBoxScreenOpener() {
    }

    public static void request(BlockPos pos) {
        PacketDistributor.sendToServer(new ResidentialControlBoxOpenRequestPacket(pos));
    }

    public static void open(ResidentialControlBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (!packet.hasBuildingBounds() && packet.capacity() <= 0 && packet.residents().isEmpty()) {
            BuildingBoundsRenderer.setBuildingBoundsVisible(packet.controlBoxPos(), null, false);
        }
        openedControlBoxPos = packet.controlBoxPos().immutable();
        minecraft.execute(() -> minecraft.setScreen(new ResidentialControlBoxScreen(createUi(packet), Component.empty())));
    }

    public static void refreshIfOpen(ResidentialControlBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || openedControlBoxPos == null || !openedControlBoxPos.equals(packet.controlBoxPos())) {
            return;
        }
        openedControlBoxPos = packet.controlBoxPos().immutable();
        minecraft.execute(() -> {
            if (openedControlBoxPos != null && openedControlBoxPos.equals(packet.controlBoxPos()) && minecraft.screen instanceof ResidentialControlBoxScreen) {
                minecraft.setScreen(new ResidentialControlBoxScreen(createUi(packet), Component.empty()));
            }
        });
    }

    private static ModularUI createUi(ResidentialControlBoxOpenResponsePacket packet) {
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

        root.addChild(topButton("gui.button.done", 5, 5, 50, ResidentialControlBoxScreenOpener::close));
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

        panel.addChild(label(Component.translatable("gui.residential_control_box.panel_title"), Horizontal.CENTER, 0xFFFFFF, 16));
        panel.addChild(label(buildingLine(packet), Horizontal.LEFT, 0xFFF5F5A0, 13));
        panel.addChild(label(Component.translatable(packet.buildingTypeKey()), Horizontal.LEFT, 0xFFF5F5A0, 13));
        panel.addChild(label(residentLine(packet), Horizontal.LEFT, 0xFFF5F5A0, 13));
        panel.addChild(BuildingIntegrityUi.progressBar(packet.integrityAvailable(), packet.integrityPercent(), INTEGRITY_HEIGHT));

        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(6);
            layout.marginTop(2);
        });
        row.addChild(actionButton(boundsText(packet), () -> toggleBounds(packet), packet.hasBuildingBounds()));
        row.addChild(actionButton(BuildingIntegrityUi.repairText(packet.integrityRepairCost()), () -> repair(packet), packet.integrityAvailable() && (packet.integrityRepairableBlocks() > 0 || packet.integrityManualRepairBlocks() > 0)));
        panel.addChild(row);

        UIElement occupancyRow = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(6);
        });
        occupancyRow.addChild(actionButton(Component.translatable("gui.residential_control_box.assign_existing"), () -> occupancy(packet, ResidentialControlBoxOccupancyPacket.Action.ASSIGN_EXISTING), packet.hasBuildingBounds()));
        occupancyRow.addChild(actionButton(Component.translatable("gui.residential_control_box.spawn_new"), () -> occupancy(packet, ResidentialControlBoxOccupancyPacket.Action.SPAWN_NEW), packet.hasBuildingBounds()));
        panel.addChild(occupancyRow);

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
        slot.addChild(button);
        button.setActive(active);
        return slot;
    }

    private static UIElement label(Component text, Horizontal horizontal, int color, int height) {
        Label label = new Label();
        label.setText(text);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
        label.textStyle(style -> style
                .textColor(color)
                .textShadow(true)
                .textWrap(TextWrap.HOVER_ROLL)
                .textAlignHorizontal(horizontal)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static Component buildingLine(ResidentialControlBoxOpenResponsePacket packet) {
        String buildingName = packet.buildingName();
        if ("gui.residential_control_box.unknown_building".equals(buildingName)) {
            return Component.translatable("gui.residential_control_box.building_line", Component.translatable(buildingName));
        }
        return Component.translatable("gui.residential_control_box.building_line", buildingName);
    }

    private static Component residentLine(ResidentialControlBoxOpenResponsePacket packet) {
        Component residentText;
        if (packet.residents().isEmpty()) {
            residentText = Component.translatable("gui.residential_control_box.no_resident");
        } else {
            residentText = Component.literal(packet.residents().stream()
                    .map(ResidentialControlBoxOpenResponsePacket.ResidentEntry::name)
                    .collect(Collectors.joining(", ")));
        }
        return Component.translatable("gui.residential_control_box.resident_line", residentText)
                .append(Component.literal(" "))
                .append(Component.translatable("gui.residential_control_box.capacity", packet.residentCount(), packet.capacity()));
    }

    private static Component boundsText(ResidentialControlBoxOpenResponsePacket packet) {
        return Component.translatable("gui.residential_control_box.show_building_bounds", onOffText(BuildingBoundsRenderer.isBuildingBoundsVisible(packet.controlBoxPos())));
    }

    private static Component onOffText(boolean enabled) {
        return Component.translatable(enabled ? "gui.switch.on" : "gui.switch.off");
    }

    private static void toggleBounds(ResidentialControlBoxOpenResponsePacket packet) {
        boolean next = !BuildingBoundsRenderer.isBuildingBoundsVisible(packet.controlBoxPos());
        AABB bounds = new AABB(packet.boundsMin().getX(), packet.boundsMin().getY(), packet.boundsMin().getZ(), packet.boundsMax().getX() + 1, packet.boundsMax().getY() + 1, packet.boundsMax().getZ() + 1);
        BuildingBoundsRenderer.setBuildingBoundsVisible(packet.controlBoxPos(), bounds, packet.residentialPoiPositions(), next);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new ResidentialControlBoxScreen(createUi(packet), Component.empty()));
        }
    }

    private static void demolish(ResidentialControlBoxOpenResponsePacket packet) {
        BuildingBoundsRenderer.setBuildingBoundsVisible(packet.controlBoxPos(), null, false);
        PacketDistributor.sendToServer(new ResidentialControlBoxDemolishPacket(packet.controlBoxPos()));
    }

    private static void occupancy(ResidentialControlBoxOpenResponsePacket packet, ResidentialControlBoxOccupancyPacket.Action action) {
        PacketDistributor.sendToServer(new ResidentialControlBoxOccupancyPacket(packet.controlBoxPos(), action));
    }

    private static void repair(ResidentialControlBoxOpenResponsePacket packet) {
        PacketDistributor.sendToServer(new ResidentialControlBoxOccupancyPacket(packet.controlBoxPos(), ResidentialControlBoxOccupancyPacket.Action.REPAIR_BUILDING));
    }

    private static void close() {
        openedControlBoxPos = null;
        Minecraft.getInstance().setScreen(null);
    }

    private static final class ResidentialControlBoxScreen extends ModularUIScreen {
        private ResidentialControlBoxScreen(ModularUI modularUI, Component title) {
            super(modularUI, title);
        }

        @Override
        public void removed() {
            super.removed();
            Minecraft minecraft = Minecraft.getInstance();
            if (!(minecraft.screen instanceof ResidentialControlBoxScreen)) {
                openedControlBoxPos = null;
            }
        }
    }
}
