package common.cn.kafei.simukraft.mineraldrilling;

import net.minecraft.core.BlockPos;

import java.util.List;

/** MineralDrillingDefinition: 钻井平台专用 JSON 的运行时定义。 */
public record MineralDrillingDefinition(String id, List<OutputContainerDefinition> outputContainers) {
    public MineralDrillingDefinition {
        id = id != null ? id : "";
        outputContainers = outputContainers != null ? List.copyOf(outputContainers) : List.of();
    }

    /** OutputContainerDefinition: 声明钻井产物可写入的结构坐标容器。 */
    public record OutputContainerDefinition(String id, String type, List<BlockPos> positions) {
        public OutputContainerDefinition {
            id = id != null ? id : "output";
            type = type != null ? type : "structure_pos";
            positions = positions != null ? List.copyOf(positions) : List.of();
        }
    }
}
