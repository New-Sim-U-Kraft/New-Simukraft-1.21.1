package common.cn.kafei.simukraft.commercial;

import java.util.Locale;

public enum CommercialVisibility {
    PLAYER,
    NPC,
    MIXED;

    /** visibleToPlayer: 判断该交易是否对玩家显示。 */
    public boolean visibleToPlayer() {
        return this == PLAYER || this == MIXED;
    }

    /** visibleToNpc: 判断该交易是否允许 NPC 自动处理。 */
    public boolean visibleToNpc() {
        return this == NPC || this == MIXED;
    }

    /** fromName: 从 JSON 字段解析可见性，非法值默认玩家可见。 */
    public static CommercialVisibility fromName(String name) {
        if (name == null || name.isBlank()) {
            return PLAYER;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        if ("BOTH".equals(normalized) || "ALL".equals(normalized)) {
            return MIXED;
        }
        for (CommercialVisibility visibility : values()) {
            if (visibility.name().equals(normalized)) {
                return visibility;
            }
        }
        return PLAYER;
    }
}
