package dev.cyanotypes.app.service;

/**
 * Thrown when a print file cannot be produced. This is a server-side failure, not a bad
 * request: the upload was fine, the encoder was not.
 */
public final class ExportFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExportFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
