package common.cn.kafei.simukraft.item.food;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class BuffFoodItem extends Item {
    public BuffFoodItem(Properties properties) {
        super(properties);
    }

    public static FoodProperties.Builder createFoodBuilder(
            int nutrition,
            float saturation,
            @NotNull List<EffectEntry> effects) {
        FoodProperties.Builder builder = new FoodProperties.Builder()
                .nutrition(nutrition)
                .saturationModifier(saturation);

        for (EffectEntry entry : effects) {
            builder.effect(Objects.requireNonNull(entry.effectSupplier()), entry.probability());
        }

        return builder;
    }

    public record EffectEntry(@NotNull Supplier<MobEffectInstance> effectSupplier, float probability) {
    }
}
