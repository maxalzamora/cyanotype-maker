package dev.cyanotypes.app.web;

import dev.cyanotypes.imaging.ExportFormat;
import dev.cyanotypes.app.service.ExportService;
import dev.cyanotypes.app.service.RenderService;
import dev.cyanotypes.imaging.ImageProbe;
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
 * One endpoint for every output. {@code format=png} is a preview at the photograph's own
 * resolution; {@code tiff} and {@code pdf} are print files laid out on paper.
 */
@RestController
@RequestMapping("/api")
public class RenderController {

    private final RenderService renderService;
    private final ExportService exportService;

    public RenderController(RenderService renderService, ExportService exportService) {
        this.renderService = renderService;
        this.exportService = exportService;
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
                ? preview(file, profile)
                : print(file, profile, format, paper, marginMm, dpi, mirror);
    }

    private ResponseEntity<byte[]> preview(MultipartFile file, String profile) {
        RenderedNegative result = renderService.render(file, profile);
        ImageProbe source = result.source();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"negative.png\"")
                .header("X-Source-Format", source.format().name())
                .header("X-Source-Width", Integer.toString(source.width()))
                .header("X-Source-Height", Integer.toString(source.height()))
                .header("X-Profile", result.profile().id())
                .body(result.encoded());
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
