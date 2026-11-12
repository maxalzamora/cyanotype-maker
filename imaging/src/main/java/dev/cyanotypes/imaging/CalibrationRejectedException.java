package dev.cyanotypes.imaging;

/**
 * The scan cannot be used as a calibration measurement. Distinct from a malformed upload:
 * the file was a valid image, it just does not contain a readable wedge.
 *
 * <p>Reasons are a closed set so the UI can explain the fix, which is always "take the
 * photograph again, differently".
 */
public final class CalibrationRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** Too few pixels to resolve one sample per patch. */
        TOO_SMALL,
        /** The stripes run vertically; the target was photographed on its side. */
        WRONG_ORIENTATION,
        /** The darkest and lightest patches are nearly the same density. */
        NO_CONTRAST,
        /** The measured densities do not step in order, so the crop missed the bars. */
        NOT_MONOTONE
    }

    private final Reason reason;

    public CalibrationRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
