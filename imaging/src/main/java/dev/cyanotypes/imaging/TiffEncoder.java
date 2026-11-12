package dev.cyanotypes.imaging;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Writes a baseline TIFF: 16-bit greyscale, uncompressed, single channel, with an exact
 * resolution and an embedded ICC profile.
 *
 * <p>Written by hand rather than through {@code ImageIO} because the interesting tags —
 * resolution unit and ICC profile — are the ones the metadata API is most awkward about,
 * and because every byte of the result is then a decision rather than a default. The
 * layout is the baseline subset of TIFF 6.0: one IFD, strips, no extensions.
 *
 * <p>16 bits is not decoration. The tone curve and the resampling step both produce
 * intermediate values; carrying 16 bits to the file means they are not quantised twice on
 * the way out.
 */
public final class TiffEncoder {

    private static final int TYPE_SHORT = 3;
    private static final int TYPE_LONG = 4;
    private static final int TYPE_RATIONAL = 5;
    private static final int TYPE_ASCII = 2;
    private static final int TYPE_UNDEFINED = 7;

    private static final int TAG_IMAGE_WIDTH = 256;
    private static final int TAG_IMAGE_LENGTH = 257;
    private static final int TAG_BITS_PER_SAMPLE = 258;
    private static final int TAG_COMPRESSION = 259;
    private static final int TAG_PHOTOMETRIC = 262;
    private static final int TAG_STRIP_OFFSETS = 273;
    private static final int TAG_SAMPLES_PER_PIXEL = 277;
    private static final int TAG_ROWS_PER_STRIP = 278;
    private static final int TAG_STRIP_BYTE_COUNTS = 279;
    private static final int TAG_X_RESOLUTION = 282;
    private static final int TAG_Y_RESOLUTION = 283;
    private static final int TAG_PLANAR_CONFIGURATION = 284;
    private static final int TAG_RESOLUTION_UNIT = 296;
    private static final int TAG_SOFTWARE = 305;
    private static final int TAG_SAMPLE_FORMAT = 339;
    private static final int TAG_ICC_PROFILE = 34675;

    private static final int ENTRY_COUNT = 16;
    private static final int HEADER_SIZE = 8;
    private static final int IFD_SIZE = 2 + ENTRY_COUNT * 12 + 4;

    /** Target strip size. Small enough to stream, large enough not to bloat the IFD. */
    private static final int TARGET_STRIP_BYTES = 1 << 20;

    public byte[] encode(Gray16 image, int dpi, byte[] iccProfile) {
        if (dpi <= 0) {
            throw new IllegalArgumentException("dpi must be positive");
        }
        if (iccProfile == null || iccProfile.length == 0) {
            throw new IllegalArgumentException("an ICC profile is required");
        }

        int width = image.width();
        int height = image.height();
        int bytesPerRow = width * 2;
        int rowsPerStrip = Math.max(1, Math.min(height, TARGET_STRIP_BYTES / bytesPerRow));
        int strips = (height + rowsPerStrip - 1) / rowsPerStrip;

        byte[] software = "cyanotypes 0.1.0\u0000".getBytes(StandardCharsets.US_ASCII);

        int dataOffset = HEADER_SIZE + IFD_SIZE;
        int position = dataOffset;

        int stripOffsetsOffset = align2(position);
        position = stripOffsetsOffset + strips * 4;
        int stripCountsOffset = align2(position);
        position = stripCountsOffset + strips * 4;
        int xResolutionOffset = align2(position);
        position = xResolutionOffset + 8;
        int yResolutionOffset = align2(position);
        position = yResolutionOffset + 8;
        int softwareOffset = align2(position);
        position = softwareOffset + software.length;
        int iccOffset = align2(position);
        position = iccOffset + iccProfile.length;
        int stripDataOffset = align2(position);

        int total = stripDataOffset + width * height * 2;
        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);

        // --- header ---
        out.put((byte) 'I').put((byte) 'I');
        out.putShort((short) 42);
        out.putInt(HEADER_SIZE);

        // --- IFD ---
        out.putShort((short) ENTRY_COUNT);
        entry(out, TAG_IMAGE_WIDTH, TYPE_LONG, 1, width);
        entry(out, TAG_IMAGE_LENGTH, TYPE_LONG, 1, height);
        entry(out, TAG_BITS_PER_SAMPLE, TYPE_SHORT, 1, 16);
        entry(out, TAG_COMPRESSION, TYPE_SHORT, 1, 1);          // none
        entry(out, TAG_PHOTOMETRIC, TYPE_SHORT, 1, 1);          // BlackIsZero
        entry(out, TAG_STRIP_OFFSETS, TYPE_LONG, strips, stripOffsetsOffset);
        entry(out, TAG_SAMPLES_PER_PIXEL, TYPE_SHORT, 1, 1);
        entry(out, TAG_ROWS_PER_STRIP, TYPE_LONG, 1, rowsPerStrip);
        entry(out, TAG_STRIP_BYTE_COUNTS, TYPE_LONG, strips, stripCountsOffset);
        entry(out, TAG_X_RESOLUTION, TYPE_RATIONAL, 1, xResolutionOffset);
        entry(out, TAG_Y_RESOLUTION, TYPE_RATIONAL, 1, yResolutionOffset);
        entry(out, TAG_PLANAR_CONFIGURATION, TYPE_SHORT, 1, 1);
        entry(out, TAG_RESOLUTION_UNIT, TYPE_SHORT, 1, 2);      // inch
        entry(out, TAG_SOFTWARE, TYPE_ASCII, software.length, softwareOffset);
        entry(out, TAG_SAMPLE_FORMAT, TYPE_SHORT, 1, 1);        // unsigned
        entry(out, TAG_ICC_PROFILE, TYPE_UNDEFINED, iccProfile.length, iccOffset);
        out.putInt(0);                                          // no next IFD

        // --- out-of-line values ---
        for (int strip = 0; strip < strips; strip++) {
            int firstRow = strip * rowsPerStrip;
            int rows = Math.min(rowsPerStrip, height - firstRow);
            out.putInt(stripOffsetsOffset + strip * 4,
                    stripDataOffset + firstRow * bytesPerRow);
            out.putInt(stripCountsOffset + strip * 4, rows * bytesPerRow);
        }
        out.putInt(xResolutionOffset, dpi).putInt(xResolutionOffset + 4, 1);
        out.putInt(yResolutionOffset, dpi).putInt(yResolutionOffset + 4, 1);
        putAt(out, softwareOffset, software);
        putAt(out, iccOffset, iccProfile);

        // --- pixels ---
        out.position(stripDataOffset);
        for (short sample : image.samples()) {
            out.putShort(sample);
        }
        return out.array();
    }

    private static void entry(ByteBuffer out, int tag, int type, long count, long value) {
        out.putShort((short) tag);
        out.putShort((short) type);
        out.putInt((int) count);
        out.putInt((int) value);
    }

    private static void putAt(ByteBuffer out, int offset, byte[] bytes) {
        int saved = out.position();
        out.position(offset);
        out.put(bytes);
        out.position(saved);
    }

    private static int align2(int value) {
        return (value + 1) & ~1;
    }
}
