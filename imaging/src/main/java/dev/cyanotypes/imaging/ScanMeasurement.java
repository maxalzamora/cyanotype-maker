package dev.cyanotypes.imaging;

import java.util.List;

/**
 * What a photograph of a printed target turned out to contain: one measurement per patch,
 * plus the two numbers that say whether to trust it.
 *
 * @param patches     left to right, one per bar of {@link CalibrationTarget}
 * @param densityRange measured density between the lightest and darkest patch; a print
 *                     with almost no range cannot calibrate anything
 * @param maxStepBack  largest amount by which a patch is lighter than the one before it;
 *                     should be zero for a correctly oriented crop
 */
public record ScanMeasurement(List<Patch> patches, double densityRange, double maxStepBack) {

    public ScanMeasurement {
        patches = List.copyOf(patches);
    }

    /**
     * @param reflectance        linear light from the scan, relative to the scan's white
     * @param density            {@code -log10(reflectance)}
     * @param normalisedDensity  0 at paper white, 1 at the darkest patch
     */
    public record Patch(
            int index,
            int fileValue,
            double reflectance,
            double density,
            double normalisedDensity) {
    }

    /** The measurement as a curve a profile can invert. */
    public MeasuredResponse response() {
        return MeasuredResponse.of(patches.stream()
                .map(patch -> new MeasuredResponse.Point(
                        patch.normalisedDensity(), patch.fileValue()))
                .toList());
    }
}
