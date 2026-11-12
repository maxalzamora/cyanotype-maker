package dev.cyanotypes.imaging;

/**
 * How the negative is to be printed: stock, margin, sampling density and orientation.
 *
 * @param dpi    pixels per inch written to the file, and the resolution the negative is
 *               sampled at. 600 is a sensible floor for inkjet film; below ~300 the
 *               printer's halftone starts to show.
 * @param mirror flip horizontally. Needed when the film is coated on one side and must
 *               face the paper ink-down: you then look through the base, which mirrors it.
 */
public record PrintSpec(Paper paper, double marginMm, int dpi, boolean mirror) {

    public static final int DEFAULT_DPI = 600;
    public static final double DEFAULT_MARGIN_MM = 10.0;

    public PrintSpec {
        if (paper == null) {
            throw new IllegalArgumentException("paper is required");
        }
        if (marginMm < 0 || marginMm * 2 >= Math.min(paper.widthMm(), paper.heightMm())) {
            throw new IllegalArgumentException("margin leaves no printable area: " + marginMm + "mm");
        }
        if (dpi < 150 || dpi > 2400) {
            throw new IllegalArgumentException("dpi outside 150..2400: " + dpi);
        }
    }

    public static PrintSpec of(Paper paper, boolean mirror) {
        return new PrintSpec(paper, DEFAULT_MARGIN_MM, DEFAULT_DPI, mirror);
    }
}
