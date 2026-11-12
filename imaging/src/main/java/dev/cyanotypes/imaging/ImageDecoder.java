package dev.cyanotypes.imaging;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Decodes bytes that have already passed {@link ImageInspector}.
 *
 * <p>Kept separate from the pipeline because calibration reads a photograph that will
 * <em>not</em> be inverted: it is measured, not transformed. Both paths must agree on how
 * bytes become pixels, so there is exactly one decoder.
 */
public final class ImageDecoder {

    private ImageDecoder() {
    }

    public static BufferedImage decode(byte[] data, ImageFormat format) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (stream == null) {
                throw corrupt("could not open image stream");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new ImageRejectedException(
                        ImageRejectedException.Reason.UNSUPPORTED_FORMAT,
                        "no decoder available for " + format);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (ImageRejectedException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw corrupt("could not decode image: " + e.getMessage());
        }
    }

    private static ImageRejectedException corrupt(String message) {
        return new ImageRejectedException(ImageRejectedException.Reason.CORRUPT, message);
    }
}
