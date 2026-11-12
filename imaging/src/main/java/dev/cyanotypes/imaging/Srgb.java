package dev.cyanotypes.imaging;

/**
 * sRGB transfer function and luminance.
 *
 * <p>Kept explicit because this is where image pipelines usually go wrong: averaging
 * gamma-encoded bytes is not the same as averaging light. Decode to linear, mix,
 * encode back.
 */
public final class Srgb {

    private Srgb() {
    }

    /** sRGB-encoded value in [0,1] to linear light in [0,1]. */
    public static double decode(double encoded) {
        if (encoded <= 0.04045) {
            return encoded / 12.92;
        }
        return Math.pow((encoded + 0.055) / 1.055, 2.4);
    }

    /** Linear light in [0,1] to sRGB-encoded value in [0,1]. */
    public static double encode(double linear) {
        if (linear <= 0.0031308) {
            return linear * 12.92;
        }
        return 1.055 * Math.pow(linear, 1.0 / 2.4) - 0.055;
    }

    /** Rec. 709 luminance of linear RGB. */
    public static double luminance(double redLinear, double greenLinear, double blueLinear) {
        return 0.2126 * redLinear + 0.7152 * greenLinear + 0.0722 * blueLinear;
    }
}
