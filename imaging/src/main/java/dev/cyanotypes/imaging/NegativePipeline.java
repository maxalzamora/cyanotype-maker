package dev.cyanotypes.imaging;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Orchestrates one render: inspect, decode, invert, encode.
 *
 * <p>Every stage is a pure function, so this class has no framework dependencies and no
 * I/O beyond the byte array it is handed.
 */
public final class NegativePipeline {

    private final ImageInspector inspector;
    private final NegativeRenderer renderer;
    private final PngEncoder encoder;

    public NegativePipeline(ImageInspector inspector, NegativeRenderer renderer, PngEncoder encoder) {
        this.inspector = inspector;
        this.renderer = renderer;
        this.encoder = encoder;
    }

    public RenderedNegative render(byte[] input, ProcessProfile profile) {
        ImageProbe probe = inspector.inspect(input);
        BufferedImage decoded = decode(input, probe);
        BufferedImage negative = renderer.render(decoded, profile);
        return new RenderedNegative(probe, profile, negative, encoder.encode(negative));
    }

    private static BufferedImage decode(byte[] data, ImageProbe probe) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (stream == null) {
                throw corrupt("could not open image stream");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new ImageRejectedException(
                        ImageRejectedException.Reason.UNSUPPORTED_FORMAT,
                        "no decoder available for " + probe.format());
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
