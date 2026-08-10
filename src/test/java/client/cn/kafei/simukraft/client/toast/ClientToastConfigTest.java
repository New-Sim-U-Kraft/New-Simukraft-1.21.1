package client.cn.kafei.simukraft.client.toast;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class ClientToastConfigTest {
    /** Verifies all six toast anchor positions. */
    @Test
    void calculatesPositionsForAllAnchors() {
        assertPosition(ClientToastConfig.Anchor.TOP_LEFT, 7, 11);
        assertPosition(ClientToastConfig.Anchor.TOP_RIGHT, 207, 11);
        assertPosition(ClientToastConfig.Anchor.BOTTOM_LEFT, 7, 171);
        assertPosition(ClientToastConfig.Anchor.BOTTOM_RIGHT, 207, 171);
        assertPosition(ClientToastConfig.Anchor.TOP_CENTER, 107, 11);
        assertPosition(ClientToastConfig.Anchor.BOTTOM_CENTER, 107, 171);
    }

    private static void assertPosition(ClientToastConfig.Anchor anchor, int expectedX, int expectedY) {
        assertArrayEquals(
                new int[] {expectedX, expectedY},
                ClientToastConfig.calculatePosition(anchor, 7, 11, 300, 200, 100, 40));
    }
}
