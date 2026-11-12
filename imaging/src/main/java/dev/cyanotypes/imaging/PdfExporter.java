package dev.cyanotypes.imaging;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Puts the negative on a page of the right physical size.
 *
 * <p>A TIFF is a picture; a PDF is an instruction sheet. This one says "A4, this image,
 * centred, this big, 10 mm margins", so the printer has nothing left to decide. That
 * matters for a contact print, where a 2% scale error is a 2% scale error in the final
 * image.
 *
 * <p>PDF carries 8-bit images comfortably and 16-bit ones unevenly across viewers, so the
 * raster is reduced here. The TIFF remains the archival 16-bit file.
 */
public final class PdfExporter {

    public byte[] write(Gray16 image, PrintLayout layout) throws IOException {
        if (image.width() != layout.pixelWidth() || image.height() != layout.pixelHeight()) {
            throw new IllegalArgumentException(
                    "image is " + image.width() + "x" + image.height()
                            + " but the layout expects "
                            + layout.pixelWidth() + "x" + layout.pixelHeight());
        }

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(new PDRectangle(
                    (float) layout.pageWidthPt(), (float) layout.pageHeightPt()));
            document.addPage(page);

            PDImageXObject picture = LosslessFactory.createFromImage(document, image.toByteGray());

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(picture,
                        (float) layout.drawX(),
                        (float) layout.drawY(),
                        (float) layout.drawWidthPt(),
                        (float) layout.drawHeightPt());
            }

            document.save(out);
            return out.toByteArray();
        }
    }
}
