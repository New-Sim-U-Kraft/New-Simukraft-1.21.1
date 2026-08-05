package client.cn.kafei.simukraft.client.medical;

import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import common.cn.kafei.simukraft.medical.MedicalControlBoxService;
import common.cn.kafei.simukraft.medical.MedicalControlBoxView;
import common.cn.kafei.simukraft.network.medical.MedicalControlBoxDemolishPacket;
import common.cn.kafei.simukraft.network.medical.MedicalControlBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.medical.MedicalControlBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.npc.hire.NpcHireFirePacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** 医疗控制箱 LDLib 单页界面。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class MedicalControlBoxScreenOpener {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 248;
    private static final int BUTTON_WIDTH = 132;
    private static final int BUTTON_HEIGHT = 22;

    private MedicalControlBoxScreenOpener() {
    }

    /** request：请求服务端刷新医疗控制箱视图。 */
    public static void request(BlockPos pos) {
        PacketDistributor.sendToServer(new MedicalControlBoxOpenRequestPacket(pos));
    }

    /** open：打开医疗控制箱界面。 */
    public static void open(MedicalControlBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> minecraft.setScreen(new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(createUi(packet), Component.empty())));
        }
    }

    private static ModularUI createUi(MedicalControlBoxOpenResponsePacket packet) {
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
        root.addChild(doneButton());
        root.addChild(demolishButton(packet));

        UIElement panel = new UIElement().layout(layout -> {
            layout.widthPercent(92);
            layout.maxWidth(PANEL_WIDTH);
            layout.height(PANEL_HEIGHT);
            layout.paddingAll(10);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(5);
        }).addClass("simukraft_panel");
        panel.addChild(label(Component.translatable("gui.simukraft.medical.title"), Horizontal.CENTER, 0xFFFFFFFF, 16));
        panel.addChild(label(Component.translatable("gui.simukraft.medical.building_line",
                packet.hasBuilding() ? packet.buildingName() : Component.translatable("gui.simukraft.medical.none").getString()), Horizontal.LEFT, 0xFFF5F5A0, 13));
        panel.addChild(label(Component.translatable("gui.simukraft.medical.status_line", Component.translatable(packet.statusKey())), Horizontal.LEFT, 0xFFF5F5A0, 13));
        panel.addChild(label(Component.translatable("gui.simukraft.medical.doctor_line",
                packet.hasDoctor() ? packet.doctorName() : Component.translatable("gui.simukraft.medical.none").getString()), Horizontal.LEFT, 0xFFF5F5A0, 13));
        panel.addChild(label(Component.translatable("gui.simukraft.medical.beds_line", packet.occupiedBedCount(), packet.bedCount()), Horizontal.LEFT, 0xFFF5F5A0, 13));
        panel.addChild(label(Component.translatable("gui.simukraft.medical.range_line", packet.serviceRangeRings(), packet.coveredChunkCount()), Horizontal.LEFT, 0xFFF5F5A0, 13));
        panel.addChild(patientList(packet));

        UIElement actions = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
            layout.gapAll(6);
        });
        actions.addChild(actionButton(Component.translatable("gui.simukraft.medical.hire"), () -> hire(packet),
                packet.hasBuilding() && packet.definitionValid() && !packet.hasDoctor()));
        actions.addChild(actionButton(Component.translatable("gui.simukraft.medical.fire"), () -> fire(packet), packet.hasDoctor()));
        panel.addChild(actions);
        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static UIElement patientList(MedicalControlBoxOpenResponsePacket packet) {
        UIElement content = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(2);
        });
        if (packet.patients().isEmpty()) {
            content.addChild(label(Component.translatable("gui.simukraft.medical.no_patients"), Horizontal.LEFT, 0xFFB8B8B8, 13));
        } else {
            for (MedicalControlBoxView.PatientEntry patient : packet.patients()) {
                content.addChild(label(Component.translatable("gui.simukraft.medical.patient_line", patient.name(),
                        Component.translatable(patient.conditionKey()), String.format(java.util.Locale.ROOT, "%.1f/20", patient.health())),
                        Horizontal.LEFT, 0xFFFFFFFF, 13));
            }
        }
        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        scroller.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        });
        scroller.addScrollViewChild(content);
        return scroller;
    }

    private static Button doneButton() {
        Button button = new Button();
        button.setText(Component.translatable("gui.button.done"));
        button.setOnClick(event -> Minecraft.getInstance().setScreen(null));
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(5);
            layout.top(5);
            layout.width(50);
            layout.height(22);
        });
        return button;
    }

    private static Button demolishButton(MedicalControlBoxOpenResponsePacket packet) {
        Button button = new Button();
        button.setText(Component.translatable("gui.button.demolish"));
        button.setActive(packet.hasBuilding());
        if (packet.hasBuilding()) {
            button.setOnClick(event -> demolish(packet));
        }
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(5);
            layout.top(5);
            layout.width(50);
            layout.height(22);
        });
        return button;
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

    private static UIElement label(Component text, Horizontal horizontal, int color, int height) {
        Label label = new Label();
        label.setText(text);
        label.setOverflowVisible(false);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
        label.textStyle(style -> style.textColor(color).textShadow(true).textWrap(TextWrap.HOVER_ROLL)
                .textAlignHorizontal(horizontal).textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static void hire(MedicalControlBoxOpenResponsePacket packet) {
        NpcHireScreen.request(packet.boxPos(), MedicalControlBoxService.HIRE_SOURCE_TYPE, MedicalControlBoxService.HIRE_ROLE);
    }

    private static void fire(MedicalControlBoxOpenResponsePacket packet) {
        if (packet.doctorId() != null) {
            PacketDistributor.sendToServer(new NpcHireFirePacket(packet.boxPos(), MedicalControlBoxService.HIRE_SOURCE_TYPE,
                    MedicalControlBoxService.HIRE_ROLE, packet.doctorId()));
        }
        Minecraft.getInstance().setScreen(null);
    }

    private static void demolish(MedicalControlBoxOpenResponsePacket packet) {
        PacketDistributor.sendToServer(new MedicalControlBoxDemolishPacket(packet.boxPos()));
        Minecraft.getInstance().setScreen(null);
    }
}
