package dev.cyanotypes.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource limits for uploads, and where calibrated profiles live. Limits are enforced
 * twice on purpose: here (after reading the body) and in
 * {@link dev.cyanotypes.imaging.ImageInspector} (before decoding pixels).
 */
@ConfigurationProperties(prefix = "cyanotypes")
public record CyanotypesProperties(Limits limits, Calibration calibration) {

    public record Limits(int maxUploadBytes, int maxPixels, int maxDimension) {
    }

    public record Calibration(String directory) {
        public Calibration {
            if (directory == null || directory.isBlank()) {
                directory = "data/profiles";
            }
        }
    }
}
