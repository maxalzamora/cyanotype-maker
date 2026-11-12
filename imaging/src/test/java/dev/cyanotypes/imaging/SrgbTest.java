package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SrgbTest {

    @Test
    void decodeIsLinearNearBlack() {
        assertEquals(0.0, Srgb.decode(0.0), 1e-12);
        assertEquals(0.04045 / 12.92, Srgb.decode(0.04045), 1e-12);
    }

    @Test
    void midGreyDecodesToAboutOneFifthOfTheLight() {
        // sRGB 0.5 is 21.4% linear light, not 50%. Getting this wrong flattens contrast.
        assertEquals(0.2140, Srgb.decode(0.5), 0.0001);
    }

    @Test
    void encodeInvertsDecode() {
        for (int i = 0; i <= 255; i++) {
            double encoded = i / 255.0;
            assertEquals(encoded, Srgb.encode(Srgb.decode(encoded)), 1e-9,
                    "round trip failed at 8-bit value " + i);
        }
    }

    @Test
    void luminanceOfWhiteIsOne() {
        assertEquals(1.0, Srgb.luminance(1, 1, 1), 1e-12);
        assertTrue(Srgb.luminance(0, 1, 0) > Srgb.luminance(1, 0, 0));
    }
}
