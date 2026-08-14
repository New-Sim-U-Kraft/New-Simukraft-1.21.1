package common.cn.kafei.simukraft.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class NpcWorkChunkLoadServiceTest {
    @Test
    void collectWorkAreaChunks_includesPaddingAcrossChunkBoundaries() {
        Set<Long> chunks = NpcWorkChunkLoadService.collectWorkAreaChunks(
                new BlockPos(0, 64, 0), new BlockPos(15, 64, 15), 4);

        assertEquals(9, chunks.size());
        assertTrue(chunks.contains(ChunkPos.asLong(-1, -1)));
        assertTrue(chunks.contains(ChunkPos.asLong(1, 1)));
    }

    @Test
    void collectWorkAreaChunks_handlesNegativeCoordinates() {
        Set<Long> chunks = NpcWorkChunkLoadService.collectWorkAreaChunks(
                new BlockPos(17, 64, -17), new BlockPos(-17, 64, 17), 0);

        assertEquals(16, chunks.size());
        assertTrue(chunks.contains(ChunkPos.asLong(-2, -2)));
        assertTrue(chunks.contains(ChunkPos.asLong(1, 1)));
    }
}
