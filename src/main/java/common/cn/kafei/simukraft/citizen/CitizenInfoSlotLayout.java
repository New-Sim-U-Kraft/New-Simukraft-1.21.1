package common.cn.kafei.simukraft.citizen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.mojang.datafixers.util.Pair;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** NPC 信息容器的真实槽位布局，服务端和客户端必须使用相同添加顺序。 */
@SuppressWarnings("null")
public final class CitizenInfoSlotLayout {
    public static final int WORKSPACE_WIDTH = 430;
    public static final int WORKSPACE_HEIGHT = 228;
    public static final int EQUIPMENT_LEFT_X = 84;
    public static final int EQUIPMENT_RIGHT_X = 186;
    public static final int HEAD_Y = 22;
    public static final int CHEST_Y = 46;
    public static final int LEGS_Y = 70;
    public static final int FEET_Y = 94;
    public static final int MAIN_HAND_Y = 58;
    public static final int OFF_HAND_Y = 94;
    public static final int NPC_INVENTORY_X = 220;
    public static final int NPC_INVENTORY_Y = 20;
    public static final int PLAYER_INVENTORY_X = 218;
    public static final int PLAYER_INVENTORY_Y = 140;
    public static final int SLOT_SIZE = 18;

    private CitizenInfoSlotLayout() {
    }

    /** create：创建 4 个盔甲、2 个手持、14 个背包和玩家原版背包槽位。 */
    public static UIElement create(CitizenInventory inventory, LivingEntity owner) {
        UIElement layer = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(WORKSPACE_WIDTH);
            layout.height(WORKSPACE_HEIGHT);
        });

        addEquipmentSlot(layer, inventory, owner, CitizenInventory.HEAD_SLOT, EquipmentSlot.HEAD,
                EQUIPMENT_LEFT_X, HEAD_Y, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET);
        addEquipmentSlot(layer, inventory, owner, CitizenInventory.CHEST_SLOT, EquipmentSlot.CHEST,
                EQUIPMENT_LEFT_X, CHEST_Y, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE);
        addEquipmentSlot(layer, inventory, owner, CitizenInventory.LEGS_SLOT, EquipmentSlot.LEGS,
                EQUIPMENT_LEFT_X, LEGS_Y, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS);
        addEquipmentSlot(layer, inventory, owner, CitizenInventory.FEET_SLOT, EquipmentSlot.FEET,
                EQUIPMENT_LEFT_X, FEET_Y, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
        layer.addChild(itemSlot(new Slot(inventory, CitizenInventory.MAIN_HAND_SLOT, 0, 0),
                EQUIPMENT_RIGHT_X, MAIN_HAND_Y));
        addEmptyIconSlot(layer, inventory, CitizenInventory.OFF_HAND_SLOT,
                EQUIPMENT_RIGHT_X, OFF_HAND_Y, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);

        for (int slot = 0; slot < CitizenInventory.BACKPACK_SIZE; slot++) {
            int x = NPC_INVENTORY_X + slot % CitizenInventory.BACKPACK_COLUMNS * SLOT_SIZE;
            int y = NPC_INVENTORY_Y + slot / CitizenInventory.BACKPACK_COLUMNS * SLOT_SIZE;
            layer.addChild(itemSlot(new Slot(inventory, slot, 0, 0), x, y));
        }

        InventorySlots playerSlots = new InventorySlots();
        playerSlots.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(PLAYER_INVENTORY_X - 1);
            layout.top(PLAYER_INVENTORY_Y - 1);
            layout.width(162);
            layout.height(76);
        });
        layer.addChild(playerSlots);
        return layer;
    }

    private static void addEquipmentSlot(UIElement parent,
                                         CitizenInventory inventory,
                                         LivingEntity owner,
                                         int inventorySlot,
                                         EquipmentSlot equipmentSlot,
                                         int x,
                                         int y,
                                         ResourceLocation emptyIcon) {
        Slot slot = new Slot(inventory, inventorySlot, 0, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return owner == null || stack.canEquip(equipmentSlot, owner);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            /** getNoItemIcon：复用原版玩家物品栏的装备空槽图标。 */
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, emptyIcon);
            }
        };
        parent.addChild(itemSlot(slot, x, y));
    }

    /** addEmptyIconSlot：添加不限制物品类型、但带原版空槽图标的副手槽。 */
    private static void addEmptyIconSlot(UIElement parent,
                                         CitizenInventory inventory,
                                         int inventorySlot,
                                         int x,
                                         int y,
                                         ResourceLocation emptyIcon) {
        Slot slot = new Slot(inventory, inventorySlot, 0, 0) {
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, emptyIcon);
            }
        };
        parent.addChild(itemSlot(slot, x, y));
    }

    private static ItemSlot itemSlot(Slot slot, int x, int y) {
        ItemSlot itemSlot = new ItemSlot(slot);
        itemSlot.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(SLOT_SIZE);
            layout.height(SLOT_SIZE);
        });
        itemSlot.slotStyle(style -> style.showItemTooltips(true));
        return itemSlot;
    }
}
