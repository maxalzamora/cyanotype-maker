package dev.cyanotypes.app.web;

import dev.cyanotypes.app.service.ProfileRegistry;
import dev.cyanotypes.imaging.NegativeLut;
import dev.cyanotypes.imaging.NegativeRenderer;
import dev.cyanotypes.imaging.PngEncoder;
import dev.cyanotypes.imaging.ProcessProfile;
import dev.cyanotypes.imaging.StepWedge;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-check endpoints. These exist so correctness is observable from a browser rather
 * than only from a test run, which matters for a project about print accuracy.
 */
@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

    private static final int STEPS = 21;
    private static final int CELL = 8;

    private final NegativeRenderer renderer;
    private final PngEncoder encoder;
    private final ProfileRegistry profiles;

    public DiagnosticsController(NegativeRenderer renderer, PngEncoder encoder,
                                 ProfileRegistry profiles) {
        this.renderer = renderer;
        this.encoder = encoder;
        this.profiles = profiles;
    }

    /**
     * Renders the wedge through a profile and reports what came back.
     *
     * <p>This checks the render path, not the mathematics: expected values come from the
     * same LUT the renderer uses, so a mismatch means the LUT was not applied faithfully.
     * The mathematical claims are pinned separately, with hard-coded numbers, in
     * {@code NegativeLutTest}.
     */
    @GetMapping(path = "/step-wedge", produces = MediaType.APPLICATION_JSON_VALUE)
    public WedgeReport stepWedge(
            @RequestParam(name = "profile", defaultValue = "cyanotype") String profile) {

        ProcessProfile resolved = profiles.byId(profile);
        NegativeLut lut = NegativeLut.of(resolved);
        BufferedImage negative = renderer.render(StepWedge.source(STEPS), resolved);

        List<Step> rows = new ArrayList<>(STEPS);
        boolean allCorrect = true;
        for (int i = 0; i < STEPS; i++) {
            int source = StepWedge.grayAt(STEPS, i);
            int expected = lut.map(source);
            int actual = negative.getRaster().getSample(i * CELL + CELL / 2, 16, 0);
            boolean ok = actual == expected;
            allCorrect &= ok;
            rows.add(new Step(i, source, expected, actual, ok));
        }

        int[] table = lut.table();
        return new WedgeReport(resolved.id(), STEPS, allCorrect,
                isNonIncreasing(table), table[0], table[255], rows);
    }

    @GetMapping(path = "/step-wedge.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> stepWedgeImage(
            @RequestParam(name = "profile", defaultValue = "cyanotype") String profile) {
        ProcessProfile resolved = profiles.byId(profile);
        BufferedImage negative = renderer.render(StepWedge.source(STEPS), resolved);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(encoder.encode(negative));
    }

    private static boolean isNonIncreasing(int[] table) {
        for (int i = 1; i < table.length; i++) {
            if (table[i] > table[i - 1]) {
                return false;
            }
        }
        return true;
    }

    /**
     * One row of the wedge self-check.
     *
     * @param sourceGray    greyscale value of the synthetic input patch
     * @param expectedValue what the profile's LUT says
     * @param actualValue   what the renderer actually wrote
     */
    public record Step(int index, int sourceGray, int expectedValue, int actualValue, boolean ok) {
    }

    /**
     * @param monotonic true when the profile never sends a lighter source to a darker
     *                  negative value, which a real print would show as a contour
     * @param atBlack   output for a black source: clear film, so the densest print
     * @param atWhite   output for a white source: solid ink, so paper white
     */
    public record WedgeReport(
            String profile,
            int steps,
            boolean allCorrect,
            boolean monotonic,
            int atBlack,
            int atWhite,
            List<Step> rows) {
    }
}
