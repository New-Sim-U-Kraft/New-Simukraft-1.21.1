package common.cn.kafei.simukraft.mineraldrilling;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** MineralDrillingSlotLayout: 以固定顺序创建钻井工具槽与玩家背包槽。 */
@SuppressWarnings("null")
public final class MineralDrillingSlotLayout {
    private MineralDrillingSlotLayout() {
    }

    /** create: 创建两格机器库存和原版 9x3 加快捷栏槽位。 */
    public static UIElement create(MineralDrillingInventory inventory, MineralDrillingUiMetrics metrics) {
        UIElement layer = absolute(0, 0, metrics.width(), metrics.height());
        // 槽位层只命中子槽位，避免透明空白区遮挡下层按钮和深度滑块。
        layer.setAllowHitTest(false);
        int machineSlotX = metrics.machineSlotX();
        int firstSlotY = metrics.firstMachineSlotY();

        layer.addChild(machineSlot(inventory, MineralDrillingInventory.DRILL_ROD_SLOT,
                machineSlotX, firstSlotY));
        layer.addChild(machineSlot(inventory, MineralDrillingInventory.DRILL_BIT_SLOT,
                machineSlotX, firstSlotY + 32));

        int inventoryX = metrics.playerSlotsX();
        int inventoryY = metrics.playerSlotsY();

        InventorySlots playerSlots = new InventorySlots();
        playerSlots.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(inventoryX);
            layout.top(inventoryY);
            layout.width(MineralDrillingUiMetrics.PLAYER_SLOTS_WIDTH);
            layout.height(MineralDrillingUiMetrics.PLAYER_SLOTS_HEIGHT);
        });
        playerSlots.transform(transform -> transform
                .scale(MineralDrillingUiMetrics.PLAYER_SLOTS_VISUAL_SCALE)
                .pivot(0.5F, 0.5F));
        playerSlots.apply(MineralDrillingSlotLayout::styleSlot);
        layer.addChild(playerSlots);
        return layer;
    }

    private static ItemSlot machineSlot(MineralDrillingInventory inventory, int inventorySlot, int x, int y) {
        Slot slot = new Slot(inventory, inventorySlot, 0, 0) {
            /** mayPlace: 在服务端槽位协议层再次校验钻杆与钻头类型。 */
            @Override
            public boolean mayPlace(ItemStack stack) {
                return inventory.canPlaceItem(inventorySlot, stack);
            }

            /** getMaxStackSize: 钻头限制一件，钻杆遵循库存定义。 */
            @Override
            public int getMaxStackSize() {
                return MineralDrillingInventory.slotLimit(inventorySlot);
            }
        };
        ItemSlot itemSlot = new ItemSlot(slot);
        itemSlot.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(MineralDrillingUiMetrics.SLOT_SIZE);
            layout.height(MineralDrillingUiMetrics.SLOT_SIZE);
        });
        styleSlot(itemSlot);
        return itemSlot;
    }

    /** styleSlot: 与 NPC 信息界面相同，仅保留提示开关，让 ORE 样式表负责槽位背景。 */
    private static void styleSlot(ItemSlot itemSlot) {
        itemSlot.slotStyle(style -> style.showItemTooltips(true));
    }

    private static UIElement absolute(int x, int y, int width, int height) {
        return new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(width);
            layout.height(height);
        });
    }
}
