package dev.cyanotypes.app.service;

import dev.cyanotypes.app.config.CyanotypesProperties;
import dev.cyanotypes.imaging.NegativePipeline;
import dev.cyanotypes.imaging.ProcessProfile;
import dev.cyanotypes.imaging.RenderedNegative;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RenderService {

    private final NegativePipeline pipeline;
    private final ProfileRegistry profiles;
    private final CyanotypesProperties.Limits limits;

    public RenderService(NegativePipeline pipeline, ProfileRegistry profiles,
                         CyanotypesProperties properties) {
        this.pipeline = pipeline;
        this.profiles = profiles;
        this.limits = properties.limits();
    }

    public RenderedNegative render(MultipartFile file, String profileId) {
        // Resolve before touching the bytes: an unknown profile is the caller's mistake
        // and should not cost a decode.
        ProcessProfile profile = profiles.byId(profileId);
        byte[] bytes = Uploads.read(file, limits, "an image");
        return pipeline.render(bytes, profile);
    }
}
