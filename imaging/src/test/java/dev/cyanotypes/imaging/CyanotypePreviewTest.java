package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CyanotypePreviewTest {

    @Test
    void opaqueFilmLeavesThePaperBare() {
        int print = CyanotypePreview.colourFor(0);
        assertClose(CyanotypePreview.PAPER, print, 1, "value 0 blocks UV, so the paper stays white");
    }

    @Test
    void clearFilmPrintsFullPrussianBlue() {
        int print = CyanotypePreview.colourFor(255);
        assertClose(CyanotypePreview.INK, print, 1, "value 255 passes all UV, so the ink is full");
    }

    @Test
    void midtonesStayOnTheBluePath() {
        int print = CyanotypePreview.colourFor(128);
        int red = (print >> 16) & 0xFF;
        int green = (print >> 8) & 0xFF;
        int blue = print & 0xFF;

        assertTrue(blue > green && green > red,
                "a print is blue, not a grey ramp: " + hex(print));
        assertTrue(luminance(print) > luminance(CyanotypePreview.INK));
        assertTrue(luminance(print) < luminance(CyanotypePreview.PAPER));
    }

    @Test
    void moreExposureIsNeverLighter() {
        // 8-bit rounding makes neighbouring entries equal now and then; it must never
        // make a later one lighter, which would show as a contour in a smooth sky.
        for (int value = 0; value < 255; value++) {
            assertTrue(luminance(CyanotypePreview.colourFor(value))
                            >= luminance(CyanotypePreview.colourFor(value + 1)),
                    "the palette reversed between " + value + " and " + (value + 1));
        }
    }

    @Test
    void rejectsValuesOutsideAByte() {
        assertThrows(IllegalArgumentException.class, () -> CyanotypePreview.colourFor(-1));
        assertThrows(IllegalArgumentException.class, () -> CyanotypePreview.colourFor(256));
    }

    @Test
    void downsamplesToTheLimitWithoutCropping() {
        BufferedImage print = CyanotypePreview.render(flat(400, 300, 128), 140);

        assertEquals(140, print.getWidth());
        assertEquals(105, print.getHeight(), "the aspect ratio is kept");
    }

    @Test
    void neverUpscalesASmallNegative() {
        BufferedImage print = CyanotypePreview.render(flat(20, 10, 128), 1400);

        assertEquals(20, print.getWidth());
        assertEquals(10, print.getHeight());
    }

    @Test
    void averagesTheBlockBeforeColouring() {
        BufferedImage negative = new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_GRAY);
        negative.getRaster().setSample(0, 0, 0, 0);
        negative.getRaster().setSample(1, 0, 0, 0);
        negative.getRaster().setSample(0, 1, 0, 0);
        negative.getRaster().setSample(1, 1, 0, 255);

        BufferedImage print = CyanotypePreview.render(negative, 1);

        assertEquals(1, print.getWidth());
        assertEquals(1, print.getHeight());
        assertClose(CyanotypePreview.colourFor(64), print.getRGB(0, 0), 6,
                "downsampling averages the film's density, so three opaque quarters and one "
                        + "clear quarter become a quarter-tone print");
    }

    @Test
    void rejectsAnImageWithoutASingleBand() {
        BufferedImage rgb = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class, () -> CyanotypePreview.render(rgb, 100));
    }

    @Test
    void rejectsANonPositiveLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> CyanotypePreview.render(flat(4, 4, 0), 0));
    }

    private static BufferedImage flat(int width, int height, int value) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.getRaster().setSample(x, y, 0, value);
            }
        }
        return image;
    }

    private static double luminance(int rgb) {
        return Srgb.luminance(
                Srgb.decode(((rgb >> 16) & 0xFF) / 255.0),
                Srgb.decode(((rgb >> 8) & 0xFF) / 255.0),
                Srgb.decode((rgb & 0xFF) / 255.0));
    }

    private static void assertClose(int expected, int actual, int tolerance, String message) {
        int dr = Math.abs(((expected >> 16) & 0xFF) - ((actual >> 16) & 0xFF));
        int dg = Math.abs(((expected >> 8) & 0xFF) - ((actual >> 8) & 0xFF));
        int db = Math.abs((expected & 0xFF) - (actual & 0xFF));
        assertTrue(dr <= tolerance && dg <= tolerance && db <= tolerance,
                message + ": expected " + hex(expected) + " but was " + hex(actual));
    }

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
