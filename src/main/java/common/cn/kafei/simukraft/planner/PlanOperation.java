package common.cn.kafei.simukraft.planner;

import java.util.Locale;

/**
 * 规划师操作类型：
 * - REMOVE 清除：拆掉区域内的方块，掉落物入箱（满则掉地），从上往下做。
 * - FILL 填充：在空气/可替换植物处放入指定方块（材料来自箱子），从下往上做。
 * - REPLACE 替换：把区域内匹配"源方块"的方块换成"目标方块"（目标来自箱子），从下往上做。
 */
public enum PlanOperation {
    REMOVE,
    FILL,
    REPLACE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "gui.simukraft.plan_area.op." + id();
    }

    // 收获/拆除从上往下、建造/填充从下往上，符合直觉也避免悬空。
    public boolean ascendingY() {
        return this != REMOVE;
    }

    public boolean needsFillBlock() {
        return this == FILL || this == REPLACE;
    }

    public boolean needsSourceBlock() {
        return this == REPLACE;
    }

    public static PlanOperation fromId(String id) {
        if (id == null) {
            return REMOVE;
        }
        for (PlanOperation op : values()) {
            if (op.id().equalsIgnoreCase(id) || op.name().equalsIgnoreCase(id)) {
                return op;
            }
        }
        return REMOVE;
    }
}
