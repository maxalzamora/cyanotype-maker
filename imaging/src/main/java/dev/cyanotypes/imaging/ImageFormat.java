package dev.cyanotypes.imaging;

import java.nio.charset.StandardCharsets;

/**
 * Formats we are willing to open, identified by magic bytes.
 *
 * <p>The declared {@code Content-Type} of an upload is a hint from the client and is
 * never trusted. This is the only source of truth about what a file actually is.
 */
public enum ImageFormat {
    PNG,
    JPEG,
    TIFF,
    GIF,
    UNKNOWN;

    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    public static ImageFormat sniff(byte[] data) {
        if (data == null || data.length < 4) {
            return UNKNOWN;
        }
        if (startsWith(data, PNG_SIGNATURE)) {
            return PNG;
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (isTiff(data)) {
            return TIFF;
        }
        if (data.length >= 6) {
            String header = new String(data, 0, 6, StandardCharsets.US_ASCII);
            if ("GIF87a".equals(header) || "GIF89a".equals(header)) {
                return GIF;
            }
        }
        return UNKNOWN;
    }

    private static boolean isTiff(byte[] data) {
        if (data.length < 4) {
            return false;
        }
        boolean littleEndian = data[0] == 'I' && data[1] == 'I' && data[2] == 0x2A && data[3] == 0x00;
        boolean bigEndian = data[0] == 'M' && data[1] == 'M' && data[2] == 0x00 && data[3] == 0x2A;
        return littleEndian || bigEndian;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
