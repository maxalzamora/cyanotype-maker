package dev.cyanotypes.imaging;

import java.awt.image.BufferedImage;

/**
 * Bilinear resampling of an 8-bit greyscale image into a 16-bit one, done on raw samples.
 *
 * <p>{@code Graphics2D} would treat {@code TYPE_BYTE_GRAY} as linear light, decode it,
 * resample, and re-encode, which shifts every value. Resampling samples directly is both
 * predictable and easy to reason about at the edges.
 */
public final class GrayResampler {

    private GrayResampler() {
    }

    public static Gray16 resize(BufferedImage source, int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("target dimensions must be positive");
        }
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        var raster = source.getRaster();
        short[] samples = new short[targetWidth * targetHeight];

        for (int y = 0; y < targetHeight; y++) {
            double sourceY = (y + 0.5) * sourceHeight / targetHeight - 0.5;
            int y0 = clamp((int) Math.floor(sourceY), sourceHeight);
            int y1 = Math.min(y0 + 1, sourceHeight - 1);
            double fy = clamp(sourceY - y0, 0.0, 1.0);

            for (int x = 0; x < targetWidth; x++) {
                double sourceX = (x + 0.5) * sourceWidth / targetWidth - 0.5;
                int x0 = clamp((int) Math.floor(sourceX), sourceWidth);
                int x1 = Math.min(x0 + 1, sourceWidth - 1);
                double fx = clamp(sourceX - x0, 0.0, 1.0);

                double top = lerp(raster.getSample(x0, y0, 0), raster.getSample(x1, y0, 0), fx);
                double bottom = lerp(raster.getSample(x0, y1, 0), raster.getSample(x1, y1, 0), fx);
                double value = lerp(top, bottom, fy);

                samples[y * targetWidth + x] = (short) Math.min(65535,
                        Math.round(value * 257.0));
            }
        }
        return new Gray16(targetWidth, targetHeight, samples);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int clamp(int value, int size) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, size - 1);
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
