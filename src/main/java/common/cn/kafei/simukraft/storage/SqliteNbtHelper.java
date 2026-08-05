package common.cn.kafei.simukraft.storage;

import net.minecraft.nbt.CompoundTag;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@SuppressWarnings("null")
final class SqliteNbtHelper {
    private SqliteNbtHelper() {
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
