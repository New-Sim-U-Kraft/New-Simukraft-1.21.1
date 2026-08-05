package common.cn.kafei.simukraft.storage.core;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 一次需要返回结果的 SQL 操作（读-改-写）。
 * <p>事务边界由 {@link TransactionRunner} 掌握，实现体只负责在给定连接上执行语句并返回结果，
 * 不得自行 commit / rollback / close 连接。
 */
@FunctionalInterface
public interface SqlFunction<T> {
    T apply(Connection connection) throws SQLException;
}
