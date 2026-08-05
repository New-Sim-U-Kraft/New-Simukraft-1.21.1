package common.cn.kafei.simukraft.mineraldrilling;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineralDrillingDefinitionLoaderTest {
    /** parsesOutputContainerPositions: 解析钻井 JSON 的结构坐标输出容器声明。 */
    @Test
    void parsesOutputContainerPositions() {
        String json = """
                {
                  "type": "drilling",
                  "id": "simukraft:test_drilling_platform",
                  "containers": {
                    "output": {
                      "type": "structure_pos",
                      "positions": [[7, 8, 1], [8, 8, 1], [7, 8, 1]]
                    }
                  }
                }
                """;

        MineralDrillingDefinition definition = MineralDrillingDefinitionLoader.parse(json, "fallback").orElseThrow();

        assertEquals("simukraft:test_drilling_platform", definition.id());
        assertEquals(1, definition.outputContainers().size());
        assertEquals("output", definition.outputContainers().getFirst().id());
        assertEquals("structure_pos", definition.outputContainers().getFirst().type());
        assertEquals(java.util.List.of(new BlockPos(7, 8, 1), new BlockPos(8, 8, 1)),
                definition.outputContainers().getFirst().positions());
    }

    /** rejectsNonDrillingJson: 专用解析器不得接收普通工业 JSON。 */
    @Test
    void rejectsNonDrillingJson() {
        assertTrue(MineralDrillingDefinitionLoader.parse("{\"type\":\"industrial\"}", "fallback").isEmpty());
    }
}
