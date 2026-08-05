package common.cn.kafei.simukraft.industrial;

import java.util.List;

/**
 * 工业输入表达式工具：把 AND/OR 树转换成界面和统计需要的叶子物品。
 */

@SuppressWarnings("null")
public final class IndustrialInputRequirements {
    private IndustrialInputRequirements() {
    }

    public static List<IndustrialDefinition.ItemRequirement> flattenItems(List<IndustrialDefinition.InputRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        return requirements.stream()
                .flatMap(requirement -> requirement.itemLeaves().stream())
                .toList();
    }

    public static boolean hasConsumableItem(List<IndustrialDefinition.InputRequirement> requirements) {
        return flattenItems(requirements).stream().anyMatch(IndustrialDefinition.ItemRequirement::consume);
    }
}
