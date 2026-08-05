package common.cn.kafei.simukraft.storage.core;

import java.sql.SQLException;

/**
 * SQLite 失败分类：一条写彻底失败（重试耗尽）后，决定是"丢弃该操作继续"还是"整库降级"。
 *
 * <ul>
 *   <li>{@link StorageFault#OP_FAULT}：操作自身的数据问题（约束冲突、op 代码抛出的运行时异常）。
 *       记日志、丢弃该操作、管线继续——一条坏数据不应拖垮整个存储；</li>
 *   <li>{@link StorageFault#ENV_FAULT}：环境故障（IO 错误、磁盘满、库损坏、忙超时耗尽、未知错误）。
 *       通知降级处理器，此后所有写入被拒绝，避免用不完整的内存状态覆盖磁盘。未知错误一律按环境处理，宁可保守。</li>
 * </ul>
 */
public final class StorageErrorPolicy {
    public enum StorageFault {
        OP_FAULT,
        ENV_FAULT
    }

    // SQLITE_CONSTRAINT = 19；扩展错误码（如 CONSTRAINT_PRIMARYKEY = 1555）的低 8 位是主码。
    private static final int SQLITE_CONSTRAINT = 19;

    private StorageErrorPolicy() {
    }

    public static StorageFault classify(SQLException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && (sqlException.getErrorCode() & 0xFF) == SQLITE_CONSTRAINT) {
                return StorageFault.OP_FAULT;
            }
        }
        return StorageFault.ENV_FAULT;
    }

    /** classify: op 代码抛出的运行时异常（NPE、非法参数等）是操作自身的问题，按 OP_FAULT 处理。 */
    public static StorageFault classify(RuntimeException exception) {
        return StorageFault.OP_FAULT;
    }

    /** 环境故障回调。由数据库句柄接线到降级标记。 */
    @FunctionalInterface
    public interface FaultHandler {
        void onEnvironmentFault(String context, Throwable cause);
    }
}
