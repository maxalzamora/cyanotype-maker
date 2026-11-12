package dev.cyanotypes.imaging;

/**
 * Thrown when a caller asks for an output format that is not offered.
 */
public final class UnknownFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String requestedId;

    public UnknownFormatException(String requestedId) {
        super("unknown format '" + requestedId + "'; try png, tiff or pdf");
        this.requestedId = requestedId;
    }

    public String requestedId() {
        return requestedId;
    }
}
