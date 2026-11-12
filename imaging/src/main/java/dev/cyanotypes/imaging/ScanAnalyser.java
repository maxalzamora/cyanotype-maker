package dev.cyanotypes.imaging;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Reads a photograph of a printed {@link CalibrationTarget} back into densities.
 *
 * <p>The print is measured through the same relative optics a scanner or phone gives:
 * sRGB bytes are decoded to linear light, the median over the middle of each bar is taken
 * (medians, not means, so a dust speck or a specular highlight does not move a patch),
 * and the result is converted to density and normalised against the print's own lightest
 * and darkest patches.
 *
 * <p>Everything here is a pure function of the pixels, so a synthetic scan stands in for a
 * real one in tests.
 */
public final class ScanAnalyser {

    /**
     * Below this, the print is too flat to say anything about tone: reflectance ratios
     * under 2:1 come from an underexposed print, a washed-out scan, or the wrong image.
     */
    public static final double MIN_DENSITY_RANGE = 0.30;

    /** A bar cannot be lighter than its left neighbour by more than this and still be a wedge. */
    public static final double STEP_BACK_TOLERANCE = 0.05;

    /** Reflectance floor, matching {@link NegativeLut}. */
    private static final double MIN_REFLECTANCE = 1e-4;

    /** Pixels sampled per patch. Beyond this, precision does not improve. */
    private static final int SAMPLES_PER_PATCH = 20_000;

    /** Resolution of the cheap orientation probe. */
    private static final int ORIENTATION_GRID = 96;

    public ScanMeasurement measure(BufferedImage scan) {
        Objects.requireNonNull(scan, "scan");
        int width = scan.getWidth();
        int height = scan.getHeight();
        if (width < CalibrationTarget.PATCHES * 2 || height < 8) {
            throw new CalibrationRejectedException(CalibrationRejectedException.Reason.TOO_SMALL,
                    "the scan is " + width + "x" + height
                            + "; the target needs at least " + CalibrationTarget.PATCHES * 2
                            + " pixels across and 8 down");
        }
        rejectRotated(scan);

        double[] reflectance = new double[CalibrationTarget.PATCHES];
        for (int index = 0; index < CalibrationTarget.PATCHES; index++) {
            Rectangle window = CalibrationTarget.sampleWindow(index, width, height);
            reflectance[index] = medianLinearLuminance(scan, window);
        }

        double[] density = new double[reflectance.length];
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < reflectance.length; i++) {
            density[i] = -Math.log10(Math.max(reflectance[i], MIN_REFLECTANCE));
            min = Math.min(min, density[i]);
            max = Math.max(max, density[i]);
        }
        double range = max - min;
        if (range < MIN_DENSITY_RANGE) {
            throw new CalibrationRejectedException(CalibrationRejectedException.Reason.NO_CONTRAST,
                    "the wedges differ by only " + round(range)
                            + " in density; expected at least " + MIN_DENSITY_RANGE
                            + ". Photograph the print square-on in even light, filling the frame");
        }

        double[] normalised = new double[density.length];
        for (int i = 0; i < density.length; i++) {
            normalised[i] = (density[i] - min) / range;
        }

        double maxStepBack = 0;
        for (int i = 1; i < normalised.length; i++) {
            maxStepBack = Math.max(maxStepBack, normalised[i - 1] - normalised[i]);
        }
        if (maxStepBack > STEP_BACK_TOLERANCE) {
            throw new CalibrationRejectedException(CalibrationRejectedException.Reason.NOT_MONOTONE,
                    isMirrored(normalised)
                            ? "the wedge reads right to left: the negative was probably printed or"
                                    + " scanned mirrored. Flip it and photograph it again"
                            : "patch densities go backwards by " + round(maxStepBack)
                                    + " near patch " + worstStep(normalised)
                                    + "; the crop probably includes the paper margin or misses a bar");
        }
        // Real measurements wobble by a percent or two. Take the running maximum so the
        // fitted curve stays monotone without throwing away an otherwise good scan.
        for (int i = 1; i < normalised.length; i++) {
            normalised[i] = Math.max(normalised[i], normalised[i - 1]);
        }

