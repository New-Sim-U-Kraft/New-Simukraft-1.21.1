package common.cn.kafei.simukraft.storage.core;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 一次写入操作。事务边界由调用方（{@link TransactionRunner}）掌握，
 * 实现体只负责在给定连接上执行语句，不得自行 commit / rollback / close 连接。
 */
@FunctionalInterface
public interface SqlWrite {
    void write(Connection connection) throws SQLException;
}
