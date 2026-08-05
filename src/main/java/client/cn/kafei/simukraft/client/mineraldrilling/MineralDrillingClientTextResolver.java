package client.cn.kafei.simukraft.client.mineraldrilling;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** MineralDrillingClientTextResolver: 在物理客户端将同步的物品 ID 解析为本地化显示名。 */
@OnlyIn(Dist.CLIENT)
final class MineralDrillingClientTextResolver {
    private MineralDrillingClientTextResolver() {
    }

    /** resolveProductText: 仅用客户端物品注册表生成钻井产物文本，服务端始终只发送原始 ID。 */
    static Component resolveProductText(String productId) {
        if (productId == null || productId.isBlank()) {
            return noProductText();
        }
        ResourceLocation itemId = ResourceLocation.tryParse(productId);
        if (itemId == null) {
            return noProductText();
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            return noProductText();
        }
        return Component.translatable("gui.simukraft.mineral_drilling.product",
                new ItemStack(item).getHoverName());
    }

    /** noProductText: 构造当前客户端语言下的空产物提示。 */
    private static Component noProductText() {
        return Component.translatable("gui.simukraft.mineral_drilling.product",
                Component.translatable("gui.simukraft.mineral_drilling.none"));
    }
}
