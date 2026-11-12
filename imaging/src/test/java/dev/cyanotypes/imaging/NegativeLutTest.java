package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NegativeLutTest {

    @Test
    void linearProfileIsExactInversionAcrossTheWholeTable() {
        int[] table = NegativeLut.of(ProcessProfile.linear()).table();
        for (int value = 0; value < 256; value++) {
            assertEquals(255 - value, table[value], "value " + value);
        }
    }

    @Test
    void densityProfileCoversTheFullRange() {
        NegativeLut lut = NegativeLut.of(ProcessProfile.cyanotype());
        assertEquals(255, lut.map(0), "black source needs clear film");
        assertEquals(0, lut.map(255), "white source needs solid ink");
    }

    /**
     * The number that proves the density mapping is real: sRGB 128 is 21.6% reflectance,
     * so its optical density (0.666) is 47.6% of a 1.4 Dmax range -> 121, not 127.
     */
    @Test
    void densityProfilePlacesMidGreyByDensityNotByValue() {
        assertEquals(121, NegativeLut.of(ProcessProfile.cyanotype()).map(128));
    }

    @Test
    void everyProfileIsMonotonic() {
        for (ProcessProfile profile : ProcessProfile.all()) {
            int[] table = NegativeLut.of(profile).table();
            for (int value = 1; value < 256; value++) {
                assertTrue(table[value] <= table[value - 1],
                        profile.id() + " went backwards at " + value);
            }
        }
    }

    @Test
    void levelsBoundTheValuesActuallyWritten() {
        ProcessProfile bounded = new ProcessProfile("bounded", "Bounded", ProcessProfile.Mapping.LINEAR,
                0.0, 1.0, 16, 240, ToneCurve.identity());
        int[] table = NegativeLut.of(bounded).table();

        assertEquals(240, table[0]);
        assertEquals(16, table[255]);
        for (int value : table) {
            assertTrue(value >= 16 && value <= 240, "value outside levels: " + value);
        }
    }

    @Test
    void theSoftProfileGivesUpBothEndsOfTheRange() {
        NegativeLut soft = NegativeLut.of(ProcessProfile.cyanotypeSoft());
        // Shadows stay off solid ink and highlights stay off fully clear film, which is
        // what stops a soft print from blocking up.
        assertTrue(soft.map(0) > 220, "expected lifted shadows, got " + soft.map(0));
        assertTrue(soft.map(255) < 35, "expected held highlights, got " + soft.map(255));
    }

    @Test
    void theLutIsNotSharedState() {
        int[] first = NegativeLut.of(ProcessProfile.linear()).table();
        first[0] = 999;
        assertEquals(255, NegativeLut.of(ProcessProfile.linear()).table()[0]);
    }
}
