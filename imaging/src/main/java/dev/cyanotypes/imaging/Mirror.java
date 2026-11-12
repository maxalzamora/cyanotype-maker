package dev.cyanotypes.imaging;

import java.awt.image.BufferedImage;

/**
 * Flips horizontally. Only needed for one-sided transparency film, where the ink side must
 * face the paper and you therefore view the image through its base.
 */
public final class Mirror {

    private Mirror() {
    }

    public static BufferedImage horizontal(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage flipped = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        var from = source.getRaster();
        var to = flipped.getRaster();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // setSample keeps the bytes untouched; setRGB would reinterpret them.
                to.setSample(width - 1 - x, y, 0, from.getSample(x, y, 0));
            }
        }
        return flipped;
    }
}
