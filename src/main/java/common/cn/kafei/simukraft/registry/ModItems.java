package common.cn.kafei.simukraft.registry;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.item.ManifestItem;
import common.cn.kafei.simukraft.item.PortableCityCoreItem;
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

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
