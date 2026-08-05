package client.cn.kafei.simukraft.client.farmland;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.selection.TwoPointSelectionScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import common.cn.kafei.simukraft.farmland.FarmlandBoxService;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxActionPacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class FarmlandBoxScreenOpener {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 220;
    private static final int ACTION_WIDTH = 150;
    private static final int ACTION_HEIGHT = 22;
    private FarmlandBoxScreenOpener() {
    }

    public static void request(BlockPos pos) {
        PacketDistributor.sendToServer(new FarmlandBoxOpenRequestPacket(pos));
    }

    public static void open(FarmlandBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        packet.boxPos().immutable();
        minecraft.execute(() -> minecraft.setScreen(new FarmlandBoxScreen(createUi(packet), Component.empty())));
    }

    private static ModularUI createUi(FarmlandBoxOpenResponsePacket packet) {
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

        root.addChild(topButton("gui.button.done", 5, 5, 50, FarmlandBoxScreenOpener::close));
        root.addChild(topButton("gui.button.demolish", -5, 5, 60, () -> action(packet, FarmlandBoxActionPacket.Action.DEMOLISH)));

        UIElement panel = new UIElement().layout(layout -> {
            layout.widthPercent(94);
            layout.maxWidth(PANEL_WIDTH);
            layout.height(PANEL_HEIGHT);
            layout.maxHeight(PANEL_HEIGHT);
            layout.paddingAll(10);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(5);
        }).addClass("simukraft_panel");

        panel.addChild(label(Component.translatable("gui.simukraft.farmland_box.title"), Horizontal.CENTER, 0xFFFFFF, 16));
        panel.addChild(label(cropLine(packet), Horizontal.LEFT, 0xFFF5F5A0, 12));
        panel.addChild(label(areaLine(packet), Horizontal.LEFT, 0xFFF5F5A0, 12));
        panel.addChild(label(chestLine(packet), Horizontal.LEFT, 0xFFF5F5A0, 12));
        panel.addChild(label(farmerLine(packet), Horizontal.LEFT, 0xFFF5F5A0, 12));
        panel.addChild(label(runningLine(packet), Horizontal.LEFT, 0xFFF5F5A0, 12));

        boolean editable = !packet.running();
        panel.addChild(actionRow(
                actionButton(Component.translatable("gui.simukraft.farmland_box.cycle_crop"), () -> FarmlandCropScreen.open(packet), editable),
                actionButton(Component.translatable("gui.simukraft.farmland_box.set_area"), () -> TwoPointSelectionScreen.openFarmland(packet), editable)));
        UIElement runRow = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(6);
            layout.marginTop(2);
        });
        runRow.addChild(actionButton(toggleRunText(packet), () -> action(packet, FarmlandBoxActionPacket.Action.TOGGLE_RUN), true));
        panel.addChild(runRow);
        panel.addChild(actionRow(
                actionButton(Component.translatable("gui.simukraft.farmland_box.hire_farmer"), () -> hire(packet), !packet.hasFarmer()),
                actionButton(Component.translatable("gui.simukraft.farmland_box.fire_farmer"), () -> action(packet, FarmlandBoxActionPacket.Action.FIRE), packet.hasFarmer())));

        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement actionRow(UIElement left, UIElement right) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(6);
            layout.marginTop(2);
        });
        row.addChild(left);
        row.addChild(right);
        return row;
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

    private static Label label(Component text, Horizontal horizontal, int color, int height) {
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

    private static Component cropLine(FarmlandBoxOpenResponsePacket packet) {
        Component crop = packet.cropId().isBlank()
                ? Component.translatable("gui.simukraft.farmland_box.none")
                : Component.translatable("gui.simukraft.farmland_box.crop." + packet.cropId());
        return Component.translatable("gui.simukraft.farmland_box.crop_line", crop);
    }

    private static Component areaLine(FarmlandBoxOpenResponsePacket packet) {
        if (!packet.hasPlot()) {
            return Component.translatable("gui.simukraft.farmland_box.area_line", Component.translatable("gui.simukraft.farmland_box.none"));
        }
        int width = packet.plotMax().getX() - packet.plotMin().getX() + 1;
        int depth = packet.plotMax().getZ() - packet.plotMin().getZ() + 1;
        return Component.translatable("gui.simukraft.farmland_box.area_line", Component.literal(width + "x" + depth));
    }

    private static Component chestLine(FarmlandBoxOpenResponsePacket packet) {
        Component value = packet.hasChest()
                ? Component.literal(packet.chestPos().getX() + ", " + packet.chestPos().getY() + ", " + packet.chestPos().getZ())
                : Component.translatable("gui.simukraft.farmland_box.none");
        return Component.translatable("gui.simukraft.farmland_box.chest_line", value);
    }

    private static Component farmerLine(FarmlandBoxOpenResponsePacket packet) {
        Component value = packet.hasFarmer()
                ? Component.literal(packet.farmerName())
                : Component.translatable("gui.simukraft.farmland_box.none");
        return Component.translatable("gui.simukraft.farmland_box.farmer_line", value);
    }

    private static Component runningLine(FarmlandBoxOpenResponsePacket packet) {
        return Component.translatable("gui.simukraft.farmland_box.running_line",
                Component.translatable(packet.running() ? "gui.switch.on" : "gui.switch.off"));
    }

    private static Component toggleRunText(FarmlandBoxOpenResponsePacket packet) {
        return Component.translatable(packet.running()
                ? "gui.simukraft.farmland_box.stop"
                : "gui.simukraft.farmland_box.start");
    }

    private static void action(FarmlandBoxOpenResponsePacket packet, FarmlandBoxActionPacket.Action action) {
        PacketDistributor.sendToServer(new FarmlandBoxActionPacket(packet.boxPos(), action));
    }

    private static void hire(FarmlandBoxOpenResponsePacket packet) {
        NpcHireScreen.request(packet.boxPos(), FarmlandBoxService.HIRE_SOURCE_TYPE, FarmlandBoxService.HIRE_ROLE);
    }

    private static void close() {
        Minecraft.getInstance().setScreen(null);
    }

    private static final class FarmlandBoxScreen extends ModularUIScreen {
        private FarmlandBoxScreen(ModularUI modularUI, Component title) {
            super(modularUI, title);
        }

        @Override
        public void removed() {
            super.removed();
            Minecraft minecraft = Minecraft.getInstance();
            if (!(minecraft.screen instanceof FarmlandBoxScreen)) {
            }
        }
    }
}
