package dev.cyanotypes.imaging;

/**
 * Paper stock for a contact print, in millimetres.
 */
public enum Paper {
    A4(210.0, 297.0),
    A3(297.0, 420.0),
    LETTER(215.9, 279.4),
    INCH_8X10(203.2, 254.0);

    private final double widthMm;
    private final double heightMm;

    Paper(double widthMm, double heightMm) {
        this.widthMm = widthMm;
        this.heightMm = heightMm;
    }

    public double widthMm() {
        return widthMm;
    }

    public double heightMm() {
        return heightMm;
    }

    public static Paper byId(String id) {
        for (Paper paper : values()) {
            if (paper.name().equalsIgnoreCase(id)) {
                return paper;
            }
        }
        throw new UnknownPaperException(id);
    }
}
