package common.cn.kafei.simukraft.virtualvein;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Climate;

import java.util.Objects;

public record VirtualVeinDefinition(String id,
                                    String displayName,
                                    int priority,
                                    VirtualVeinRange continentalness,
                                    VirtualVeinRange erosion,
                                    VirtualVeinRange depth,
                                    VirtualVeinRange temperature,
                                    VirtualVeinRange humidity,
                                    VirtualVeinRange weirdness,
                                    int minY,
                                    int maxY,
                                    ResourceLocation productId,
                                    int minAmount,
                                    int maxAmount,
                                    int periodTicks) {
    private static final int OVERWORLD_MIN_Y = -64;
    private static final int OVERWORLD_MAX_Y = 320;

    public VirtualVeinDefinition {
        id = requireText(id, "id");
        displayName = requireText(displayName, "displayName");
        continentalness = Objects.requireNonNull(continentalness, "continentalness");
        erosion = Objects.requireNonNull(erosion, "erosion");
        depth = Objects.requireNonNull(depth, "depth");
        temperature = Objects.requireNonNull(temperature, "temperature");
        humidity = Objects.requireNonNull(humidity, "humidity");
        weirdness = Objects.requireNonNull(weirdness, "weirdness");
        if (minY > maxY || minY < OVERWORLD_MIN_Y || maxY > OVERWORLD_MAX_Y) {
            throw new IllegalArgumentException("Virtual vein Y range must stay within the overworld bounds");
        }
        productId = Objects.requireNonNull(productId, "productId");
        if (minAmount <= 0 || maxAmount < minAmount || periodTicks <= 0) {
            throw new IllegalArgumentException("Invalid virtual vein production");
        }
    }

    /** matches: 按多重噪声参数点的六项范围判断矿脉资格。 */
    public boolean matches(Climate.ParameterPoint point) {
        return matches(continentalness, point.continentalness())
                && matches(erosion, point.erosion())
                && matches(depth, point.depth())
                && matches(temperature, point.temperature())
                && matches(humidity, point.humidity())
                && matches(weirdness, point.weirdness());
    }

    private static boolean matches(VirtualVeinRange definitionRange, Climate.Parameter parameterRange) {
        return definitionRange.overlaps(
                Climate.unquantizeCoord(parameterRange.min()),
                Climate.unquantizeCoord(parameterRange.max())
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
