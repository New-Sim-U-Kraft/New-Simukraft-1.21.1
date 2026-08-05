package client.cn.kafei.simukraft.client.mineraldrilling;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("null")
class MineralDrillingClientTextResolverTest {
    /** resolvesProductIdWithClientItemName: 使用本地物品注册表生成可本地化的物品名称。 */
    @Test
    void resolvesProductIdWithClientItemName() {
        Component expected = Component.translatable("gui.simukraft.mineral_drilling.product",
                new ItemStack(Items.RAW_COPPER).getHoverName());

        assertEquals(expected, MineralDrillingClientTextResolver.resolveProductText("minecraft:raw_copper"));
    }

    /** fallsBackForInvalidProductId: 无效资源 ID 不显示服务器传来的原始文本。 */
    @Test
    void fallsBackForInvalidProductId() {
        Component expected = Component.translatable("gui.simukraft.mineral_drilling.product",
                Component.translatable("gui.simukraft.mineral_drilling.none"));

        assertEquals(expected, MineralDrillingClientTextResolver.resolveProductText("invalid product id"));
    }
}
