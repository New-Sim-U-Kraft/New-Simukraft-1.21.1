package common.cn.kafei.simukraft.virtualvein;

import java.util.List;
import java.util.Objects;

public record VirtualVeinFieldProfile(String dimensionId,
                                      VirtualVeinFieldKey key,
                                      String centerBiomeId,
                                      long createdGameTime,
                                      List<VirtualVeinSlot> slots) {
    public VirtualVeinFieldProfile {
        if (dimensionId == null || dimensionId.isBlank() || centerBiomeId == null || centerBiomeId.isBlank()) {
            throw new IllegalArgumentException("Virtual vein field identifiers must not be blank");
        }
        key = Objects.requireNonNull(key, "key");
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        if (slots.size() > 2) {
            throw new IllegalArgumentException("A virtual vein field supports at most two slots");
        }
    }

    public List<VirtualVeinSlot> slotsAtY(int y) {
        return slots.stream().filter(slot -> slot.acceptsY(y)).toList();
    }
}
