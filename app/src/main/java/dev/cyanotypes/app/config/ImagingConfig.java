package dev.cyanotypes.app.config;

import dev.cyanotypes.imaging.ImageInspector;
import dev.cyanotypes.imaging.NegativePipeline;
import dev.cyanotypes.imaging.NegativeRenderer;
import dev.cyanotypes.imaging.PngEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the imaging module. The module itself knows nothing about Spring; this is
 * the only place the two meet.
 */
@Configuration
public class ImagingConfig {

    @Bean
    ImageInspector imageInspector(CyanotypesProperties properties) {
        CyanotypesProperties.Limits limits = properties.limits();
        return new ImageInspector(limits.maxPixels(), limits.maxDimension());
    }

    @Bean
    NegativeRenderer negativeRenderer() {
        return new NegativeRenderer();
    }

    @Bean
    PngEncoder pngEncoder() {
        return new PngEncoder();
    }

    @Bean
    NegativePipeline negativePipeline(ImageInspector inspector, NegativeRenderer renderer,
                                      PngEncoder encoder) {
        return new NegativePipeline(inspector, renderer, encoder);
    }
}
