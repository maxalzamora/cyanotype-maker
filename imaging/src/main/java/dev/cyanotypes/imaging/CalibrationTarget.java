package dev.cyanotypes.imaging;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * The printable calibration target: one vertical bar per file value, full height, edge to
 * edge.
 *
 * <p>This is deliberately the least clever image the program can make. It is written
 * <em>without</em> any tone mapping, because its purpose is to discover what the mapping
 * should be. If the target were rendered through a profile, calibrating with it would be
 * circular.
 *
 * <p>Bars rather than a grid, and no labels inside the frame, for one reason: the person
 * photographing the finished print only has to crop to a block of stripes. Nothing in the
 * image has to be located, so nothing in the image can be mislocated. Labels live in the
 * instructions, not in the pixels.
 */
public final class CalibrationTarget {

    /** Odd number so a middle patch exists; 21 matches the diagnostics wedge. */
    public static final int PATCHES = 21;

    /** 3:1, large enough that a phone photograph resolves each bar comfortably. */
    public static final int WIDTH = 2100;
    public static final int HEIGHT = 700;

    /**
     * How much of each cell is sampled. The gap absorbs a crop that is a few percent off:
     * the sampler still only ever sees the inside of a bar.
     */
    private static final double SAMPLE_HALF_WIDTH = 0.35;
    private static final double SAMPLE_TOP = 0.20;
    private static final double SAMPLE_BOTTOM = 0.80;

    private CalibrationTarget() {
    }

    /** The 8-bit value written into patch {@code index}, 0 (solid ink) to 255 (clear). */
    public static int patchValue(int index) {
        if (index < 0 || index >= PATCHES) {
            throw new IllegalArgumentException("patch index out of range: " + index);
        }
        return (int) Math.round(index * 255.0 / (PATCHES - 1));
    }

    /** Which patch a column of a target of the given width belongs to. */
    public static int patchAt(int x, int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        return Math.min(PATCHES - 1, Math.max(0, x) * PATCHES / width);
    }

    /** The target itself: raw values, no profile, 8-bit greyscale. */
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        var raster = image.getRaster();
        for (int x = 0; x < WIDTH; x++) {
            int value = patchValue(patchAt(x, WIDTH));
            for (int y = 0; y < HEIGHT; y++) {
                // setSample, not setRGB: see NegativeRenderer for why this matters.
                raster.setSample(x, y, 0, value);
            }
        }
        return image;
    }

    /**
     * The area sampled for patch {@code index}, in pixels of an image of this size.
     *
     * <p>Shared with the renderer so a target can never drift from the grid that reads it
     * back: the same fractions define both.
     */
    public static Rectangle sampleWindow(int index, int width, int height) {
        if (index < 0 || index >= PATCHES) {
            throw new IllegalArgumentException("patch index out of range: " + index);
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("image dimensions must be positive");
        }
        double cell = width / (double) PATCHES;
        double centre = (index + 0.5) * cell;
        int x0 = (int) Math.floor(centre - SAMPLE_HALF_WIDTH * cell);
        int x1 = (int) Math.ceil(centre + SAMPLE_HALF_WIDTH * cell);
        int y0 = (int) Math.floor(SAMPLE_TOP * height);
        int y1 = (int) Math.ceil(SAMPLE_BOTTOM * height);

        x0 = Math.max(0, Math.min(width - 1, x0));
        x1 = Math.max(x0 + 1, Math.min(width, x1));
        y0 = Math.max(0, Math.min(height - 1, y0));
        y1 = Math.max(y0 + 1, Math.min(height, y1));
        return new Rectangle(x0, y0, x1 - x0, y1 - y0);
    }
}
