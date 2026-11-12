package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class MirrorTest {

    @Test
    void flipsColumnsWithoutTouchingTheValues() {
        BufferedImage source = new BufferedImage(3, 1, BufferedImage.TYPE_BYTE_GRAY);
        source.getRaster().setSample(0, 0, 0, 10);
        source.getRaster().setSample(1, 0, 0, 20);
        source.getRaster().setSample(2, 0, 0, 30);

        BufferedImage flipped = Mirror.horizontal(source);

        assertNotSame(source, flipped);
        assertEquals(30, flipped.getRaster().getSample(0, 0, 0));
        assertEquals(20, flipped.getRaster().getSample(1, 0, 0));
        assertEquals(10, flipped.getRaster().getSample(2, 0, 0));
        assertEquals(10, source.getRaster().getSample(0, 0, 0), "source must not be mutated");
    }
}
