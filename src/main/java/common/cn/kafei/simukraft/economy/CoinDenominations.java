package common.cn.kafei.simukraft.economy;

import common.cn.kafei.simukraft.registry.ModItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** CoinDenominations: 现金硬币面值与找零。金币 1 元、银币 0.1 元、铜币 0.01 元。 */
public final class CoinDenominations {
    public static final double GOLD_YUAN = 1.00D;
    public static final double SILVER_YUAN = 0.10D;
    public static final double COPPER_YUAN = 0.01D;

    private CoinDenominations() {
    }

    /** cashValue: 把金/银/铜枚数折成元。 */
    public static double cashValue(int gold, int silver, int copper) {
        return EconomyService.normalizeAmount(gold * GOLD_YUAN + silver * SILVER_YUAN + copper * COPPER_YUAN);
    }

    /** countCash: 统计玩家背包里的现金总额。 */
    public static double countCash(Player player) {
        if (player == null) {
            return 0.0D;
        }
        return cashValue(countItem(player.getInventory(), ModItems.GOLD_COIN.get()),
                countItem(player.getInventory(), ModItems.SILVER_COIN.get()),
                countItem(player.getInventory(), ModItems.COPPER_COIN.get()));
    }

    /** extractCash: 从背包按金、银、铜顺序扣现金，返回实际扣到的金额。 */
    public static double extractCash(Player player, double requested) {
        double want = EconomyService.normalizeAmount(requested);
        if (player == null || want <= 0.0D) {
            return 0.0D;
        }
        double take = Math.min(countCash(player), want);
        Inventory inventory = player.getInventory();
        double remaining = take;
        remaining -= removeByValue(inventory, ModItems.GOLD_COIN.get(), GOLD_YUAN, remaining);
        remaining -= removeByValue(inventory, ModItems.SILVER_COIN.get(), SILVER_YUAN, remaining);
        remaining -= removeByValue(inventory, ModItems.COPPER_COIN.get(), COPPER_YUAN, remaining);
        return EconomyService.normalizeAmount(take - remaining);
    }

    /** insertCash: 按贪心找零把现金发进背包，发不下的部分返回。 */
    public static double insertCash(Player player, double amount) {
        double remaining = EconomyService.normalizeAmount(amount);
        if (player == null || remaining <= 0.0D) {
            return remaining;
        }
        int[] coins = breakdown(remaining);
        remaining -= give(player, ModItems.GOLD_COIN.get(), coins[0]) * GOLD_YUAN;
        remaining -= give(player, ModItems.SILVER_COIN.get(), coins[1]) * SILVER_YUAN;
        remaining -= give(player, ModItems.COPPER_COIN.get(), coins[2]) * COPPER_YUAN;
        return EconomyService.normalizeAmount(Math.max(0.0D, remaining));
    }

    /** breakdown: 把金额拆成金、银、铜枚数。 */
    public static int[] breakdown(double amount) {
        int cents = (int) Math.round(EconomyService.normalizeAmount(amount) * 100.0D);
        int gold = cents / 100;
        cents -= gold * 100;
        int silver = cents / 10;
        cents -= silver * 10;
        return new int[] {gold, silver, cents};
    }

    /** formatYuan: 金额显示，满千用 K。 */
    public static String formatYuan(double amount) {
        double value = EconomyService.normalizeAmount(amount);
        if (Math.abs(value) >= 1000.0D) {
            return String.format(Locale.ROOT, "%.2fK", value / 1000.0D);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** formatCount: 枚数显示，满千用 K。 */
    public static String formatCount(int count) {
        if (count >= 1000) {
            return (count % 1000 == 0)
                    ? (count / 1000) + "K"
                    : String.format(Locale.ROOT, "%.1fK", count / 1000.0D);
        }
        return Integer.toString(count);
    }

    private static int countItem(Inventory inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static double removeByValue(Inventory inventory, Item item, double unit, double remaining) {
        if (remaining < unit - 0.0001D) {
            return 0.0D;
        }
        int need = (int) Math.floor((remaining + 0.0001D) / unit);
        int have = countItem(inventory, item);
        int take = Math.min(need, have);
        int left = take;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int batch = Math.min(left, stack.getCount());
            stack.shrink(batch);
            left -= batch;
        }
        return EconomyService.normalizeAmount(take * unit);
    }

    private static int give(Player player, Item item, int count) {
        if (player == null || count <= 0) {
            return 0;
        }
        int given = 0;
        int remaining = count;
        int max = Math.max(1, item.getDefaultMaxStackSize());
        while (remaining > 0) {
            int batch = Math.min(max, remaining);
            ItemStack stack = new ItemStack(item, batch);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            given += batch;
            remaining -= batch;
        }
        return given;
    }
}
