package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The claim calibration makes is narrow and testable: a source tone lands at the same
 * place in the print's density range as it would have under the assumed model, but the
 * byte used to get there now comes from a measurement instead of a straight line.
 */
class CalibratedProfileTest {

    private static final double MIN_REFLECTANCE = 1e-4;

    @Test
    void aMeasuredProfilePutsEverySourceToneWhereItBelongs() {
        ScanMeasurement measurement =
                new ScanAnalyser().measure(SimulatedScan.of(SimulatedScan::sCurve, 900, 300));
        ProcessProfile profile =
                ProcessProfile.calibrated("measured", "Measured", measurement.response());
        NegativeLut lut = NegativeLut.of(profile);

        for (int source = 0; source <= 255; source += 3) {
            double wanted = wantedDensity(source);
            double achieved = SimulatedScan.sCurve(lut.map(source));
            assertEquals(wanted, achieved, 0.02,
                    "source value " + source + " maps to " + lut.map(source));
        }
    }

    @Test
    void aMeasuredProfileOfALinearPrintReproducesTheAssumedModel() {
        ScanMeasurement measurement = new ScanAnalyser()
                .measure(SimulatedScan.of(SimulatedScan::linear, 840, 240, false, 0.0));
        NegativeLut measured = NegativeLut.of(
                ProcessProfile.calibrated("measured", "Measured", measurement.response()));
        NegativeLut assumed = NegativeLut.of(ProcessProfile.cyanotype());

        for (int source = 0; source <= 255; source++) {
            assertEquals(assumed.map(source), measured.map(source), 2,
                    "source value " + source);
        }
    }

    @Test
    void aMeasuredProfileStillNeverGoesBackwards() {
        ScanMeasurement measurement =
                new ScanAnalyser().measure(SimulatedScan.of(SimulatedScan::sCurve, 900, 300));
        int[] table = NegativeLut.of(
                ProcessProfile.calibrated("measured", "Measured", measurement.response())).table();

        for (int i = 1; i < table.length; i++) {
            assertTrue(table[i] <= table[i - 1],
                    "a lighter source must never ask for more ink at " + i);
        }
    }

    private static double wantedDensity(int sourceValue) {
        double luminance = Srgb.decode(sourceValue / 255.0);
        double density = -Math.log10(Math.max(luminance, MIN_REFLECTANCE));
        return Math.min(1.0, Math.max(0.0, density / 1.4));
    }
}
