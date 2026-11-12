package dev.cyanotypes.imaging;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Objects;

/**
 * Renders a negative as the cyanotype it would make.
 *
 * <p>The negative already carries the print's density: a high byte is clear film, which
 * lets UV through and turns the paper blue. So the preview is not a second tone mapping.
 * It is the same density, given the colour of the process. Whatever range the film
 * reaches is what the print reaches, which is why a soft profile shows a soft print
 * rather than a full-scale one.
 *
 * <p>Paper and ink are mixed in density -- a geometric interpolation of reflectances,
 * which is how stacked absorbing layers combine. That keeps the midtones on the blue
 * path instead of washing transparent ink over white.
 */
public final class CyanotypePreview {

    /** Unexposed, washed paper: warm white, not white. */
    public static final int PAPER = 0xF6F1E4;

    /** Fully exposed Prussian blue. */
    public static final int INK = 0x0F3355;

    /** A little tooth, so the preview reads as paper rather than a colour ramp. */
    private static final double GRAIN = 0.015;

    /** Hand-coated emulsion is never perfectly even; 4% at the corners. */
    private static final double FALLOFF = 0.04;

    /** Negative value to print colour, built once. */
    private static final int[] PALETTE = palette();

    private CyanotypePreview() {
    }

    /**
     * Converts a single-band negative into an 8-bit RGB print, downsampling so the long
     * side is at most {@code maxDimension}. Downsampling averages the negative samples
     * before colouring, which is the same as averaging light in the print.
     */
    public static BufferedImage render(BufferedImage negative, int maxDimension) {
        Objects.requireNonNull(negative, "negative");
        if (maxDimension <= 0) {
            throw new IllegalArgumentException("maxDimension must be positive");
        }
        int width = negative.getWidth();
        int height = negative.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("negative has no pixels");
        }
        var raster = negative.getRaster();
        if (raster.getNumBands() != 1) {
            throw new IllegalArgumentException("expected a single-band negative, got "
                    + raster.getNumBands() + " bands");
        }

        double scale = Math.min(1.0, maxDimension / (double) Math.max(width, height));
        int outWidth = Math.max(1, (int) Math.round(width * scale));
        int outHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage print = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) print.getRaster().getDataBuffer()).getData();

        for (int y = 0; y < outHeight; y++) {
            int y0 = (int) ((long) y * height / outHeight);
            int y1 = (int) Math.max(y0 + 1L, (long) (y + 1) * height / outHeight);
            double dy = (y + 0.5) / outHeight - 0.5;
            for (int x = 0; x < outWidth; x++) {
                int x0 = (int) ((long) x * width / outWidth);
                int x1 = (int) Math.max(x0 + 1L, (long) (x + 1) * width / outWidth);

                long sum = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        sum += raster.getSample(sx, sy, 0);
                    }
                }
                int value = (int) Math.round(sum / (double) ((x1 - x0) * (y1 - y0)));

                double dx = (x + 0.5) / outWidth - 0.5;
                double radius2 = (dx * dx + dy * dy) * 2.0;
                double factor = (1.0 - FALLOFF * radius2) * (1.0 + grain(x, y));
                pixels[y * outWidth + x] = shade(PALETTE[value], factor);
            }
        }
        return print;
    }

    /** The colour of a negative value, before grain and falloff. */
    public static int colourFor(int negativeValue) {
        if (negativeValue < 0 || negativeValue > 255) {
            throw new IllegalArgumentException("negative value out of range: " + negativeValue);
        }
        return PALETTE[negativeValue];
    }

    private static int[] palette() {
        double[] paper = linear(PAPER);
        double[] ink = linear(INK);
        int[] table = new int[256];
        for (int value = 0; value < 256; value++) {
            double u = value / 255.0;
            table[value] = (channel(paper[0], ink[0], u) << 16)
                    | (channel(paper[1], ink[1], u) << 8)
                    | channel(paper[2], ink[2], u);
        }
        return table;
    }

    private static double[] linear(int rgb) {
        return new double[] {
                Srgb.decode(((rgb >> 16) & 0xFF) / 255.0),
                Srgb.decode(((rgb >> 8) & 0xFF) / 255.0),
                Srgb.decode((rgb & 0xFF) / 255.0)
        };
    }

    /** Geometric blend: density adds, so reflectances multiply. */
    private static int channel(double paper, double ink, double u) {
        double mixed = Math.exp((1.0 - u) * Math.log(paper) + u * Math.log(ink));
        return (int) Math.round(255.0 * Srgb.encode(Math.min(1.0, Math.max(0.0, mixed))));
    }

    private static int shade(int rgb, double factor) {
        int r = clamp(((rgb >> 16) & 0xFF) * factor);
        int g = clamp(((rgb >> 8) & 0xFF) * factor);
        int b = clamp((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(double channel) {
        return (int) Math.min(255.0, Math.max(0.0, Math.round(channel)));
    }

    /** Deterministic white noise in [-GRAIN, GRAIN]. */
    private static double grain(int x, int y) {
        int hash = (x * 0x27D4EB2D) ^ (y * 0x165667B1);
        hash ^= hash >>> 15;
        hash *= 0x85EBCA6B;
        hash ^= hash >>> 13;
        double unit = (hash & 0xFFFF) / 65535.0;
        return (unit - 0.5) * 2.0 * GRAIN;
    }
}
