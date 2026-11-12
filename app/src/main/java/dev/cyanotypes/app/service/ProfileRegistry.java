package dev.cyanotypes.app.service;

import dev.cyanotypes.imaging.ProcessProfile;
import dev.cyanotypes.imaging.UnknownProfileException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Every profile the application can render with: the three built-in assumptions first,
 * then whatever has been calibrated on this machine.
 *
 * <p>A thin class, deliberately. It exists so the imaging module does not have to know
 * that profiles can be stored, and so the render path has one place to ask.
 */
@Service
public class ProfileRegistry {

    private final CalibratedProfileStore store;

    public ProfileRegistry(CalibratedProfileStore store) {
        this.store = store;
    }

    public List<ProcessProfile> all() {
        List<ProcessProfile> all = new ArrayList<>(ProcessProfile.all());
        all.addAll(store.profiles());
        return List.copyOf(all);
    }

    public ProcessProfile byId(String id) {
        for (ProcessProfile profile : all()) {
            if (profile.id().equals(id)) {
                return profile;
            }
        }
        throw new UnknownProfileException(id, all().stream().map(ProcessProfile::id).toList());
    }
}
