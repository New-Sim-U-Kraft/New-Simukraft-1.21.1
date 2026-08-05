package common.cn.kafei.simukraft.registry;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.item.ManifestItem;
import common.cn.kafei.simukraft.item.GeologicalHammerItem;
import common.cn.kafei.simukraft.item.PortableCityCoreItem;
import common.cn.kafei.simukraft.item.food.BuffFoodItem;
import common.cn.kafei.simukraft.item.food.ModFoods;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("null")
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SimuKraft.MOD_ID);

    public static final DeferredHolder<Item, Item> MANIFEST = ITEMS.register("manifest", ManifestItem::new);
    public static final DeferredHolder<Item, Item> PORTABLE_CITY_CORE = ITEMS.register("portable_city_core", PortableCityCoreItem::new);
    public static final DeferredHolder<Item, Item> GOLD_COIN = ITEMS.register("gold_coin", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> HAMBURGER = ITEMS.register("hamburger", () -> new BuffFoodItem(new Item.Properties().food(ModFoods.HAMBURGER)));
    public static final DeferredHolder<Item, Item> FRENCH_FRIES = ITEMS.register("french_fries", () -> new BuffFoodItem(new Item.Properties().food(ModFoods.FRENCH_FRIES)));
    public static final DeferredHolder<Item, Item> CHEESE_CHUNK = ITEMS.register("cheese_chunk", () -> new BuffFoodItem(new Item.Properties().food(ModFoods.CHEESE_CHUNK)));
    public static final DeferredHolder<Item, Item> CHEESE_BURGER = ITEMS.register("cheese_burger", () -> new BuffFoodItem(new Item.Properties().food(ModFoods.CHEESE_BURGER)));
    // 矿物钻井用具
    public static final DeferredHolder<Item, Item> GEOLOGICAL_HAMMER = ITEMS.register("geological_hammer", GeologicalHammerItem::new);
    public static final DeferredHolder<Item, Item> DRILL_ROD_SEGMENT = ITEMS.register("drill_rod_segment", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SHALLOW_DRILL_BIT = ITEMS.register("shallow_drill_bit", () -> new Item(new Item.Properties().durability(500))); // 浅层钻头，开采 500 个产物后损坏
    public static final DeferredHolder<Item, Item> DEEP_DRILL_BIT = ITEMS.register("deep_drill_bit", () -> new Item(new Item.Properties().durability(900))); // 深层钻头，开采 900 个产物后损坏

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
