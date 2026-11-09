// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng;

import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.error.PngIntegrityException;

/**
 * PNG images support 5 colour types as defined at http://www.w3.org/TR/PNG/#11IHDR
 */
public enum PngColourType {
    PNG_GREYSCALE(0, 1, "1, 2, 4, 8, 16", "Greyscale", "Each pixel is a greyscale sample"),
    PNG_TRUECOLOUR(2, 3, "8, 16", "Truecolour", "Each pixel is an R,G,B triple"),
    PNG_INDEXED_COLOUR(3, 1, "1, 2, 4, 8", "Indexed-colour", "Each pixel is a palette index; a PLTE chunk shall appear."),
    PNG_GREYSCALE_WITH_ALPHA(4, 2, "4, 8, 16", "Greyscale with alpha", "Each pixel is a greyscale sample followed by an alpha sample."),
    PNG_TRUECOLOUR_WITH_ALPHA(6, 4, "8, 16", "Truecolour with alpha", "Each pixel is an R,G,B triple followed by an alpha sample.");

    public final int code;
    public final int componentsPerPixel;
    public final String allowedBitDepths;
    public final String name;
    public final String descriptino;

    PngColourType(int code, int componentsPerPixel, String allowedBitDepths, String name, String descriptino) {
        this.code = code;
        this.componentsPerPixel = componentsPerPixel;
        this.allowedBitDepths = allowedBitDepths;
        this.name = name;
        this.descriptino = descriptino;
    }

    public boolean isIndexed() {
        return (code & 0x01) > 0;
    }

    public boolean hasAlpha() {
        return (code & 0x04) > 0;
    }

    public boolean supportsSubByteDepth() {
        return code == 0 || code == 3;
    }

    public static PngColourType fromByte(byte b) throws PngException {
        switch (b) {
            case 0:
                return PNG_GREYSCALE;
            case 2:
                return PNG_TRUECOLOUR;
            case 3:
                return PNG_INDEXED_COLOUR;
            case 4:
                return PNG_GREYSCALE_WITH_ALPHA;
            case 6:
                return PNG_TRUECOLOUR_WITH_ALPHA;
            default:
                throw new PngIntegrityException(String.format("Valid PNG colour types are 0, 2, 3, 4, 6. Type '%d' is invalid", b));
        }
    }
}
