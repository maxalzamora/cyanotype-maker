package dev.cyanotypes.imaging;

/**
 * Thrown when a caller asks for a profile that does not exist. Carries the requested id
 * so the transport layer can say which one was wrong.
 */
public final class UnknownProfileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String requestedId;

    public UnknownProfileException(String requestedId) {
        super("unknown profile '" + requestedId + "'; try one of "
                + ProcessProfile.all().stream().map(ProcessProfile::id).toList());
        this.requestedId = requestedId;
    }

    public String requestedId() {
        return requestedId;
    }
}
