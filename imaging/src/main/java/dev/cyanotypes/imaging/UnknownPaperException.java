package dev.cyanotypes.imaging;

/**
 * Thrown when a caller asks for a paper size that does not exist.
 */
public final class UnknownPaperException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String requestedId;

    public UnknownPaperException(String requestedId) {
        super("unknown paper '" + requestedId + "'; try a4, a3, letter or inch_8x10");
        this.requestedId = requestedId;
    }

    public String requestedId() {
        return requestedId;
    }
}
