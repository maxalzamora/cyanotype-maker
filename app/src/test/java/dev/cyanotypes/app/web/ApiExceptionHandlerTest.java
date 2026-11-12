package dev.cyanotypes.app.web;

import dev.cyanotypes.imaging.ImageRejectedException;
import dev.cyanotypes.imaging.UnknownProfileException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsEveryRejectionReasonToTheRightStatus() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(ImageRejectedException.Reason.EMPTY));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                statusOf(ImageRejectedException.Reason.UNSUPPORTED_FORMAT));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT,
                statusOf(ImageRejectedException.Reason.CORRUPT));
        assertEquals(HttpStatus.CONTENT_TOO_LARGE,
                statusOf(ImageRejectedException.Reason.TOO_LARGE));
    }

    @Test
    void aBodyThatIsNotMultipartIsAClientErrorNotAServerError() {
        ResponseEntity<ApiExceptionHandler.ApiError> response =
                handler.handleNotMultipart(new MultipartException("not multipart"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void theErrorBodyCarriesAMachineReadableReason() {
        ResponseEntity<ApiExceptionHandler.ApiError> response = handler.handleRejection(
                new ImageRejectedException(ImageRejectedException.Reason.TOO_LARGE, "too big"));
        assertEquals("TOO_LARGE", response.getBody().reason());
        assertEquals("too big", response.getBody().message());
    }

    @Test
    void anUnknownProfileIsAClientError() {
        ResponseEntity<ApiExceptionHandler.ApiError> response =
                handler.handleUnknownProfile(new UnknownProfileException("nope"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("UNKNOWN_PROFILE", response.getBody().reason());
    }

    private HttpStatus statusOf(ImageRejectedException.Reason reason) {
        return (HttpStatus) handler
                .handleRejection(new ImageRejectedException(reason, "test"))
                .getStatusCode();
    }
}
