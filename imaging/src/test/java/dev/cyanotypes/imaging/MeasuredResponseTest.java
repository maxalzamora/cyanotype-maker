package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasuredResponseTest {

    @Test
    void invertsTheCurveItWasGiven() {
        List<MeasuredResponse.Point> points = List.of(
                new MeasuredResponse.Point(0.00, 12),
                new MeasuredResponse.Point(0.25, 70),
                new MeasuredResponse.Point(0.50, 128),
                new MeasuredResponse.Point(0.75, 190),
                new MeasuredResponse.Point(1.00, 250));

        MeasuredResponse response = MeasuredResponse.of(points);

        for (MeasuredResponse.Point point : points) {
            assertEquals(point.fileValue(), response.valueFor(point.normalisedDensity()), 0.5);
        }
    }

    @Test
    void aSaturatedEndKeepsTheFirstByteThatReachesIt() {
        MeasuredResponse response = MeasuredResponse.of(List.of(
                new MeasuredResponse.Point(0.0, 0),
                new MeasuredResponse.Point(0.0, 3),
                new MeasuredResponse.Point(0.5, 120),
                new MeasuredResponse.Point(1.0, 252),
                new MeasuredResponse.Point(1.0, 255)));

        assertEquals(3, response.size(), "duplicate densities collapse to one point each");
        assertEquals(0.0, response.valueFor(0.0), 1e-9,
                "the lightest tone must not demand more ink than the first byte that reaches it");
        assertEquals(252.0, response.valueFor(1.0), 1e-9);
    }

    @Test
    void holdsTheEndsOutsideTheMeasuredRange() {
        MeasuredResponse response = MeasuredResponse.of(List.of(
                new MeasuredResponse.Point(0.1, 30),
                new MeasuredResponse.Point(0.9, 220)));

        assertEquals(30.0, response.valueFor(0.0), 1e-9);
        assertEquals(220.0, response.valueFor(1.0), 1e-9);
    }

    @Test
    void aMonotoneCurveNeverGoesBackwards() {
        MeasuredResponse response = MeasuredResponse.of(List.of(
                new MeasuredResponse.Point(0.0, 5),
                new MeasuredResponse.Point(0.2, 40),
                new MeasuredResponse.Point(0.55, 120),
                new MeasuredResponse.Point(0.8, 200),
                new MeasuredResponse.Point(1.0, 255)));

        double previous = -1;
        for (int i = 0; i <= 1000; i++) {
            double value = response.valueFor(i / 1000.0);
            assertTrue(value >= previous - 1e-9, "density fell at " + i / 1000.0);
            previous = value;
        }
    }

    @Test
    void refusesAMeasurementWithNoOrder() {
        assertThrows(IllegalArgumentException.class, () -> MeasuredResponse.of(List.of(
                new MeasuredResponse.Point(0.2, 200),
                new MeasuredResponse.Point(0.8, 40))));
    }

    @Test
    void refusesSomethingThatIsNotACurve() {
        assertThrows(IllegalArgumentException.class, () -> MeasuredResponse.of(List.of()));
        assertThrows(IllegalArgumentException.class, () -> MeasuredResponse.of(List.of(
                new MeasuredResponse.Point(0.5, 100))));
        assertThrows(IllegalArgumentException.class, () -> MeasuredResponse.of(List.of(
                new MeasuredResponse.Point(0.5, 100),
                new MeasuredResponse.Point(0.5, 200))));
    }

    @Test
    void refusesPointsOutsideTheDefinition() {
        assertThrows(IllegalArgumentException.class,
                () -> new MeasuredResponse.Point(1.2, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new MeasuredResponse.Point(0.5, 300));
    }
}
