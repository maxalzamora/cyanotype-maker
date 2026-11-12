package dev.cyanotypes.imaging;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Validates an upload and reads its dimensions <em>without</em> allocating pixel buffers.
 *
 * <p>This is the defence against decompression bombs: a 40 KB PNG can declare
 * 20000&times;20000 pixels, which is 1.6 GB once decoded. {@link ImageReader#getWidth(int)}
 * only parses the header, so we can refuse the file before paying for it.
 */
public final class ImageInspector {

    static {
        // Keep ImageIO's header reads in memory. Callers already cap upload size.
        ImageIO.setUseCache(false);
    }

    private final int maxPixels;
    private final int maxDimension;

    public ImageInspector(int maxPixels, int maxDimension) {
        if (maxPixels <= 0 || maxDimension <= 0) {
            throw new IllegalArgumentException("limits must be positive");
        }
        this.maxPixels = maxPixels;
        this.maxDimension = maxDimension;
    }

    public ImageProbe inspect(byte[] data) {
        if (data == null || data.length == 0) {
            throw new ImageRejectedException(ImageRejectedException.Reason.EMPTY, "upload is empty");
        }

        ImageFormat format = ImageFormat.sniff(data);
        if (format == ImageFormat.UNKNOWN) {
            throw new ImageRejectedException(
                    ImageRejectedException.Reason.UNSUPPORTED_FORMAT,
                    "not a PNG, JPEG, TIFF or GIF");
        }

        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (stream == null) {
                throw corrupt("could not open image stream");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new ImageRejectedException(
                        ImageRejectedException.Reason.UNSUPPORTED_FORMAT,
                        "no decoder available for " + format);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw corrupt("image reports empty dimensions");
                }
                if (width > maxDimension || height > maxDimension) {
                    throw tooLarge(width, height);
                }
                if ((long) width * height > maxPixels) {
                    throw tooLarge(width, height);
                }
                return new ImageProbe(format, width, height);
            } finally {
                reader.dispose();
            }
        } catch (ImageRejectedException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw corrupt("could not read image header: " + e.getMessage());
        }
    }

    private ImageRejectedException tooLarge(int width, int height) {
        return new ImageRejectedException(
                ImageRejectedException.Reason.TOO_LARGE,
                "image is " + width + "x" + height + "; limit is "
                        + maxDimension + "px per side and " + maxPixels + " pixels total");
    }

    private static ImageRejectedException corrupt(String message) {
        return new ImageRejectedException(ImageRejectedException.Reason.CORRUPT, message);
    }
}
