package dev.cyanotypes.app.web;

import dev.cyanotypes.imaging.PngEncoder;
import dev.cyanotypes.imaging.SimulatedScan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * The calibration loop over real HTTP wiring: Spring's multipart binding, Jackson, the
 * profile store and the exception handler, rather than the classes in isolation.
 */
@SpringBootTest
class CalibrationApiTest {

    private static final Path PROFILES = temporaryDirectory();
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @DynamicPropertySource
    static void profileDirectory(DynamicPropertyRegistry registry) {
        registry.add("cyanotypes.calibration.directory", PROFILES::toString);
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void aTargetAndASimulatedScanProduceAProfileThatTheApiThenOffers() throws Exception {
        MockHttpServletResponse target = mvc.perform(get("/api/calibration/target")
                        .param("format", "pdf"))
                .andReturn().getResponse();
        assertEquals(200, target.getStatus());
        assertEquals("application/pdf", target.getContentType());
        assertEquals("21", target.getHeader("X-Calibration-Patches"));
        assertEquals("%PDF", target.getContentAsString().substring(0, 4));

        byte[] scan = mvc.perform(get("/api/calibration/simulated-scan.png"))
                .andReturn().getResponse().getContentAsByteArray();
        MvcResult calibrated = mvc.perform(multipart("/api/calibration")
                        .file(new MockMultipartFile("file", "scan.png", "image/png", scan))
                        .param("name", "HTTP loop"))
                .andReturn();
        assertEquals(200, calibrated.getResponse().getStatus(),
                calibrated.getResponse().getContentAsString());

        JsonNode report = MAPPER.readTree(calibrated.getResponse().getContentAsString());
        String id = report.get("profileId").asString();
        assertTrue(id.startsWith("cal-"), id);
        assertEquals("HTTP loop", report.get("name").asString());
        assertEquals(21, report.get("patches").size());
        assertEquals(256, report.get("lut").size());
        assertTrue(report.get("notes").size() >= 2);
        assertTrue(Files.exists(PROFILES.resolve(id + ".json")));

        String profiles = mvc.perform(get("/api/profiles"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(profiles.contains(id), profiles);

        MockHttpServletResponse negative = mvc.perform(multipart("/api/render")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", png()))
                        .param("profile", id)
                        .param("format", "tiff"))
                .andReturn().getResponse();
        assertEquals(200, negative.getStatus());
        assertEquals(id, negative.getHeader("X-Profile"));
        assertEquals("image/tiff", negative.getContentType());
    }

    @Test
    void anUnreadableScanComesBackAsAReasonNotAStackTrace() throws Exception {
        BufferedImage flat = SimulatedScan.of(value -> 0.5, 420, 140);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(flat, "png", out);

        MvcResult result = mvc.perform(multipart("/api/calibration")
                        .file(new MockMultipartFile("file", "flat.png", "image/png",
                                out.toByteArray())))
                .andReturn();

        assertEquals(422, result.getResponse().getStatus());
        JsonNode error = MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("CALIBRATION_NO_CONTRAST", error.get("reason").asString());
        assertTrue(error.get("message").asString().contains("density"));
    }

    @Test
    void aCalibrationCanBeRemovedButTheBuiltInsCannot() throws Exception {
        byte[] scan = mvc.perform(get("/api/calibration/simulated-scan.png"))
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode report = MAPPER.readTree(mvc.perform(multipart("/api/calibration")
                        .file(new MockMultipartFile("file", "scan.png", "image/png", scan)))
                .andReturn().getResponse().getContentAsString());
        String id = report.get("profileId").asString();

        assertEquals(204, mvc.perform(delete("/api/calibration/" + id))
                .andReturn().getResponse().getStatus());

        String profiles = mvc.perform(get("/api/profiles"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(!profiles.contains(id), profiles);

        MvcResult refused = mvc.perform(delete("/api/calibration/cyanotype")).andReturn();
        assertEquals(400, refused.getResponse().getStatus());
        assertTrue(refused.getResponse().getContentAsString().contains("built in"));
    }

    @Test
    void theTargetPreviewIsAnImageTheBrowserCanShow() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/calibration/target")
                        .param("format", "png"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("image/png", response.getContentType());
        assertTrue(response.getContentAsByteArray().length > 1000);
        assertEquals("inline; filename=\"calibration-target.png\"",
                response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
    }

    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
        return new PngEncoder().encode(image);
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("cyanotypes-api-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
