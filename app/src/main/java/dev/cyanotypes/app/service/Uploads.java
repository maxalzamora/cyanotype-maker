package dev.cyanotypes.app.service;

import dev.cyanotypes.app.config.CyanotypesProperties;
import dev.cyanotypes.imaging.ImageRejectedException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** One place where an upload becomes bytes, with the two limits every upload needs. */
final class Uploads {

    private Uploads() {
    }

    /**
     * @param what what the caller expects the file to be, for the error message
     */
    static byte[] read(MultipartFile file, CyanotypesProperties.Limits limits, String what) {
        if (file == null || file.isEmpty()) {
            throw new ImageRejectedException(
                    ImageRejectedException.Reason.EMPTY, "choose " + what + " to upload");
        }
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
        return bytes;
    }
}
