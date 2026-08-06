package common.cn.kafei.simukraft.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class CityChunkManagerTest {
    @Test
    void countsDisconnectedComponentsAsEnclaves() {
        CityChunkManager manager = new CityChunkManager();
        UUID cityId = UUID.randomUUID();
        ChunkPos core = new ChunkPos(0, 0);

        manager.assignInitialArea(cityId, core);
        assertEquals(0, manager.countEnclaves(cityId, core.toLong()));
        assertTrue(manager.isConnectedToCore(cityId, ChunkPos.asLong(2, 0), core.toLong()));

        manager.claimChunk(cityId, ChunkPos.asLong(10, 10));
        manager.claimChunk(cityId, ChunkPos.asLong(11, 10));
        assertFalse(manager.isConnectedToCore(cityId, ChunkPos.asLong(12, 10), core.toLong()));
        assertEquals(1, manager.countEnclaves(cityId, core.toLong()));

        manager.claimChunk(cityId, ChunkPos.asLong(20, 20));
        assertEquals(2, manager.countEnclaves(cityId, core.toLong()));
    }
}
