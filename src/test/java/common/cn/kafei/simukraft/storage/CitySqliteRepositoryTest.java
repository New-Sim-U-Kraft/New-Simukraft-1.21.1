package common.cn.kafei.simukraft.storage;

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
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("null")
class CitySqliteRepositoryTest {
    private static final String DIMENSION = "minecraft:overworld";

    @TempDir
    Path tempDir;

    /**
     * 回归：saveAll 曾经先 "DELETE FROM cities WHERE dimension_id = ?" 再重写内存快照。
     * 一旦内存不完整（加载失败、加载未完成、按维度轮流保存），库里真实存在的城市就会被抹掉，
     * 而且外键级联还会连带删掉 city_members / finance_transactions / commercial_daily_income。
     */
    @Test
    void saveAllDoesNotDeleteCitiesMissingFromTheSnapshot() throws Exception {
        UUID keptCityId = UUID.randomUUID();
        UUID otherCityId = UUID.randomUUID();

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("cities.sqlite"))) {
            CitySqliteRepository repository = new CitySqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, citiesTag(cityTag(keptCityId, "Kept"), cityTag(otherCityId, "Other")), DIMENSION);
            }
            assertEquals(2, loadedCityCount(repository));

            // 只包含一座城市的"残缺快照"
            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, citiesTag(cityTag(keptCityId, "Kept")), DIMENSION);
            }

            assertEquals(2, loadedCityCount(repository), "保存不得删除快照里缺失的城市");
        }
    }

    @Test
    void deleteRemovesOnlyTheTargetCity() throws Exception {
        UUID keptCityId = UUID.randomUUID();
        UUID removedCityId = UUID.randomUUID();

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("cities-delete.sqlite"))) {
            CitySqliteRepository repository = new CitySqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, citiesTag(cityTag(keptCityId, "Kept"), cityTag(removedCityId, "Removed")), DIMENSION);
            }

            try (Connection connection = database.borrowConnection()) {
                repository.delete(connection, removedCityId);
            }

            CompoundTag loaded = repository.loadAll(DIMENSION);
            assertNotNull(loaded);
            ListTag cities = loaded.getList("Cities", CompoundTag.TAG_COMPOUND);
            assertEquals(1, cities.size());
            assertEquals(keptCityId, cities.getCompound(0).getUUID("CityId"));
        }
    }

    @Test
    void membersAndFinanceTransactionsRoundTrip() throws Exception {
        UUID cityId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CompoundTag city = cityTag(cityId, "Ledger");

        ListTag members = new ListTag();
        CompoundTag member = new CompoundTag();
        member.putUUID("PlayerId", memberId);
        member.putString("PlayerName", "Mayor");
        member.putString("PermissionLevel", "MAYOR");
        members.add(member);
        city.put("Members", members);

        ListTag transactions = new ListTag();
        CompoundTag transaction = new CompoundTag();
        transaction.putLong("Time", 1234L);
        transaction.putUUID("ActorId", memberId);
        transaction.putString("ActorName", "Mayor");
        transaction.putDouble("Amount", -25.5D);
        transaction.putDouble("BalanceAfter", 74.5D);
        transaction.putString("Type", "EXPENSE");
        transaction.putString("Reason", "construction");
        transactions.add(transaction);
        city.put("FinanceTransactions", transactions);

        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("cities-ledger.sqlite"))) {
            CitySqliteRepository repository = new CitySqliteRepository(database);
            try (Connection connection = database.borrowConnection()) {
                repository.saveAll(connection, citiesTag(city), DIMENSION);
            }

            CompoundTag loaded = repository.loadAll(DIMENSION);
            assertNotNull(loaded);
            CompoundTag loadedCity = loaded.getList("Cities", CompoundTag.TAG_COMPOUND).getCompound(0);
            assertEquals(100.0D, loadedCity.getDouble("Funds"));

            ListTag loadedMembers = loadedCity.getList("Members", CompoundTag.TAG_COMPOUND);
            assertEquals(1, loadedMembers.size());
            assertEquals(memberId, loadedMembers.getCompound(0).getUUID("PlayerId"));

            ListTag loadedTransactions = loadedCity.getList("FinanceTransactions", CompoundTag.TAG_COMPOUND);
            assertEquals(1, loadedTransactions.size());
            assertEquals(-25.5D, loadedTransactions.getCompound(0).getDouble("Amount"));
            assertEquals("construction", loadedTransactions.getCompound(0).getString("Reason"));
        }
    }

    @Test
    void loadAllReturnsNullForAnEmptyDimension() throws Exception {
        try (SimuSqliteDatabase database = openDatabase(tempDir.resolve("cities-empty.sqlite"))) {
            assertNull(new CitySqliteRepository(database).loadAll(DIMENSION));
        }
    }

    private static int loadedCityCount(CitySqliteRepository repository) {
        CompoundTag loaded = repository.loadAll(DIMENSION);
        return loaded == null ? 0 : loaded.getList("Cities", CompoundTag.TAG_COMPOUND).size();
    }

    private static CompoundTag citiesTag(CompoundTag... cities) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (CompoundTag city : cities) {
            list.add(city);
        }
        root.put("Cities", list);
        return root;
    }

    private static CompoundTag cityTag(UUID cityId, String name) {
        CompoundTag city = new CompoundTag();
        city.putUUID("CityId", cityId);
        city.putString("CityName", name);
        city.putString("DimensionId", DIMENSION);
        city.putInt("CoreX", 1);
        city.putInt("CoreY", 64);
        city.putInt("CoreZ", 2);
        city.putDouble("Funds", 100.0D);
        city.putInt("CityLevel", 1);
        city.put("Members", new ListTag());
        city.put("FinanceTransactions", new ListTag());
        return city;
    }

    private static SimuSqliteDatabase openDatabase(Path databasePath) throws Exception {
        Constructor<SimuSqliteDatabase> constructor = SimuSqliteDatabase.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(databasePath);
    }
}
