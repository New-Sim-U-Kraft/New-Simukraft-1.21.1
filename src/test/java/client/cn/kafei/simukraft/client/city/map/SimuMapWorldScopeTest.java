package client.cn.kafei.simukraft.client.city.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SimuMapWorldScopeTest {
    @Test
    void sanitizeForPath_keepsChineseWorldNamesDistinct() {
        assertEquals("新的世界", SimuMapStorage.sanitizeForPath("新的世界"));
        assertEquals("我的世界", SimuMapStorage.sanitizeForPath("我的世界"));
        assertNotEquals(
                SimuMapStorage.sanitizeForPath("新的世界"),
                SimuMapStorage.sanitizeForPath("我的世界"));
    }

    @Test
    void sanitizeForPath_onlyReplacesIllegalPathCharacters() {
        assertEquals("a_b", SimuMapStorage.sanitizeForPath("a/b"));
        assertEquals("host_25565", SimuMapStorage.sanitizeForPath("host:25565"));
    }

    @Test
    void singlePlayerWorldIds_differByFolderAndPath() {
        String first = SimuMapStorage.createSinglePlayerWorldId(Path.of("run", "saves", "新的世界"));
        String second = SimuMapStorage.createSinglePlayerWorldId(Path.of("run", "saves", "我的世界"));
        String copy = SimuMapStorage.createSinglePlayerWorldId(Path.of("other", "saves", "新的世界"));

        assertTrue(first.startsWith("sp_新的世界_"));
        assertNotEquals(first, second);
        assertNotEquals(first, copy);
    }

    @Test
    void cacheIdentity_rejectsForeignWorldOrDimension() {
        assertTrue(SimuMapStorage.matchesCacheIdentity(
                "sp_world_1", "minecraft_overworld", "sp_world_1", "minecraft_overworld"));
        assertFalse(SimuMapStorage.matchesCacheIdentity(
                "sp_world_1", "minecraft_overworld", "sp_world_2", "minecraft_overworld"));
        assertFalse(SimuMapStorage.matchesCacheIdentity(
                "sp_world_1", "minecraft_overworld", "sp_world_1", "minecraft_the_nether"));
        assertFalse(SimuMapStorage.matchesCacheIdentity(
                "sp_world_1", "minecraft_overworld", SimuMapStorage.UNRESOLVED_WORLD_ID, "minecraft_overworld"));
    }

    @Test
    void unresolvedWorldId_isNotWritable() {
        assertFalse(SimuMapStorage.isResolvedWorldId(SimuMapStorage.UNRESOLVED_WORLD_ID));
        assertFalse(SimuMapStorage.isResolvedWorldId(null));
        assertTrue(SimuMapStorage.isResolvedWorldId("sp_新的世界_abc"));
    }

    @Test
    void regionData_tracksFilledCountAndSaveDirty() {
        SimuMapRegionData data = new SimuMapRegionData(0, 0);
        assertTrue(data.isEmpty());
        assertTrue(data.needsSave());

        data.setData(1, 1, (short) 64, 0xFF112233, false, 15);
        assertFalse(data.isEmpty());
        assertTrue(data.needsSave());

        data.markSaved();
        assertFalse(data.needsSave());

        data.setData(1, 1, (short) 64, 0xFF112233, false, 15);
        assertFalse(data.needsSave());

        data.setData(2, 2, (short) 70, 0xFF445566, false, 15);
        assertTrue(data.needsSave());
    }
}
