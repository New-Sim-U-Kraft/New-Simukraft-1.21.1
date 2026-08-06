package common.cn.kafei.simukraft.citizen;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class CitizenFoodConsumptionServiceTest {
    @Test
    void identifiesFoodPoisoningItems() {
        assertTrue(CitizenFoodConsumptionService.isFoodPoisoningItem(new ItemStack(Items.SPIDER_EYE)));
        assertTrue(CitizenFoodConsumptionService.isFoodPoisoningItem(new ItemStack(Items.ROTTEN_FLESH)));
        assertTrue(CitizenFoodConsumptionService.isFoodPoisoningItem(new ItemStack(Items.PUFFERFISH)));
    }

    @Test
    void doesNotMarkSafeFoodAsPoisonous() {
        assertFalse(CitizenFoodConsumptionService.isFoodPoisoningItem(new ItemStack(Items.BREAD)));
    }
}
