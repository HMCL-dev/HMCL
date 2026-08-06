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

import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.girod.javafx.svgimage.LoaderParameters;
import org.girod.javafx.svgimage.SVGImage;
import org.girod.javafx.svgimage.SVGLoader;
import org.girod.javafx.svgimage.ScaleQuality;
import org.glavo.webp.WebPImage;
import org.glavo.webp.WebPImageLoadOptions;
import org.glavo.webp.javafx.WebPFXImage;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.image.apng.Png;
import org.jackhuang.hmcl.ui.image.apng.argb8888.Argb8888Bitmap;
import org.jackhuang.hmcl.ui.image.apng.argb8888.Argb8888BitmapSequence;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngAnimationControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngFrameControl;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.error.PngIntegrityException;
import org.jackhuang.hmcl.ui.image.internal.AnimationImageImpl;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Provides image-format detection and loaders for image formats not handled
/// directly by JavaFX.
///
/// @author Glavo
@NotNullByDefault
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
        var options = new WebPImageLoadOptions(requestedWidth, requestedHeight, preserveRatio, smooth);
        return new WebPFXImage(WebPImage.read(input, options));
    };

    public static final ImageLoader SVG = (input, requestedWidth, requestedHeight, preserveRatio, smooth) -> {
        String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);

        LoaderParameters parameters = new LoaderParameters();
        parameters.autoStartAnimations = false;

        @Nullable SVGImage image;

        if (Platform.isFxApplicationThread()) {
            image = SVGLoader.load(content, parameters);
        } else {
            // TODO: Currently, SVGLoader.load(...) requires the javafx.swing module if it operates on a non-JavaFX thread.
            image = CompletableFuture.supplyAsync(
                    () -> SVGLoader.load(content, parameters),
                    Schedulers.javafx()
            ).get();
        }

        if (image == null)
            throw new IOException("Failed to load SVG image");

        var snapshotParameters = new SnapshotParameters();
        snapshotParameters.setFill(Color.TRANSPARENT);

        if (requestedWidth <= 0. || requestedHeight <= 0.) {
            return image.toImage(snapshotParameters);
        }

        double scaleX = requestedWidth / image.getScaledWidth();
        double scaleY = requestedHeight / image.getScaledHeight();

        if (preserveRatio || scaleX == scaleY) {
            double scale = Math.min(scaleX, scaleY);
            return image.scale(scale).toImage(snapshotParameters);
        } else {
            // FIXME: Use DEFAULT_SVG_SNAPSHOT_PARAMS
            return image.toImageScaled(ScaleQuality.RENDER_QUALITY, scaleX, scaleY);
        }
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
                pixels = scale(defaultImage.array(),
                        defaultImage.width(), defaultImage.height(),
                        targetWidth, targetHeight);
            } else {
                targetWidth = defaultImage.width();
                targetHeight = defaultImage.height();
                pixels = defaultImage.array();
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
            "svg", SVG,
            "apng", APNG
    );

    public static final Map<String, ImageLoader> CONTENT_TYPE_TO_LOADER = Map.of(
            "image/webp", WEBP,
            "image/svg+xml", SVG,
            "image/apng", APNG
    );

    public static final Set<String> DEFAULT_EXTS = Set.of(
            "jpg", "jpeg", "bmp", "gif"
    );

    public static final Set<String> DEFAULT_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/bmp", "image/gif"
    );

    // ------

    /// Maximum number of leading bytes inspected when selecting an image loader.
    public static final int HEADER_BUFFER_SIZE = 1024;

    private static final byte[] RIFF_HEADER = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_HEADER = {'W', 'E', 'B', 'P'};

    public static boolean isWebP(byte[] headerBuffer) {
        return headerBuffer.length > 12
                && Arrays.equals(headerBuffer, 0, 4, RIFF_HEADER, 0, 4)
                && Arrays.equals(headerBuffer, 8, 12, WEBP_HEADER, 0, 4);
    }

    /// ASCII local name of an SVG document element.
    private static final String SVG_ELEMENT_NAME = "svg";

    /// ASCII prefix of an XML comment.
    private static final String XML_COMMENT_START = "<!--";

    /// ASCII suffix of an XML comment.
    private static final String XML_COMMENT_END = "-->";

    /// ASCII prefix of an XML processing instruction.
    private static final String XML_PROCESSING_INSTRUCTION_START = "<?";

    /// ASCII suffix of an XML processing instruction.
    private static final String XML_PROCESSING_INSTRUCTION_END = "?>";

    /// Case-sensitive ASCII prefix of an XML document type declaration.
    private static final String XML_DOCTYPE_START = "<!DOCTYPE";

    /// Returns whether the supplied prefix identifies an SVG XML document.
    ///
    /// An optional UTF-8 byte-order mark, XML whitespace, comments, processing
    /// instructions, and a document type declaration may precede the document
    /// element. The method identifies the document element only; it does not
    /// validate the remainder of the XML document. XML markup must use its
    /// ASCII byte representation; UTF-16 and UTF-32 prefixes are not recognized.
    ///
    /// @param headerBuffer leading bytes of the candidate image
    /// @return whether the first XML document element is `svg`
    public static boolean isSVG(byte[] headerBuffer) {
        int offset = hasUtf8ByteOrderMark(headerBuffer) ? 3 : 0;

        while (true) {
            offset = skipXmlWhitespace(headerBuffer, offset);

            if (isSvgElementStart(headerBuffer, offset))
                return true;

            if (matchesAscii(headerBuffer, offset, XML_COMMENT_START)) {
                offset = indexAfterAscii(
                        headerBuffer,
                        offset + XML_COMMENT_START.length(),
                        XML_COMMENT_END
                );
            } else if (matchesAscii(headerBuffer, offset, XML_PROCESSING_INSTRUCTION_START)) {
                offset = indexAfterAscii(
                        headerBuffer,
                        offset + XML_PROCESSING_INSTRUCTION_START.length(),
                        XML_PROCESSING_INSTRUCTION_END
                );
            } else if (isDoctypeStart(headerBuffer, offset)) {
                offset = skipDoctype(headerBuffer, offset);
            } else {
                return false;
            }

            if (offset < 0)
                return false;
        }
    }

    /// Returns whether the byte array starts with a UTF-8 byte-order mark.
    ///
    /// @param data bytes to inspect
    /// @return whether `data` begins with `EF BB BF`
    private static boolean hasUtf8ByteOrderMark(byte[] data) {
        return svgScanLimit(data) >= 3
                && data[0] == (byte) 0xef
                && data[1] == (byte) 0xbb
                && data[2] == (byte) 0xbf;
    }

    /// Returns the bounded length available to SVG detection.
    ///
    /// @param data source bytes
    /// @return the lesser of the array length and [#HEADER_BUFFER_SIZE]
    private static int svgScanLimit(byte[] data) {
        return Math.min(data.length, HEADER_BUFFER_SIZE);
    }

    /// Skips XML whitespace from an array offset.
    ///
    /// @param data  source bytes
    /// @param start inclusive offset at which to begin
    /// @return the first offset that does not contain XML whitespace
    private static int skipXmlWhitespace(byte[] data, int start) {
        int limit = svgScanLimit(data);
        while (start < limit && isXmlWhitespace(data[start]))
            start++;
        return start;
    }

    /// Returns whether an SVG document-element qualified name begins at an array offset.
    ///
    /// @param data  source bytes
    /// @param start offset of a possible element opening delimiter
    /// @return whether the unprefixed or prefixed element has the local name `svg`
    private static boolean isSvgElementStart(byte[] data, int start) {
        int limit = svgScanLimit(data);
        if (start < 0 || start >= limit || data[start] != '<')
            return false;

        int nameStart = start + 1;
        int localNameStart = nameStart;
        boolean hasPrefix = false;

        for (int index = nameStart; index < limit; index++) {
            byte value = data[index];
            if (value == '>' || isXmlWhitespace(value)
                    || value == '/' && index + 1 < limit && data[index + 1] == '>') {
                return index - localNameStart == SVG_ELEMENT_NAME.length()
                        && matchesAscii(data, localNameStart, SVG_ELEMENT_NAME);
            }

            if (value == ':') {
                if (hasPrefix || index == nameStart)
                    return false;
                hasPrefix = true;
                localNameStart = index + 1;
                continue;
            }

            boolean nameStartCharacter = index == nameStart || index == localNameStart;
            if (nameStartCharacter
                    ? !isAsciiXmlNameStart(value)
                    : !isAsciiXmlNamePart(value)) {
                return false;
            }
        }

        return false;
    }

    /// Returns whether a byte can begin an ASCII XML name component.
    ///
    /// @param value byte to inspect
    /// @return whether the byte is an ASCII letter or underscore
    private static boolean isAsciiXmlNameStart(byte value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value == '_';
    }

    /// Returns whether a byte can continue an ASCII XML name component.
    ///
    /// @param value byte to inspect
    /// @return whether the byte is accepted after the first name character
    private static boolean isAsciiXmlNamePart(byte value) {
        return isAsciiXmlNameStart(value)
                || value >= '0' && value <= '9'
                || value == '-'
                || value == '.';
    }

    /// Returns whether an XML document type declaration begins at an offset.
    ///
    /// @param data  source bytes
    /// @param start offset of a possible declaration
    /// @return whether the case-sensitive keyword is followed by XML whitespace
    private static boolean isDoctypeStart(byte[] data, int start) {
        int keywordEnd = start + XML_DOCTYPE_START.length();
        return matchesAscii(data, start, XML_DOCTYPE_START)
                && keywordEnd < svgScanLimit(data)
                && isXmlWhitespace(data[keywordEnd]);
    }

    /// Skips an XML document type declaration, including its internal subset.
    ///
    /// Quoted values, comments, and processing instructions are ignored while
    /// locating the declaration-closing `>`.
    ///
    /// @param data  source bytes
    /// @param start offset of the `<!DOCTYPE` prefix
    /// @return the offset after the declaration, or `-1` if it is incomplete
    private static int skipDoctype(byte[] data, int start) {
        int internalSubsetDepth = 0;
        byte quote = 0;
        int limit = svgScanLimit(data);

        for (int index = start + XML_DOCTYPE_START.length(); index < limit; index++) {
            byte value = data[index];

            if (quote != 0) {
                if (value == quote)
                    quote = 0;
                continue;
            }

            if (value == '\'' || value == '"') {
                quote = value;
                continue;
            }

            if (matchesAscii(data, index, XML_COMMENT_START)) {
                int commentEnd = indexAfterAscii(
                        data,
                        index + XML_COMMENT_START.length(),
                        XML_COMMENT_END
                );
                if (commentEnd < 0)
                    return -1;
                index = commentEnd - 1;
                continue;
            }

            if (matchesAscii(data, index, XML_PROCESSING_INSTRUCTION_START)) {
                int instructionEnd = indexAfterAscii(
                        data,
                        index + XML_PROCESSING_INSTRUCTION_START.length(),
                        XML_PROCESSING_INSTRUCTION_END
                );
                if (instructionEnd < 0)
                    return -1;
                index = instructionEnd - 1;
                continue;
            }

            if (value == '[') {
                internalSubsetDepth++;
            } else if (value == ']' && internalSubsetDepth > 0) {
                internalSubsetDepth--;
            } else if (value == '>' && internalSubsetDepth == 0) {
                return index + 1;
            }
        }

        return -1;
    }

    /// Finds an ASCII sequence in a byte array.
    ///
    /// @param data       source bytes
    /// @param start      inclusive offset at which to begin searching
    /// @param asciiValue ASCII sequence to find
    /// @return the offset after the first match, or `-1` if no match exists
    private static int indexAfterAscii(byte[] data, int start, String asciiValue) {
        int lastStart = svgScanLimit(data) - asciiValue.length();
        for (int index = start; index <= lastStart; index++) {
            if (matchesAscii(data, index, asciiValue))
                return index + asciiValue.length();
        }
        return -1;
    }

    /// Compares an ASCII string with bytes at an array offset.
    ///
    /// @param data       source bytes
    /// @param start      offset at which to compare
    /// @param asciiValue ASCII sequence to compare
    /// @return whether the complete sequence matches
    private static boolean matchesAscii(byte[] data, int start, String asciiValue) {
        if (start < 0 || start > svgScanLimit(data) - asciiValue.length())
            return false;

        for (int index = 0; index < asciiValue.length(); index++) {
            if (Byte.toUnsignedInt(data[start + index]) != asciiValue.charAt(index))
                return false;
        }
        return true;
    }

    /// Returns whether a byte is XML whitespace.
    ///
    /// @param value byte to inspect
    /// @return whether the byte is space, tab, carriage return, or line feed
    private static boolean isXmlWhitespace(byte value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private static final byte[] PNG_HEADER = {
            (byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47,
            (byte) 0x0d, (byte) 0x0a, (byte) 0x1a, (byte) 0x0a,
    };

    private record PngChunkHeader(int length, int chunkType) {
        private static final int IDAT_HEADER = 0x49444154;
        private static final int acTL_HEADER = 0x6163544c;

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

        @Nullable PngChunkHeader header;
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
        if (isSVG(headerBuffer))
            return SVG;
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
            PngFrameControl control = frame.control();

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
                    System.arraycopy(frame.bitmap().array(),
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

                        int srcPixel = frame.bitmap().array()[srcIndex];
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

        @Nullable PngAnimationControl animationControl = sequence.getAnimationControl();
        int cycleCount;
        if (animationControl != null) {
            cycleCount = animationControl.numPlays();
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

    private static int[] rgbaToArgb(ByteBuffer rgba) {
        int pixelCount = rgba.remaining() / 4;
        int[] argb = new int[pixelCount];
        for (int i = 0; i < pixelCount; i++) {
            int r = rgba.get() & 0xFF;
            int g = rgba.get() & 0xFF;
            int b = rgba.get() & 0xFF;
            int a = rgba.get() & 0xFF;
            argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return argb;
    }

    /// Prevents instantiation.
    private ImageUtils() {
    }
}
