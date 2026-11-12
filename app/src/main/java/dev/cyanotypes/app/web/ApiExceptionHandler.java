package dev.cyanotypes.app.web;

import dev.cyanotypes.app.service.ExportFailedException;
import dev.cyanotypes.app.service.ProfileStoreException;
import dev.cyanotypes.imaging.CalibrationRejectedException;
import dev.cyanotypes.imaging.ImageRejectedException;
import dev.cyanotypes.imaging.UnknownFormatException;
import dev.cyanotypes.imaging.UnknownPaperException;
import dev.cyanotypes.imaging.UnknownProfileException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ImageRejectedException.class)
    public ResponseEntity<ApiError> handleRejection(ImageRejectedException e) {
        HttpStatus status = switch (e.reason()) {
            case EMPTY -> HttpStatus.BAD_REQUEST;
            case UNSUPPORTED_FORMAT -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case CORRUPT -> HttpStatus.UNPROCESSABLE_CONTENT;
            case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
        };
        return ResponseEntity.status(status)
                .body(new ApiError(e.reason().name(), e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooManyBytes(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ApiError("TOO_LARGE", "upload exceeds the configured request limit"));
    }

    @ExceptionHandler(UnknownProfileException.class)
    public ResponseEntity<ApiError> handleUnknownProfile(UnknownProfileException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("UNKNOWN_PROFILE", e.getMessage()));
    }

    @ExceptionHandler(UnknownPaperException.class)
    public ResponseEntity<ApiError> handleUnknownPaper(UnknownPaperException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("UNKNOWN_PAPER", e.getMessage()));
    }

    @ExceptionHandler(UnknownFormatException.class)
    public ResponseEntity<ApiError> handleUnknownFormat(UnknownFormatException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("UNKNOWN_FORMAT", e.getMessage()));
    }

    /**
     * Bad values for margin, dpi and the like are rejected by the records that carry them,
     * before anything is allocated. Only request parameters reach this handler.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadParameter(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(ExportFailedException.class)
    public ResponseEntity<ApiError> handleExportFailure(ExportFailedException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("EXPORT_FAILED", e.getMessage()));
    }

    /**
     * A valid image that is not a usable measurement. 422 rather than 400: the request was
     * well formed, the photograph just cannot be read as a wedge. The reason suffix tells
     * the UI which advice to show.
     */
    @ExceptionHandler(CalibrationRejectedException.class)
    public ResponseEntity<ApiError> handleCalibrationRejection(CalibrationRejectedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError("CALIBRATION_" + e.reason().name(), e.getMessage()));
    }

    @ExceptionHandler(ProfileStoreException.class)
    public ResponseEntity<ApiError> handleProfileStore(ProfileStoreException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("PROFILE_STORE_FAILED", e.getMessage()));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleNotMultipart(MultipartException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("NOT_MULTIPART", "send the image as multipart/form-data"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("MISSING_FILE", "expected a multipart part named 'file'"));
    }

    public record ApiError(String reason, String message) {
    }
}
