package client.cn.kafei.simukraft.client.commercial;

import common.cn.kafei.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import common.cn.kafei.simukraft.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
final class CommercialClientTradeUi {
    private CommercialClientTradeUi() {
    }

    /** costStatus: 生成客户端成本满足状态。 */
    static Component costStatus(CommercialTradeOpenResponsePacket packet, CommercialTradeOpenResponsePacket.OfferEntry offer, int times) {
        return Component.translatable(costEnough(packet, offer, times) ? "gui.simukraft.commercial.cost_ok" : "gui.simukraft.commercial.cost_missing");
    }

    /** costEnough: 预判断城市资金和玩家背包是否满足成本。 */
    static boolean costEnough(CommercialTradeOpenResponsePacket packet, CommercialTradeOpenResponsePacket.OfferEntry offer, int times) {
        for (CommercialTradeOpenResponsePacket.ResourceEntry resource : offer.cost()) {
            if (isMoney(resource)) {
                if (packet.cityBalance() + 0.0001D < resource.money() * times) {
                    return false;
                }
            } else if (countPlayerItems(resource) < resource.count() * times) {
                return false;
            }
        }
        return true;
    }

    /** stockEnough: 预判断库存是否足够或是否还有容量。 */
    static boolean stockEnough(CommercialTradeOpenResponsePacket.OfferEntry offer, int times) {
        if (offer == null || offer.stockItem().isBlank() || offer.maxStock() <= 0) {
            return true;
        }
        int amount = stockItemAmount(offer, times);
        if (amount <= 0) {
            return true;
        }
        if (stockLeaves(offer)) {
            return offer.currentStock() >= amount;
        }
        return offer.currentStock() + amount <= offer.maxStock();
    }

    /** stockText: 格式化库存文本。 */
    static String stockText(CommercialTradeOpenResponsePacket.OfferEntry offer) {
        return offer.maxStock() > 0 ? offer.currentStock() + "/" + offer.maxStock() : "-";
    }

    /** stockColor: 按当前数量返回库存提示颜色。 */
    static int stockColor(CommercialTradeOpenResponsePacket.OfferEntry offer, int times) {
        return stockEnough(offer, times) ? 0xFF2A602A : 0xFF8A2020;
    }

    /** resourceLabel: 生成资源数量标签。 */
    static Component resourceLabel(CommercialTradeOpenResponsePacket.ResourceEntry resource) {
        if (isMoney(resource)) {
            return Component.literal(money(resource.money()));
        }
        return Component.literal("x" + Math.max(1, resource.count()));
    }

    /** resourceStack: 生成资源图标物品。 */
    static ItemStack resourceStack(CommercialTradeOpenResponsePacket.ResourceEntry resource) {
        if (isMoney(resource)) {
            return new ItemStack(ModItems.GOLD_COIN.get());
        }
        return new ItemStack(itemById(resource.itemId()), Math.max(1, resource.count()));
    }

    /** money: 格式化城市资金金额。 */
    static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static boolean stockLeaves(CommercialTradeOpenResponsePacket.OfferEntry offer) {
        return offer.result().stream().anyMatch(resource -> !isMoney(resource) && offer.stockItem().equals(resource.itemId()));
    }

    private static int stockItemAmount(CommercialTradeOpenResponsePacket.OfferEntry offer, int times) {
        List<CommercialTradeOpenResponsePacket.ResourceEntry> resources = stockLeaves(offer) ? offer.result() : offer.cost();
        return resources.stream()
                .filter(resource -> !isMoney(resource) && offer.stockItem().equals(resource.itemId()))
                .mapToInt(resource -> resource.count() * times)
                .sum();
    }

    private static int countPlayerItems(CommercialTradeOpenResponsePacket.ResourceEntry resource) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        Item item = itemById(resource.itemId());
        int count = 0;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean isMoney(CommercialTradeOpenResponsePacket.ResourceEntry resource) {
        return "money".equalsIgnoreCase(resource.type());
    }

    private static Item itemById(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Items.BARRIER;
        }
        try {
            return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(Items.BARRIER);
        } catch (Exception exception) {
            return Items.BARRIER;
        }
    }
}
