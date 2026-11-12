package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

/**
 * Test images, including deliberately hostile ones.
 */
final class ImageFixtures {

    private ImageFixtures() {
    }

    static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = (x * 17 + y * 31) & 0xFF;
                image.setRGB(x, y, (value << 16) | (value << 8) | value);
            }
        }
        return encode(image, "png");
    }

    static byte[] jpeg(int width, int height) {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpeg");
    }

    /**
     * A real 1x1 PNG whose IHDR has been rewritten to claim arbitrary dimensions.
     * The file is a few dozen bytes, so decoding it as {@code width x height} is
     * impossible — which is exactly the point: rejection must happen on the header.
     */
    static byte[] pngClaimingDimensions(int width, int height) {
        byte[] base = png(1, 1);
        byte[] forged = base.clone();
        writeInt(forged, 16, width);   // IHDR width
        writeInt(forged, 20, height);  // IHDR height
        CRC32 crc = new CRC32();
        crc.update(forged, 12, 17);    // "IHDR" + 13 data bytes
        writeInt(forged, 29, (int) crc.getValue());
        return forged;
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("no writer for " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
