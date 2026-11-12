package dev.cyanotypes.app.service;

import dev.cyanotypes.imaging.CyanotypePreview;
import dev.cyanotypes.imaging.ImageProbe;
import dev.cyanotypes.imaging.PngEncoder;
import dev.cyanotypes.imaging.ProcessProfile;
import dev.cyanotypes.imaging.RenderedNegative;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;

/**
 * The same render, coloured like the print it would make. Separate from {@link RenderService}
 * because the preview is a different output, not a different tone: it only changes how the
 * negative's density is shown.
 */
@Service
public class PreviewService {

    /** Enough detail for a retina screen; larger photographs are averaged down, not cropped. */
    public static final int MAX_DIMENSION = 1400;

    private final RenderService renderService;
    private final PngEncoder pngEncoder;

    public PreviewService(RenderService renderService, PngEncoder pngEncoder) {
        this.renderService = renderService;
        this.pngEncoder = pngEncoder;
    }

    public Preview preview(MultipartFile file, String profileId) {
        RenderedNegative negative = renderService.render(file, profileId);
        BufferedImage print = CyanotypePreview.render(negative.image(), MAX_DIMENSION);
        return new Preview(print.getWidth(), print.getHeight(), pngEncoder.encode(print),
                negative.source(), negative.profile());
    }

    public record Preview(int width, int height, byte[] encoded, ImageProbe source,
                          ProcessProfile profile) {
    }
}
