package dev.cyanotypes.imaging;

/**
 * A request was refused before (or instead of) producing an image.
 *
 * <p>{@link Reason} exists so the transport layer can map rejections to status codes
 * without parsing English out of the message.
 */
public final class ImageRejectedException extends RuntimeException {

    public enum Reason {
        /** Nothing was uploaded. */
        EMPTY,
        /** Magic bytes are not a format we decode. */
        UNSUPPORTED_FORMAT,
        /** Recognised format, but the bytes do not form a readable image. */
        CORRUPT,
        /** Header is readable but the image exceeds our resource limits. */
        TOO_LARGE
    }

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public ImageRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ImageRejectedException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
