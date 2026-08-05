package common.cn.kafei.simukraft.item;

import common.cn.kafei.simukraft.registry.ModItems;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlot;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlotState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Tool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
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

    /** addsMemorialDescription: 地质锤物品提示应显示本地化叙述。 */
    @Test
    void addsMemorialDescriptionToTooltip() {
        ItemStack hammer = new ItemStack(ModItems.GEOLOGICAL_HAMMER.get());
        var tooltip = new ArrayList<net.minecraft.network.chat.Component>();

        hammer.getItem().appendHoverText(hammer, net.minecraft.world.item.Item.TooltipContext.EMPTY,
                tooltip, TooltipFlag.Default.NORMAL);

        assertTrue(tooltip.stream().anyMatch(component ->
                component.getContents() instanceof TranslatableContents contents
                        && "tooltip.simukraft.geological_hammer.description".equals(contents.getKey())));
    }

    /** limitsProspectingToSixtyBlocksBelowClick: 探查范围仅覆盖点击处向下 60 格且不低于世界下限。 */
    @Test
    void limitsProspectingToSixtyBlocksBelowClick() {
        assertEquals(40, GeologicalHammerItem.scanMinimumY(100, -64));
        assertEquals(-64, GeologicalHammerItem.scanMinimumY(-40, -64));
    }

    /** matchesOnlyVeinsIntersectingProspectingRange: 仅命中与向下探查范围相交的矿脉。 */
    @Test
    void matchesOnlyVeinsIntersectingProspectingRange() {
        VirtualVeinSlot slot = new VirtualVeinSlot("copper", "铜矿脉",
                ResourceLocation.withDefaultNamespace("raw_copper"), 20, 45,
                1, 20, 100, 100, VirtualVeinSlotState.ACTIVE);

        assertTrue(slot.intersectsYRange(40, 100));
        assertFalse(slot.intersectsYRange(-40, 19));
    }
}
