package common.cn.kafei.simukraft.citizen;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class CitizenInventoryTest {
    @Test
    void serializesAllSlotsWithNativeItemStackNbt() {
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CitizenInventory inventory = new CitizenInventory();
        inventory.setItem(0, new ItemStack(Items.OAK_LOG, 23));
        inventory.setItem(CitizenInventory.MAIN_HAND_SLOT, new ItemStack(Items.IRON_AXE));

        CompoundTag saved = inventory.saveToTag(registries);
        CitizenInventory loaded = new CitizenInventory();
        loaded.loadFromTag(saved, registries);

        assertEquals(23, loaded.getItem(0).getCount());
        assertTrue(loaded.getItem(0).is(Items.OAK_LOG));
        assertTrue(loaded.getItem(CitizenInventory.MAIN_HAND_SLOT).is(Items.IRON_AXE));
    }

    @Test
    void insertionIsAtomicWhenBackpackIsFull() {
        CitizenInventory inventory = new CitizenInventory();
        for (int slot = 0; slot < CitizenInventory.BACKPACK_SIZE; slot++) {
            inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        }

        assertFalse(inventory.insertBackpackAll(List.of(new ItemStack(Items.DIRT, 1))));
        assertEquals(CitizenInventory.BACKPACK_SIZE, inventory.occupiedBackpackSlots());
        assertTrue(inventory.getItem(0).is(Items.STONE));
    }

    @Test
    void insertionMergesStacksAndExtractionUsesBackpackOnly() {
        CitizenInventory inventory = new CitizenInventory();
        inventory.setItem(0, new ItemStack(Items.OAK_SAPLING, 3));

        assertTrue(inventory.insertBackpackAll(List.of(new ItemStack(Items.OAK_SAPLING, 2))));
        assertEquals(5, inventory.getItem(0).getCount());
        ItemStack extracted = inventory.extractFirstBackpack(stack -> stack.is(Items.OAK_SAPLING)).orElseThrow();
        assertEquals(1, extracted.getCount());
        assertEquals(4, inventory.getItem(0).getCount());
    }
}
