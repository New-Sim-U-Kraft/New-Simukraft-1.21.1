package common.cn.kafei.simukraft.mineraldrilling;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class MineralDrillingOutputServiceTest {
    /** storesAcrossBarrels: 验证产物先补齐同类槽位，再写入下一个木桶。 */
    @Test
    void storesAcrossBarrels() {
        SimpleContainer firstBarrel = new SimpleContainer(2);
        firstBarrel.setItem(0, new ItemStack(Items.IRON_INGOT, 60));
        firstBarrel.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        SimpleContainer secondBarrel = new SimpleContainer(1);
        ItemStack output = new ItemStack(Items.IRON_INGOT, 12);

        assertTrue(MineralDrillingOutputService.canStoreInContainers(List.of(firstBarrel, secondBarrel), output));
        assertTrue(MineralDrillingOutputService.storeInContainers(List.of(firstBarrel, secondBarrel), output));
        assertEquals(64, firstBarrel.getItem(0).getCount());
        assertEquals(8, secondBarrel.getItem(0).getCount());
    }

    /** rejectsFullBarrels: 验证容量不足时不写入任何木桶，供矿脉扣减前置校验使用。 */
    @Test
    void rejectsFullBarrelsWithoutChangingInventory() {
        SimpleContainer barrel = new SimpleContainer(1);
        barrel.setItem(0, new ItemStack(Items.IRON_INGOT, 64));

        assertFalse(MineralDrillingOutputService.canStoreInContainers(List.of(barrel), new ItemStack(Items.IRON_INGOT)));
        assertFalse(MineralDrillingOutputService.storeInContainers(List.of(barrel), new ItemStack(Items.IRON_INGOT)));
        assertEquals(64, barrel.getItem(0).getCount());
    }

    /** mergesOverlappingVeinOutputs: 批量预检累计相同产物，并将两条矿脉的产物合并写入木桶。 */
    @Test
    void mergesOverlappingVeinOutputs() {
        SimpleContainer barrel = new SimpleContainer(1);
        barrel.setItem(0, new ItemStack(Items.IRON_INGOT, 60));
        List<ItemStack> outputs = List.of(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.IRON_INGOT, 2));

        assertTrue(MineralDrillingOutputService.canStoreAllInContainers(List.of(barrel), outputs));
        assertTrue(MineralDrillingOutputService.storeAllInContainers(List.of(barrel), outputs));
        assertEquals(64, barrel.getItem(0).getCount());

        SimpleContainer insufficient = new SimpleContainer(1);
        insufficient.setItem(0, new ItemStack(Items.IRON_INGOT, 62));
        assertFalse(MineralDrillingOutputService.canStoreAllInContainers(List.of(insufficient), outputs));
        assertEquals(62, insufficient.getItem(0).getCount());
    }

    /** requiresBarrel: 验证不允许在没有结构木桶时产出并消耗矿脉。 */
    @Test
    void requiresAtLeastOneBarrel() {
        assertFalse(MineralDrillingOutputService.canStoreInContainers(List.of(), new ItemStack(Items.IRON_INGOT)));
    }
}
