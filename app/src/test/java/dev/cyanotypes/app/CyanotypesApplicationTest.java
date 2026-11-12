package dev.cyanotypes.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the Spring context wires: property binding, beans, and the imaging module
 * plugged in through {@code ImagingConfig}.
 */
@SpringBootTest
class CyanotypesApplicationTest {

    @Test
    void contextLoads() {
        // no assertions needed; a failure to start fails the test
    }
}
