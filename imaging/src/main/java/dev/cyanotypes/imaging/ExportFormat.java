package dev.cyanotypes.imaging;

/**
 * Output container. PNG is a preview at the source's own resolution; TIFF and PDF are
 * print files, resampled onto the paper requested.
 */
public enum ExportFormat {

    PNG("png", "image/png", ".png"),
    TIFF("tiff", "image/tiff", ".tiff"),
    PDF("pdf", "application/pdf", ".pdf");

    private final String id;
    private final String contentType;
    private final String extension;

    ExportFormat(String id, String contentType, String extension) {
        this.id = id;
        this.contentType = contentType;
        this.extension = extension;
    }

    public String id() {
        return id;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public boolean isPrintFormat() {
        return this != PNG;
    }

    public static ExportFormat byId(String id) {
        for (ExportFormat format : values()) {
            if (format.id.equalsIgnoreCase(id)) {
                return format;
            }
        }
        throw new UnknownFormatException(id);
    }
}
