package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrayResamplerTest {

    @Test
    void aConstantImageStaysConstant() {
        Gray16 resized = GrayResampler.resize(gray(4, 4, 100), 9, 7);
        for (int y = 0; y < resized.height(); y++) {
            for (int x = 0; x < resized.width(); x++) {
                assertEquals(100 * 257, resized.sample(x, y));
            }
        }
    }

    @Test
    void interpolationLandsBetweenNeighbours() {
        BufferedImage twoByOne = gray(2, 1, 0);
        twoByOne.getRaster().setSample(1, 0, 0, 255);

        Gray16 resized = GrayResampler.resize(twoByOne, 3, 1);

        assertEquals(0, resized.sample(0, 0));
        assertTrue(resized.sample(1, 0) > 32000 && resized.sample(1, 0) < 33000,
                "midpoint should be about half of 65535, got " + resized.sample(1, 0));
        assertEquals(65535, resized.sample(2, 0));
    }

    @Test
    void wideningToSixteenBitsMultipliesByTwiceFiveSeven() {
        Gray16 resized = GrayResampler.resize(gray(4, 4, 255), 4, 4);
        assertEquals(65535, resized.sample(0, 0));
    }

    @Test
    void downscalingKeepsTheRange() {
        BufferedImage ramp = new BufferedImage(64, 1, BufferedImage.TYPE_BYTE_GRAY);
        for (int x = 0; x < 64; x++) {
            ramp.getRaster().setSample(x, 0, 0, x * 4);
        }
        Gray16 resized = GrayResampler.resize(ramp, 8, 1);

        assertTrue(resized.sample(0, 0) < 4000, "dark end: " + resized.sample(0, 0));
        assertTrue(resized.sample(7, 0) > 60000, "bright end: " + resized.sample(7, 0));
        for (int x = 1; x < 8; x++) {
            assertTrue(resized.sample(x, 0) > resized.sample(x - 1, 0),
                    "not increasing at " + x);
        }
    }

    @Test
    void rejectsNonPositiveTargets() {
        assertThrows(IllegalArgumentException.class,
                () -> GrayResampler.resize(gray(2, 2, 0), 0, 2));
    }

    private static BufferedImage gray(int width, int height, int value) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.getRaster().setSample(x, y, 0, value);
            }
        }
        return image;
    }
}
