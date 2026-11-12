package dev.cyanotypes.app.web;

import dev.cyanotypes.imaging.Paper;
import dev.cyanotypes.imaging.PrintSpec;
import dev.cyanotypes.imaging.ProcessProfile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * The fixed vocabulary of the API: what you may ask for. The UI builds its selectors from
 * these, so adding a profile or a paper size is a change in the imaging module only.
 */
@RestController
public class CatalogController {

    @GetMapping("/api/profiles")
    public List<ProfileSummary> profiles() {
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

    @GetMapping("/api/papers")
    public List<PaperSummary> papers() {
        return Arrays.stream(Paper.values())
                .map(paper -> new PaperSummary(paper.name().toLowerCase(java.util.Locale.ROOT),
                        paper.widthMm(), paper.heightMm()))
                .toList();
    }

    @GetMapping("/api/defaults")
    public Defaults defaults() {
        return new Defaults(PrintSpec.DEFAULT_DPI, PrintSpec.DEFAULT_MARGIN_MM, "a4", "cyanotype");
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

    public record PaperSummary(String id, double widthMm, double heightMm) {
    }

    public record Defaults(int dpi, double marginMm, String paper, String profile) {
    }
}
