package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.storage.core.SqliteConnectionPool;
import common.cn.kafei.simukraft.storage.core.StorageMetrics;
import common.cn.kafei.simukraft.storage.core.TransactionRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定写队列的契约：合并、替换移队尾、跨 key 顺序、可排空。 */
class StorageWriteQueueTest {
    @TempDir
    Path tempDir;

    private SqliteConnectionPool pool;
    private StorageMetrics metrics;
    private AtomicInteger environmentFaults;

    private StorageWriteQueue newQueue(String name) {
        pool = SqliteConnectionPool.open(tempDir.resolve(name + ".sqlite"));
        metrics = new StorageMetrics();
        environmentFaults = new AtomicInteger();
        TransactionRunner transactions = new TransactionRunner(pool, (context, cause) -> environmentFaults.incrementAndGet(), metrics);
        return new StorageWriteQueue(name, transactions, metrics);
    }

    private void closeQueue(StorageWriteQueue queue) {
        queue.close(1_000L);
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    private static void awaitGate(CountDownLatch gate) {
        try {
            gate.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** awaitPending: 等队列深度到达期望值，用来卡住"写线程正阻塞、后续提交都还堆在队列里"的时机。 */
    private static void awaitPending(StorageWriteQueue queue, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (queue.pendingCount() != expected) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("等待队列深度 " + expected + " 超时，当前为 " + queue.pendingCount());
            }
            Thread.onSpinWait();
        }
    }

    @Test
    void laterSubmitForSameKeyReplacesPendingOne() throws Exception {
        List<String> executed = new CopyOnWriteArrayList<>();
        CountDownLatch gate = new CountDownLatch(1);
        StorageWriteQueue queue = newQueue("test-coalesce");
        try {
            // 先占住写线程，保证后续提交都还堆在队列里，才能观察到合并行为。
            queue.submitOnce(connection -> awaitGate(gate));
            awaitPending(queue, 0);
            queue.submit("citizens:1", connection -> executed.add("v1"));
            queue.submit("citizens:1", connection -> executed.add("v2"));
            queue.submit("citizens:1", connection -> executed.add("v3"));
            gate.countDown();

            assertTrue(queue.drain(5_000L));
            // 同一 key 只保留最后一次提交，前两次快照被合并掉。
            assertEquals(List.of("v3"), executed);
        } finally {
            closeQueue(queue);
        }
    }

    @Test
    void deleteAfterUpsertOfSameKeyWins() throws Exception {
        List<String> executed = new CopyOnWriteArrayList<>();
        CountDownLatch gate = new CountDownLatch(1);
        StorageWriteQueue queue = newQueue("test-delete-wins");
        try {
            queue.submitOnce(connection -> awaitGate(gate));
            awaitPending(queue, 0);
            queue.submit("citizens:1", connection -> executed.add("upsert"));
            queue.submit("citizens:1", connection -> executed.add("delete"));
            gate.countDown();

            assertTrue(queue.drain(5_000L));
            // upsert 与 delete 共用合并键，后提交的删除必须胜出，否则已删除的数据会复活。
            assertEquals(List.of("delete"), executed);
        } finally {
            closeQueue(queue);
        }
    }

    @Test
    void differentKeysKeepSubmissionOrder() throws Exception {
        List<Integer> executed = new CopyOnWriteArrayList<>();
        StorageWriteQueue queue = newQueue("test-order");
        try {
            for (int index = 0; index < 200; index++) {
                int value = index;
                queue.submit("row:" + index, connection -> executed.add(value));
            }
            assertTrue(queue.drain(5_000L));

            assertEquals(200, executed.size());
            List<Integer> sorted = executed.stream().sorted().toList();
            assertEquals(sorted, executed, "跨 key 的写入必须保持提交顺序");
        } finally {
            closeQueue(queue);
        }
    }

    @Test
    void replacingPayloadMovesEntryToQueueTail() throws Exception {
        List<String> executed = new CopyOnWriteArrayList<>();
        CountDownLatch gate = new CountDownLatch(1);
        StorageWriteQueue queue = newQueue("test-position");
        try {
            queue.submitOnce(connection -> awaitGate(gate));
            awaitPending(queue, 0);
            queue.submit("a", connection -> executed.add("a1"));
            queue.submit("b", connection -> executed.add("b1"));
            queue.submit("a", connection -> executed.add("a2"));
            gate.countDown();

            assertTrue(queue.drain(5_000L));
            // 覆盖 payload 必须把 a 移到队尾：留在原位会让 a2 先于 b1 执行，
            // b 若是 saveAll 的旧快照，就会把 a2 的新数据覆盖回旧值。
            assertEquals(List.of("b1", "a2"), executed);
        } finally {
            closeQueue(queue);
        }
    }

    @Test
    void newerUpsertExecutesAfterOlderBatchSnapshot() throws Exception {
        List<String> executed = new CopyOnWriteArrayList<>();
        CountDownLatch gate = new CountDownLatch(1);
        StorageWriteQueue queue = newQueue("test-tail-order");
        try {
            queue.submitOnce(connection -> awaitGate(gate));
            awaitPending(queue, 0);
            queue.submit("citizens:1", connection -> executed.add("upsert-v1"));
            queue.submitOnce(connection -> executed.add("saveAll-snapshot"));
            queue.submit("citizens:1", connection -> executed.add("upsert-v2"));
            gate.countDown();

            assertTrue(queue.drain(5_000L));
            // M1 回归：upsert(v1) → saveAll(含 v1 的旧快照) → upsert(v2)。
            // v2 必须最后执行，落库的才是最新值而不是快照里的旧值。
            assertEquals(List.of("saveAll-snapshot", "upsert-v2"), executed);
        } finally {
            closeQueue(queue);
        }
    }

    @Test
    void submitAfterCloseIsRejectedInsteadOfSilentlyQueued() {
        StorageWriteQueue queue = newQueue("test-closed");
        queue.close(1_000L);

        queue.submit("citizens:1", connection -> {
            throw new AssertionError("closed queue must not execute tasks");
        });

        assertEquals(0, queue.pendingCount());
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    @Test
    void failingTaskDoesNotKillTheWriterThread() throws Exception {
        List<String> executed = new CopyOnWriteArrayList<>();
        StorageWriteQueue queue = newQueue("test-resilience");
        try {
            queue.submitOnce(connection -> {
                throw new IllegalStateException("boom");
            });
            queue.submit("after", connection -> executed.add("after"));
            assertTrue(queue.drain(5_000L));

            assertEquals(List.of("after"), executed);
        } finally {
            closeQueue(queue);
        }
    }

    /**
     * 回归：drain 的屏障原来只是 {@code connection -> latch.countDown()}，countDown 发生在语句执行阶段，
     * 而屏障和它前面的写入通常同批同事务——此刻事务还没提交。调用方（如 /simukraft reload 在 flush 之后
     * 立刻读回）因此可能看到提交前的旧数据。屏障必须等 commit。
     */
    @Test
    void drainWaitsForTheTransactionToCommitNotJustForStatementsToRun() throws Exception {
        StorageWriteQueue queue = newQueue("test-drain-commit");
        try {
            try (Connection connection = pool.borrow(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE drain_probe(id INTEGER PRIMARY KEY)");
            }

            CountDownLatch gate = new CountDownLatch(1);
            queue.submitOnce(connection -> awaitGate(gate));
            // 等写线程把上面那条取走并卡在 gate 上，之后的提交才保证还堆在队列里。
            awaitPending(queue, 0);
            // 先占住一个合并键的位置，稍后用同键重提交把真正的写入移到屏障之后。
            queue.submit("row", connection -> { });

            AtomicInteger rowsVisibleWhenDrainReturned = new AtomicInteger(-1);
            AtomicBoolean drained = new AtomicBoolean();
            CountDownLatch finished = new CountDownLatch(1);
            Thread drainer = new Thread(() -> {
                drained.set(queue.drain(30_000L));
                rowsVisibleWhenDrainReturned.set(countProbeRows());
                finished.countDown();
            }, "drain-probe");
            drainer.start();
            awaitPending(queue, 2);

            /*
             * 同键重提交会 remove 再 put，把这条写入移到队尾——也就是屏障之后。
             * 于是"屏障语句执行完"和"事务提交"之间隔着一条 500ms 的写入：
             * 屏障若在语句阶段就放行调用方，drain 会在 INSERT 还没跑完时返回，读不到这一行。
             */
            queue.submit("row", connection -> {
                sleepQuietly(500L);
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO drain_probe(id) VALUES(1)")) {
                    statement.executeUpdate();
                }
            });
            awaitPending(queue, 2);
            gate.countDown();

            assertTrue(finished.await(30, TimeUnit.SECONDS), "drain 没有在超时前返回");
            assertTrue(drained.get());
            assertEquals(1, rowsVisibleWhenDrainReturned.get(),
                    "drain 返回即代表已落库：屏障必须等事务提交，而不是自己的语句一跑完就放行");
        } finally {
            closeQueue(queue);
        }
    }

    private int countProbeRows() {
        try (Connection connection = pool.borrow();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM drain_probe");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 回归：submit 在队列已关闭时静默丢弃且不通知等待方，submitAndWait 的 future 永不完成，
     * 调用线程（关服路径上就是主线程）会白等满整个超时。
     */
    @Test
    void submitAndWaitAfterCloseFailsFastInsteadOfWaitingForTimeout() {
        StorageWriteQueue queue = newQueue("test-sync-after-close");
        queue.close(1_000L);

        long startedAt = System.nanoTime();
        String result = queue.submitAndWait(10_000L, connection -> "never");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertNull(result, "队列已关闭时必须返回 null");
        assertTrue(elapsedMillis < 1_000L, "队列已关闭时必须立刻返回，实测等了 " + elapsedMillis + " ms");
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    @Test
    void submitAndWaitReturnsResultAfterPreviouslySubmittedWrites() throws Exception {
        List<String> executed = new CopyOnWriteArrayList<>();
        StorageWriteQueue queue = newQueue("test-sync-order");
        try {
            queue.submitOnce(connection -> executed.add("first"));
            String result = queue.submitAndWait(5_000L, connection -> {
                executed.add("sync");
                return "done";
            });

            assertEquals("done", result);
            // 同步操作必须排在它之前提交的写入之后执行，保持全序。
            assertEquals(List.of("first", "sync"), executed);
        } finally {
            closeQueue(queue);
        }
    }

    @Test
    void submitAndWaitReturnsNullOnSqlFailure() throws Exception {
        StorageWriteQueue queue = newQueue("test-sync-failure");
        try {
            String result = queue.submitAndWait(5_000L, connection -> {
                throw new java.sql.SQLException("boom");
            });
            assertNull(result, "SQL 失败必须返回 null 而不是把异常抛向调用线程");
        } finally {
            closeQueue(queue);
        }
    }

    @Test
    void submitAndWaitFromWriterThreadFailsFastInsteadOfDeadlocking() throws Exception {
        StorageWriteQueue queue = newQueue("test-sync-guard");
        try {
            String result = queue.submitAndWait(5_000L, connection -> {
                try {
                    queue.submitAndWait(5_000L, ignored -> "never");
                    return "not-guarded";
                } catch (IllegalStateException expected) {
                    return "guarded";
                }
            });
            assertEquals("guarded", result, "写线程内调用 submitAndWait 必须立即抛异常而不是死锁");
        } finally {
            closeQueue(queue);
        }
    }

    @Test
    void submitAndWaitResultIsVisibleToLaterReaders() throws Exception {
        StorageWriteQueue queue = newQueue("test-sync-visibility");
        try {
            try (Connection connection = pool.borrow(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS sync_probe(id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
            }

            String result = queue.submitAndWait(5_000L, connection -> {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO sync_probe(value) VALUES('committed')")) {
                    statement.executeUpdate();
                }
                return "done";
            });
            assertEquals("done", result);

            // submitAndWait 返回即代表事务已提交：随后借连接读必须立刻看到数据。
            try (Connection connection = pool.borrow();
                 PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM sync_probe");
                 ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1), "submitAndWait 返回后数据必须已经提交可见");
            }
        } finally {
            closeQueue(queue);
        }
    }

    @Test
    void constraintFailureDropsOnlyTheBadOperationWithoutDegrading() throws Exception {
        StorageWriteQueue queue = newQueue("test-op-fault");
        try {
            try (Connection connection = pool.borrow(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE constraint_probe(sequence INTEGER PRIMARY KEY, stage TEXT NOT NULL)");
            }

            queue.submitOnce(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO constraint_probe(sequence, stage) VALUES(1, 'before')")) {
                    statement.executeUpdate();
                }
            });
            queue.submitOnce(connection -> {
                throw new java.sql.SQLException("pk conflict", "23000", 19);
            });
            queue.submitOnce(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO constraint_probe(sequence, stage) VALUES(2, 'after')")) {
                    statement.executeUpdate();
                }
            });

            assertTrue(queue.drain(5_000L));
            try (Connection connection = pool.borrow();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT stage FROM constraint_probe ORDER BY sequence");
                 ResultSet resultSet = statement.executeQuery()) {
                List<String> persisted = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    persisted.add(resultSet.getString(1));
                }
                assertEquals(List.of("before", "after"), persisted,
                        "约束冲突只丢弃出错的那条，不拖垮同批其它写入");
            }
            assertEquals(0, environmentFaults.get(), "约束冲突是操作自身问题，不得触发降级");
            assertEquals(1, metrics.failedCount());
        } finally {
            closeQueue(queue);
        }
    }

    @Test
    void environmentFailureTriggersDegradationHandler() throws Exception {
        StorageWriteQueue queue = newQueue("test-env-fault");
        try {
            queue.submitOnce(connection -> {
                throw new java.sql.SQLException("disk full", "HY000", 14);
            });

            assertTrue(queue.drain(5_000L));
            assertEquals(1, environmentFaults.get(), "环境故障必须上报降级处理器");
        } finally {
            closeQueue(queue);
        }
    }
}
