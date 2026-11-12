package dev.cyanotypes.imaging;

/**
 * A 16-bit greyscale raster held as raw samples rather than a {@link java.awt.image.BufferedImage}.
 *
 * <p>Keeping the samples raw avoids {@code TYPE_USHORT_GRAY}'s linear colour model, which
 * would quietly reinterpret the values every time they crossed an AWT boundary.
 */
public record Gray16(int width, int height, short[] samples) {

    public Gray16 {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        if (samples == null || samples.length != width * height) {
            throw new IllegalArgumentException(
                    "expected " + (width * height) + " samples, got "
                            + (samples == null ? "none" : samples.length));
        }
    }

    /** Unsigned sample value at a coordinate. */
    public int sample(int x, int y) {
        return samples[y * width + x] & 0xFFFF;
    }

    /** Convert to 8-bit greyscale for formats that do not carry 16 bits (PDF). */
    public java.awt.image.BufferedImage toByteGray() {
        var image = new java.awt.image.BufferedImage(width, height,
                java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
        var raster = image.getRaster();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // setSample, not setRGB: TYPE_BYTE_GRAY must not re-apply the transfer curve.
                raster.setSample(x, y, 0, sample(x, y) >>> 8);
            }
        }
        return image;
    }
}
