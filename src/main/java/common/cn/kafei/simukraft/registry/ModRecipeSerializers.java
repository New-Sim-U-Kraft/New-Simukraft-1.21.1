package common.cn.kafei.simukraft.registry;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.crafting.ManifestClearRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("null")
public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER, SimuKraft.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ManifestClearRecipe>> MANIFEST_CLEAR = RECIPE_SERIALIZERS.register("manifest_clear", () -> new SimpleCraftingRecipeSerializer<>(ManifestClearRecipe::new));

    private ModRecipeSerializers() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
