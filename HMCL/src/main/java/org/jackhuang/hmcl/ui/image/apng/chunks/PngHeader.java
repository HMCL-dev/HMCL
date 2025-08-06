// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.chunks;

import org.jackhuang.hmcl.ui.image.apng.PngColourType;
import org.jackhuang.hmcl.ui.image.apng.reader.PngReadHelper;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.error.PngFeatureException;
import org.jackhuang.hmcl.ui.image.apng.error.PngIntegrityException;

import java.io.DataInput;
import java.io.IOException;

/**
 * Created by aellerton on 10/05/2015.
 */
public class PngHeader {
    /**
     * Number of pixels (columns) wide.
     */
    public final int width;

    /**
     * Number of pixels (rows) high.
     */
    public final int height;

    /**
     * The bitDepth is the number of bits for each <em>channel</em> of a given pixel.
     * A better name might be "bitsPerPixelChannel" but the name "bitDepth" is used
     * throughout the PNG specification.
     * <p>
     * A truecolour image with a bitDepth of 8 means that the red channel of a pixel
     * has 8 bits (so 256 levels of red), green has 8 bits (256 levels of green), and
     * blue has 8 bits (so 256 levels of green). That means the total bitsPerPixel
     * for that bitmap will be 8+8+8 = 24.
     * <p>
     * A truecolour <em>with alpha</em> image with bitDepth of 8 will be the same
     * except every alpha element of every pixel will have 8 bits (so 256 levels of
     * alpha transparency), meaning that the total bitsPerPixel for that bitmap will
     * be 8+8+8+8=32.
     * <p>
     * A truecolour with alpha image with <em>bitDepth of 16</em> means that each of
     * red, green blue and alpha have 16-bits respectively, meaning that the total
     * bitsPerPixel will be 16+16+16+16 = 64.
     * <p>
     * A greyscale image (no alpha) with bitDepth of 16 has only a grey channel for
     * each pixel, so the bitsPerPixel will also be 16.
     * <p>
     * But a greyscale image <em>with alpha</em> with a bitDepth of 16 has a grey
     * channel and an alpha channel, each with 16 bits so the bitsPerPixel will be
     * 16+16=32.
     * <p>
     * As for palette-based images...
     * <ul>
     * <li>A monochrome image or image with 2 colour palette has bitDepth=1.</li>
     * <li>An image with 4 colour palette has bitDepth=2.</li>
     * <li>An image with 8 colour palette has bitDepth=3.</li>
     * <li>An image with 16 colour palette has bitDepth=4.</li>
     * <li>A greyscale image with 16 levels of gray <em>and an alpha channel</em>
     *   has bitDepth=4 and bitsPerPixel=8 because the gray and the alpha channel
     *   each have 4 bits.</li>
     * </ul>
     *
     * @see #bitsPerPixel
     */
    public final byte bitDepth;

    /**
     * Every PNG image must be exactly one of the standard types as defined by the
     * PNG specification. Better names might have been "imageType" or "imageFormat"
     * but the name "colourType" is used throughout the PNG spec.
     */
    public final PngColourType colourType;

    /**
     * Compression type of the file.
     * In practice this is redundant: it may be zip and nothing else.
     */
    public final byte compressionMethod;

    /**
     * Filter method used by the file.
     * In practice this is redundant because the filter types are set in the
     * specification and have never been (and never will be) extended.
     */
    public final byte filterMethod;

    /**
     * An image is either interlaced or not interlaced.
     * At the time of writing only non-interlaced is supported by this library.
     */
    public final byte interlaceMethod;

    /**
     * The number of bits that comprise a single pixel in this bitmap (or every
     * frame if animated). This is distinct from bitDepth.
     *
     * @see #bitDepth
     */
    public final int bitsPerPixel;
    public final int bytesPerRow;
    public final int filterOffset;

    public PngHeader(int width, int height, byte bitDepth, PngColourType colourType) {
        this(width, height, bitDepth, colourType, (byte) 0, (byte) 0, (byte) 0);
    }

