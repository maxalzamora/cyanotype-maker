package dev.cyanotypes.imaging;

import java.awt.image.BufferedImage;

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
        BufferedImage decoded = ImageDecoder.decode(input, probe.format());
        BufferedImage negative = renderer.render(decoded, profile);
        return new RenderedNegative(probe, profile, negative, encoder.encode(negative));
    }
}
