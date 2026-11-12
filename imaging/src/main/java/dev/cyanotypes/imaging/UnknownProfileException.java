package dev.cyanotypes.imaging;

import java.util.List;

/**
 * Thrown when a caller asks for a profile that does not exist. Carries the requested id
 * so the transport layer can say which one was wrong.
 */
public final class UnknownProfileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String requestedId;

    public UnknownProfileException(String requestedId) {
        this(requestedId, ProcessProfile.all().stream().map(ProcessProfile::id).toList());
    }

    public UnknownProfileException(String requestedId, List<String> known) {
        super("unknown profile '" + requestedId + "'; try one of " + known);
        this.requestedId = requestedId;
    }

    public String requestedId() {
        return requestedId;
    }
}
