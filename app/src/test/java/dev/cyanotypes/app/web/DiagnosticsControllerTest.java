package dev.cyanotypes.app.web;

import dev.cyanotypes.app.service.CalibratedProfileStore;
import dev.cyanotypes.app.service.ProfileRegistry;
import dev.cyanotypes.imaging.NegativeRenderer;
import dev.cyanotypes.imaging.PngEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsControllerTest {

    @TempDir
    Path profilesDirectory;

    private DiagnosticsController controller;

    @BeforeEach
    void setUp() {
        controller = new DiagnosticsController(new NegativeRenderer(), new PngEncoder(),
                new ProfileRegistry(new CalibratedProfileStore(
                        profilesDirectory, JsonMapper.builder().build())));
    }

    @Test
    void theRenderedWedgeMatchesTheProfileLut() {
        DiagnosticsController.WedgeReport report = controller.stepWedge("cyanotype");

        assertEquals("cyanotype", report.profile());
        assertEquals(21, report.steps());
        assertEquals(21, report.rows().size());
        assertTrue(report.allCorrect(), "step wedge mismatches: " + report.rows());
        assertTrue(report.monotonic());
        assertEquals(255, report.atBlack());
        assertEquals(0, report.atWhite());
    }

    @Test
    void theLinearProfileStillReportsAnExactInversion() {
        DiagnosticsController.WedgeReport report = controller.stepWedge("linear");

        assertEquals(127, report.rows().get(10).expectedValue());
        assertEquals(127, report.rows().get(10).actualValue());
    }

    @Test
    void theSoftProfileDoesNotReachEitherExtreme() {
        DiagnosticsController.WedgeReport report = controller.stepWedge("cyanotype-soft");

        assertTrue(report.atBlack() > 220, "expected lifted shadows, got " + report.atBlack());
        assertTrue(report.atWhite() < 35, "expected held highlights, got " + report.atWhite());
    }
}
