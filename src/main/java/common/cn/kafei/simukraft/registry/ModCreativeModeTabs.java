package common.cn.kafei.simukraft.registry;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("null")
public final class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SimuKraft.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SIMUKRAFT_TAB = CREATIVE_MODE_TABS.register("simukraft_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.simukraft"))
            .icon(() -> new ItemStack(ModBlocks.BUILD_BOX.get()))
            .displayItems((parameters, output) -> {
                // 核心功能
                output.accept(ModBlocks.CITY_CORE.get());
                output.accept(ModBlocks.BUILD_BOX.get());
                output.accept(ModItems.PORTABLE_CITY_CORE.get());
                // 控制箱
                output.accept(ModBlocks.COMMERCIAL_CONTROL_BOX.get());
                output.accept(ModBlocks.INDUSTRIAL_CONTROL_BOX.get());
                output.accept(ModBlocks.MINERAL_DRILLING_CONTROL_BOX.get());
                output.accept(ModBlocks.MEDICAL_CONTROL_BOX.get());
                output.accept(ModBlocks.RESIDENTIAL_CONTROL_BOX.get());
                output.accept(ModBlocks.OTHER_CONTROL_BOX.get());
                output.accept(ModBlocks.LOGISTICS_SERVER_BOX.get());
                output.accept(ModBlocks.LOGISTICS_CLIENT_BOX.get());
                output.accept(ModBlocks.NSUK_FARMLAND_BOX.get());
                // 杂项
                output.accept(ModBlocks.CHEESE_BLOCK.get());
                output.accept(ModItems.MANIFEST.get());
                output.accept(ModItems.GOLD_COIN.get());
                output.accept(ModItems.HAMBURGER.get());
                output.accept(ModItems.FRENCH_FRIES.get());
                output.accept(ModItems.CHEESE_CHUNK.get());
                output.accept(ModItems.CHEESE_BURGER.get());
                // 矿物钻井用具
                output.accept(ModItems.GEOLOGICAL_HAMMER.get());
                output.accept(ModItems.DRILL_ROD_SEGMENT.get());
                output.accept(ModItems.SHALLOW_DRILL_BIT.get());
                output.accept(ModItems.DEEP_DRILL_BIT.get());
            })
            .build());

    // NSUK建筑方块标签页 - 专门存放装饰与建筑类方块
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BUILDING_BLOCKS_TAB = CREATIVE_MODE_TABS.register("building_blocks_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.simukraft.building_blocks"))
            .icon(() -> new ItemStack(ModBlocks.RED_LIGHT_BLOCK.get()))
            .displayItems((parameters, output) -> {
                // 工业外壳
                output.accept(ModBlocks.INDUSTRIAL_HOUSING.get());
                output.accept(ModBlocks.INDUSTRIAL_HOUSING_STAIRS.get());
                output.accept(ModBlocks.INDUSTRIAL_HOUSING_SLAB.get());
                output.accept(ModBlocks.INDUSTRIAL_HOUSING_TRAPDOOR.get());
                // 金属栏杆
                output.accept(ModBlocks.METAL_RAILING.get());
                // 发光方块（彩虹顺序）
                output.accept(ModBlocks.RED_LIGHT_BLOCK.get());
                output.accept(ModBlocks.ORANGE_LIGHT_BLOCK.get());
                output.accept(ModBlocks.YELLOW_LIGHT_BLOCK.get());
                output.accept(ModBlocks.GREEN_LIGHT_BLOCK.get());
                output.accept(ModBlocks.BLUE_LIGHT_BLOCK.get());
                output.accept(ModBlocks.PURPLE_LIGHT_BLOCK.get());
                output.accept(ModBlocks.WHITE_LIGHT_BLOCK.get());
                output.accept(ModBlocks.RAINBOW_LIGHT_BLOCK.get());
            })
            .build());

    private ModCreativeModeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
