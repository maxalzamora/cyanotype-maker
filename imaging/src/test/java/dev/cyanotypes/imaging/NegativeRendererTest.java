package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NegativeRendererTest {

    private final NegativeRenderer renderer = new NegativeRenderer();
    private final ProcessProfile linear = ProcessProfile.linear();

    /** With the linear profile the transform is exactly an inversion, at every value. */
    @Test
    void linearProfileInvertsEveryGreyscaleValueExactly() {
        BufferedImage ramp = new BufferedImage(256, 1, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 256; x++) {
            ramp.setRGB(x, 0, new Color(x, x, x).getRGB());
        }

        BufferedImage negative = renderer.render(ramp, linear);
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, negative.getType());

        for (int x = 0; x < 256; x++) {
            assertEquals(255 - x, negative.getRaster().getSample(x, 0, 0),
                    "source " + x + " should become " + (255 - x));
        }
    }

    @Test
    void blackSourceBecomesClearFilmAndWhiteSourceBecomesSolidInk() {
        assertEquals(255, valueOf(new Color(0, 0, 0)));
        assertEquals(0, valueOf(new Color(255, 255, 255)));
    }

    @Test
    void midGreyStaysMidGreyUnderTheLinearProfile() {
        // 50% sRGB -> 21.4% linear -> back to 50% perceptual -> 127.
        // A value near 186 here would mean gamma was applied twice.
        assertEquals(127, valueOf(new Color(128, 128, 128)));
    }

    @Test
    void colourIsCollapsedByLuminanceNotByChannelAverage() {
        // Pure red: luminance 0.2126 linear -> 0.4986 perceptual -> 127 grey.
        // A channel average would give 85, which is visibly wrong.
        assertEquals(128, valueOf(new Color(255, 0, 0)));
    }

    @Test
    void transparencyIsFlattenedOntoWhite() {
        BufferedImage argb = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        argb.setRGB(0, 0, 0x00FF0000); // fully transparent
        assertEquals(0, renderer.render(argb, linear).getRaster().getSample(0, 0, 0));
    }

    @Test
    void theDensityProfileRendersDifferentlyFromALinearInversion() {
        int linearValue = renderer.render(swatch(new Color(128, 128, 128)), linear)
                .getRaster().getSample(0, 0, 0);
        int densityValue = renderer.render(swatch(new Color(128, 128, 128)),
                        ProcessProfile.cyanotype())
                .getRaster().getSample(0, 0, 0);

        assertEquals(127, linearValue);
        assertEquals(121, densityValue);
    }

    private int valueOf(Color colour) {
        return renderer.render(swatch(colour), linear).getRaster().getSample(0, 0, 0);
    }

    private static BufferedImage swatch(Color colour) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, colour.getRGB());
        return image;
    }
}
