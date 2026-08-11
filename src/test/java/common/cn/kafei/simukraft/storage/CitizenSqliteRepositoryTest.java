package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.medical.DiseaseType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenSqliteRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void medicalPregnancyAndAgeFieldsRoundTripThroughSqlite() throws Exception {
        UUID citizenId = UUID.randomUUID();
        UUID medicalBedId = UUID.randomUUID();
        UUID babyBedId = UUID.randomUUID();
        CitizenData citizen = new CitizenData(citizenId);
        citizen.setName("Medical Test");
        citizen.setPregnant(true);
        citizen.setPregnantSince(5L);
        citizen.setReservedBabyBedPoiId(babyBedId);
        citizen.setLastAgeGrowthDay(9L);
        citizen.setDisease(DiseaseType.FOOD_POISONING, 6L);
        citizen.medical().addDiseaseTreatmentTicks(240L);
        citizen.medical().setMedicalBedPoiId(medicalBedId);
        citizen.medical().setPostpartumUntilDay(12L);
        citizen.medical().setLastHospitalMealDay(10L);

        CompoundTag root = new CompoundTag();
        ListTag citizens = new ListTag();
        citizens.add(citizen.toTag());
        root.put("Citizens", citizens);

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("citizens.sqlite"))) {
            CitizenSqliteRepository repository = new CitizenSqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, root);
            }
            CompoundTag loadedRoot = repository.loadAll();

            assertNotNull(loadedRoot);
            CitizenData loaded = CitizenData.fromTag(
                    loadedRoot.getList("Citizens", CompoundTag.TAG_COMPOUND).getCompound(0));
            assertTrue(loaded.pregnant());
            assertEquals(5L, loaded.pregnantSince());
            assertEquals(babyBedId, loaded.reservedBabyBedPoiId());
            assertEquals(9L, loaded.lastAgeGrowthDay());
            assertEquals(DiseaseType.FOOD_POISONING, loaded.disease());
            assertEquals(240L, loaded.medical().diseaseTreatmentTicks());
            assertEquals(medicalBedId, loaded.medical().medicalBedPoiId());
            assertEquals(12L, loaded.medical().postpartumUntilDay());
            assertEquals(10L, loaded.medical().lastHospitalMealDay());
        }
    }

    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
