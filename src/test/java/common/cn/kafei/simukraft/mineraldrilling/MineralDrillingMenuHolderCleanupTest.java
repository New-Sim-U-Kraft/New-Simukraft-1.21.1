package common.cn.kafei.simukraft.mineraldrilling;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineralDrillingMenuHolderCleanupTest {
    /** successfulDemolitionClosesWithoutRefreshing: 成功拆除只关闭菜单，不重建空状态。 */
    @Test
    void successfulDemolitionClosesWithoutRefreshing() {
        AtomicInteger refreshes = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();

        MineralDrillingMenuHolder.completeServerAction(
                true, true, refreshes::incrementAndGet, closes::incrementAndGet);

        assertEquals(0, refreshes.get());
        assertEquals(1, closes.get());
    }

    /** rejectedDemolitionRefreshesSnapshot: 被拒绝的拆除仍刷新服务端状态提示。 */
    @Test
    void rejectedDemolitionRefreshesSnapshot() {
        AtomicInteger refreshes = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();

        MineralDrillingMenuHolder.completeServerAction(
                false, true, refreshes::incrementAndGet, closes::incrementAndGet);

        assertEquals(1, refreshes.get());
        assertEquals(0, closes.get());
    }
}
