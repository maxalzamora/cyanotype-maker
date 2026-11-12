package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StepWedgeTest {

    @Test
    void spansTheWholeRangeInclusively() {
        assertEquals(0, StepWedge.grayAt(21, 0));
        assertEquals(255, StepWedge.grayAt(21, 20));
        assertEquals(128, StepWedge.grayAt(21, 10));
    }

    @Test
    void rendersOnePatchPerStep() {
        assertEquals(21 * 8, StepWedge.source(21).getWidth());
        assertEquals(32, StepWedge.source(21).getHeight());
    }
}
