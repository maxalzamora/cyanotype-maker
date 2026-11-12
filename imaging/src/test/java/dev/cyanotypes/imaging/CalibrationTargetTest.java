package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationTargetTest {

    @Test
    void patchValuesSpanBlackToWhiteOnce() {
        assertEquals(0, CalibrationTarget.patchValue(0));
        assertEquals(255, CalibrationTarget.patchValue(CalibrationTarget.PATCHES - 1));
        assertEquals(128, CalibrationTarget.patchValue(10));

        for (int i = 1; i < CalibrationTarget.PATCHES; i++) {
            assertTrue(CalibrationTarget.patchValue(i) > CalibrationTarget.patchValue(i - 1),
                    "patch values must increase at " + i);
        }
    }

    @Test
    void theRenderedTargetCarriesTheRawBytesWithNoToneCurve() {
        BufferedImage target = CalibrationTarget.render();
        assertEquals(CalibrationTarget.WIDTH, target.getWidth());
        assertEquals(CalibrationTarget.HEIGHT, target.getHeight());

        var raster = target.getRaster();
        for (int i = 0; i < CalibrationTarget.PATCHES; i++) {
            Rectangle window = CalibrationTarget.sampleWindow(i, target.getWidth(), target.getHeight());
            assertEquals(CalibrationTarget.patchValue(i),
                    raster.getSample(window.x + window.width / 2, window.y + window.height / 2, 0));
        }
    }

    @Test
    void columnsMapToPatchesAcrossTheWholeWidth() {
        assertEquals(0, CalibrationTarget.patchAt(0, 2100));
        assertEquals(20, CalibrationTarget.patchAt(2099, 2100));
        for (int i = 0; i < CalibrationTarget.PATCHES; i++) {
            int cell = 2100 / CalibrationTarget.PATCHES;
            assertEquals(i, CalibrationTarget.patchAt(i * cell, 2100));
            assertEquals(i, CalibrationTarget.patchAt(i * cell + cell - 1, 2100));
        }
    }

    @Test
    void sampleWindowsSitInsideTheirOwnBar() {
        int width = 1050;
        int height = 400;
        for (int i = 0; i < CalibrationTarget.PATCHES; i++) {
            Rectangle window = CalibrationTarget.sampleWindow(i, width, height);
            double cell = width / (double) CalibrationTarget.PATCHES;
            assertTrue(window.x >= Math.floor(i * cell));
            assertTrue(window.x + window.width <= Math.ceil((i + 1) * cell) + 1);
            assertTrue(window.height < height, "the window leaves a margin top and bottom");
        }
    }

    @Test
    void windowsStayAtLeastOnePixelEvenForTinyImages() {
        Rectangle window = CalibrationTarget.sampleWindow(0, CalibrationTarget.PATCHES, 3);
        assertTrue(window.width >= 1);
        assertTrue(window.height >= 1);
    }

    @Test
    void rejectsIndexesOutsideTheTarget() {
        assertThrows(IllegalArgumentException.class, () -> CalibrationTarget.patchValue(-1));
        assertThrows(IllegalArgumentException.class,
                () -> CalibrationTarget.patchValue(CalibrationTarget.PATCHES));
    }
}
