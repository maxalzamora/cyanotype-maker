package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessProfileTest {

    @Test
    void idsAreUniqueAndLookupWorks() {
        List<ProcessProfile> all = ProcessProfile.all();
        assertEquals(all.size(), all.stream().map(ProcessProfile::id).distinct().count());
        assertEquals("cyanotype", ProcessProfile.byId("cyanotype").id());
    }

    @Test
    void unknownIdNamesTheAlternatives() {
        UnknownProfileException e = assertThrows(UnknownProfileException.class,
                () -> ProcessProfile.byId("nope"));
        assertEquals("nope", e.requestedId());
        assertTrue(e.getMessage().contains("cyanotype"));
    }

    @Test
    void rejectsImpossibleDensityRanges() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessProfile(
                "bad", "Bad", ProcessProfile.Mapping.DENSITY,
                1.4, 1.4, 0, 255, ToneCurve.identity()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessProfile(
                "bad", "Bad", ProcessProfile.Mapping.DENSITY,
                0.0, 0.1, 200, 100, ToneCurve.identity()));
    }

    @Test
    void theDensityProfilesShareAPrintRangeButDifferInShape() {
        assertNotEquals(
                NegativeLut.of(ProcessProfile.cyanotype()).map(64),
                NegativeLut.of(ProcessProfile.cyanotypeSoft()).map(64));
    }
}
