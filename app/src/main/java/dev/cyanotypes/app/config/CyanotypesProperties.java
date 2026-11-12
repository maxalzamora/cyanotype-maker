package dev.cyanotypes.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource limits for uploads. Enforced twice on purpose: here (after reading the body)
 * and in {@link dev.cyanotypes.imaging.ImageInspector} (before decoding pixels).
 */
@ConfigurationProperties(prefix = "cyanotypes")
public record CyanotypesProperties(Limits limits) {

    public record Limits(int maxUploadBytes, int maxPixels, int maxDimension) {
    }
}
