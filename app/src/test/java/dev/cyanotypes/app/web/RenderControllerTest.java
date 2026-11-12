package dev.cyanotypes.app.web;

import dev.cyanotypes.app.config.CyanotypesProperties;
import dev.cyanotypes.app.service.RenderService;
import dev.cyanotypes.imaging.ImageInspector;
import dev.cyanotypes.imaging.ImageRejectedException;
import dev.cyanotypes.imaging.NegativePipeline;
import dev.cyanotypes.imaging.NegativeRenderer;
import dev.cyanotypes.imaging.PngEncoder;
import dev.cyanotypes.imaging.UnknownProfileException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenderControllerTest {

    private final RenderController controller = new RenderController(new RenderService(
            new NegativePipeline(new ImageInspector(80_000_000, 20_000),
                    new NegativeRenderer(), new PngEncoder()),
            new CyanotypesProperties(
                    new CyanotypesProperties.Limits(25 * 1024 * 1024, 80_000_000, 20_000))));

    @Test
    void returnsAPngAndReportsTheRealSourceSizeAndProfile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", png(12, 7));

        ResponseEntity<byte[]> response = controller.render(file, "cyanotype");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertEquals("PNG", response.getHeaders().getFirst("X-Source-Format"));
        assertEquals("12", response.getHeaders().getFirst("X-Source-Width"));
        assertEquals("7", response.getHeaders().getFirst("X-Source-Height"));
        assertEquals("cyanotype", response.getHeaders().getFirst("X-Profile"));
        byte[] body = response.getBody();
        assertNotNull(body);
        assertEquals((byte) 0x89, body[0]);
        assertEquals('P', body[1]);
    }

    @Test
    void ignoresTheDeclaredContentTypeAndTrustsTheBytes() throws IOException {
        MockMultipartFile lying = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", png(4, 4));

        ResponseEntity<byte[]> response = controller.render(lying, "linear");

        assertEquals("PNG", response.getHeaders().getFirst("X-Source-Format"),
                "magic bytes, not the client's Content-Type, decide the format");
        assertEquals("linear", response.getHeaders().getFirst("X-Profile"));
    }

    @Test
    void rejectsAnUnknownProfileBeforeDecoding() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[] {1, 2, 3});
        assertThrows(UnknownProfileException.class, () -> controller.render(file, "no-such-profile"));
    }

    @Test
    void rejectsSomethingThatIsNotAnImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.png", "image/png", "<html>nope</html>".getBytes());

        ImageRejectedException e = assertThrows(ImageRejectedException.class,
                () -> controller.render(file, "cyanotype"));
        assertEquals(ImageRejectedException.Reason.UNSUPPORTED_FORMAT, e.reason());
    }

    @Test
    void rejectsAnEmptyUpload() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        ImageRejectedException e = assertThrows(ImageRejectedException.class,
                () -> controller.render(empty, "cyanotype"));
        assertEquals(ImageRejectedException.Reason.EMPTY, e.reason());
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }
}
