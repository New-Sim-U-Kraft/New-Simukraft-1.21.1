package client.cn.kafei.simukraft.client.logistics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogisticsMapOverlayTest {
    @Test
    void laneOffset_spreadsOverlappingRoutesAroundCenter() {
        assertEquals(0.0F, LogisticsMapOverlay.laneOffset(0, 1));
        assertEquals(-2.5F, LogisticsMapOverlay.laneOffset(0, 2), 0.001F);
        assertEquals(2.5F, LogisticsMapOverlay.laneOffset(1, 2), 0.001F);
        assertEquals(0.0F, LogisticsMapOverlay.laneOffset(1, 3), 0.001F);
    }

    @Test
    void offsetAlongNormal_movesHorizontalLineVertically() {
        float[] offset = LogisticsMapOverlay.offsetAlongNormal(0, 0, 10, 0, 4);
        assertArrayEquals(new float[] {0, 4, 10, 4}, offset, 0.001F);
    }

    @Test
    void arrowStops_placeSingleArrowNearDestination() {
        assertEquals(0, LogisticsMapOverlay.arrowStops(10.0F).length);
        float[] medium = LogisticsMapOverlay.arrowStops(20.0F);
        assertEquals(1, medium.length);
        assertEquals(0.84F, medium[0], 0.001F);
        float[] longPath = LogisticsMapOverlay.arrowStops(100.0F);
        assertEquals(1, longPath.length);
        assertEquals(0.91F, longPath[0], 0.001F);
    }

    @Test
    void animationPhase_wrapsInsidePeriod() {
        assertEquals(0.0F, LogisticsMapOverlay.animationPhase(0L, 900L), 0.001F);
        assertEquals(0.5F, LogisticsMapOverlay.animationPhase(450L, 900L), 0.001F);
        assertEquals(0.0F, LogisticsMapOverlay.animationPhase(900L, 900L), 0.001F);
    }

    @Test
    void flowShift_movesMarkAlongSpacing() {
        assertEquals(0.0F, LogisticsMapOverlay.flowShift(0.0F, 26.0F), 0.001F);
        assertEquals(13.0F, LogisticsMapOverlay.flowShift(0.5F, 26.0F), 0.001F);
        assertEquals(0.0F, LogisticsMapOverlay.flowShift(1.0F, 26.0F), 0.001F);
    }

    @Test
    void containsMarker_usesIconHitbox() {
        assertTrue(LogisticsMapOverlay.containsMarker(5, 5, 0, 0));
        assertTrue(LogisticsMapOverlay.containsMarker(10, 0, 0, 0));
        assertFalse(LogisticsMapOverlay.containsMarker(11, 0, 0, 0));
    }

    @Test
    void lineSteps_coversPixelLength() {
        assertEquals(10, LogisticsMapOverlay.lineSteps(0, 0, 10, 0));
        assertEquals(1, LogisticsMapOverlay.lineSteps(3, 4, 3, 4));
    }

    @Test
    void distanceToSegment_usesClosestPointOnLine() {
        assertEquals(0.0, LogisticsMapOverlay.distanceToSegment(5, 0, 0, 0, 10, 0), 0.001);
        assertEquals(3.0, LogisticsMapOverlay.distanceToSegment(5, 3, 0, 0, 10, 0), 0.001);
        assertEquals(10.0, LogisticsMapOverlay.distanceToSegment(20, 0, 0, 0, 10, 0), 0.001);
        assertTrue(LogisticsMapOverlay.isNearRoute(5, 3, 0, 0, 10, 0, 0));
        assertFalse(LogisticsMapOverlay.isNearRoute(5, 20, 0, 0, 10, 0, 0));
    }
}
