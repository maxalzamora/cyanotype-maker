package dev.cyanotypes.imaging;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageInspectorTest {

    private final ImageInspector inspector = new ImageInspector(80_000_000, 20_000);

    @Test
    void readsDimensionsWithoutDecoding() {
        ImageProbe probe = inspector.inspect(ImageFixtures.png(37, 19));
        assertEquals(ImageFormat.PNG, probe.format());
        assertEquals(37, probe.width());
        assertEquals(19, probe.height());
    }

    @Test
    void rejectsEmptyUpload() {
        ImageRejectedException e = assertThrows(ImageRejectedException.class,
                () -> inspector.inspect(new byte[0]));
        assertEquals(ImageRejectedException.Reason.EMPTY, e.reason());
    }

    @Test
    void rejectsNonImageBytesEvenWithAnImageContentType() {
        ImageRejectedException e = assertThrows(ImageRejectedException.class,
                () -> inspector.inspect("<html>hello</html>".getBytes()));
        assertEquals(ImageRejectedException.Reason.UNSUPPORTED_FORMAT, e.reason());
    }

    @Test
    void rejectsDecompressionBombBeforeDecoding() {
        // ~40 bytes on disk, 30000x30000 (900 MP, ~2.7 GB decoded) in the header.
        byte[] bomb = ImageFixtures.pngClaimingDimensions(30_000, 30_000);
        ImageRejectedException e = assertThrows(ImageRejectedException.class,
                () -> inspector.inspect(bomb));
        assertEquals(ImageRejectedException.Reason.TOO_LARGE, e.reason());
    }

    @Test
    void rejectsImagesOverTheTotalPixelBudget() {
        byte[] wide = ImageFixtures.pngClaimingDimensions(10_000, 10_000);
        ImageRejectedException e = assertThrows(ImageRejectedException.class,
                () -> inspector.inspect(wide));
        assertEquals(ImageRejectedException.Reason.TOO_LARGE, e.reason());
    }

    @Test
    void rejectsTruncatedFilesAsCorrupt() {
        byte[] truncated = Arrays.copyOf(ImageFixtures.png(8, 8), 12);
        ImageRejectedException e = assertThrows(ImageRejectedException.class,
                () -> inspector.inspect(truncated));
        assertEquals(ImageRejectedException.Reason.CORRUPT, e.reason());
    }
}
