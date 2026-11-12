package dev.cyanotypes.imaging;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the printer and the process actually do, as a monotone curve from file value to
 * print density.
 *
 * <p>Each point says: writing this byte into the negative produces a patch whose density
 * sits this far along the print's own density range. {@code 0} is paper white and
 * {@code 1} is the darkest blue the print can reach. The scale is relative on purpose —
 * a phone photograph cannot measure absolute density, but it can order tones and it can
 * find the two ends of the range, which is all a curve needs.
 *
 * <p>{@link #valueFor(double)} is the inverse, and that is what a profile uses: "for a
 * source tone that belongs at 0.4 of the print's range, which byte do I write?"
 */
public final class MeasuredResponse {

    /** Two points is the mathematical minimum; anything less is not a curve. */
    public static final int MIN_POINTS = 2;

    /** Points closer than this in density would make the interpolating spline ill-posed. */
    private static final double DENSITY_EPSILON = 1e-3;

    /**
     * @param normalisedDensity position in the print's density range, 0 (lightest) to 1
     *                          (darkest)
     * @param fileValue         the byte written into the negative that produced it
     */
    public record Point(double normalisedDensity, int fileValue) {
        public Point {
            if (normalisedDensity < 0 || normalisedDensity > 1) {
                throw new IllegalArgumentException(
                        "normalised density outside 0..1: " + normalisedDensity);
            }
            if (fileValue < 0 || fileValue > 255) {
                throw new IllegalArgumentException("file value outside 0..255: " + fileValue);
            }
        }
    }

    private final List<Point> points;
    private final ToneCurve valueForDensity;

    private MeasuredResponse(List<Point> points, ToneCurve valueForDensity) {
        this.points = points;
        this.valueForDensity = valueForDensity;
    }

    /**
     * Fits a curve through measured points.
     *
     * <p>Duplicates are dropped rather than averaged: when the process saturates, several
     * bytes produce the same density, and the useful one is the <em>first</em> byte that
     * gets there. Averaging would invent a value with a density no byte actually reached.
     */
    public static MeasuredResponse of(List<Point> raw) {
        if (raw == null || raw.size() < MIN_POINTS) {
            throw new IllegalArgumentException(
                    "a measured response needs at least " + MIN_POINTS + " points");
        }
        List<Point> sorted = new ArrayList<>(raw);
        sorted.sort(Comparator.comparingDouble(Point::normalisedDensity)
                .thenComparingInt(Point::fileValue));

        List<Point> kept = new ArrayList<>(sorted.size());
        for (Point point : sorted) {
            if (kept.isEmpty()
                    || point.normalisedDensity() - kept.getLast().normalisedDensity() > DENSITY_EPSILON) {
                kept.add(point);
            }
        }
        if (kept.size() < MIN_POINTS) {
            throw new IllegalArgumentException(
                    "the measurement only resolves " + kept.size()
                            + " distinct density level(s); the print has no usable range");
        }
        for (int i = 1; i < kept.size(); i++) {
            if (kept.get(i).fileValue() < kept.get(i - 1).fileValue()) {
                throw new IllegalArgumentException(
                        "measured densities do not increase with file value at patch "
                                + kept.get(i).fileValue());
            }
        }

        List<ToneCurve.Point> controls = new ArrayList<>(kept.size());
        for (Point point : kept) {
            controls.add(new ToneCurve.Point(point.normalisedDensity(), point.fileValue() / 255.0));
        }
        return new MeasuredResponse(List.copyOf(kept), ToneCurve.through(controls));
    }

    /**
     * The byte to write for a source tone that belongs at {@code normalisedDensity}.
     *
     * <p>Outside the measured range the curve holds its end: a print cannot go lighter
     * than its lightest measurable tone.
     */
    public double valueFor(double normalisedDensity) {
        double clamped = Math.max(0.0, Math.min(1.0, normalisedDensity));
        return 255.0 * valueForDensity.apply(clamped);
    }

    public List<Point> points() {
        return points;
    }

    public int size() {
        return points.size();
    }
}
