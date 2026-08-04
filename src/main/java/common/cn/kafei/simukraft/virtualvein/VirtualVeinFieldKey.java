package common.cn.kafei.simukraft.virtualvein;

public record VirtualVeinFieldKey(int cellX, int cellZ, int centerX, int centerZ, String biomeId) {
    public VirtualVeinFieldKey {
        if (biomeId == null || biomeId.isBlank()) {
            throw new IllegalArgumentException("Virtual vein field biome ID must not be blank");
        }
    }
}
