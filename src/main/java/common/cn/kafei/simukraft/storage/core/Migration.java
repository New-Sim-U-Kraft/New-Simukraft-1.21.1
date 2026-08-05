package common.cn.kafei.simukraft.storage.core;

import java.sql.Connection;
import java.sql.SQLException;

/** 一次 schema 迁移。{@link #version()} 是本次迁移完成后的 {@code PRAGMA user_version}。 */
public interface Migration {
    int version();

    String description();

    void apply(Connection connection) throws SQLException;
}
