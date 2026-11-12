package dev.cyanotypes.imaging;

import java.util.ArrayList;
import java.util.List;

/**
 * A monotone interpolation curve in the unit square, built from control points.
 *
 * <p>Uses Fritsch&ndash;Carlson tangents. A plain cubic spline can overshoot between
 * points, and an overshoot in a tone curve means a band of tones that go backwards —
 * visible as a contour in the print. This variant cannot overshoot.
 *
 * <p>Calibration will eventually produce these points from measured densities; for now
 * they express intent ("soften the render") in a form that is directly testable.
 */
public final class ToneCurve {

    /** A control point. Both coordinates are in [0,1]. */
    public record Point(double x, double y) {
        public Point {
            if (x < 0 || x > 1 || y < 0 || y > 1) {
                throw new IllegalArgumentException("point outside the unit square: " + x + "," + y);
            }
        }
    }

    private final double[] xs;
    private final double[] ys;
    private final double[] tangents;

    private ToneCurve(double[] xs, double[] ys, double[] tangents) {
        this.xs = xs;
        this.ys = ys;
        this.tangents = tangents;
    }

    public static ToneCurve identity() {
        return new ToneCurve(new double[] {0, 1}, new double[] {0, 1}, new double[] {1, 1});
    }

    public static ToneCurve through(List<Point> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("a curve needs at least two points");
        }
        int n = points.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = points.get(i).x();
            ys[i] = points.get(i).y();
            if (i > 0 && xs[i] <= xs[i - 1]) {
                throw new IllegalArgumentException("x values must increase strictly");
            }
        }

        double[] secants = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            secants[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]);
        }

        double[] tangents = new double[n];
        tangents[0] = secants[0];
        tangents[n - 1] = secants[n - 2];
        for (int i = 1; i < n - 1; i++) {
            tangents[i] = (secants[i - 1] + secants[i]) / 2.0;
        }

        // Fritsch-Carlson: clamp tangents into the monotonicity region.
        for (int i = 0; i < n - 1; i++) {
            if (secants[i] == 0.0) {
                tangents[i] = 0.0;
                tangents[i + 1] = 0.0;
                continue;
            }
            double alpha = tangents[i] / secants[i];
            double beta = tangents[i + 1] / secants[i];
            double magnitude = alpha * alpha + beta * beta;
            if (magnitude > 9.0) {
                double scale = 3.0 / Math.sqrt(magnitude);
                tangents[i] = scale * alpha * secants[i];
                tangents[i + 1] = scale * beta * secants[i];
            }
        }
        return new ToneCurve(xs, ys, tangents);
    }

    public double apply(double x) {
        if (x <= xs[0]) {
            return ys[0];
        }
        if (x >= xs[xs.length - 1]) {
            return ys[ys.length - 1];
        }
        int i = interval(x);
        double h = xs[i + 1] - xs[i];
        double t = (x - xs[i]) / h;
        double t2 = t * t;
        double t3 = t2 * t;
        double h00 = 2 * t3 - 3 * t2 + 1;
        double h10 = t3 - 2 * t2 + t;
        double h01 = -2 * t3 + 3 * t2;
        double h11 = t3 - t2;
        return h00 * ys[i] + h10 * h * tangents[i] + h01 * ys[i + 1] + h11 * h * tangents[i + 1];
    }

    private int interval(double x) {
        int low = 0;
        int high = xs.length - 1;
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (xs[middle] <= x) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /** Sample the curve, mostly for tests and diagnostics. */
    public List<double[]> sample(int samples) {
        List<double[]> out = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            double x = i / (double) (samples - 1);
            out.add(new double[] {x, apply(x)});
        }
        return out;
    }
}
