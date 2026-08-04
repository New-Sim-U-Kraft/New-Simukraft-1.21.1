package common.cn.kafei.simukraft.virtualvein;

public record VirtualVeinRange(double min, double max) {
    public VirtualVeinRange {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
            throw new IllegalArgumentException("Invalid virtual vein range");
        }
    }

    /** overlaps: 判断配置范围是否与原版群系参数范围相交。 */
    public boolean overlaps(double otherMin, double otherMax) {
        return otherMin <= otherMax && min <= otherMax && max >= otherMin;
    }
}
