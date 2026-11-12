package dev.cyanotypes.imaging;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Turns a positive photograph into the ink pattern you print on transparency.
 *
 * <p>The transform is chosen by a {@link ProcessProfile}, precompiled into a
 * {@link NegativeLut}. Per pixel we only need perceptual lightness, then one lookup.
 *
 * <p>In the output file a <em>high</em> value means clear film: UV passes, the paper
 * turns blue, the print is dark. That is why the result looks like a photographic
 * negative.
 */
public final class NegativeRenderer {

    public BufferedImage render(BufferedImage source, ProcessProfile profile) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(profile, "profile");

        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage flat = flattenToRgb(source);
        NegativeLut lut = NegativeLut.of(profile);

        BufferedImage negative = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        var raster = negative.getRaster();
        int[] row = new int[width];

        for (int y = 0; y < height; y++) {
            flat.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                int argb = row[x];
                double red = Srgb.decode(((argb >> 16) & 0xFF) / 255.0);
                double green = Srgb.decode(((argb >> 8) & 0xFF) / 255.0);
                double blue = Srgb.decode((argb & 0xFF) / 255.0);

                double lightness = Srgb.encode(Srgb.luminance(red, green, blue));
                int sourceGray = (int) Math.round(lightness * 255.0);
                sourceGray = Math.max(0, Math.min(255, sourceGray));

                // setSample, not setRGB. TYPE_BYTE_GRAY carries a *linear* colour model,
                // so setRGB would decode our value and double-apply the transfer function,
                // washing the negative out. Writing the raster sample directly stores
                // exactly the byte we want in the file.
                raster.setSample(x, y, 0, lut.map(sourceGray));
            }
        }
        return negative;
    }

    /**
     * Normalises colour model and alpha in one step: draw onto an opaque white canvas.
     * CMYK, indexed and ARGB inputs all come out as straight TYPE_INT_RGB pixels.
     */
    private static BufferedImage flattenToRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage flat = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = flat.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return flat;
    }
}
