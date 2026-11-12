package dev.cyanotypes.app.service;

import dev.cyanotypes.imaging.MeasuredResponse;
import dev.cyanotypes.imaging.NegativeLut;
import dev.cyanotypes.imaging.ProcessProfile;
import dev.cyanotypes.imaging.UnknownProfileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibratedProfileStoreTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-11-12T10:30:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void savesAReadableFileAndReloadsItAsAWorkingProfile() throws IOException {
        CalibratedProfileStore store = new CalibratedProfileStore(directory, mapper, FIXED);

        CalibratedProfileStore.StoredProfile stored =
                store.save("My print", response(), 1.12);

        Path file = directory.resolve(stored.id() + ".json");
        assertTrue(Files.exists(file));
        String json = Files.readString(file);
        assertTrue(json.contains("\"points\""), json);
        assertTrue(json.contains("\"normalisedDensity\""), json);
        assertTrue(json.contains("My print"), json);

        CalibratedProfileStore reloaded = new CalibratedProfileStore(directory, mapper, FIXED);
        assertEquals(List.of(stored.id()),
                reloaded.profiles().stream().map(ProcessProfile::id).toList());

        ProcessProfile profile = reloaded.profiles().getFirst();
        assertEquals(ProcessProfile.Mapping.CALIBRATED, profile.mapping());
        assertEquals(1.12, stored.measuredDensityRange(), 1e-9);
        int[] table = NegativeLut.of(profile).table();
        assertEquals(10, table[255], 2, "a white source asks for the lightest measured byte");
        assertEquals(250, table[0], 2, "a black source asks for the darkest measured byte");
    }

    @Test
    void idsDoNotCollideWithinTheSameSecond() {
        CalibratedProfileStore store = new CalibratedProfileStore(directory, mapper, FIXED);

        String first = store.save(null, response(), 1.0).id();
        String second = store.save(null, response(), 1.0).id();

        assertNotEquals(first, second);
        assertEquals("cal-20261112-103000", first);
        assertEquals("cal-20261112-103000-2", second);
        assertEquals(2, store.profiles().size());
    }

    @Test
    void aBlankNameGetsATimestampedDefault() {
        CalibratedProfileStore store = new CalibratedProfileStore(directory, mapper, FIXED);

        assertEquals("Calibrated 12 Nov 2026, 10:30",
                store.save("  ", response(), 1.0).name());
    }

    @Test
    void namesAreCleanedAndCapped() {
        CalibratedProfileStore store = new CalibratedProfileStore(directory, mapper, FIXED);

        assertEquals("Cyanotype A4", store.save("  Cyanotype\n A4\t", response(), 1.0).name());

        String longName = "x".repeat(200);
        assertEquals(60, store.save(longName, response(), 1.0).name().length());
    }

    @Test
    void anUnreadableFileIsSkippedRatherThanFailingStartup() throws IOException {
        CalibratedProfileStore store = new CalibratedProfileStore(directory, mapper, FIXED);
        store.save("Good", response(), 1.0);
        Files.writeString(directory.resolve("cal-broken.json"), "{ not json");

        CalibratedProfileStore reloaded = new CalibratedProfileStore(directory, mapper, FIXED);

        assertEquals(1, reloaded.profiles().size());
        assertEquals("Good", reloaded.profiles().getFirst().displayName());
    }

    @Test
    void newestProfilesComeFirst() {
        CalibratedProfileStore store = new CalibratedProfileStore(directory, mapper, FIXED);
        store.save("Older", response(), 1.0);
        // Same clock: ids differ by suffix, so force a later timestamp for the next file.
        CalibratedProfileStore later = new CalibratedProfileStore(directory, mapper,
                Clock.fixed(Instant.parse("2026-11-13T10:30:00Z"), ZoneOffset.UTC));
        later.save("Newer", response(), 1.0);

        CalibratedProfileStore reloaded = new CalibratedProfileStore(directory, mapper, FIXED);

        assertEquals(List.of("Newer", "Older"),
                reloaded.profiles().stream().map(ProcessProfile::displayName).toList());
    }

    @Test
    void deleteRemovesBothTheFileAndTheProfile() throws IOException {
        CalibratedProfileStore store = new CalibratedProfileStore(directory, mapper, FIXED);
        String id = store.save("Temporary", response(), 1.0).id();

        store.delete(id);

        assertFalse(Files.exists(directory.resolve(id + ".json")));
        assertTrue(store.profiles().isEmpty());
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(0, files.count(), "no temporary files left behind");
        }
    }

    @Test
    void refusesToDeleteAProfileItDidNotCreate() {
        CalibratedProfileStore store = new CalibratedProfileStore(directory, mapper, FIXED);

        UnknownProfileException e =
                assertThrows(UnknownProfileException.class, () -> store.delete("cyanotype"));
        assertEquals("cyanotype", e.requestedId());
    }

    private static MeasuredResponse response() {
        return MeasuredResponse.of(List.of(
                new MeasuredResponse.Point(0.0, 10),
                new MeasuredResponse.Point(0.5, 128),
                new MeasuredResponse.Point(1.0, 250)));
    }
}
