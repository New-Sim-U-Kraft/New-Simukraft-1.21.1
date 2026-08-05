package common.cn.kafei.simukraft.mineraldrilling;

import common.cn.kafei.simukraft.registry.ModItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class MineralDrillingInventoryTest {
    /** serializesValidatedToolSlotsWithNativeComponents: 验证两格工具库存的原版 NBT 往返。 */
    @Test
    void serializesValidatedToolSlotsWithNativeComponents() {
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        MineralDrillingInventory inventory = new MineralDrillingInventory();
        ItemStack rods = new ItemStack(ModItems.DRILL_ROD_SEGMENT.get(), 12);
        rods.set(DataComponents.CUSTOM_NAME, Component.literal("主钻杆"));
        inventory.setItem(MineralDrillingInventory.DRILL_ROD_SLOT, rods);
        inventory.setItem(MineralDrillingInventory.DRILL_BIT_SLOT,
                new ItemStack(ModItems.DEEP_DRILL_BIT.get(), 5));

        CompoundTag saved = inventory.saveToTag(registries);
        MineralDrillingInventory loaded = new MineralDrillingInventory();
        loaded.loadFromTag(saved, registries);

        assertEquals(12, loaded.getItem(MineralDrillingInventory.DRILL_ROD_SLOT).getCount());
        assertEquals(Component.literal("主钻杆"),
                loaded.getItem(MineralDrillingInventory.DRILL_ROD_SLOT).get(DataComponents.CUSTOM_NAME));
        assertEquals(1, loaded.getItem(MineralDrillingInventory.DRILL_BIT_SLOT).getCount());
        assertTrue(loaded.getItem(MineralDrillingInventory.DRILL_BIT_SLOT).is(ModItems.DEEP_DRILL_BIT.get()));
    }

    /** rejectsItemsOutsideTheirDedicatedSlots: 验证容器层不接受错误物品与越界槽位。 */
    @Test
    void rejectsItemsOutsideTheirDedicatedSlots() {
        MineralDrillingInventory inventory = new MineralDrillingInventory();
        inventory.setItem(MineralDrillingInventory.DRILL_ROD_SLOT, new ItemStack(Items.STONE));
        inventory.setItem(MineralDrillingInventory.DRILL_BIT_SLOT,
                new ItemStack(ModItems.DRILL_ROD_SEGMENT.get()));

        assertTrue(inventory.isEmpty());
        assertFalse(inventory.canPlaceItem(-1, ItemStack.EMPTY));
        assertFalse(inventory.canPlaceItem(MineralDrillingInventory.SLOT_COUNT, ItemStack.EMPTY));
    }

    /** drillBitsHaveConfiguredDurability: 钻头必须不可堆叠并采用各自的耐久上限。 */
    @Test
    void drillBitsHaveConfiguredDurability() {
        ItemStack shallow = new ItemStack(ModItems.SHALLOW_DRILL_BIT.get());
        ItemStack deep = new ItemStack(ModItems.DEEP_DRILL_BIT.get());

        assertTrue(shallow.isDamageableItem());
        assertTrue(deep.isDamageableItem());
        assertEquals(500, shallow.getMaxDamage());
        assertEquals(900, deep.getMaxDamage());
        assertEquals(1, shallow.getMaxStackSize());
        assertEquals(1, deep.getMaxStackSize());
    }
}
