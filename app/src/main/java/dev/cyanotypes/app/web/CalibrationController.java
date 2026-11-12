package dev.cyanotypes.app.web;

import dev.cyanotypes.app.service.CalibrationService;
import dev.cyanotypes.imaging.CalibrationTarget;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Calibration is two requests because it is two real-world acts: print the target, then
 * photograph the result. Everything in between happens on paper.
 */
@RestController
@RequestMapping("/api/calibration")
public class CalibrationController {

    private final CalibrationService calibrationService;

    public CalibrationController(CalibrationService calibrationService) {
        this.calibrationService = calibrationService;
    }

    /**
     * The file to print and expose. {@code format=png} is a preview; {@code tiff} and
     * {@code pdf} are laid out on paper exactly like a negative.
     */
    @GetMapping("/target")
    public ResponseEntity<byte[]> target(
            @RequestParam(name = "format", defaultValue = "pdf") String format,
            @RequestParam(name = "paper", defaultValue = "a4") String paper,
            @RequestParam(name = "margin", defaultValue = "10") double margin,
            @RequestParam(name = "dpi", defaultValue = "600") int dpi) {

        CalibrationService.TargetFile file = calibrationService.target(format, paper, margin, dpi);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + file.fileName() + "\"")
                .header("X-Calibration-Patches", Integer.toString(CalibrationTarget.PATCHES))
                .header("X-Calibration-Format", file.format())
                .body(file.bytes());
    }

    @PostMapping
    public ResponseEntity<CalibrationService.Report> calibrate(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "name", required = false) String name) {
        return ResponseEntity.ok(calibrationService.calibrate(file, name));
    }

    /**
     * A photograph of a print that does not exist, for trying the loop without a
     * darkroom. Clearly synthetic: it is the S-curve the analyser's tests use.
     */
    @GetMapping(path = "/simulated-scan.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> simulatedScan() {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"simulated-scan.png\"")
                .header("X-Simulated", "true")
                .body(calibrationService.simulatedScan());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        calibrationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
