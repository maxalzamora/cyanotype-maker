package dev.cyanotypes.imaging;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A minimal Grey Gamma 2.2 ICC profile, built rather than bundled.
 *
 * <p>Java's built-in {@code CS_GRAY} profile is <em>linear</em>: converting 0.5 through it
 * yields sRGB 0.735. Labelling that "Gray Gamma 2.2" would be a lie that shifts every
 * tone in the print, so the profile is written from the spec instead.
 *
 * <p>This is a valid ICC v2 monitor profile with the three tags a grey profile needs:
 * {@code desc}, {@code wtpt} (D50) and a {@code kTRC} gamma curve. LittleCMS and Java both
 * accept it.
 */
public final class GrayProfile {

    private static final int HEADER_SIZE = 128;
    private static final int TAG_COUNT = 3;
    private static final int TAG_ENTRY_SIZE = 12;

    private GrayProfile() {
    }

    /** The profile bytes for a display-referred grey space with the given gamma. */
    public static byte[] gamma(double gamma) {
        if (gamma < 1.0 || gamma > 3.0) {
            throw new IllegalArgumentException("gamma outside the ICC u8Fixed8 range: " + gamma);
        }
        byte[] description = "Gray Gamma ".concat(formatGamma(gamma)).getBytes(StandardCharsets.US_ASCII);
        int descSize = 12 + description.length + 1 + 78;
        int descPadded = (descSize + 3) & ~3;
        int trcSize = 16;

        int tagTableSize = 4 + TAG_COUNT * TAG_ENTRY_SIZE;
        int descOffset = HEADER_SIZE + tagTableSize;
        int wtptOffset = descOffset + descPadded;
        int trcOffset = wtptOffset + 20;
        int total = trcOffset + trcSize;

        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN);

        // --- header ---
        out.putInt(total);
        out.put(fourCc("none"));            // preferred CMM
        out.putInt(0x02100000);             // ICC v2.1
        out.put(fourCc("mntr"));            // device class
        out.put(fourCc("GRAY"));            // data colour space
        out.put(fourCc("XYZ "));            // PCS
        out.putShort((short) 2026).putShort((short) 11).putShort((short) 12);
        out.putShort((short) 0).putShort((short) 0).putShort((short) 0);
        out.put(fourCc("acsp"));            // profile file signature
        out.putInt(0);                      // platform
        out.putInt(0);                      // flags
        out.putInt(0);                      // manufacturer
        out.putInt(0);                      // model
        out.putLong(0L);                    // attributes
        out.putInt(0);                      // rendering intent: perceptual
        out.putInt(0x0000F6D6).putInt(0x00010000).putInt(0x0000D32D); // D50
        out.putInt(0);                      // creator
        out.put(new byte[16]);              // profile id (v2: unused)
        out.put(new byte[28]);              // reserved

        // --- tag table ---
        out.putInt(TAG_COUNT);
        out.put(fourCc("desc")).putInt(descOffset).putInt(descSize);
        out.put(fourCc("wtpt")).putInt(wtptOffset).putInt(20);
        out.put(fourCc("kTRC")).putInt(trcOffset).putInt(14);

        // --- desc ---
        out.put(fourCc("desc")).putInt(0);
        out.putInt(description.length + 1);
        out.put(description).put((byte) 0);
        out.putInt(0);                      // unicode language code
        out.putInt(0);                      // unicode count
        out.putShort((short) 0);            // script code
        out.put((byte) 0);                  // macintosh count
        out.put(new byte[67]);
        while (out.position() < wtptOffset) {
            out.put((byte) 0);
        }

        // --- wtpt: D50 ---
        out.put(fourCc("XYZ ")).putInt(0);
        out.putInt(0x0000F6D6).putInt(0x00010000).putInt(0x0000D32D);

        // --- kTRC: single gamma value as u8Fixed8 ---
        out.put(fourCc("curv")).putInt(0);
        out.putInt(1);
        out.putShort((short) Math.round(gamma * 256));
        out.putShort((short) 0);

        return out.array();
    }

    private static byte[] fourCc(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != 4) {
            throw new IllegalArgumentException("four character code must be 4 bytes: " + value);
        }
        return bytes;
    }

    private static String formatGamma(double gamma) {
        return gamma == Math.rint(gamma) ? String.valueOf((int) gamma) : String.valueOf(gamma);
    }
}
