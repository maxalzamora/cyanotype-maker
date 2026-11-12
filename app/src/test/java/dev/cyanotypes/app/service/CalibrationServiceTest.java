package dev.cyanotypes.app.service;

import dev.cyanotypes.app.config.CyanotypesProperties;
import dev.cyanotypes.imaging.CalibrationRejectedException;
import dev.cyanotypes.imaging.CalibrationTarget;
import dev.cyanotypes.imaging.ImageInspector;
import dev.cyanotypes.imaging.NegativePipeline;
import dev.cyanotypes.imaging.NegativeRenderer;
import dev.cyanotypes.imaging.PdfExporter;
import dev.cyanotypes.imaging.PngEncoder;
import dev.cyanotypes.imaging.ProcessProfile;
import dev.cyanotypes.imaging.RenderedNegative;
import dev.cyanotypes.imaging.ScanAnalyser;
import dev.cyanotypes.imaging.SimulatedScan;
import dev.cyanotypes.imaging.StepWedge;
import dev.cyanotypes.imaging.TiffEncoder;
import dev.cyanotypes.imaging.UnknownProfileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationServiceTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-11-12T10:30:00Z"), ZoneOffset.UTC);
    private static final CyanotypesProperties PROPERTIES = new CyanotypesProperties(
            new CyanotypesProperties.Limits(25 * 1024 * 1024, 80_000_000, 20_000),
                new CyanotypesProperties.Calibration("data/profiles"));

    @TempDir
    Path profilesDirectory;

    private final ImageInspector inspector = new ImageInspector(80_000_000, 20_000);
    private final PngEncoder encoder = new PngEncoder();

    private CalibratedProfileStore store;
    private ProfileRegistry registry;
    private RenderService renderService;
    private CalibrationService service;

    @BeforeEach
    void setUp() {
        store = new CalibratedProfileStore(profilesDirectory,
                JsonMapper.builder().build(), FIXED);
        registry = new ProfileRegistry(store);
        renderService = new RenderService(
                new NegativePipeline(inspector, new NegativeRenderer(), encoder),
                registry, PROPERTIES);
        ExportService exportService =
                new ExportService(renderService, new TiffEncoder(), new PdfExporter());
        service = new CalibrationService(inspector, new ScanAnalyser(), store, registry,
                exportService, encoder, PROPERTIES);
    }

    @Test
    void theDefaultTargetIsAPrintablePdf() {
        CalibrationService.TargetFile target = service.target("pdf");

        assertEquals("application/pdf", target.contentType());
        assertEquals("calibration-target.pdf", target.fileName());
        assertEquals("%PDF", new String(target.bytes(), 0, 4));
    }

    @Test
    void thePreviewTargetIsTheRawWedgeAtItsNativeSize() throws IOException {
        CalibrationService.TargetFile target = service.target("png");

        assertEquals("image/png", target.contentType());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(target.bytes()));
        assertEquals(CalibrationTarget.WIDTH, image.getWidth());
        assertEquals(CalibrationTarget.HEIGHT, image.getHeight());

        var raster = image.getRaster();
        int middle = CalibrationTarget.PATCHES / 2;
        var window = CalibrationTarget.sampleWindow(middle, image.getWidth(), image.getHeight());
        assertEquals(CalibrationTarget.patchValue(middle),
                raster.getSample(window.x + window.width / 2, window.y + window.height / 2, 0));
    }

    @Test
    void aSimulatedScanProducesAUsableProfile() throws IOException {
        CalibrationService.Report report = service.calibrate(
                upload(SimulatedScan.of(SimulatedScan::sCurve, 900, 300)), "Studio print");

        assertEquals("Studio print", report.name());
        assertEquals(CalibrationTarget.PATCHES, report.patches().size());
        assertEquals(256, report.lut().size());
        assertTrue(report.notes().size() >= 2, "the report should explain itself");

        ProcessProfile profile = registry.byId(report.profileId());
        assertEquals(ProcessProfile.Mapping.CALIBRATED, profile.mapping());
        assertEquals(1, store.profiles().size());

        for (int i = 1; i < report.lut().size(); i++) {
            assertTrue(report.lut().get(i) <= report.lut().get(i - 1),
                    "the LUT must be monotone at " + i);
        }
        assertTrue(report.lut().getFirst() >= 250,
                "a black source should ask for near-clear film, got " + report.lut().getFirst());
    }

    @Test
    void oddMeasurementsAreSmoothedRatherThanRejected() throws IOException {
        // A print whose density plateaus at both ends: duplicate densities are normal.
        CalibrationService.Report report = service.calibrate(
                upload(SimulatedScan.of(value -> 0.2 + 0.6 * Math.min(1.0, value / 200.0), 900, 300)),
                "Plated print");

        assertTrue(report.measuredDensityRange() > 0.4);
        assertEquals(CalibrationTarget.PATCHES, report.patches().size());
    }

    @Test
    void theNewProfileCanImmediatelyRenderANegative() throws IOException {
        CalibrationService.Report report = service.calibrate(
                upload(SimulatedScan.of(SimulatedScan::sCurve, 900, 300)), null);

        RenderedNegative rendered = renderService.render(
                upload(StepWedge.source(21)), report.profileId());

        assertEquals(report.profileId(), rendered.profile().id());
        var raster = rendered.image().getRaster();
        assertEquals(255, raster.getSample(4, 16, 0),
                "black in the source must come out clear on the film");
        assertEquals(0, raster.getSample(20 * 8 + 4, 16, 0),
                "white in the source must come out as solid ink");
    }

    @Test
    void aFlatPhotographIsRejectedAndNothingIsStored() throws IOException {
        CalibrationRejectedException e = assertThrows(CalibrationRejectedException.class,
                () -> service.calibrate(upload(SimulatedScan.of(value -> 0.5, 420, 140)), "Flat"));

        assertEquals(CalibrationRejectedException.Reason.NO_CONTRAST, e.reason());
        assertTrue(store.profiles().isEmpty());
    }

    @Test
    void theRawTargetIsNotMistakenForAPhotographOfThePrint() throws IOException {
        // The target is the negative, so its bars run the opposite way to a print's.
        CalibrationRejectedException e = assertThrows(CalibrationRejectedException.class,
                () -> service.calibrate(upload(CalibrationTarget.render()), "Not a print"));

        assertEquals(CalibrationRejectedException.Reason.NOT_MONOTONE, e.reason());
        assertTrue(store.profiles().isEmpty());
    }

    @Test
    void aCalibrationCanBeDeletedButABuiltInCannot() {
        CalibrationService.Report report = service.calibrate(
                upload(SimulatedScan.of(SimulatedScan::sCurve, 900, 300)), "Disposable");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> service.delete("cyanotype"));
        assertTrue(e.getMessage().contains("built in"), e.getMessage());

        service.delete(report.profileId());

        assertTrue(store.profiles().isEmpty());
        assertThrows(UnknownProfileException.class, () -> registry.byId(report.profileId()));
    }

    @Test
    void aProfileSavedUnderOneNameIsFoundAgainAfterRestart() {
        CalibrationService.Report report = service.calibrate(
                upload(SimulatedScan.of(SimulatedScan::sCurve, 900, 300)), "Persistent");

        ProfileRegistry afterRestart = new ProfileRegistry(new CalibratedProfileStore(
                profilesDirectory, JsonMapper.builder().build(), FIXED));

        assertEquals(report.profileId(), afterRestart.byId(report.profileId()).id());
    }

    private MockMultipartFile upload(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return new MockMultipartFile("file", "scan.png", "image/png", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
