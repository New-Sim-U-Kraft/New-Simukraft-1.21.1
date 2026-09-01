package client.cn.kafei.simukraft.client.city.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimuMapColorMathTest {
    @Test
    void multiplyTint_turnsGrayscaleGrassTextureIntoOlive() {
        int grassTexture = 0xFF9A9A9A;
        int plainsTint = 0x91BD59;
        int color = SimuBlockColors.multiplyTint(grassTexture, plainsTint);

        int expected = 0xFF000000
                | ((0x9A * 0x91 / 255) << 16)
                | ((0x9A * 0xBD / 255) << 8)
                | (0x9A * 0x59 / 255);
        assertEquals(expected, color);
        assertTrue(((color >> 16) & 0xFF) < 0x70);
        assertTrue(((color >> 8) & 0xFF) > 0x60);
    }

    @Test
    void multiplyTint_ignoresWhiteTint() {
        assertEquals(0xFF336699, SimuBlockColors.multiplyTint(0xFF336699, 0xFFFFFF));
    }

    @Test
    void slopeBrightness_raisesNorthwestRidgesAndDarkensValleys() {
        float ridge = SimuBlockColors.slopeBrightness(70, 64, 64, false);
        float valley = SimuBlockColors.slopeBrightness(64, 70, 70, false);
        float flat = SimuBlockColors.slopeBrightness(64, 64, 64, false);

        assertTrue(ridge > 0.3f);
        assertTrue(valley < -0.3f);
        assertEquals(0.0f, flat, 0.0001f);
    }

    @Test
    void slopeBrightness_isWeakerOnWater() {
        float land = SimuBlockColors.slopeBrightness(66, 64, 64, false);
        float water = SimuBlockColors.slopeBrightness(66, 64, 64, true);
        assertTrue(Math.abs(water) < Math.abs(land));
    }

    @Test
    void adjustBrightness_lightensAndDarkensWithoutClippingAlpha() {
        int lighter = SimuBlockColors.adjustBrightness(0xFF408040, 0.25f);
        int darker = SimuBlockColors.adjustBrightness(0xFF408040, -0.25f);

        assertEquals(0xFF, (lighter >>> 24) & 0xFF);
        assertTrue(((lighter >>> 8) & 0xFF) > 0x80);
        assertTrue((darker & 0xFF) < 0x40);
    }
}
