package common.cn.kafei.simukraft.registry;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.commercial.CommercialTradeMenuProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("null")
public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, SimuKraft.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> COMMERCIAL_TRADE = MENUS.register(
            "commercial_trade",
            () -> IMenuTypeExtension.create(CommercialTradeMenuProvider::createClientMenu));

    private ModMenuTypes() {
    }

    /** register: 注册模组容器菜单类型。 */
    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
