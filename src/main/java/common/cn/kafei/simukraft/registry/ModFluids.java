package common.cn.kafei.simukraft.registry;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("null")
public final class ModFluids {
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(BuiltInRegistries.FLUID, SimuKraft.MOD_ID);

    public static final DeferredHolder<Fluid, FlowingFluid> SOURCE_MILK = FLUIDS.register("milk_fluid",
            () -> new BaseFlowingFluid.Source(milkProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_MILK = FLUIDS.register("flowing_milk",
            () -> new BaseFlowingFluid.Flowing(milkProperties()));

    public static final BaseFlowingFluid.Properties MILK_PROPERTIES = new BaseFlowingFluid.Properties(
            ModFluidTypes.MILK, SOURCE_MILK, FLOWING_MILK)
            .bucket(() -> Items.MILK_BUCKET)
            .slopeFindDistance(4)
            .levelDecreasePerBlock(1)
            .block(ModFluids::milkBlock);

    private ModFluids() {
    }

    public static void register(IEventBus modEventBus) {
        FLUIDS.register(modEventBus);
    }

    private static BaseFlowingFluid.Properties milkProperties() {
        return MILK_PROPERTIES;
    }

    private static LiquidBlock milkBlock() {
        return ModBlocks.MILK_BLOCK.get();
    }
}
