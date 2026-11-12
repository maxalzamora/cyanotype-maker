package dev.cyanotypes.app.service;

import dev.cyanotypes.app.config.CyanotypesProperties;
import dev.cyanotypes.imaging.ImageRejectedException;
import dev.cyanotypes.imaging.NegativePipeline;
import dev.cyanotypes.imaging.ProcessProfile;
import dev.cyanotypes.imaging.RenderedNegative;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class RenderService {

    private final NegativePipeline pipeline;
    private final CyanotypesProperties.Limits limits;

    public RenderService(NegativePipeline pipeline, CyanotypesProperties properties) {
        this.pipeline = pipeline;
        this.limits = properties.limits();
    }

    public RenderedNegative render(MultipartFile file, String profileId) {
        if (file == null || file.isEmpty()) {
            throw new ImageRejectedException(
                    ImageRejectedException.Reason.EMPTY, "choose an image to upload");
        }
        // Resolve before touching the bytes: an unknown profile is the caller's mistake
        // and should not cost a decode.
        ProcessProfile profile = ProcessProfile.byId(profileId);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ImageRejectedException(
                    ImageRejectedException.Reason.CORRUPT, "could not read the upload", e);
        }
        if (bytes.length > limits.maxUploadBytes()) {
            throw new ImageRejectedException(
                    ImageRejectedException.Reason.TOO_LARGE,
                    "upload is " + bytes.length + " bytes; limit is " + limits.maxUploadBytes());
        }
        return pipeline.render(bytes, profile);
    }
}
