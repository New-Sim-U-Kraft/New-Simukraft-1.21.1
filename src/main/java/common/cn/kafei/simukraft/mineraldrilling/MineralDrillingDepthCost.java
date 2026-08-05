package common.cn.kafei.simukraft.mineraldrilling;

/** MineralDrillingDepthCost: 计算钻杆段随钻井历史深度的新增消耗。 */
public final class MineralDrillingDepthCost {
    public static final int BLOCKS_PER_ROD_SEGMENT = 10;

    private MineralDrillingDepthCost() {
    }

    /** segmentsForDepth: 计算从控制箱 Y 到目标 Y 至少需要的钻杆段数。 */
    public static int segmentsForDepth(int originY, int targetDepth) {
        long downwardDistance = (long) originY - targetDepth;
        if (downwardDistance <= 0L) {
            return 0;
        }
        long segments = (downwardDistance + BLOCKS_PER_ROD_SEGMENT - 1L)
                / BLOCKS_PER_ROD_SEGMENT;
        return (int) Math.min(Integer.MAX_VALUE, segments);
    }

    /** additionalSegments: 只计算历史最低深度之后还需消耗的钻杆段数。 */
    public static int additionalSegments(int originY, int lowestReachedDepth, int targetDepth) {
        int previous = segmentsForDepth(originY, lowestReachedDepth);
        int required = segmentsForDepth(originY, targetDepth);
        return Math.max(0, required - previous);
    }
}
