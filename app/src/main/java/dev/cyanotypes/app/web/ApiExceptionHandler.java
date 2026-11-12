package dev.cyanotypes.app.web;

import dev.cyanotypes.imaging.ImageRejectedException;
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
