package common.cn.kafei.simukraft.item;

import common.cn.kafei.simukraft.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GeologicalHammerItemTest {
    @Test
    void usesIronPickaxeToolPropertiesWithConfiguredDurabilityCost() {
        ItemStack hammer = new ItemStack(ModItems.GEOLOGICAL_HAMMER.get());
        ItemStack ironPickaxe = Items.IRON_PICKAXE.getDefaultInstance();
        Tool hammerTool = hammer.get(DataComponents.TOOL);
        Tool ironPickaxeTool = ironPickaxe.get(DataComponents.TOOL);

        assertNotNull(hammerTool);
        assertNotNull(ironPickaxeTool);
        assertEquals(800, hammer.getMaxDamage());
        assertEquals(2, hammerTool.damagePerBlock());
        assertEquals(ironPickaxeTool.rules(), hammerTool.rules());
        assertEquals(ironPickaxeTool.defaultMiningSpeed(), hammerTool.defaultMiningSpeed());
        assertEquals(ironPickaxe.get(DataComponents.ATTRIBUTE_MODIFIERS), hammer.get(DataComponents.ATTRIBUTE_MODIFIERS));
    }
}
