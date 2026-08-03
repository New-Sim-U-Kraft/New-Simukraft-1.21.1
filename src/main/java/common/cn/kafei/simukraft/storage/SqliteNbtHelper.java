package common.cn.kafei.simukraft.storage;

import net.minecraft.nbt.CompoundTag;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

@SuppressWarnings("Null")
final class SqliteNbtHelper {
    private SqliteNbtHelper() {
    }

    static void clearTables(java.sql.Connection connection, String... tables) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String table : tables) {
                statement.executeUpdate("DELETE FROM " + table);
            }
        }
    }

    static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    static void putNullableUuid(CompoundTag tag, String key, String value) {
        if (value != null && !value.isBlank()) {
            tag.putUUID(key, java.util.UUID.fromString(value));
        }
    }
}
