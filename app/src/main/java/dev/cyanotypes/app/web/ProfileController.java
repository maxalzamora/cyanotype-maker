package dev.cyanotypes.app.web;

import dev.cyanotypes.imaging.ProcessProfile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The profiles a caller may ask for. The UI builds its selector from this, so adding a
 * profile is a one-line change in the imaging module and no change here.
 */
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    @GetMapping
    public List<ProfileSummary> list() {
        return ProcessProfile.all().stream()
                .map(profile -> new ProfileSummary(
                        profile.id(),
                        profile.displayName(),
                        profile.mapping().name(),
                        profile.printDmin(),
                        profile.printDmax(),
                        profile.blackLevel(),
                        profile.whiteLevel()))
                .toList();
    }

    public record ProfileSummary(
            String id,
            String name,
            String mapping,
            double printDmin,
            double printDmax,
            int blackLevel,
            int whiteLevel) {
    }
}
