package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NegativePipelineTest {

    private final NegativePipeline pipeline = new NegativePipeline(
            new ImageInspector(80_000_000, 20_000),
            new NegativeRenderer(),
            new PngEncoder());

    @Test
    void rendersAValidPngEndToEnd() {
        RenderedNegative result = pipeline.render(ImageFixtures.png(3, 2), ProcessProfile.linear());

        assertEquals(ImageFormat.PNG, result.source().format());
        assertEquals(3, result.source().width());
        assertEquals("linear", result.profile().id());
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'},
                new byte[] {result.encoded()[0], result.encoded()[1],
                        result.encoded()[2], result.encoded()[3]});
        assertEquals(255, result.image().getRaster().getSample(0, 0, 0));
    }

    @Test
    void refusesGarbageWithoutThrowingSomethingUnexpected() {
        assertThrows(ImageRejectedException.class,
                () -> pipeline.render("not an image".getBytes(), ProcessProfile.linear()));
    }
}
