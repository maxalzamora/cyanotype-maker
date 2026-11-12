package dev.cyanotypes.imaging;

import java.awt.image.BufferedImage;

/**
 * Result of one render. Keeps the probe and the profile around so the transport layer
 * can report what actually happened, not what the client asked for.
 */
public record RenderedNegative(
        ImageProbe source,
        ProcessProfile profile,
        BufferedImage image,
        byte[] encoded) {
}
