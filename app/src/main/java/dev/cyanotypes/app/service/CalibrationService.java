package dev.cyanotypes.app.service;

import dev.cyanotypes.app.config.CyanotypesProperties;
import dev.cyanotypes.imaging.CalibrationTarget;
import dev.cyanotypes.imaging.ExportFormat;
import dev.cyanotypes.imaging.ImageDecoder;
import dev.cyanotypes.imaging.ImageInspector;
import dev.cyanotypes.imaging.ImageProbe;
import dev.cyanotypes.imaging.MeasuredResponse;
import dev.cyanotypes.imaging.NegativeLut;
import dev.cyanotypes.imaging.Paper;
import dev.cyanotypes.imaging.PngEncoder;
import dev.cyanotypes.imaging.PrintSpec;
import dev.cyanotypes.imaging.ProcessProfile;
import dev.cyanotypes.imaging.ScanAnalyser;
import dev.cyanotypes.imaging.ScanMeasurement;
import dev.cyanotypes.imaging.SimulatedScan;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The calibration loop: hand out a target, take back a photograph of the print, turn the
 * measurement into a profile.
 *
 * <p>The target is printed through the same paper layout as a negative, because the thing
 * being measured is the whole chain — inkjet, transparency, exposure, chemistry — and
 * changing the layout between the measurement and the use would measure something else.
 */
@Service
public class CalibrationService {

    private final ImageInspector inspector;
    private final ScanAnalyser analyser;
    private final CalibratedProfileStore store;
    private final ProfileRegistry registry;
    private final ExportService exportService;
    private final PngEncoder pngEncoder;
    private final CyanotypesProperties.Limits limits;

    public CalibrationService(ImageInspector inspector, ScanAnalyser analyser,
                              CalibratedProfileStore store, ProfileRegistry registry,
                              ExportService exportService, PngEncoder pngEncoder,
                              CyanotypesProperties properties) {
        this.inspector = inspector;
        this.analyser = analyser;
        this.store = store;
        this.registry = registry;
        this.exportService = exportService;
        this.pngEncoder = pngEncoder;
        this.limits = properties.limits();
    }

    /**
     * The file to print. {@code png} is a quick preview at the target's native size; the
     * print formats go through the paper layout.
     */
    public TargetFile target(String formatId, String paperId, double marginMm, int dpi) {
        ExportFormat format = ExportFormat.byId(formatId);
        BufferedImage target = CalibrationTarget.render();

        if (format == ExportFormat.PNG) {
            return new TargetFile(pngEncoder.encode(target), format.contentType(),
                    "calibration-target.png", format.id());
        }
        PrintSpec spec = new PrintSpec(Paper.byId(paperId), marginMm, dpi, false);
        ExportService.PrintFile printed = exportService.printFile(target, format, spec);
        return new TargetFile(printed.bytes(), format.contentType(),
                printed.fileName("calibration-target"), format.id());
    }

    /** The target at print resolution, for the one-click downloads in the UI. */
    public TargetFile target(String formatId) {
        return target(formatId, "a4", PrintSpec.DEFAULT_MARGIN_MM, 600);
    }

    public Report calibrate(MultipartFile file, String name) {
        byte[] bytes = Uploads.read(file, limits, "a photograph of the print");
        ImageProbe probe = inspector.inspect(bytes);
        BufferedImage scan = ImageDecoder.decode(bytes, probe.format());
        ScanMeasurement measurement = analyser.measure(scan);

        MeasuredResponse response = measurement.response();
        CalibratedProfileStore.StoredProfile stored =
                store.save(name, response, measurement.densityRange());

        int[] lut = NegativeLut.of(stored.toProfile()).table();
        List<Integer> table = new ArrayList<>(lut.length);
        for (int value : lut) {
            table.add(value);
        }
        return new Report(stored.id(), stored.name(), stored.createdAt(),
                stored.measuredDensityRange(), measurement.maxStepBack(),
                advice(measurement, stored), measurement.patches(), table);
    }

    /** Removes a calibration. The built-ins cannot be removed. */
    public void delete(String profileId) {
        ProcessProfile profile = registry.byId(profileId);
        if (profile.mapping() != ProcessProfile.Mapping.CALIBRATED) {
            throw new IllegalArgumentException(
                    "'" + profileId + "' is built in and cannot be deleted");
        }
        store.delete(profileId);
    }

    public byte[] simulatedScan() {
        return pngEncoder.encode(SimulatedScan.of(SimulatedScan::sCurve, 1260, 420));
    }

    private static List<String> advice(ScanMeasurement measurement,
                                       CalibratedProfileStore.StoredProfile stored) {
        List<String> notes = new ArrayList<>(3);
        double range = measurement.densityRange();
        if (range >= 0.9) {
            notes.add("Strong contrast across the whole wedge.");
        } else if (range >= 0.6) {
            notes.add(String.format(Locale.ROOT,
                    "Workable contrast: %.2f of density. A heavier exposure would give the"
                            + " profile more room.", range));
        } else {
            notes.add(String.format(Locale.ROOT,
                    "Thin contrast: %.2f of density. Shadows will flatten before the profile"
                            + " can help.", range));
        }
        if (measurement.maxStepBack() > 0) {
            notes.add(String.format(Locale.ROOT,
                    "Densities wobbled by %.3f; the curve was smoothed to stay monotone.",
                    measurement.maxStepBack()));
        }
        notes.add("Profile '" + stored.id() + "' is now selectable wherever a profile is.");
        return List.copyOf(notes);
    }

    /**
     * @param patches one row per bar of the target, with the densities that were measured
     * @param lut     the 256-entry transform the new profile produces, for the UI to draw
     */
    public record Report(
            String profileId,
            String name,
            String createdAt,
            double measuredDensityRange,
            double maxStepBack,
            List<String> notes,
            List<ScanMeasurement.Patch> patches,
            List<Integer> lut) {
    }

    public record TargetFile(byte[] bytes, String contentType, String fileName, String format) {
    }
}
