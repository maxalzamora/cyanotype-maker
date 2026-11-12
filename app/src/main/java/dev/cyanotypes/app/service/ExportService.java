package dev.cyanotypes.app.service;

import dev.cyanotypes.imaging.ExportFormat;
import dev.cyanotypes.imaging.Gray16;
import dev.cyanotypes.imaging.GrayProfile;
import dev.cyanotypes.imaging.GrayResampler;
import dev.cyanotypes.imaging.Mirror;
import dev.cyanotypes.imaging.Paper;
import dev.cyanotypes.imaging.PdfExporter;
import dev.cyanotypes.imaging.PrintLayout;
import dev.cyanotypes.imaging.PrintSpec;
import dev.cyanotypes.imaging.ProcessProfile;
import dev.cyanotypes.imaging.RenderedNegative;
import dev.cyanotypes.imaging.TiffEncoder;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Turns a rendered negative into a print file: resampled to the paper, optionally
 * mirrored, then wrapped in TIFF or PDF.
 */
@Service
public class ExportService {

    /**
     * Grey Gamma 2.2, built once. The TIFF carries it so a print tool does not have to
     * guess, and cannot guess wrong.
     */
    private static final byte[] GRAY_GAMMA_22 = GrayProfile.gamma(2.2);

    private final RenderService renderService;
    private final TiffEncoder tiffEncoder;
    private final PdfExporter pdfExporter;

    public ExportService(RenderService renderService, TiffEncoder tiffEncoder,
                         PdfExporter pdfExporter) {
        this.renderService = renderService;
        this.tiffEncoder = tiffEncoder;
        this.pdfExporter = pdfExporter;
    }

    public ExportedNegative export(MultipartFile file, String profileId, String formatId,
                                   String paperId, double marginMm, int dpi, boolean mirror) {
        ExportFormat format = ExportFormat.byId(formatId);
        if (!format.isPrintFormat()) {
            throw new IllegalArgumentException("use the render endpoint for " + format.id());
        }
        PrintSpec spec = new PrintSpec(Paper.byId(paperId), marginMm, dpi, mirror);

        RenderedNegative rendered = renderService.render(file, profileId);
        BufferedImage negative = mirror
                ? Mirror.horizontal(rendered.image())
                : rendered.image();

        PrintLayout layout = PrintLayout.fit(spec, negative.getWidth(), negative.getHeight());
        Gray16 raster = GrayResampler.resize(negative, layout.pixelWidth(), layout.pixelHeight());

        byte[] body;
        try {
            body = switch (format) {
                case TIFF -> tiffEncoder.encode(raster, spec.dpi(), GRAY_GAMMA_22);
                case PDF -> pdfExporter.write(raster, layout);
                case PNG -> throw new IllegalStateException("handled by the render path");
            };
        } catch (IOException e) {
            throw new ExportFailedException("could not build the " + format.id() + " file", e);
        }

        return new ExportedNegative(body, format, rendered.profile(), spec, layout,
                effectivePpi(raster, layout));
    }

    /**
     * What the file actually carries, which is not always the requested dpi: the fit is
     * rounded to whole pixels, so a 600 dpi request can come out at 599.7.
     */
    private static double effectivePpi(Gray16 raster, PrintLayout layout) {
        double inchesWide = layout.drawWidthPt() / 72.0;
        return inchesWide == 0 ? 0 : raster.width() / inchesWide;
    }

    /** Result of a print export, with everything the response needs. */
    public record ExportedNegative(
            byte[] bytes,
            ExportFormat format,
            ProcessProfile profile,
            PrintSpec spec,
            PrintLayout layout,
            double effectivePpi) {

        public String fileName() {
            return "negative" + format.extension();
        }
    }
}
