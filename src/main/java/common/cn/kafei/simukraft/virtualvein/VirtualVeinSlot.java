package common.cn.kafei.simukraft.virtualvein;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record VirtualVeinSlot(String veinId,
                              String displayName,
                              ResourceLocation productId,
                              int minY,
                              int maxY,
                              int amount,
                              int periodTicks,
                              int initialReserve,
                              int remainingReserve,
                              VirtualVeinSlotState state) {
    public VirtualVeinSlot {
        veinId = requireText(veinId, "veinId");
        displayName = requireText(displayName, "displayName");
        productId = Objects.requireNonNull(productId, "productId");
        if (minY > maxY || amount <= 0 || periodTicks <= 0 || initialReserve <= 0 || remainingReserve < 0 || remainingReserve > initialReserve) {
            throw new IllegalArgumentException("Invalid virtual vein slot");
        }
        state = Objects.requireNonNull(state, "state");
        if (state == VirtualVeinSlotState.ACTIVE && remainingReserve == 0) {
            throw new IllegalArgumentException("Active virtual vein slot must retain reserves");
        }
        if (state == VirtualVeinSlotState.DEPLETED && remainingReserve != 0) {
            throw new IllegalArgumentException("Depleted virtual vein slot must not retain reserves");
        }
    }

    public boolean acceptsY(int y) {
        return y >= minY && y <= maxY;
    }

    public boolean intersectsShallowRange(int minY) {
        return maxY >= minY;
    }

    /** intersectsYRange: 判断矿脉是否与给定的世界 Y 范围相交。 */
    public boolean intersectsYRange(int rangeMinY, int rangeMaxY) {
        return rangeMinY <= rangeMaxY && minY <= rangeMaxY && maxY >= rangeMinY;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
