package common.cn.kafei.simukraft.virtualvein;

/** VirtualVeinFieldResolver: 根据细胞噪声解析不规则矿区。 */
@SuppressWarnings("null")
public final class VirtualVeinFieldResolver {
    public static final int FIELD_SIZE = 256;
    private static final int HALF_FIELD_SIZE = FIELD_SIZE / 2;
    private static final int JITTER_RANGE = 80;
    private static final long FIELD_SALT = 0x53A8B179D4E2C65FL;

    private VirtualVeinFieldResolver() {
    }

    public static VirtualVeinFieldKey resolve(long worldSeed, int blockX, int blockZ, String biomeId) {
        int baseCellX = Math.floorDiv(blockX, FIELD_SIZE);
        int baseCellZ = Math.floorDiv(blockZ, FIELD_SIZE);
        Candidate nearest = null;
        for (int cellX = baseCellX - 1; cellX <= baseCellX + 1; cellX++) {
            for (int cellZ = baseCellZ - 1; cellZ <= baseCellZ + 1; cellZ++) {
                Candidate candidate = candidate(worldSeed, cellX, cellZ);
                long distanceX = (long) blockX - candidate.centerX;
                long distanceZ = (long) blockZ - candidate.centerZ;
                long distanceSquared = distanceX * distanceX + distanceZ * distanceZ;
                if (nearest == null || distanceSquared < nearest.distanceSquared
                        || distanceSquared == nearest.distanceSquared && candidate.compareTo(nearest) < 0) {
                    nearest = candidate.withDistance(distanceSquared);
                }
            }
        }
        return new VirtualVeinFieldKey(nearest.cellX, nearest.cellZ, nearest.centerX, nearest.centerZ, biomeId);
    }

    private static Candidate candidate(long worldSeed, int cellX, int cellZ) {
        long hash = mix(worldSeed ^ FIELD_SALT ^ ((long) cellX * 0x9E3779B97F4A7C15L) ^ ((long) cellZ * 0xC2B2AE3D27D4EB4FL));
        int offsetX = bounded(hash, JITTER_RANGE * 2 + 1) - JITTER_RANGE;
        int offsetZ = bounded(mix(hash + 0x165667B19E3779F9L), JITTER_RANGE * 2 + 1) - JITTER_RANGE;
        int centerX = cellX * FIELD_SIZE + HALF_FIELD_SIZE + offsetX;
        int centerZ = cellZ * FIELD_SIZE + HALF_FIELD_SIZE + offsetZ;
        return new Candidate(cellX, cellZ, centerX, centerZ, Long.MAX_VALUE);
    }

    public static long seededValue(long worldSeed, VirtualVeinFieldKey key, String salt) {
        long value = worldSeed ^ FIELD_SALT;
        value ^= (long) key.cellX() * 0x9E3779B97F4A7C15L;
        value ^= (long) key.cellZ() * 0xC2B2AE3D27D4EB4FL;
        value ^= key.biomeId().hashCode() * 0xD6E8FEB86659FD93L;
        value ^= salt.hashCode() * 0x165667B19E3779F9L;
        return mix(value);
    }

    public static double unit(long seededValue) {
        return (seededValue >>> 11) * 0x1.0p-53;
    }

    public static int inclusiveInt(long seededValue, int min, int max) {
        if (min == max) {
            return min;
        }
        long range = (long) max - min + 1L;
        long positive = seededValue & Long.MAX_VALUE;
        return (int) (min + positive % range);
    }

    private static int bounded(long value, int bound) {
        return (int) ((value & Long.MAX_VALUE) % bound);
    }

    private static long mix(long value) {
        value = (value ^ value >>> 33) * 0xff51afd7ed558ccdL;
        value = (value ^ value >>> 33) * 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }

    private record Candidate(int cellX, int cellZ, int centerX, int centerZ, long distanceSquared) {
        private Candidate withDistance(long value) {
            return new Candidate(cellX, cellZ, centerX, centerZ, value);
        }

        private int compareTo(Candidate other) {
            int xComparison = Integer.compare(cellX, other.cellX);
            return xComparison != 0 ? xComparison : Integer.compare(cellZ, other.cellZ);
        }
    }
}