    public PngHeader(int width, int height, byte bitDepth, PngColourType colourType, byte compressionMethod, byte filterMethod, byte interlaceMethod) {
        this.width = width;
        this.height = height;
        this.bitDepth = bitDepth;
        this.colourType = colourType;
        this.compressionMethod = compressionMethod;
        this.filterMethod = filterMethod;
        this.interlaceMethod = interlaceMethod;
        this.bitsPerPixel = bitDepth * colourType.componentsPerPixel;
        this.bytesPerRow = PngReadHelper.calculateBytesPerRow(width, bitDepth, colourType, interlaceMethod);
        //this.filterOffset = this.bitsPerPixel < 8 ? 1 : this.bitsPerPixel>>3; // minimum of 1 byte. RGB888 will be 3 bytes. RGBAFFFF is 8 bytes.
        this.filterOffset = (this.bitsPerPixel + 7) >> 3; // libpng

        // from pypng
//        # Derived values
//        # http://www.w3.org/TR/PNG/#6Colour-values
//        colormap =  bool(self.color_type & 1)
//        greyscale = not (self.color_type & 2)
//        alpha = bool(self.color_type & 4)
//        color_planes = (3,1)[greyscale or colormap]
//        planes = color_planes + alpha
    }

    @Override
    public String toString() {
        return "PngHeader{" +
                "width=" + width +
                ", height=" + height +
                ", bitDepth=" + bitDepth +
                ", colourType=" + colourType +
                ", compressionMethod=" + compressionMethod +
                ", filterMethod=" + filterMethod +
                ", interlaceMethod=" + interlaceMethod +
                '}';
    }

    public boolean isInterlaced() {
        return interlaceMethod == 1;
    }

    public boolean isZipCompression() {
        return compressionMethod == 0;
    }

    public boolean isGreyscale() {
        return colourType == PngColourType.PNG_GREYSCALE | colourType == PngColourType.PNG_GREYSCALE_WITH_ALPHA;
    }

    /**
     * @return true if the image type indicates there is an alpha value for every pixel.
     * Note that this take into account any transparency or background chunk.
     */
    public boolean hasAlphaChannel() {
        return colourType == PngColourType.PNG_GREYSCALE_WITH_ALPHA | colourType == PngColourType.PNG_TRUECOLOUR_WITH_ALPHA;
    }

    public static PngHeader makeTruecolour(int width, int height) {
        return new PngHeader(width, height, (byte) 8, PngColourType.PNG_TRUECOLOUR);
    }

    public static PngHeader makeTruecolourAlpha(int width, int height) {
        return new PngHeader(width, height, (byte) 8, PngColourType.PNG_TRUECOLOUR_WITH_ALPHA);
    }

    public PngHeader adjustFor(PngFrameControl frame) {
        if (frame == null) {
            return this;
        } else {
            return new PngHeader(frame.width, frame.height, this.bitDepth, this.colourType, this.compressionMethod, this.filterMethod, this.interlaceMethod);
        }
    }

    public static void checkHeaderParameters(int width, int height, byte bitDepth, PngColourType colourType, byte compressionMethod, byte filterMethod, byte interlaceMethod) throws PngException {

        switch (bitDepth) {
            case 1:
            case 2:
            case 4:
            case 8:
            case 16:
                break; // all fine
            default:
                throw new PngIntegrityException("Invalid bit depth " + bitDepth);
        }

        // thanks to pypng
        if (colourType.isIndexed() && bitDepth > 8) {
            throw new PngIntegrityException(String.format(
                    "Indexed images (colour type %d) cannot have bitdepth > 8 (bit depth %d)." +
                            " See http://www.w3.org/TR/2003/REC-PNG-20031110/#table111 .", colourType.code, bitDepth));
        }

        if (bitDepth < 8 && !colourType.supportsSubByteDepth()) {
            throw new PngIntegrityException(String.format(
                    "Illegal combination of bit depth (%d) and colour type (%d)." +
                            " See http://www.w3.org/TR/2003/REC-PNG-20031110/#table111 .", colourType.code, bitDepth));
        }

        if (interlaceMethod != 0) {
            throw new PngFeatureException("Interlaced images are not yet supported");
        }
    }

    public static PngHeader from(DataInput dis) throws IOException, PngException {
        int width = dis.readInt();
        int height = dis.readInt();
        byte bitDepth = dis.readByte();
        PngColourType colourType = PngColourType.fromByte(dis.readByte());
        byte compressionMethod = dis.readByte();
        byte filterMethod = dis.readByte();
        byte interlaceMethod = dis.readByte();
        checkHeaderParameters(width, height, bitDepth, colourType, compressionMethod, filterMethod, interlaceMethod);
        return new PngHeader(width, height, bitDepth, colourType, compressionMethod, filterMethod, interlaceMethod);
    }
}
