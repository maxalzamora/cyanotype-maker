package dev.cyanotypes.imaging;

/**
 * Where the negative goes on the page, in PDF points (1/72 inch), and how many pixels it
 * needs at the requested sampling density.
 *
 * <p>The image is fitted inside the margins with its aspect ratio intact and centred, so a
 * landscape photograph and a portrait one both sit square on the paper. No cropping: a
 * contact print is the whole frame.
 */
public record PrintLayout(
        int pixelWidth,
        int pixelHeight,
        double pageWidthPt,
        double pageHeightPt,
        double drawX,
        double drawY,
        double drawWidthPt,
        double drawHeightPt) {

    private static final double MM_PER_INCH = 25.4;
    private static final double POINTS_PER_INCH = 72.0;

    /** Guards against a request that would allocate gigabytes for a single export. */
    public static final long MAX_OUTPUT_PIXELS = 200_000_000L;

    public static PrintLayout fit(PrintSpec spec, int sourceWidth, int sourceHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("source dimensions must be positive");
        }
        double pageWidth = toPoints(spec.paper().widthMm());
        double pageHeight = toPoints(spec.paper().heightMm());
        double margin = toPoints(spec.marginMm());

        double availableWidth = pageWidth - 2 * margin;
        double availableHeight = pageHeight - 2 * margin;

        double scale = Math.min(availableWidth / sourceWidth, availableHeight / sourceHeight);
        double drawWidth = sourceWidth * scale;
        double drawHeight = sourceHeight * scale;

        int pixelWidth = (int) Math.round(drawWidth / POINTS_PER_INCH * spec.dpi());
        int pixelHeight = (int) Math.round(drawHeight / POINTS_PER_INCH * spec.dpi());

        if ((long) pixelWidth * pixelHeight > MAX_OUTPUT_PIXELS) {
            throw new ImageRejectedException(ImageRejectedException.Reason.TOO_LARGE,
                    "export would be " + pixelWidth + "x" + pixelHeight + " pixels; lower the dpi");
        }

        return new PrintLayout(
                pixelWidth, pixelHeight,
                pageWidth, pageHeight,
                (pageWidth - drawWidth) / 2.0,
                (pageHeight - drawHeight) / 2.0,
                drawWidth, drawHeight);
    }

    public static double toPoints(double millimetres) {
        return millimetres / MM_PER_INCH * POINTS_PER_INCH;
    }

    /** Effective sampling density of this layout, for reporting. */
    public double effectiveDpi() {
        return drawWidthPt / POINTS_PER_INCH == 0 ? 0
                : pixelWidth / (drawWidthPt / POINTS_PER_INCH);
    }
}
