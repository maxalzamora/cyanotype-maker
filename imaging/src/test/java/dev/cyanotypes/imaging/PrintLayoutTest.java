package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrintLayoutTest {

    @Test
    void fitsLandscapeInsideA4PortraitWithTheAspectRatioIntact() {
        PrintSpec spec = new PrintSpec(Paper.A4, 10.0, 600, false);
        PrintLayout layout = PrintLayout.fit(spec, 900, 600);

        assertEquals(595.28, layout.pageWidthPt(), 0.01);
        assertEquals(841.89, layout.pageHeightPt(), 0.01);

        // The width is the limiting dimension, so both margins are filled exactly.
        assertEquals(595.28 - 2 * 28.35, layout.drawWidthPt(), 0.05);
        assertEquals(layout.drawWidthPt() * 600 / 900, layout.drawHeightPt(), 0.05);

        assertEquals(layout.pixelWidth(), layout.pixelHeight() * 3 / 2, 1);
    }

    @Test
    void centresTheImage() {
        PrintLayout layout = PrintLayout.fit(new PrintSpec(Paper.A4, 10.0, 600, false), 900, 600);
        double leftGap = layout.drawX();
        double rightGap = layout.pageWidthPt() - layout.drawX() - layout.drawWidthPt();
        assertEquals(leftGap, rightGap, 0.01);
        assertTrue(layout.drawY() > 0);
    }

    @Test
    void resolutionScalesThePixelGrid() {
        PrintLayout at300 = PrintLayout.fit(new PrintSpec(Paper.A4, 10.0, 300, false), 900, 600);
        PrintLayout at600 = PrintLayout.fit(new PrintSpec(Paper.A4, 10.0, 600, false), 900, 600);

        assertEquals(at300.pixelWidth() * 2, at600.pixelWidth(), 1);
        assertEquals(at300.pixelHeight() * 2, at600.pixelHeight(), 1);
    }

    @Test
    void aPortraitSourceFitsByHeight() {
        PrintLayout layout = PrintLayout.fit(new PrintSpec(Paper.A4, 10.0, 600, false), 600, 900);
        assertEquals(841.89 - 2 * 28.35, layout.drawHeightPt(), 0.05);
    }

    @Test
    void rejectsAMarginThatLeavesNoPaper() {
        assertThrows(IllegalArgumentException.class,
                () -> new PrintSpec(Paper.A4, 150.0, 600, false));
    }

    @Test
    void refusesAnAbsurdResolutionForThePaperSize() {
        assertThrows(ImageRejectedException.class,
                () -> PrintLayout.fit(new PrintSpec(Paper.A3, 1.0, 2400, false), 4000, 3000));
    }
}
