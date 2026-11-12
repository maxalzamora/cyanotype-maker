package dev.cyanotypes.app.service;

import tools.jackson.databind.ObjectMapper;
import dev.cyanotypes.imaging.MeasuredResponse;
import dev.cyanotypes.imaging.ProcessProfile;
import dev.cyanotypes.imaging.UnknownProfileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Calibrated profiles on disk, one JSON file each.
 *
 * <p>A file per profile rather than a database: the whole point of a calibration is that a
 * person can look at the numbers, copy the file to another machine, or delete a bad run.
 * Nothing here is concurrent, and nothing here is bigger than a few kilobytes.
 *
 * <p>The in-memory list is the source of truth during a run; the directory is re-read at
 * startup. A profile that cannot be parsed is skipped with a warning rather than stopping
 * the application, because losing the built-in profiles over one bad file would be worse.
 */
public final class CalibratedProfileStore {

    private static final Logger LOG = LoggerFactory.getLogger(CalibratedProfileStore.class);

    private static final DateTimeFormatter ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter NAME_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");
    private static final int MAX_NAME_LENGTH = 60;

    /** The density range a source tone is placed into; matches {@link ProcessProfile#cyanotype()}. */
    private static final double SOURCE_DENSITY_RANGE = 1.4;

    private final Path directory;
    private final ObjectMapper mapper;
    private final Clock clock;
    private volatile List<ProcessProfile> profiles;

    public CalibratedProfileStore(Path directory, ObjectMapper mapper) {
        this(directory, mapper, Clock.systemDefaultZone());
    }

    public CalibratedProfileStore(Path directory, ObjectMapper mapper, Clock clock) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.profiles = load();
    }

    /** Newest first. */
    public List<ProcessProfile> profiles() {
        return profiles;
    }

    /**
     * Persists a measurement and makes it immediately available for rendering.
     *
     * @param requestedName display name; a timestamped default is used when blank
     */
    public StoredProfile save(String requestedName, MeasuredResponse response,
                              double measuredDensityRange) {
        LocalDateTime now = LocalDateTime.now(clock);
        StoredProfile stored = new StoredProfile(
                freshId(now),
                cleanName(requestedName, now),
                SOURCE_DENSITY_RANGE,
                now.toString(),
                response.points(),
                measuredDensityRange);

        write(stored);

        List<ProcessProfile> next = new ArrayList<>(profiles.size() + 1);
        next.add(stored.toProfile());
        next.addAll(profiles);
        profiles = List.copyOf(next);
        return stored;
    }

    /** Removes a calibration from disk and from the live list. Built-ins are not ours to delete. */
    public void delete(String id) {
        if (profiles.stream().noneMatch(profile -> profile.id().equals(id))) {
            throw new UnknownProfileException(id,
                    profiles.stream().map(ProcessProfile::id).toList());
        }
        try {
            Files.deleteIfExists(directory.resolve(id + ".json"));
        } catch (IOException e) {
            throw new ProfileStoreException("could not delete profile " + id, e);
        }
        profiles = profiles.stream()
                .filter(profile -> !profile.id().equals(id))
                .toList();
    }

    private void write(StoredProfile stored) {
        Path target = directory.resolve(stored.id() + ".json");
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, stored.id(), ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), stored);
            Files.move(temporary, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new ProfileStoreException("could not save profile " + stored.id(), e);
        }
    }

    private List<ProcessProfile> load() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<ProcessProfile> loaded = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            StoredProfile stored =
                                    mapper.readValue(path.toFile(), StoredProfile.class);
                            loaded.add(stored.toProfile());
                        } catch (RuntimeException e) {
                            LOG.warn("skipping unreadable calibration profile {}: {}",
                                    path.getFileName(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("could not list " + directory, e);
        }
        return List.copyOf(loaded);
    }

    private String freshId(LocalDateTime now) {
        String base = "cal-" + now.format(ID_FORMAT);
        String id = base;
        int suffix = 2;
        while (Files.exists(directory.resolve(id + ".json"))) {
            id = base + "-" + suffix;
            suffix++;
        }
        return id;
    }

    private static String cleanName(String requested, LocalDateTime now) {
        String name = requested == null
                ? ""
                : requested.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (name.isEmpty()) {
            name = "Calibrated " + now.format(NAME_FORMAT);
        }
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH).trim();
    }

    /**
     * On-disk form. Deliberately flat and stringly where it can be: this file is meant to
     * be readable and editable by hand.
     */
    public record StoredProfile(
            String id,
            String name,
            double sourceDensityRange,
            String createdAt,
            List<MeasuredResponse.Point> points,
            double measuredDensityRange) {

        public ProcessProfile toProfile() {
            return ProcessProfile.calibrated(id, name, sourceDensityRange,
                    MeasuredResponse.of(points));
        }
    }
}
