package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PngEncoderTest {

    @Test
    void writesEightBitGreyscalePng() throws IOException {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setSample(0, 0, 0, 0);
        image.getRaster().setSample(1, 0, 0, 255);

        byte[] encoded = new PngEncoder().encode(image);

        assertEquals((byte) 0x89, encoded[0]);
        assertEquals('P', encoded[1]);
        assertEquals(8, encoded[24], "bit depth");
        assertEquals(0, encoded[25], "colour type 0 = greyscale");

        BufferedImage reread = ImageIO.read(new ByteArrayInputStream(encoded));
        assertEquals(0, reread.getRaster().getSample(0, 0, 0));
        assertEquals(255, reread.getRaster().getSample(1, 0, 0));
    }
}
