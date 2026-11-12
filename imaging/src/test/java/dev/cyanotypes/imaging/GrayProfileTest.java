package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrayProfileTest {

    @Test
    void javaAcceptsTheProfileAsAGreySpace() {
        ICC_Profile profile = ICC_Profile.getInstance(GrayProfile.gamma(2.2));

        assertEquals(ColorSpace.TYPE_GRAY, profile.getColorSpaceType());
        assertEquals(1, profile.getNumComponents());
        assertEquals(ICC_Profile.CLASS_DISPLAY, profile.getProfileClass());
        // ...and the raw signature really is 'mntr', not just the enum.
        byte[] data = profile.getData();
        assertEquals("mntr", new String(data, 12, 4));
        assertEquals("GRAY", new String(data, 16, 4));

        ColorSpace space = new ICC_ColorSpace(profile);
        float[] rgb = space.toRGB(new float[] {0.5f, 0.5f, 0.5f});
        // Gamma 2.2 is close to, but not exactly, sRGB's piecewise curve.
        assertEquals(0.504, rgb[0], 0.01);
    }

    @Test
    void midGreyStaysNearMidGreyUnderTheProfile() {
        ColorSpace space = new ICC_ColorSpace(ICC_Profile.getInstance(GrayProfile.gamma(2.2)));
        for (int value = 0; value <= 255; value += 51) {
            float in = value / 255f;
            float out = space.toRGB(new float[] {in, in, in})[0];
            assertTrue(Math.abs(in - out) < 0.05,
                    "value " + value + " came back as " + out);
        }
    }

    @Test
    void aLinearProfileWouldBeVisiblyWrong() {
        // Documents why the built-in CS_GRAY profile is not used: 0.5 through it is 0.735.
        ColorSpace linear = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        assertEquals(0.735, linear.toRGB(new float[] {0.5f, 0.5f, 0.5f})[0], 0.01);
    }

    @Test
    void refusesAGammaOutsideTheFixedPointRange() {
        assertThrows(IllegalArgumentException.class, () -> GrayProfile.gamma(0.5));
        assertThrows(IllegalArgumentException.class, () -> GrayProfile.gamma(4.0));
    }
}
