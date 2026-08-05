package common.cn.kafei.simukraft.storage.core;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 存储运行指标。提交线程与写线程就地累加，读取方只能拿文本快照。
 * <p>用于 /simukraft storage 调试命令与故障现场定位：队列深度、写入条数、
 * 失败条数、慢批次数、单批最大条数、是否降级。
 */
public final class StorageMetrics {
    /** 提交耗时达到该阈值的批次记为慢批并打告警日志。 */
    public static final long SLOW_BATCH_MILLIS = 200L;

    private final LongAdder submitted = new LongAdder();
    private final LongAdder dropped = new LongAdder();
    private final LongAdder batches = new LongAdder();
    private final LongAdder executed = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder slowBatches = new LongAdder();
    private final AtomicLong maxBatchOps = new AtomicLong();

    public void recordSubmitted() {
        submitted.increment();
    }

    public void recordDropped() {
        dropped.increment();
    }

    public void recordBatch(int operations, long elapsedMillis) {
        batches.increment();
        executed.add(operations);
        maxBatchOps.accumulateAndGet(operations, Math::max);
        if (elapsedMillis >= SLOW_BATCH_MILLIS) {
            slowBatches.increment();
        }
    }

    public void recordFailed(int operations) {
        failed.add(operations);
    }

    public long failedCount() {
        return failed.sum();
    }

    public long slowBatchCount() {
        return slowBatches.sum();
    }

    public String summarize(int queueDepth, boolean degraded) {
        return String.format(Locale.ROOT,
                "queue=%d submitted=%d executed=%d failed=%d dropped=%d batches=%d slowBatches=%d maxBatchOps=%d degraded=%s",
                queueDepth, submitted.sum(), executed.sum(), failed.sum(), dropped.sum(),
                batches.sum(), slowBatches.sum(), maxBatchOps.get(), degraded);
    }
}
