package dev.cyanotypes.app.service;

/**
 * A calibration profile could not be read or written. Distinct from a bad measurement:
 * the scan was fine, the disk was not.
 */
public final class ProfileStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProfileStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
