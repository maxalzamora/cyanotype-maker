package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageFormatTest {

    @Test
    void recognisesTheFormatsWeDecode() {
        assertEquals(ImageFormat.PNG, ImageFormat.sniff(ImageFixtures.png(2, 2)));
        assertEquals(ImageFormat.JPEG, ImageFormat.sniff(ImageFixtures.jpeg(2, 2)));
        assertEquals(ImageFormat.TIFF, ImageFormat.sniff(new byte[] {'I', 'I', 0x2A, 0x00}));
        assertEquals(ImageFormat.TIFF, ImageFormat.sniff(new byte[] {'M', 'M', 0x00, 0x2A}));
        assertEquals(ImageFormat.GIF, ImageFormat.sniff("GIF89a".getBytes()));
    }

    @Test
    void rejectsEverythingElse() {
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.sniff("<html>not an image".getBytes()));
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.sniff(new byte[0]));
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.sniff(null));
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.sniff(Arrays.copyOf(
                ImageFixtures.png(1, 1), 4)));
    }
}
