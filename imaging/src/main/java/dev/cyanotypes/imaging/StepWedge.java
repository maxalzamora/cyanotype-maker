package dev.cyanotypes.imaging;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * A synthetic greyscale ramp, used to prove the pipeline does exactly what it claims.
 *
 * <p>Because the input values are known exactly, the expected output of a correct
 * inversion is known exactly. This is the cheapest possible test fixture and it catches
 * the classic failures: gamma applied twice, a range that saturates early, an off-by-one.
 */
public final class StepWedge {

    private StepWedge() {
    }

    /** Equal-width patches spanning 0..255, at least two steps. */
    public static BufferedImage source(int steps) {
        if (steps < 2) {
            throw new IllegalArgumentException("a wedge needs at least two steps");
        }
        int cell = 8;
        int height = 32;
        BufferedImage image = new BufferedImage(steps * cell, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int i = 0; i < steps; i++) {
                int value = grayAt(steps, i);
                graphics.setColor(new Color(value, value, value));
                graphics.fillRect(i * cell, 0, cell, height);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** The greyscale value of patch {@code index} in a wedge of {@code steps}. */
    public static int grayAt(int steps, int index) {
        return (int) Math.round(255.0 * index / (steps - 1));
    }
}