        List<ScanMeasurement.Patch> patches = new ArrayList<>(normalised.length);
        for (int i = 0; i < normalised.length; i++) {
            patches.add(new ScanMeasurement.Patch(i, CalibrationTarget.patchValue(i),
                    reflectance[i], density[i], normalised[i]));
        }
        return new ScanMeasurement(patches, range, maxStepBack);
    }

    /**
     * Rejects the common mistake of photographing the strip sideways.
     *
     * <p>A correct crop varies left to right and is nearly constant top to bottom. Only
     * rejects when the vertical variation is clearly the stronger of the two, so a mildly
     * uneven photograph is never refused on suspicion.
     */
    private static void rejectRotated(BufferedImage scan) {
        int width = scan.getWidth();
        int height = scan.getHeight();
        int xStep = Math.max(1, width / ORIENTATION_GRID);
        int yStep = Math.max(1, height / ORIENTATION_GRID);

        double[] sum = new double[4];
        int[] count = new int[4];
        for (int y = 0; y < height; y += yStep) {
            for (int x = 0; x < width; x += xStep) {
                double luminance = Srgb.decode(scan.getRGB(x, y) & 0xFF);
                if (x < width / 3) {
                    sum[0] += luminance;
                    count[0]++;
                } else if (x >= 2 * width / 3) {
                    sum[1] += luminance;
                    count[1]++;
                }
                if (y < height / 3) {
                    sum[2] += luminance;
                    count[2]++;
                } else if (y >= 2 * height / 3) {
                    sum[3] += luminance;
                    count[3]++;
                }
            }
        }
        double horizontal = Math.abs(mean(sum[0], count[0]) - mean(sum[1], count[1]));
        double vertical = Math.abs(mean(sum[2], count[2]) - mean(sum[3], count[3]));
        if (vertical > 2 * horizontal + 0.05) {
            throw new CalibrationRejectedException(
                    CalibrationRejectedException.Reason.WRONG_ORIENTATION,
                    "the bars run vertically in this image; rotate the photograph so the"
                            + " wedge reads left to right and try again");
        }
    }

    private static double mean(double sum, int count) {
        return count == 0 ? 0 : sum / count;
    }

    /** Median linear luminance of the central pixels of one bar. */
    private static double medianLinearLuminance(BufferedImage scan, Rectangle window) {
        long area = (long) window.width * window.height;
        int stride = (int) Math.max(1, Math.round(Math.sqrt(area / (double) SAMPLES_PER_PATCH)));
        int samples = (window.width / stride + 1) * (window.height / stride + 1);
        double[] values = new double[samples];
        int n = 0;
        for (int y = window.y; y < window.y + window.height; y += stride) {
            for (int x = window.x; x < window.x + window.width; x += stride) {
                int rgb = scan.getRGB(x, y);
                double red = Srgb.decode(((rgb >> 16) & 0xFF) / 255.0);
                double green = Srgb.decode(((rgb >> 8) & 0xFF) / 255.0);
                double blue = Srgb.decode((rgb & 0xFF) / 255.0);
                values[n++] = Srgb.luminance(red, green, blue);
            }
        }
        Arrays.sort(values, 0, n);
        return n % 2 == 1
                ? values[n / 2]
                : (values[n / 2 - 1] + values[n / 2]) / 2.0;
    }

    private static int worstStep(double[] normalised) {
        double worst = 0;
        int at = 0;
        for (int i = 1; i < normalised.length; i++) {
            double stepBack = normalised[i - 1] - normalised[i];
            if (stepBack > worst) {
                worst = stepBack;
                at = i;
            }
        }
        return at;
    }

    /** True when reading the bars the other way round would put them back in order. */
    private static boolean isMirrored(double[] normalised) {
        for (int i = 1; i < normalised.length; i++) {
            if (normalised[i] > normalised[i - 1] + STEP_BACK_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
