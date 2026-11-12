package dev.cyanotypes.app.web;

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

@RestController
@RequestMapping("/api")
public class RenderController {

    private final RenderService service;

    public RenderController(RenderService service) {
        this.service = service;
    }

    @PostMapping(path = "/render", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> render(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "profile", defaultValue = "cyanotype") String profile) {

        RenderedNegative result = service.render(file, profile);
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
}
