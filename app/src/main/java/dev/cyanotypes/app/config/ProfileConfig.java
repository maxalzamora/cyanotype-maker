package dev.cyanotypes.app.config;

import tools.jackson.databind.ObjectMapper;
import dev.cyanotypes.app.service.CalibratedProfileStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Where calibrations are kept. A directory of JSON files, not a database: this project is
 * about the transform, and a profile is small enough to read in an editor.
 */
@Configuration
public class ProfileConfig {

    @Bean
    CalibratedProfileStore calibratedProfileStore(CyanotypesProperties properties,
                                                  ObjectMapper mapper) {
        return new CalibratedProfileStore(
                Path.of(properties.calibration().directory()), mapper);
    }
}
