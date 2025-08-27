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
import javafx.animation.Timeline;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.jackhuang.hmcl.ui.image.apng.Png;
import org.jackhuang.hmcl.ui.image.apng.argb8888.Argb8888Bitmap;
import org.jackhuang.hmcl.ui.image.apng.argb8888.Argb8888BitmapSequence;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngAnimationControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngFrameControl;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.error.PngIntegrityException;
import org.jackhuang.hmcl.ui.image.internal.AnimationImageImpl;
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

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

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
            var sequence = Png.readArgb8888BitmapSequence(input);

            final int width = sequence.header.width;
            final int height = sequence.header.height;

            boolean doScale;
            if (requestedWidth > 0 && requestedHeight > 0
                    && (requestedWidth != width || requestedHeight != height)) {
                doScale = true;

                if (preserveRatio) {
                    double scaleX = (double) requestedWidth / width;
                    double scaleY = (double) requestedHeight / height;
                    double scale = Math.min(scaleX, scaleY);

                    requestedWidth = (int) (width * scale);
                    requestedHeight = (int) (height * scale);
                }
            } else {
                doScale = false;
            }

            if (sequence.isAnimated()) {
                try {
                    return toImage(sequence, doScale, requestedWidth, requestedHeight);
                } catch (Throwable e) {
                    LOG.warning("Failed to load animated image", e);
                }
            }

            Argb8888Bitmap defaultImage = sequence.defaultImage;
            int targetWidth;
            int targetHeight;
            int[] pixels;
            if (doScale) {
                targetWidth = requestedWidth;
                targetHeight = requestedHeight;
                pixels = scale(defaultImage.array,
                        defaultImage.width, defaultImage.height,
                        targetWidth, targetHeight);
            } else {
                targetWidth = defaultImage.width;
                targetHeight = defaultImage.height;
                pixels = defaultImage.array;
            }

            WritableImage image = new WritableImage(targetWidth, targetHeight);
            image.getPixelWriter().setPixels(0, 0, targetWidth, targetHeight,
                    PixelFormat.getIntArgbInstance(), pixels,
                    0, targetWidth);
            return image;
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

    private static Image toImage(Argb8888BitmapSequence sequence,
                                 boolean doScale,
                                 int targetWidth, int targetHeight) throws PngException {
        final int width = sequence.header.width;
        final int height = sequence.header.height;

        List<Argb8888BitmapSequence.Frame> frames = sequence.getAnimationFrames();

        var framePixels = new int[frames.size()][];
        var durations = new int[framePixels.length];

        int[] buffer = new int[Math.multiplyExact(width, height)];
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            var frame = frames.get(frameIndex);
            PngFrameControl control = frame.control;

            if (frameIndex == 0 && (
                    control.xOffset != 0 || control.yOffset != 0
                            || control.width != width || control.height != height)) {
                throw new PngIntegrityException("Invalid first frame: " + control);
            }

            if (control.xOffset < 0 || control.yOffset < 0
                    || width < 0 || height < 0
                    || control.xOffset + control.width > width
                    || control.yOffset + control.height > height
                    || control.delayNumerator < 0 || control.delayDenominator < 0
            ) {
                throw new PngIntegrityException("Invalid frame control: " + control);
            }

            int[] currentFrameBuffer = buffer.clone();
            if (control.blendOp == 0) {
                for (int row = 0; row < control.height; row++) {
                    System.arraycopy(frame.bitmap.array,
                            row * control.width,
                            currentFrameBuffer,
                            (control.yOffset + row) * width + control.xOffset,
                            control.width);
                }
            } else if (control.blendOp == 1) {
                // APNG_BLEND_OP_OVER - Alpha blending
                for (int row = 0; row < control.height; row++) {
                    for (int col = 0; col < control.width; col++) {
                        int srcIndex = row * control.width + col;
                        int dstIndex = (control.yOffset + row) * width + control.xOffset + col;

                        int srcPixel = frame.bitmap.array[srcIndex];
                        int dstPixel = currentFrameBuffer[dstIndex];

                        int srcAlpha = (srcPixel >>> 24) & 0xFF;
                        if (srcAlpha == 0) {
                            continue;
                        } else if (srcAlpha == 255) {
                            currentFrameBuffer[dstIndex] = srcPixel;
                        } else {
                            int srcR = (srcPixel >>> 16) & 0xFF;
                            int srcG = (srcPixel >>> 8) & 0xFF;
                            int srcB = srcPixel & 0xFF;

                            int dstAlpha = (dstPixel >>> 24) & 0xFF;
                            int dstR = (dstPixel >>> 16) & 0xFF;
                            int dstG = (dstPixel >>> 8) & 0xFF;
                            int dstB = dstPixel & 0xFF;

                            int invSrcAlpha = 255 - srcAlpha;

                            int outAlpha = srcAlpha + (dstAlpha * invSrcAlpha + 127) / 255;
                            int outR, outG, outB;

                            if (outAlpha == 0) {
                                outR = outG = outB = 0;
                            } else {
                                outR = (srcR * srcAlpha + dstR * dstAlpha * invSrcAlpha / 255 + outAlpha / 2) / outAlpha;
                                outG = (srcG * srcAlpha + dstG * dstAlpha * invSrcAlpha / 255 + outAlpha / 2) / outAlpha;
                                outB = (srcB * srcAlpha + dstB * dstAlpha * invSrcAlpha / 255 + outAlpha / 2) / outAlpha;
                            }

                            outAlpha = Math.min(outAlpha, 255);
                            outR = Math.min(outR, 255);
                            outG = Math.min(outG, 255);
                            outB = Math.min(outB, 255);

                            currentFrameBuffer[dstIndex] = (outAlpha << 24) | (outR << 16) | (outG << 8) | outB;
                        }
                    }
                }
            } else {
                throw new PngIntegrityException("Unsupported blendOp " + control.blendOp + " at frame " + frameIndex);
            }

            if (doScale)
                framePixels[frameIndex] = scale(currentFrameBuffer,
                        width, height,
                        targetWidth, targetHeight);
            else
                framePixels[frameIndex] = currentFrameBuffer;

            if (control.delayNumerator == 0) {
                durations[frameIndex] = 10;
            } else {
                int durationsMills = 1000 * control.delayNumerator;
                if (control.delayDenominator == 0)
                    durationsMills /= 100;
                else
                    durationsMills /= control.delayDenominator;

                durations[frameIndex] = durationsMills;
            }

            switch (control.disposeOp) {
                case 0:  // APNG_DISPOST_OP_NONE
                    System.arraycopy(currentFrameBuffer, 0, buffer, 0, currentFrameBuffer.length);
                    break;
                case 1: // APNG_DISPOSE_OP_BACKGROUND
                    for (int row = 0; row < control.height; row++) {
                        int fromIndex = (control.yOffset + row) * width + control.xOffset;
                        Arrays.fill(buffer, fromIndex, fromIndex + control.width, 0);
                    }
                    break;
                case 2: // APNG_DISPOSE_OP_PREVIOUS
                    // Do nothing, keep the previous frame.
                    break;
                default:
                    throw new PngIntegrityException("Unsupported disposeOp " + control.disposeOp + " at frame " + frameIndex);
            }
        }

        PngAnimationControl animationControl = sequence.getAnimationControl();
        int cycleCount;
        if (animationControl != null) {
            cycleCount = animationControl.numPlays;
            if (cycleCount == 0)
                cycleCount = Timeline.INDEFINITE;
        } else {
            cycleCount = Timeline.INDEFINITE;
        }

        if (doScale)
            return new AnimationImageImpl(targetWidth, targetHeight, framePixels, durations, cycleCount);
        else
            return new AnimationImageImpl(width, height, framePixels, durations, cycleCount);
    }

    private ImageUtils() {
    }
}
