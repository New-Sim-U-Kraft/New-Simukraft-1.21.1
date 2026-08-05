package common.cn.kafei.simukraft.commercial;

import common.cn.kafei.simukraft.economy.EconomyService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@SuppressWarnings("null")
public record CommercialResource(Type type, String itemId, int count, double money) {
    public CommercialResource {
        itemId = itemId != null ? itemId.trim() : "";
        count = Math.max(0, count);
        money = EconomyService.normalizeAmount(money);
    }

    /** money: 创建资金资源。 */
    public static CommercialResource money(double amount) {
        return new CommercialResource(Type.MONEY, "", 0, amount);
    }

    /** item: 创建物品资源。 */
    public static CommercialResource item(String itemId, int count) {
        return new CommercialResource(Type.ITEM, itemId, count, 0.0D);
    }

    /** valid: 判断资源是否可用于交易。 */
    public boolean valid() {
        return switch (type) {
            case MONEY -> money > 0.0D;
            case ITEM -> !itemId.isBlank() && count > 0 && item() != Items.AIR;
        };
    }

    /** stack: 按资源数量创建物品堆。 */
    public ItemStack stack(int multiplier) {
        if (type != Type.ITEM || multiplier <= 0) {
            return ItemStack.EMPTY;
        }
        Item item = item();
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count * multiplier));
    }

    /** item: 根据资源 ID 解析物品。 */
    public Item item() {
        if (itemId.isBlank()) {
            return Items.AIR;
        }
        try {
            return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(Items.AIR);
        } catch (Exception exception) {
            return Items.AIR;
        }
    }

    /** amountFor: 计算指定交易次数下的资金数量。 */
    public double moneyFor(int multiplier) {
        return EconomyService.normalizeAmount(money * Math.max(1, multiplier));
    }

    /** countFor: 计算指定交易次数下的物品数量。 */
    public int countFor(int multiplier) {
        return Math.max(0, count * Math.max(1, multiplier));
    }

    public enum Type {
        MONEY,
        ITEM
    }
}
