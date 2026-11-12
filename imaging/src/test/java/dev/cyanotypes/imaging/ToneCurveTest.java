package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToneCurveTest {

    @Test
    void identityIsTheIdentity() {
        ToneCurve curve = ToneCurve.identity();
        for (int i = 0; i <= 100; i++) {
            double x = i / 100.0;
            assertEquals(x, curve.apply(x), 1e-9);
        }
    }

    @Test
    void passesThroughItsControlPoints() {
        ToneCurve curve = ToneCurve.through(List.of(
                new ToneCurve.Point(0.0, 0.10),
                new ToneCurve.Point(0.5, 0.50),
                new ToneCurve.Point(1.0, 0.90)));
        assertEquals(0.10, curve.apply(0.0), 1e-9);
        assertEquals(0.50, curve.apply(0.5), 1e-9);
        assertEquals(0.90, curve.apply(1.0), 1e-9);
    }

    @Test
    void clampsOutsideTheControlRange() {
        ToneCurve curve = ToneCurve.through(List.of(
                new ToneCurve.Point(0.2, 0.3),
                new ToneCurve.Point(0.8, 0.7)));
        assertEquals(0.3, curve.apply(0.0), 1e-9);
        assertEquals(0.7, curve.apply(1.0), 1e-9);
    }

    /** A natural cubic would overshoot here; Fritsch-Carlson must not. */
    @Test
    void neverOvershootsOnASteepStep() {
        ToneCurve curve = ToneCurve.through(List.of(
                new ToneCurve.Point(0.0, 0.0),
                new ToneCurve.Point(0.1, 0.9),
                new ToneCurve.Point(0.9, 0.95),
                new ToneCurve.Point(1.0, 1.0)));

        double previous = -1;
        for (double[] sample : curve.sample(1000)) {
            double y = sample[1];
            assertTrue(y >= -1e-9 && y <= 1 + 1e-9, "outside the unit square: " + y);
            assertTrue(y >= previous - 1e-9, "not monotone at x=" + sample[0]);
            previous = y;
        }
    }

    @Test
    void rejectsUnorderedControlPoints() {
        assertThrows(IllegalArgumentException.class, () -> ToneCurve.through(List.of(
                new ToneCurve.Point(0.5, 0.1),
                new ToneCurve.Point(0.5, 0.9))));
    }

    @Test
    void rejectsASinglePoint() {
        assertThrows(IllegalArgumentException.class,
                () -> ToneCurve.through(List.of(new ToneCurve.Point(0.0, 0.0))));
    }

    @Test
    void rejectsPointsOutsideTheUnitSquare() {
        assertThrows(IllegalArgumentException.class, () -> new ToneCurve.Point(1.2, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new ToneCurve.Point(0.5, -0.1));
    }
}
