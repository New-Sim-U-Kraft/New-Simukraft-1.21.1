package client.cn.kafei.simukraft.client.farmland;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxSetCropPacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 作物选择弹出菜单：列出全部受支持作物，点选后发给服务端；服务端回包会自动重开农田盒主界面。
 */

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class FarmlandCropScreen {
    private static final int ITEM_HEIGHT = 22;

    private FarmlandCropScreen() {
    }

    public static void open(FarmlandBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new CropScreen(createUi(packet), Component.empty())));
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
        root.addChild(topButton("gui.button.back", () -> back(packet.boxPos())));

        UIElement panel = new UIElement().layout(layout -> {
            layout.widthPercent(90);
            layout.maxWidth(240);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.paddingAll(10);
            layout.gapAll(5);
        }).addClass("simukraft_panel");

        panel.addChild(label(Component.translatable("gui.simukraft.farmland_box.select_crop_title"), Horizontal.CENTER, 0xFFFFFF, 16));
        for (FarmCrop crop : FarmCrop.values()) {
            boolean selected = crop.id().equals(packet.cropId());
            Component text = selected
                    ? Component.translatable("gui.simukraft.farmland_box.crop_selected", Component.translatable(crop.translationKey()))
                    : Component.translatable(crop.translationKey());
            panel.addChild(cropButton(text, packet.boxPos(), crop));
        }

        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement cropButton(Component text, BlockPos boxPos, FarmCrop crop) {
        UIElement slot = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(ITEM_HEIGHT);
        });
        Button button = new Button();
        button.setText(text);
        button.setOnClick(event -> select(boxPos, crop));
        button.layout(layout -> {
            layout.widthPercent(100);
            layout.height(ITEM_HEIGHT);
        });
        slot.addChild(button);
        return slot;
    }

    private static Button topButton(String key, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(5);
            layout.top(5);
            layout.width(50);
            layout.height(22);
        });
        return button;
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
                .textAlignHorizontal(horizontal)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static void select(BlockPos boxPos, FarmCrop crop) {
        // 发送选择后，服务端回包会自动重开农田盒主界面，无需客户端再请求。
        PacketDistributor.sendToServer(new FarmlandBoxSetCropPacket(boxPos, crop.id()));
    }

    private static void back(BlockPos boxPos) {
        PacketDistributor.sendToServer(new FarmlandBoxOpenRequestPacket(boxPos));
    }

    private static final class CropScreen extends ModularUIScreen {
        private CropScreen(ModularUI modularUI, Component title) {
            super(modularUI, title);
        }
    }
}
