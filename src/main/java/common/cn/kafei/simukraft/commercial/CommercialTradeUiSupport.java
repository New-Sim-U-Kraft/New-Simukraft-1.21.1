package common.cn.kafei.simukraft.commercial;

import common.cn.kafei.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import common.cn.kafei.simukraft.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
public final class CommercialTradeUiSupport {
    private CommercialTradeUiSupport() {
    }

    /** costEnough: 根据快照预判城市资金和玩家背包是否满足成本。 */
    public static boolean costEnough(CommercialTradeOpenResponsePacket packet, CommercialTradeOpenResponsePacket.OfferEntry offer, Player player, int times) {
        for (CommercialTradeOpenResponsePacket.ResourceEntry resource : offer.cost()) {
            if (isMoney(resource)) {
                if (packet.cityBalance() + 0.0001D < resource.money() * times) {
                    return false;
                }
            } else if (countPlayerItems(player, resource) < resource.count() * times) {
                return false;
            }
        }
        return true;
    }

    /** stockEnough: 根据快照预判商店库存是否足够或是否有容量。 */
    public static boolean stockEnough(CommercialTradeOpenResponsePacket.OfferEntry offer, int times) {
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
    public static String stockText(CommercialTradeOpenResponsePacket.OfferEntry offer) {
        return offer.maxStock() > 0 ? offer.currentStock() + "/" + offer.maxStock() : "-";
    }

    /** stockColor: 根据库存是否满足返回提示颜色。 */
    public static int stockColor(CommercialTradeOpenResponsePacket.OfferEntry offer, int times) {
        return stockEnough(offer, times) ? 0xFF2A602A : 0xFF8A2020;
    }

    /** money: 格式化城市资金金额。 */
    public static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** moneyShort: 格式化槽位角标资金金额。 */
    public static String moneyShort(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.contains(".") && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    /** resourceStack: 将交易资源转换为显示物品。 */
    public static ItemStack resourceStack(CommercialTradeOpenResponsePacket.ResourceEntry resource) {
        if (isMoney(resource)) {
            return new ItemStack(ModItems.GOLD_COIN.get());
        }
        return new ItemStack(itemById(resource.itemId()), Math.max(1, resource.count()));
    }

    /** isMoney: 判断资源是否为城市资金。 */
    public static boolean isMoney(CommercialTradeOpenResponsePacket.ResourceEntry resource) {
        return resource != null && "money".equalsIgnoreCase(resource.type());
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

    private static int countPlayerItems(Player player, CommercialTradeOpenResponsePacket.ResourceEntry resource) {
        if (player == null) {
            return 0;
        }
        Item item = itemById(resource.itemId());
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
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
