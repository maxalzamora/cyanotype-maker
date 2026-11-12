package dev.cyanotypes.imaging;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Writes an 8-bit PNG of whatever image it is given: greyscale for the negative, RGB for
 * the cyanotype preview. Greyscale stays greyscale so downstream tools can see the single
 * channel and so the same code path extends to the 16-bit TIFF in stage 3.
 */
public final class PngEncoder {

    public byte[] encode(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("no PNG writer available for " + image.getType());
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PNG encoding failed", e);
        }
    }
}
