package common.cn.kafei.simukraft.city;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class CityLevelDefinitionLoaderTest {
    private static final ResourceLocation TEST_ID = ResourceLocation.fromNamespaceAndPath("simukraft", "city_levels/test.json");

    @Test
    void parsesAndMergesExactItemRequirements() {
        CityLevelDefinition definition = CityLevelDefinitionLoader.parse(TEST_ID, JsonParser.parseString("""
                {
                  "level": 2,
                  "display_name": "Town",
                  "duration_ticks": 400,
                  "requirements": {
                    "funds": 125.5,
                    "population": 8,
                    "items": [
                      {"item": "minecraft:oak_log", "count": 16},
                      {"item": "minecraft:oak_log", "count": 8},
                      {"item": "minecraft:stone", "count": 32}
                    ]
                  },
                  "unlocks": {
                    "chunks": 12,
                    "enclaves": 2
                  }
                }
                """).getAsJsonObject());

        assertEquals(2, definition.level());
        assertEquals("Town", definition.displayName());
        assertEquals(125.5D, definition.requiredFunds());
        assertEquals(8, definition.requiredPopulation());
        assertEquals(12, definition.unlockedChunks());
        assertEquals(2, definition.unlockedEnclaves());
        assertEquals(400, definition.durationTicks());
        assertEquals(2, definition.items().size());
        assertEquals(24, definition.items().getFirst().count());
        assertNull(definition.items().getFirst().displayIcon());
        assertTrue(definition.items().getFirst().displayName().isBlank());
    }

    @Test
    void rejectsInvalidItemCountsAndUnknownItems() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"level":2,"requirements":{"items":[{"item":"minecraft:oak_log","count":0}]}}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"level":2,"requirements":{"items":[{"item":"simukraft:not_registered","count":1}]}}
                """));
    }

    @Test
    void parsesAndMergesItemTags() {
        CityLevelDefinition definition = parse("""
                {
                  "level": 2,
                  "requirements": {
                    "items": [
                      {"tag": "minecraft:logs", "count": 8, "display_icon": "minecraft:oak_log", "display_name": "Logs"},
                      {"item": "#minecraft:logs", "count": 4},
                      {"item": "minecraft:oak_log", "count": 2}
                    ]
                  }
                }
                """);

        assertEquals(2, definition.items().size());
        CityLevelDefinition.ItemRequirement tag = definition.items().getFirst();
        assertTrue(tag.isTag());
        assertEquals(ResourceLocation.parse("minecraft:logs"), tag.itemTag());
        assertEquals(12, tag.count());
        assertEquals(ResourceLocation.parse("minecraft:oak_log"), tag.displayIcon());
        assertEquals("Logs", tag.displayName());
        assertFalse(definition.items().get(1).isTag());
    }

    @Test
    void parsesSingleFileLevelsWrapperAndRootArray() {
        String json = """
                {"levels":[
                  {"level":1,"display_name":"First"},
                  {"level":2,"display_name":"Second"}
                ]}
                """;
        assertEquals(2, CityLevelDefinitionLoader.parseDefinitions(TEST_ID,
                JsonParser.parseString(json)).size());
        assertEquals(2, CityLevelDefinitionLoader.parseDefinitions(TEST_ID,
                JsonParser.parseString("[{\"level\":1},{\"level\":2}]")).size());
    }

    @Test
    void missingUnlocksUseLegacyDefaults() {
        CityLevelDefinition definition = parse("""
                {"level":2,"requirements":{"funds":1}}
                """
        );
        assertEquals(CityLevelDefinition.UNLIMITED, definition.unlockedChunks());
        assertEquals(CityLevelDefinition.DEFAULT_UNLOCKED_ENCLAVES, definition.unlockedEnclaves());
    }

    @Test
    void parsesUpgradeDurationAndUsesLegacyDefaults() {
        CityLevelDefinition explicit = parse("""
                {"level":2,"duration_ticks":600}
                """);
        CityLevelDefinition legacy = parse("""
                {"level":2}
                """);
        CityLevelDefinition baseline = parse("""
                {"level":1}
                """);

        assertEquals(600, explicit.durationTicks());
        assertEquals(CityLevelDefinition.DEFAULT_UPGRADE_DURATION_TICKS, legacy.durationTicks());
        assertEquals(0, baseline.durationTicks());
    }

    @Test
    void rejectsMalformedNumericRequirementsInsteadOfMakingThemFree() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"level":2,"requirements":{"funds":"free"}}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"level":2,"requirements":{"population":"many"}}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"level":2,"unlocks":{"chunks":-2}}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"level":2,"unlocks":{"enclaves":-2}}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"level":2.5}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"level":2,"requirements":{"items":[{"item":"minecraft:stone","count":1.5}]}}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"level":2,"duration_ticks":0}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"level":2,"duration_ticks":1728001}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"level":2,"requirements":{"items":[{"tag":"minecraft:logs","count":1,"display_icon":"simukraft:not_registered"}]}}
                """));
    }

    @Test
    void newAndLegacyCitiesUseLevelOneBaseline() {
        CityData city = new CityData(UUID.randomUUID(), "Test", UUID.randomUUID(), "Mayor", BlockPos.ZERO);
        assertEquals(1, city.cityLevel());

        CompoundTag legacy = new CompoundTag();
        legacy.putUUID("CityId", UUID.randomUUID());
        legacy.putString("CityName", "Legacy");
        legacy.putInt("CityLevel", 0);
        CityData loaded = CityData.fromTag(legacy);
        assertEquals(1, loaded.cityLevel());
    }

    @Test
    void cityLevelIsClampedToTheSupportedRange() {
        CityData city = new CityData(UUID.randomUUID(), "Test", UUID.randomUUID(), "Mayor", BlockPos.ZERO);
        city.setCityLevel(Integer.MIN_VALUE);
        assertEquals(CityLevelDefinition.MIN_LEVEL, city.cityLevel());
        city.setCityLevel(Integer.MAX_VALUE);
        assertEquals(CityLevelDefinition.MAX_LEVEL, city.cityLevel());

        CompoundTag corrupted = new CompoundTag();
        corrupted.putUUID("CityId", UUID.randomUUID());
        corrupted.putString("CityName", "Corrupted");
        corrupted.putInt("CityLevel", Integer.MAX_VALUE);
        CityData loaded = CityData.fromTag(corrupted);
        assertEquals(CityLevelDefinition.MAX_LEVEL, loaded.cityLevel());
    }

    @Test
    void cityUpgradeStateSurvivesNbtRoundTrip() {
        CityData city = new CityData(UUID.randomUUID(), "Test", UUID.randomUUID(), "Mayor", BlockPos.ZERO);
        city.beginUpgrade(2, 1_000L, 600);

        CityData loaded = CityData.fromTag(city.toTag());
        assertEquals(2, loaded.upgradeState().targetLevel());
        assertEquals(1_000L, loaded.upgradeState().startedAt());
        assertEquals(600, loaded.upgradeState().durationTicks());
        assertFalse(loaded.upgradeState().isComplete(1_599L));
        assertTrue(loaded.upgradeState().isComplete(1_600L));
    }

    @Test
    void financeRollbackRestoresEntriesEvictedByTheRecordLimit() {
        CityData city = new CityData(UUID.randomUUID(), "Test", UUID.randomUUID(), "Mayor", BlockPos.ZERO);
        FinanceTransactionData oldest = transaction(1L, "oldest");
        FinanceTransactionData newest = transaction(2L, "newest");
        FinanceTransactionData upgrade = transaction(3L, "city_upgrade");
        city.addFinanceTransaction(oldest, 2);
        city.addFinanceTransaction(newest, 2);

        List<FinanceTransactionData> evicted = city.addFinanceTransactionTracked(upgrade, 2);
        assertEquals(List.of(upgrade, newest), city.financeTransactions());

        city.rollbackFinanceTransaction(upgrade, evicted);
        assertEquals(List.of(newest, oldest), city.financeTransactions());
    }

    @Test
    void nextLevelRequiresAContinuousDefinition() throws Exception {
        CityLevelDefinitionLoader loader = CityLevelDefinitionLoader.INSTANCE;
        var field = CityLevelDefinitionLoader.class.getDeclaredField("definitions");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var reference = (java.util.concurrent.atomic.AtomicReference<List<CityLevelDefinition>>) field.get(loader);
        List<CityLevelDefinition> previous = reference.get();
        try {
            reference.set(List.of(new CityLevelDefinition(3, "City", 0.0D, 0, List.of())));
            assertNull(loader.nextLevel(1));
        } finally {
            reference.set(previous);
        }
    }

    private static CityLevelDefinition parse(String json) {
        return CityLevelDefinitionLoader.parse(TEST_ID, JsonParser.parseString(json).getAsJsonObject());
    }

    /** transaction: 构造回滚顺序测试使用的财政流水。 */
    private static FinanceTransactionData transaction(long time, String reason) {
        return new FinanceTransactionData(time, null, "", -1.0D, 0.0D,
                FinanceTransactionData.Type.EXPENSE, reason);
    }
}
