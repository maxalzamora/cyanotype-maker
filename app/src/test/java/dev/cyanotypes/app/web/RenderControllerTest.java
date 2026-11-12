package dev.cyanotypes.app.web;

import dev.cyanotypes.app.config.CyanotypesProperties;
import dev.cyanotypes.app.service.ExportService;
import dev.cyanotypes.app.service.RenderService;
import dev.cyanotypes.imaging.ImageInspector;
import dev.cyanotypes.imaging.ImageRejectedException;
import dev.cyanotypes.imaging.NegativePipeline;
import dev.cyanotypes.imaging.NegativeRenderer;
import dev.cyanotypes.imaging.PdfExporter;
import dev.cyanotypes.imaging.PngEncoder;
import dev.cyanotypes.imaging.TiffEncoder;
import dev.cyanotypes.imaging.UnknownFormatException;
import dev.cyanotypes.imaging.UnknownPaperException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderControllerTest {

    private final RenderService renderService = new RenderService(
            new NegativePipeline(new ImageInspector(80_000_000, 20_000),
                    new NegativeRenderer(), new PngEncoder()),
            new CyanotypesProperties(
                    new CyanotypesProperties.Limits(25 * 1024 * 1024, 80_000_000, 20_000)));
    private final RenderController controller = new RenderController(
            renderService,
            new ExportService(renderService, new TiffEncoder(), new PdfExporter()));

    @Test
    void returnsAPngAndReportsTheRealSourceSizeAndProfile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", png(12, 7));

        ResponseEntity<byte[]> response = preview(file, "cyanotype");

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

        ResponseEntity<byte[]> response = preview(lying, "linear");

        assertEquals("PNG", response.getHeaders().getFirst("X-Source-Format"),
                "magic bytes, not the client's Content-Type, decide the format");
        assertEquals("linear", response.getHeaders().getFirst("X-Profile"));
    }

    @Test
    void tiffIsATagImageFileWithPrintMetadataInTheHeaders() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", png(30, 20));

        ResponseEntity<byte[]> response = controller.render(
                file, "cyanotype", "tiff", "a4", 10.0, 300, true);

        assertEquals(MediaType.parseMediaType("image/tiff"),
                response.getHeaders().getContentType());
        assertEquals("A4", response.getHeaders().getFirst("X-Paper"));
        assertEquals("true", response.getHeaders().getFirst("X-Mirrored"));
        assertEquals("300.0", response.getHeaders().getFirst("X-Effective-Ppi"));

        byte[] body = response.getBody();
        assertNotNull(body);
        assertEquals('I', body[0]);
        assertEquals('I', body[1]);
        assertEquals(42, body[2]);
        assertEquals(0, body[3]);
    }

    @Test
    void pdfStartsWithThePdfSignature() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", png(30, 20));

        ResponseEntity<byte[]> response = controller.render(
                file, "cyanotype", "pdf", "a4", 10.0, 300, false);

        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        byte[] body = response.getBody();
        assertNotNull(body);
        assertEquals("%PDF", new String(body, 0, 4));
        assertTrue(body.length > 800);
    }

    @Test
    void rejectsAnUnknownProfileBeforeDecoding() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[] {1, 2, 3});
        assertThrows(UnknownProfileException.class, () -> preview(file, "no-such-profile"));
    }

    @Test
    void rejectsAnUnknownFormatAndPaper() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", png(8, 8));

        assertThrows(UnknownFormatException.class,
                () -> controller.render(file, "cyanotype", "webp", "a4", 10.0, 300, false));
        assertThrows(UnknownPaperException.class,
                () -> controller.render(file, "cyanotype", "tiff", "scroll", 10.0, 300, false));
    }

    @Test
    void rejectsSomethingThatIsNotAnImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.png", "image/png", "<html>nope</html>".getBytes());

        ImageRejectedException e = assertThrows(ImageRejectedException.class,
                () -> preview(file, "cyanotype"));
        assertEquals(ImageRejectedException.Reason.UNSUPPORTED_FORMAT, e.reason());
    }

    @Test
    void rejectsAnEmptyUpload() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        ImageRejectedException e = assertThrows(ImageRejectedException.class,
                () -> preview(empty, "cyanotype"));
        assertEquals(ImageRejectedException.Reason.EMPTY, e.reason());
    }

    private ResponseEntity<byte[]> preview(MockMultipartFile file, String profile) {
        return controller.render(file, profile, "png", "a4", 10.0, 600, false);
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }
}
