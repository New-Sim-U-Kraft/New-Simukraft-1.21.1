package common.cn.kafei.simukraft.storage.core;

/**
 * 需要在事务真正提交/回滚后收到通知的写入。
 * <p>{@link SqlWrite#write} 只表示语句执行完，事务此刻仍未提交；
 * 需要向调用方保证"落库完成"的同步写（submitAndWait）实现本接口，
 * 由 {@link TransactionRunner} 在 commit 成功或最终回滚后回调。
 */
public interface CommitAwareWrite extends SqlWrite {
    /** afterCommit: 事务提交（true）或最终失败回滚（false）后在写线程上回调。 */
    void afterCommit(boolean committed);
}
