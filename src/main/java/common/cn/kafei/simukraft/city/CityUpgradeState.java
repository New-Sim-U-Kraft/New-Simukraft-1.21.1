package common.cn.kafei.simukraft.city;

/** CityUpgradeState: 保存城市正在执行的等级升级任务及其服务端时间基准。 */
public record CityUpgradeState(int targetLevel, long startedAt, int durationTicks) {
    public static final CityUpgradeState NONE = new CityUpgradeState(0, 0L, 0);

    public CityUpgradeState {
        if (targetLevel < 0 || targetLevel > CityLevelDefinition.MAX_LEVEL
                || startedAt < 0L
                || durationTicks < 0 || durationTicks > CityLevelDefinition.MAX_UPGRADE_DURATION_TICKS
                || (targetLevel == 0 && (startedAt != 0L || durationTicks != 0))
                || (targetLevel > 0 && durationTicks <= 0)) {
            throw new IllegalArgumentException("Invalid city upgrade state");
        }
    }

    /** active: 判断城市是否存在未完成的升级任务。 */
    public boolean active() {
        return targetLevel > 0;
    }

    /** isComplete: 根据服务端游戏时间判断升级是否已经到期。 */
    public boolean isComplete(long gameTime) {
        return active() && gameTime >= startedAt + durationTicks;
    }

    /** progress: 计算当前升级进度，结果始终位于 0 到 1。 */
    public float progress(long gameTime) {
        if (!active()) {
            return 0.0F;
        }
        long elapsed = Math.max(0L, gameTime - startedAt);
        return Math.min(1.0F, elapsed / (float) durationTicks);
    }

    /** fromSaved: 将 NBT 中的状态转换为安全快照，损坏数据按无任务处理。 */
    public static CityUpgradeState fromSaved(int targetLevel, long startedAt, int durationTicks) {
        try {
            return targetLevel <= 0 ? NONE : new CityUpgradeState(targetLevel, startedAt, durationTicks);
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }
}
