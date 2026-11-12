package dev.cyanotypes.app.web;

import dev.cyanotypes.imaging.ExportFormat;
import dev.cyanotypes.app.service.ExportService;
import dev.cyanotypes.app.service.PreviewService;
import dev.cyanotypes.app.service.RenderService;
import dev.cyanotypes.imaging.ImageProbe;
import dev.cyanotypes.imaging.ProcessProfile;
import dev.cyanotypes.imaging.RenderedNegative;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The image endpoints. {@code format=png} is the negative at the photograph's own
 * resolution; {@code tiff} and {@code pdf} are print files laid out on paper;
 * {@code /api/preview} is the negative coloured as the print it would make.
 */
@RestController
@RequestMapping("/api")
public class RenderController {

    private final RenderService renderService;
    private final ExportService exportService;
    private final PreviewService previewService;

    public RenderController(RenderService renderService, ExportService exportService,
                            PreviewService previewService) {
        this.renderService = renderService;
        this.exportService = exportService;
        this.previewService = previewService;
    }

    @PostMapping("/render")
    public ResponseEntity<byte[]> render(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "profile", defaultValue = "cyanotype") String profile,
            @RequestParam(name = "format", defaultValue = "png") String format,
            @RequestParam(name = "paper", defaultValue = "a4") String paper,
            @RequestParam(name = "margin", defaultValue = "10") double marginMm,
            @RequestParam(name = "dpi", defaultValue = "600") int dpi,
            @RequestParam(name = "mirror", defaultValue = "false") boolean mirror) {

        ExportFormat resolved = ExportFormat.byId(format);
        return resolved == ExportFormat.PNG
                ? negativePng(file, profile)
                : print(file, profile, format, paper, marginMm, dpi, mirror);
    }

    /**
     * The print the negative would make, downsampled to screen size. The photograph is
     * resampled, never cropped, so the preview has the print's framing.
     */
    @PostMapping("/preview")
    public ResponseEntity<byte[]> preview(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "profile", defaultValue = "cyanotype") String profile) {

        PreviewService.Preview result = previewService.preview(file, profile);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"cyanotype-preview.png\"")
                .headers(describing(result.source(), result.profile()))
                .header("X-Preview-Width", Integer.toString(result.width()))
                .header("X-Preview-Height", Integer.toString(result.height()))
                .body(result.encoded());
    }

    private ResponseEntity<byte[]> negativePng(MultipartFile file, String profile) {
        RenderedNegative result = renderService.render(file, profile);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"negative.png\"")
                .headers(describing(result.source(), result.profile()))
                .body(result.encoded());
    }

    /** What the caller actually got, not what it asked for. */
    private static HttpHeaders describing(ImageProbe source, ProcessProfile profile) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Source-Format", source.format().name());
        headers.set("X-Source-Width", Integer.toString(source.width()));
        headers.set("X-Source-Height", Integer.toString(source.height()));
        headers.set("X-Profile", profile.id());
        return headers;
    }

    private ResponseEntity<byte[]> print(MultipartFile file, String profile, String format,
                                         String paper, double marginMm, int dpi, boolean mirror) {
        ExportService.ExportedNegative exported =
                exportService.export(file, profile, format, paper, marginMm, dpi, mirror);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exported.format().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + exported.fileName() + "\"")
                .header("X-Profile", exported.profile().id())
                .header("X-Paper", exported.spec().paper().name())
                .header("X-Margin-Mm", Double.toString(exported.spec().marginMm()))
                .header("X-Effective-Ppi", String.format(java.util.Locale.ROOT, "%.1f",
                        exported.effectivePpi()))
                .header("X-Print-Width-Mm", String.format(java.util.Locale.ROOT, "%.1f",
                        exported.layout().drawWidthPt() / 72.0 * 25.4))
                .header("X-Print-Height-Mm", String.format(java.util.Locale.ROOT, "%.1f",
                        exported.layout().drawHeightPt() / 72.0 * 25.4))
                .header("X-Mirrored", Boolean.toString(exported.spec().mirror()))
                .body(exported.bytes());
    }
}
