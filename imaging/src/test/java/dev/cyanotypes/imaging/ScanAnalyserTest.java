package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanAnalyserTest {

    private final ScanAnalyser analyser = new ScanAnalyser();

    @Test
    void recoversTheForwardModelFromAPhotographOfThePrint() {
        BufferedImage scan = SimulatedScan.of(SimulatedScan::sCurve, 900, 300);

        ScanMeasurement measurement = analyser.measure(scan);

        assertEquals(CalibrationTarget.PATCHES, measurement.patches().size());
        for (ScanMeasurement.Patch patch : measurement.patches()) {
            double expected = SimulatedScan.sCurve(patch.fileValue());
            assertEquals(expected, patch.normalisedDensity(), 0.03,
                    "patch " + patch.index() + " at file value " + patch.fileValue());
        }
        assertTrue(measurement.densityRange() > 0.9,
                "a full-range print should measure close to 1.0 of density");
        assertEquals(0.0, measurement.maxStepBack(), 1e-9);
    }

    @Test
    void recoversALinearResponseToo() {
        BufferedImage scan = SimulatedScan.of(SimulatedScan::linear, 840, 240, false, 0.0);

        ScanMeasurement measurement = analyser.measure(scan);

        for (ScanMeasurement.Patch patch : measurement.patches()) {
            assertEquals(patch.fileValue() / 255.0, patch.normalisedDensity(), 0.02,
                    "patch " + patch.index());
        }
    }

    @Test
    void theMeasurementConvertsToACurveThatInvertsThePrint() {
        ScanMeasurement measurement =
                analyser.measure(SimulatedScan.of(SimulatedScan::sCurve, 900, 300));

        MeasuredResponse response = measurement.response();

        for (ScanMeasurement.Patch patch : measurement.patches()) {
            assertEquals(patch.fileValue(), response.valueFor(patch.normalisedDensity()), 2.0,
                    "the fitted curve should send this density back to the byte that made it");
        }
    }

    @Test
    void rejectsAPrintWithNoDensityRange() {
        BufferedImage flat = SimulatedScan.of(value -> 0.6, 420, 140);

        CalibrationRejectedException e =
                assertThrows(CalibrationRejectedException.class, () -> analyser.measure(flat));
        assertEquals(CalibrationRejectedException.Reason.NO_CONTRAST, e.reason());
    }

    @Test
    void rejectsAWedgeWhoseBarsDoNotStepInOrder() {
        BufferedImage mirrored = Mirror.horizontal(
                SimulatedScan.of(SimulatedScan::sCurve, 840, 240));

        CalibrationRejectedException e =
                assertThrows(CalibrationRejectedException.class, () -> analyser.measure(mirrored));
        assertEquals(CalibrationRejectedException.Reason.NOT_MONOTONE, e.reason());
        assertTrue(e.getMessage().contains("mirrored"), e.getMessage());
    }

    @Test
    void rejectsASidewaysPhotographWithAnActionableMessage() {
        BufferedImage sideways = new BufferedImage(300, 900, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 900; y++) {
            int value = CalibrationTarget.patchValue(CalibrationTarget.patchAt(
                    y * CalibrationTarget.WIDTH / 900, CalibrationTarget.WIDTH));
            for (int x = 0; x < 300; x++) {
                sideways.setRGB(x, y, (value << 16) | (value << 8) | value);
            }
        }

        CalibrationRejectedException e =
                assertThrows(CalibrationRejectedException.class, () -> analyser.measure(sideways));
        assertEquals(CalibrationRejectedException.Reason.WRONG_ORIENTATION, e.reason());
    }

    @Test
    void rejectsAThumbnailTooSmallToResolveTheBars() {
        BufferedImage tiny = new BufferedImage(20, 4, BufferedImage.TYPE_INT_RGB);
        CalibrationRejectedException e =
                assertThrows(CalibrationRejectedException.class, () -> analyser.measure(tiny));
        assertEquals(CalibrationRejectedException.Reason.TOO_SMALL, e.reason());
    }
}
