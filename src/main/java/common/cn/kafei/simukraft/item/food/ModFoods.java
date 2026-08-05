package common.cn.kafei.simukraft.item.food;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;

import java.util.List;

@SuppressWarnings("null")
public final class ModFoods {

    private ModFoods() {}

    private static MobEffectInstance effect(Holder<MobEffect> effect, int duration, int amplifier) {
        return new MobEffectInstance(effect, duration, amplifier);
    }

    public static final FoodProperties HAMBURGER = BuffFoodItem.createFoodBuilder(
            8, 0.8f,
            List.of(
                    new BuffFoodItem.EffectEntry(() -> effect(MobEffects.SATURATION, 1, 0), 1.0f),
                    new BuffFoodItem.EffectEntry(() -> effect(MobEffects.DAMAGE_BOOST, 600, 0), 0.8f)
            )
    ).build();

    public static final FoodProperties FRENCH_FRIES = BuffFoodItem.createFoodBuilder(
            4, 0.4f,
            List.of(
                    new BuffFoodItem.EffectEntry(() -> effect(MobEffects.MOVEMENT_SPEED, 400, 0), 1.0f),
                    new BuffFoodItem.EffectEntry(() -> effect(MobEffects.DIG_SPEED, 300, 0), 0.6f)
            )
    ).build();

    public static final FoodProperties CHEESE_CHUNK = BuffFoodItem.createFoodBuilder(
            2, 0.3f,
            List.of(
                    new BuffFoodItem.EffectEntry(() -> effect(MobEffects.REGENERATION, 200, 0), 1.0f),
                    new BuffFoodItem.EffectEntry(() -> effect(MobEffects.DAMAGE_RESISTANCE, 300, 0), 0.5f)
            )
    ).build();

    public static final FoodProperties CHEESE_BURGER = BuffFoodItem.createFoodBuilder(
            12, 1.0f,
            List.of(
                    new BuffFoodItem.EffectEntry(() -> effect(MobEffects.SATURATION, 1, 0), 1.0f),
                    new BuffFoodItem.EffectEntry(() -> effect(MobEffects.DAMAGE_BOOST, 1200, 1), 1.0f),
                    new BuffFoodItem.EffectEntry(() -> effect(MobEffects.REGENERATION, 300, 0), 1.0f),
                    new BuffFoodItem.EffectEntry(() -> effect(MobEffects.ABSORPTION, 1200, 0), 0.8f)
            )
    ).build();
}
