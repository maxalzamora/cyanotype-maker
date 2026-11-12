package dev.cyanotypes.imaging;

import java.util.List;

/**
 * How to turn a photograph into a negative for one particular process.
 *
 * <p>Two mappings are implemented:
 *
 * <dl>
 *   <dt>{@link Mapping#LINEAR}</dt>
 *   <dd>Perceptual lightness inverted. Simple, exact, and what stage 1 did. It ignores
 *       that density is logarithmic, so it blocks up shadows in the print.</dd>
 *   <dt>{@link Mapping#DENSITY}</dt>
 *   <dd>Print density is matched to the source's optical density, then scaled into the
 *       range the process can actually reach ({@code printDmin}..{@code printDmax}).
 *       Deep shadows legitimately clip here: a print cannot be denser than its Dmax.</dd>
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
        ToneCurve curve) {

    public enum Mapping {
        LINEAR,
        DENSITY
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

    public static ProcessProfile byId(String id) {
        return ALL.stream()
                .filter(profile -> profile.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new UnknownProfileException(id));
    }
}
