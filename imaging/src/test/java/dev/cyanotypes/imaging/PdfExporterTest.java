package dev.cyanotypes.imaging;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExporterTest {

    @Test
    void writesOnePageAtTheExactPaperSize() throws IOException {
        PrintLayout layout = layout(Paper.A4, 300);
        byte[] pdf = new PdfExporter().write(raster(layout), layout);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
            PDRectangle box = document.getPage(0).getMediaBox();
            assertEquals(layout.pageWidthPt(), box.getWidth(), 0.01);
            assertEquals(layout.pageHeightPt(), box.getHeight(), 0.01);
        }
    }

    @Test
    void embedsTheNegativeAsEightBitDeviceGray() throws IOException {
        PrintLayout layout = layout(Paper.A4, 300);
        byte[] pdf = new PdfExporter().write(raster(layout), layout);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDPage page = document.getPage(0);
            boolean found = false;
            for (COSName name : page.getResources().getXObjectNames()) {
                PDXObject xobject = page.getResources().getXObject(name);
                if (xobject instanceof PDImageXObject picture) {
                    found = true;
                    assertEquals(layout.pixelWidth(), picture.getWidth());
                    assertEquals(layout.pixelHeight(), picture.getHeight());
                    assertEquals(8, picture.getBitsPerComponent());
                    assertInstanceOf(PDDeviceGray.class, picture.getColorSpace());
                }
            }
            assertTrue(found, "the page should contain one image");
        }
    }

    @Test
    void thePageIsLargerThanTheImageBecauseOfTheMargins() throws IOException {
        PrintLayout layout = layout(Paper.A4, 300);
        byte[] pdf = new PdfExporter().write(raster(layout), layout);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDRectangle box = document.getPage(0).getMediaBox();
            assertTrue(box.getWidth() > layout.drawWidthPt() + 50);
            assertTrue(box.getHeight() > layout.drawHeightPt() + 50);
        }
    }

    @Test
    void refusesARasterThatDoesNotMatchTheLayout() {
        PrintLayout layout = layout(Paper.A4, 300);
        Gray16 tooSmall = new Gray16(10, 10, new short[100]);
        assertThrows(IllegalArgumentException.class,
                () -> new PdfExporter().write(tooSmall, layout));
    }

    private static PrintLayout layout(Paper paper, int dpi) {
        return PrintLayout.fit(new PrintSpec(paper, 10.0, dpi, false), 3, 2);
    }

    private static Gray16 raster(PrintLayout layout) {
        short[] samples = new short[layout.pixelWidth() * layout.pixelHeight()];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (i % 65536);
        }
        return new Gray16(layout.pixelWidth(), layout.pixelHeight(), samples);
    }
}
