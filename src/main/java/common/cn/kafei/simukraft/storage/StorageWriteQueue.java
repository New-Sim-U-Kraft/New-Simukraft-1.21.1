package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.storage.core.CommitAwareWrite;
import common.cn.kafei.simukraft.storage.core.SqlFunction;
import common.cn.kafei.simukraft.storage.core.SqlWrite;
import common.cn.kafei.simukraft.storage.core.StorageMetrics;
import common.cn.kafei.simukraft.storage.core.TransactionRunner;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单线程有序写队列。提供四个保证：
 * <ul>
 *   <li>顺序：同一存档内的写入严格按提交顺序落库，upsert 与 delete 不会倒置；</li>
 *   <li>合并：同一 key 的后续提交覆盖前一次未执行的提交（后者胜出），不再丢弃改动；</li>
 *   <li>批量：一次取走最多 {@value #MAX_BATCH_SIZE} 条合并进一个事务，把"每写一次 fsync"降成"每批一次"；</li>
 *   <li>可排空：{@link #drainAndReport()} 能在关服时等待队列清空，写入不再随 daemon 线程被丢弃。</li>
 * </ul>
 * 提交方只负责传入不可变快照，写线程绝不触碰领域对象。
 */
public final class StorageWriteQueue {
    // 批量上限刻意不大：批越大事务越长，主线程借池化连接做同步事务时被挡住的时间也越长。
    private static final int MAX_BATCH_SIZE = 128;
    private static final long DRAIN_TIMEOUT_MILLIS = 30_000L;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    // 替换已存在 key 时先 remove 再 put，把条目移到队尾：执行顺序因此反映最后一次提交，
    // 新快照不会被留在原位、被其后入队的 saveAll 旧快照覆盖（LinkedHashMap 直接 put 会保留原插入位置）。
    private final Map<Object, SqlWrite> pending = new LinkedHashMap<>();
    private final TransactionRunner transactions;
    private final StorageMetrics metrics;
    private final Thread worker;
    private volatile boolean closed;

    public StorageWriteQueue(String threadName, TransactionRunner transactions, StorageMetrics metrics) {
        this.transactions = transactions;
        this.metrics = metrics;
        this.worker = new Thread(this::runLoop, threadName);
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /**
     * 提交一次带合并键的写入。若该 key 已有未执行的提交，则用新 payload 替换并移到队尾，
     * 使落库顺序与最后一次提交顺序一致。
     *
     * @param key 合并键，需能唯一标识目标行（例如 {@code "citizens:<uuid>"}）
     * @param write 落库 payload，须为不可变快照
     */
    public void submit(Object key, SqlWrite write) {
        if (key == null || write == null) {
            return;
        }
        boolean rejected;
        lock.lock();
        try {
            rejected = closed;
            if (!rejected) {
                pending.remove(key);
                pending.put(key, write);
                metrics.recordSubmitted();
                notEmpty.signal();
            }
        } finally {
            lock.unlock();
        }
        if (rejected) {
            metrics.recordDropped();
            SimuKraft.LOGGER.warn("Simukraft: dropping storage write for {} because the write queue is closed.", key);
            // 丢弃也要通知等待方，否则 submitAndWait / drain 的调用线程会一直等到超时。
            // 回调放在锁外：它会完成 future，可能就地触发调用方的后续动作。
            notifyRejected(write);
        }
    }

    /** notifyRejected: 告知关心事务结果的写入"它永远不会被执行"。 */
    private static void notifyRejected(SqlWrite write) {
        if (write instanceof CommitAwareWrite aware) {
            try {
                aware.afterCommit(false);
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.warn("Simukraft: post-commit callback failed for a rejected storage write", exception);
            }
        }
    }

    /** submitOnce: 提交一次不参与合并的写入，严格按提交顺序执行。 */
    public void submitOnce(SqlWrite write) {
        submit(new Object(), write);
    }

    /**
     * 排空队列：提交一个屏障并等待它所在的事务提交完毕。单线程 FIFO 保证屏障之前的写入都已落库。
     * <p>屏障必须等 commit 而不是等语句执行完：屏障和它前面的写入通常在同一批同一个事务里，
     * 语句执行完时事务还没提交，此刻放行调用方会让紧随其后的读取（如 /simukraft reload 的读回）
     * 看到提交前的旧数据。
     *
     * @param timeoutMillis 等待排空的最长毫秒数
     * @return true 表示已排空；false 表示超时
     */
    public boolean drain(long timeoutMillis) {
        if (closed) {
            return pendingCount() == 0;
        }
        CountDownLatch barrier = new CountDownLatch(1);
        submitOnce(new BarrierWrite(barrier));
        try {
            return barrier.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** drainAndReport: 排空并在超时时报告剩余条数。 */
    public boolean drainAndReport() {
        boolean drained = drain(DRAIN_TIMEOUT_MILLIS);
        if (!drained) {
            SimuKraft.LOGGER.error("Simukraft: timed out draining SQLite writes; {} pending write(s) may be lost.", pendingCount());
        }
        return drained;
    }

    /**
     * submitAndWait: 提交一个需要返回值的"读-改-写"操作并阻塞等待结果。
     * <p>操作在写线程执行，future 在事务**真正提交后**才完成——返回即代表已落库，
     * 调用方随后的读一定能看到结果。与队列中此前的写入保持全序。
     * 必须在非写线程调用：写线程等待自己会死锁，嵌套事务还会破坏当前批的事务边界。
     * <p>失败（SQL 异常、超时、中断）返回 null，调用方按"存储不可用"处理。
     */
    public <T> T submitAndWait(long timeoutMillis, SqlFunction<T> function) {
        if (Thread.currentThread() == worker) {
            throw new IllegalStateException("SQLite write queue submitAndWait must not be called from the writer thread");
        }
        SyncWrite<T> write = new SyncWrite<>(function);
        submitOnce(write);
        try {
            return write.future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException exception) {
            SimuKraft.LOGGER.error("Simukraft: synchronous storage operation failed", exception.getCause());
            return null;
        } catch (TimeoutException exception) {
            SimuKraft.LOGGER.error("Simukraft: synchronous storage operation timed out after {} ms", timeoutMillis);
            return null;
        }
    }

    /**
     * BarrierWrite: drain 用的空写入。它自己不做任何事，只在所在事务提交（或最终回滚）后放行等待方，
     * 因此"屏障已完成"等价于"此前提交的写入都已落库"。
     */
    private static final class BarrierWrite implements CommitAwareWrite {
        private final CountDownLatch latch;

        private BarrierWrite(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void write(Connection connection) {
        }

        @Override
        public void afterCommit(boolean committed) {
            latch.countDown();
        }
    }

    /** SyncWrite: 带结果的同步写。语句执行完只暂存结果，事务提交后才完成 future。 */
    private static final class SyncWrite<T> implements CommitAwareWrite {
        private final SqlFunction<T> function;
        private final CompletableFuture<T> future = new CompletableFuture<>();
        private T result;

        private SyncWrite(SqlFunction<T> function) {
            this.function = function;
        }

        @Override
        public void write(Connection connection) throws SQLException {
            result = function.apply(connection);
        }

        @Override
        public void afterCommit(boolean committed) {
            if (committed) {
                future.complete(result);
            } else {
                future.completeExceptionally(new SQLException("storage transaction failed and was rolled back"));
            }
        }
    }

    public int pendingCount() {
        lock.lock();
        try {
            return pending.size();
        } finally {
            lock.unlock();
        }
    }

    /** close: 停止接收新写入并等待写线程退出。调用方应先执行 {@link #drainAndReport()}。 */
    public void close(long timeoutMillis) {
        lock.lock();
        try {
            closed = true;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
        try {
            worker.join(timeoutMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void runLoop() {
        while (true) {
            List<SqlWrite> batch = takeBatch();
            if (batch == null) {
                return;
            }
            try {
                transactions.runBatch(batch);
            } catch (RuntimeException | Error throwable) {
                // 单批失败不能让写线程退出，否则后续所有写入都会静默积压。
                SimuKraft.LOGGER.error("Simukraft: storage write batch failed", throwable);
            }
        }
    }

    /** takeBatch: 阻塞取出下一批写入；返回 null 表示队列已关闭且没有剩余工作。 */
    private List<SqlWrite> takeBatch() {
        lock.lock();
        try {
            while (pending.isEmpty()) {
                if (closed) {
                    return null;
                }
                notEmpty.await();
            }
            List<SqlWrite> batch = new ArrayList<>(Math.min(pending.size(), MAX_BATCH_SIZE));
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext() && batch.size() < MAX_BATCH_SIZE) {
                batch.add(iterator.next().getValue());
                iterator.remove();
            }
            return batch;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.unlock();
        }
    }
}
