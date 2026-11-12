package dev.cyanotypes.imaging;

/**
 * What we know about an image after reading its header but before decoding any pixels.
 */
public record ImageProbe(ImageFormat format, int width, int height) {

    public long pixels() {
        return (long) width * height;
    }
}
