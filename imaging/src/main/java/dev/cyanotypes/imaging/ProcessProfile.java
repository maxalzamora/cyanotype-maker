package dev.cyanotypes.imaging;

import java.util.List;

/**
 * How to turn a photograph into a negative for one particular process.
 *
 * <p>Three mappings are implemented:
 *
 * <dl>
 *   <dt>{@link Mapping#LINEAR}</dt>
 *   <dd>Perceptual lightness inverted. Simple, exact, and what stage 1 did. It ignores
 *       that density is logarithmic, so it blocks up shadows in the print.</dd>
 *   <dt>{@link Mapping#DENSITY}</dt>
 *   <dd>Print density is matched to the source's optical density, then scaled into the
 *       range the process can actually reach ({@code printDmin}..{@code printDmax}).
 *       Deep shadows legitimately clip here: a print cannot be denser than its Dmax.</dd>
 *   <dt>{@link Mapping#CALIBRATED}</dt>
 *   <dd>{@code DENSITY} with the response measured from a real print instead of assumed.
 *       The same source tone is placed at the same position in the print's range; what
 *       changes is the byte needed to get it there, which comes from
 *       {@link MeasuredResponse} rather than from a straight line.</dd>
 * </dl>
 *
 * <p>{@code blackLevel} and {@code whiteLevel} bound the values written to the file.
 * A low {@code blackLevel} means heavy ink laydown, which can coalesce on transparency
 * film; a high {@code whiteLevel} leaves the film not quite clear.
 *
 * <p>{@code curve} shapes the normalised print density. {@link ToneCurve#identity()}
 * leaves it alone.
 */
public record ProcessProfile(
        String id,
        String displayName,
        Mapping mapping,
        double printDmin,
        double printDmax,
        int blackLevel,
        int whiteLevel,
        ToneCurve curve,
        MeasuredResponse response) {

    public enum Mapping {
        LINEAR,
        DENSITY,
        CALIBRATED
    }

    /** Profiles that came from the assumption rather than from a measurement. */
    public ProcessProfile(String id, String displayName, Mapping mapping,
                          double printDmin, double printDmax,
                          int blackLevel, int whiteLevel, ToneCurve curve) {
        this(id, displayName, mapping, printDmin, printDmax, blackLevel, whiteLevel, curve, null);
    }

    public ProcessProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (printDmin < 0) {
            throw new IllegalArgumentException("printDmin cannot be negative");
        }
        if (printDmax <= printDmin) {
            throw new IllegalArgumentException("printDmax must exceed printDmin");
        }
        if (blackLevel < 0 || whiteLevel > 255) {
            throw new IllegalArgumentException("levels must be inside 0..255");
        }
        if (blackLevel >= whiteLevel) {
            throw new IllegalArgumentException("blackLevel must be below whiteLevel");
        }
        if (curve == null) {
            throw new IllegalArgumentException("curve is required");
        }
        if (mapping == Mapping.CALIBRATED && response == null) {
            throw new IllegalArgumentException("a calibrated profile needs a measured response");
        }
    }

    private static final List<ProcessProfile> ALL = List.of(
            linear(),
            cyanotype(),
            cyanotypeSoft());

    /** Stage 1 behaviour. Useful as a reference and for exact tests. */
    public static ProcessProfile linear() {
        return new ProcessProfile("linear", "Linear inversion", Mapping.LINEAR,
                0.0, 1.0, 0, 255, ToneCurve.identity());
    }

    /** Typical cyanotype on watercolour paper: Dmax around 1.4. */
    public static ProcessProfile cyanotype() {
        return new ProcessProfile("cyanotype", "Cyanotype", Mapping.DENSITY,
                0.0, 1.4, 0, 255, ToneCurve.identity());
    }

    /**
     * Same process, rendered flatter so shadows do not sit on Dmax. Demonstrates what the
     * curve is for: the density range cannot be extended, but it can be used less fully.
     */
    public static ProcessProfile cyanotypeSoft() {
        return new ProcessProfile("cyanotype-soft", "Cyanotype, softened", Mapping.DENSITY,
                0.0, 1.4, 0, 255,
                ToneCurve.through(List.of(
                        new ToneCurve.Point(0.0, 0.10),
                        new ToneCurve.Point(0.5, 0.50),
                        new ToneCurve.Point(1.0, 0.90))));
    }

    public static List<ProcessProfile> all() {
        return ALL;
    }

    /**
     * A profile whose response was measured from a real print.
     *
     * <p>{@code printDmax} stays at the typical cyanotype value because it only sets
     * <em>where</em> a source tone belongs in the print's range; the measured curve maps
     * that position to a byte. The absolute density in the scan cancelled out when the
     * measurement was normalised, so measuring it again here would be double counting.
     */
    public static ProcessProfile calibrated(String id, String displayName,
                                            MeasuredResponse response) {
        return calibrated(id, displayName, 1.4, response);
    }

    /**
     * @param printDmax how much source density the print is asked to carry. Only the
     *                  <em>position</em> of a tone in the print's range depends on it; the
     *                  measured curve decides which byte gets it there.
     */
    public static ProcessProfile calibrated(String id, String displayName, double printDmax,
                                            MeasuredResponse response) {
        return new ProcessProfile(id, displayName, Mapping.CALIBRATED,
                0.0, printDmax, 0, 255, ToneCurve.identity(), response);
    }

    /**
     * Looks up a built-in profile. Calibrated profiles live in the profile store, which
     * is an application concern; the catalog there falls back to this for the built-ins.
     */
    public static ProcessProfile byId(String id) {
        return ALL.stream()
                .filter(profile -> profile.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new UnknownProfileException(id));
    }
}
