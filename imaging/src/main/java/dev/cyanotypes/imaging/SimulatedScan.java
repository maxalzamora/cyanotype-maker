package dev.cyanotypes.imaging;

import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.function.IntToDoubleFunction;

/**
 * A forward model of the print, used to check the analyser and to demonstrate the loop.
 *
 * <p>A real calibration needs a printer, a darkroom and a scanner. This stands in for all
 * three: it maps every byte the negative could carry to a print tone, then renders the
 * target through that map — sRGB encoded, slightly vignetted, slightly noisy — to produce
 * an image of the kind the analyser has to read. Tests use it to prove the measurement
 * recovers the model it was generated from; the UI offers it so the whole flow can be
 * tried without exposing a print.
 *
 * <p>It is not used when rendering a negative.
 */
public final class SimulatedScan {

    private SimulatedScan() {
    }

    /**
     * @param densityForValue normalised print density, 0..1, produced by a file value
     * @param width           photograph width in pixels
     * @param height          photograph height in pixels
     */
    public static BufferedImage of(IntToDoubleFunction densityForValue, int width, int height) {
        return of(densityForValue, width, height, true, 0.01);
    }

    public static BufferedImage of(IntToDoubleFunction densityForValue, int width, int height,
                            boolean vignette, double noiseSigma) {
        BufferedImage scan = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(20261112L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int targetX = Math.min(CalibrationTarget.WIDTH - 1,
                        x * CalibrationTarget.WIDTH / width);
                int targetY = Math.min(CalibrationTarget.HEIGHT - 1,
                        y * CalibrationTarget.HEIGHT / height);
                int value = CalibrationTarget.patchValue(
                        CalibrationTarget.patchAt(targetX, CalibrationTarget.WIDTH));

                double reflectance = Math.pow(10, -densityForValue.applyAsDouble(value));
                if (vignette) {
                    double dx = x / (double) width - 0.5;
                    double dy = y / (double) height - 0.5;
                    // A few percent of falloff, as a phone lens gives. Strong enough to
                    // matter, weak enough that normalisation still leaves the shape legible.
                    reflectance *= 1.0 - 0.03 * (dx * dx + dy * dy) * 4.0;
                }
                double encoded = Srgb.encode(Math.max(0, Math.min(1, reflectance)))
                        + random.nextGaussian() * noiseSigma;
                int level = (int) Math.round(Math.max(0, Math.min(1, encoded)) * 255.0);
                scan.setRGB(x, y, (level << 16) | (level << 8) | level);
            }
        }
        return scan;
    }

    /** A print with contrast through the midtones and compression at both ends. */
    public static double sCurve(double fileValue) {
        double t = fileValue / 255.0;
        return t * t * (3 - 2 * t);
    }

    /** A print whose density is simply proportional to the byte. */
    public static double linear(double fileValue) {
        return fileValue / 255.0;
    }
}
