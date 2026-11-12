package dev.cyanotypes.imaging;

/**
 * The whole 8-bit transform as a lookup table: source grey value in, value written to the
 * negative out.
 *
 * <p>Building it once per render keeps the per-pixel work to an array index, and makes the
 * transform itself inspectable in tests instead of hidden inside a loop.
 */
public final class NegativeLut {

    private static final double MIN_REFLECTANCE = 1e-4;

    private final ProcessProfile profile;
    private final int[] table;

    private NegativeLut(ProcessProfile profile, int[] table) {
        this.profile = profile;
        this.table = table;
    }

    public static NegativeLut of(ProcessProfile profile) {
        int[] table = new int[256];
        for (int value = 0; value < 256; value++) {
            double lightness = value / 255.0;
            double raw = switch (profile.mapping()) {
                case LINEAR -> 1.0 - lightness;
                case DENSITY -> densityFraction(lightness, profile.printDmin(), profile.printDmax());
            };
            double shaped = clamp01(profile.curve().apply(clamp01(raw)));
            int span = profile.whiteLevel() - profile.blackLevel();
            int out = (int) Math.round(profile.blackLevel() + span * shaped);
            table[value] = Math.max(0, Math.min(255, out));
        }
        return new NegativeLut(profile, table);
    }

    /**
     * Where on the print's density range this source value belongs.
     *
     * <p>Optical density is {@code -log10(reflectance)}, so equal steps of source
     * lightness are <em>not</em> equal steps of density. Treating them as equal is what
     * makes a naive inversion block up the shadows.
     */
    private static double densityFraction(double lightness, double dmin, double dmax) {
        double luminance = Srgb.decode(lightness);
        double density = -Math.log10(Math.max(luminance, MIN_REFLECTANCE));
        return (density - dmin) / (dmax - dmin);
    }

    private static double clamp01(double value) {
        return value < 0 ? 0 : Math.min(value, 1);
    }

    public int map(int sourceValue) {
        return table[Math.max(0, Math.min(255, sourceValue))];
    }

    /** Defensive copy; the table is the interesting artefact in diagnostics. */
    public int[] table() {
        return table.clone();
    }

    public ProcessProfile profile() {
        return profile;
    }
}
