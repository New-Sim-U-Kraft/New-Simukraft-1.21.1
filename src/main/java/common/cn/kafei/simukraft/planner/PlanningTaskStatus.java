package common.cn.kafei.simukraft.planner;

import java.util.Locale;

/**
 * 规划任务状态。WAITING_MATERIALS 表示等待箱子里的填充/替换方块；PAUSED_* 表示夜间休息或离线暂停。
 */
public enum PlanningTaskStatus {
    QUEUED,
    PLANNING,
    WAITING_MATERIALS,
    PAUSED_RESTING,
    PAUSED_OFFLINE,
    COMPLETED,
    INTERRUPTED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isPaused() {
        return this == PAUSED_RESTING || this == PAUSED_OFFLINE;
    }

    public static PlanningTaskStatus from(String value) {
        if (value == null || value.isBlank()) {
            return QUEUED;
        }
        for (PlanningTaskStatus status : values()) {
            if (status.id().equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return QUEUED;
    }
}
