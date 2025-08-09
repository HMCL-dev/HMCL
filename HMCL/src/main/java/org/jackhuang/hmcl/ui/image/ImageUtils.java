/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.image;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.ui.image.apng.argb8888.*;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.reader.DefaultPngChunkReader;
import org.jackhuang.hmcl.ui.image.apng.reader.PngReadHelper;
import org.jackhuang.hmcl.util.SwingFXUtils;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Glavo
 */
public final class ImageUtils {

    // ImageLoaders

    public static final ImageLoader DEFAULT = (input, requestedWidth, requestedHeight, preserveRatio, smooth) -> {
        Image image = new Image(input,
                requestedWidth, requestedHeight,
                preserveRatio, smooth);
        if (image.isError())
            throw image.getException();
        return image;
    };

    public static final ImageLoader WEBP = (input, requestedWidth, requestedHeight, preserveRatio, smooth) -> {
        WebPImageReaderSpi spi = new WebPImageReaderSpi();
        ImageReader reader = spi.createReaderInstance(null);
        BufferedImage bufferedImage;
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            reader.setInput(imageInput, true, true);
            bufferedImage = reader.read(0, reader.getDefaultReadParam());
        } finally {
            reader.dispose();
        }
        return SwingFXUtils.toFXImage(bufferedImage, requestedWidth, requestedHeight, preserveRatio, smooth);
    };

    public static final ImageLoader APNG = (input, requestedWidth, requestedHeight, preserveRatio, smooth) -> {
        if (!"true".equals(System.getProperty("hmcl.experimental.apng", "true")))
            return DEFAULT.load(input, requestedWidth, requestedHeight, preserveRatio, smooth);

        try {
            return PngReadHelper.read(input, new DefaultPngChunkReader<>(
                    new Argb8888Processor<>(
                            new BgraPreBitmapDirector(
                                    requestedWidth, requestedHeight, preserveRatio, smooth))));
        } catch (PngException e) {
            throw new IOException(e);
        }
    };

    public static final Map<String, ImageLoader> EXT_TO_LOADER = Map.of(
            "webp", WEBP,
            "apng", APNG
    );

    public static final Map<String, ImageLoader> CONTENT_TYPE_TO_LOADER = Map.of(
            "image/webp", WEBP,
            "image/apng", APNG
    );

    public static final Set<String> DEFAULT_EXTS = Set.of(
            "jpg", "jpeg", "bmp", "gif"
    );

    public static final Set<String> DEFAULT_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/bmp", "image/gif"
    );

    // ------

    public static final int HEADER_BUFFER_SIZE = 1024;

    private static final byte[] RIFF_HEADER = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_HEADER = {'W', 'E', 'B', 'P'};

    public static boolean isWebP(byte[] headerBuffer) {
        return headerBuffer.length > 12
                && Arrays.equals(headerBuffer, 0, 4, RIFF_HEADER, 0, 4)
                && Arrays.equals(headerBuffer, 8, 12, WEBP_HEADER, 0, 4);
    }

    private static final byte[] PNG_HEADER = {
            (byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47,
            (byte) 0x0d, (byte) 0x0a, (byte) 0x1a, (byte) 0x0a,
    };

    private static final class PngChunkHeader {
        private static final int IDAT_HEADER = 0x49444154;
        private static final int acTL_HEADER = 0x6163544c;

        private final int length;
        private final int chunkType;

        private PngChunkHeader(int length, int chunkType) {
            this.length = length;
            this.chunkType = chunkType;
        }

        private static @Nullable PngChunkHeader readHeader(ByteBuffer headerBuffer) {
            if (headerBuffer.remaining() < 8)
                return null;

            int length = headerBuffer.getInt();
            int chunkType = headerBuffer.getInt();

            return new PngChunkHeader(length, chunkType);
        }
    }

    public static boolean isApng(byte[] headerBuffer) {
        if (headerBuffer.length <= 20)
            return false;

        if (!Arrays.equals(
                headerBuffer, 0, 8,
                PNG_HEADER, 0, 8))
            return false;


        ByteBuffer buffer = ByteBuffer.wrap(headerBuffer, 8, headerBuffer.length - 8);

        PngChunkHeader header;
        while ((header = PngChunkHeader.readHeader(buffer)) != null) {
            // https://wiki.mozilla.org/APNG_Specification#Structure
            // To be recognized as an APNG, an `acTL` chunk must appear in the stream before any `IDAT` chunks.
            // The `acTL` structure is described below.
            if (header.chunkType == PngChunkHeader.IDAT_HEADER)
                break;

            if (header.chunkType == PngChunkHeader.acTL_HEADER)
                return true;

            final int numBytes = header.length + 4;

            if (buffer.remaining() > numBytes)
                buffer.position(buffer.position() + numBytes);
            else
                break;
        }

        return false;
    }

    public static @Nullable ImageLoader guessLoader(byte[] headerBuffer) {
        if (isWebP(headerBuffer))
            return WEBP;
        if (isApng(headerBuffer))
            return APNG;
        return null;
    }

    public static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("^\\s(?<type>image/[\\w-])");

    // APNG

    private static int[] scale(int[] pixels,
                               int sourceWidth, int sourceHeight,
                               int targetWidth, int targetHeight) {
        assert pixels.length == sourceWidth * sourceHeight;

        double xScale = ((double) sourceWidth) / targetWidth;
        double yScale = ((double) sourceHeight) / targetHeight;

        int[] result = new int[targetWidth * targetHeight];

        for (int row = 0; row < targetHeight; row++) {
            for (int col = 0; col < targetWidth; col++) {
                int sourceX = (int) (col * xScale);
                int sourceY = (int) (row * yScale);
                int color = pixels[sourceY * sourceWidth + sourceX];

                result[row * targetWidth + col] = color;
            }
        }

        return result;
    }

    private ImageUtils() {
    }
}
