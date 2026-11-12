package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiffEncoderTest {

    private static final byte[] ICC = GrayProfile.gamma(2.2);

    @Test
    void writesTheTagsThatMakeItA16BitGreyPrintFile() {
        byte[] tiff = encode(pattern(64, 32), 600);
        Map<Integer, Field> fields = fields(tiff);

        assertEquals(64, fields.get(256).value());
        assertEquals(32, fields.get(257).value());
        assertEquals(16, fields.get(258).value(), "bits per sample");
        assertEquals(1, fields.get(259).value(), "uncompressed");
        assertEquals(1, fields.get(262).value(), "BlackIsZero");
        assertEquals(1, fields.get(277).value(), "one channel");
        assertEquals(1, fields.get(284).value(), "chunky");
        assertEquals(2, fields.get(296).value(), "resolution unit is inches");
        assertEquals(1, fields.get(339).value(), "unsigned samples");
    }

    @Test
    void recordsTheResolutionThatWasAskedFor() {
        byte[] tiff = encode(pattern(16, 16), 1200);
        Map<Integer, Field> fields = fields(tiff);
        ByteBuffer buffer = ByteBuffer.wrap(tiff).order(ByteOrder.LITTLE_ENDIAN);

        int xOffset = (int) fields.get(282).value();
        int yOffset = (int) fields.get(283).value();
        assertEquals(1200, buffer.getInt(xOffset));
        assertEquals(1, buffer.getInt(xOffset + 4));
        assertEquals(1200, buffer.getInt(yOffset));
        assertEquals(1, buffer.getInt(yOffset + 4));
    }

    @Test
    void embedsTheIccProfileByteForByte() {
        byte[] tiff = encode(pattern(8, 8), 600);
        Field icc = fields(tiff).get(34675);

        assertEquals(ICC.length, icc.count());
        byte[] embedded = new byte[ICC.length];
        System.arraycopy(tiff, (int) icc.value(), embedded, 0, ICC.length);
        assertArrayEquals(ICC, embedded);
    }

    @Test
    void splitsTallImagesIntoStripsAndKeepsThePixelsInOrder() {
        Gray16 image = pattern(2000, 1000);
        byte[] tiff = encode(image, 300);
        Map<Integer, Field> fields = fields(tiff);

        assertTrue(fields.get(273).count() > 1, "expected several strips");
        assertEquals(fields.get(273).count(), fields.get(279).count());

        short[] recovered = strips(tiff, fields);
        assertArrayEquals(image.samples(), recovered);
    }

    @Test
    void namesItselfInTheSoftwareTag() {
        byte[] tiff = encode(pattern(8, 8), 600);
        Field software = fields(tiff).get(305);
        String text = new String(tiff, (int) software.value(), (int) software.count(),
                StandardCharsets.US_ASCII);
        assertTrue(text.startsWith("cyanotypes"), text);
    }

    @Test
    void refusesIncompleteArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new TiffEncoder().encode(pattern(2, 2), 0, ICC));
        assertThrows(IllegalArgumentException.class,
                () -> new TiffEncoder().encode(pattern(2, 2), 600, new byte[0]));
    }

    @Test
    void gray16GuardsItsOwnDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new Gray16(2, 2, new short[3]));
    }

    private static byte[] encode(Gray16 image, int dpi) {
        return new TiffEncoder().encode(image, dpi, ICC);
    }

    private static Gray16 pattern(int width, int height) {
        short[] samples = new short[width * height];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (i % 65536);
        }
        return new Gray16(width, height, samples);
    }

    private static short[] strips(byte[] tiff, Map<Integer, Field> fields) {
        ByteBuffer buffer = ByteBuffer.wrap(tiff).order(ByteOrder.LITTLE_ENDIAN);
        int count = (int) fields.get(273).count();
        int offsetsAt = (int) fields.get(273).value();
        int countsAt = (int) fields.get(279).value();

        byte[] all = new byte[0];
        for (int strip = 0; strip < count; strip++) {
            int offset = buffer.getInt(offsetsAt + strip * 4);
            int length = buffer.getInt(countsAt + strip * 4);
            byte[] chunk = new byte[length];
            System.arraycopy(tiff, offset, chunk, 0, length);

            byte[] merged = new byte[all.length + chunk.length];
            System.arraycopy(all, 0, merged, 0, all.length);
            System.arraycopy(chunk, 0, merged, all.length, chunk.length);
            all = merged;
        }

        short[] samples = new short[all.length / 2];
        ByteBuffer data = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples.length; i++) {
            samples[i] = data.getShort();
        }
        return samples;
    }

    private static Map<Integer, Field> fields(byte[] tiff) {
        ByteBuffer buffer = ByteBuffer.wrap(tiff).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals('I', buffer.get(0));
        assertEquals('I', buffer.get(1));
        assertEquals(42, buffer.getShort(2));

        int ifd = buffer.getInt(4);
        int count = buffer.getShort(ifd) & 0xFFFF;
        Map<Integer, Field> fields = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int entry = ifd + 2 + i * 12;
            int tag = buffer.getShort(entry) & 0xFFFF;
            int type = buffer.getShort(entry + 2) & 0xFFFF;
            long items = buffer.getInt(entry + 4) & 0xFFFFFFFFL;
            long value = buffer.getInt(entry + 8) & 0xFFFFFFFFL;
            fields.put(tag, new Field(type, items, value));
        }
        return fields;
    }

    private record Field(int type, long count, long value) {
    }
}
