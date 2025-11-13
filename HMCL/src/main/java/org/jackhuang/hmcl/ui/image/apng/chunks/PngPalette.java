// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.chunks;

import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.error.PngIntegrityException;
import org.jackhuang.hmcl.ui.image.apng.argb8888.Argb8888Palette;

import java.util.Arrays;

/**
 * A PngPalette object represents an ordered array of RGB (888) colour tuples
 * derived from a PLTE chunk.
 * <p>
 * WARNING: this class may not remain in the API.
 * When implementing the Argb8888 decoders it seems clear that every output
 * format will benefit from a specific palette implementation, so this attempt
 * at a generic palette may be removed.
 *
 * @see Argb8888Palette
 */
public class PngPalette {
    // TODO: should include alpha here? Can then store as int32s?
    public final byte[] rgb888;
    public final int[] rgba8888; // Including this duplicate for now. Not sure if will keep it.
    public final int numColours;

    public static final int LENGTH_RGB_BYTES = 3;
    public static final int BYTE_INITIAL_ALPHA = 0xff;

    public PngPalette(byte[] rgb888, int[] rgba8888) {
        this.rgb888 = rgb888;
        this.rgba8888 = rgba8888;
        this.numColours = rgb888.length / 3;
    }

    public static PngPalette from(byte[] source, int first, int length) throws PngException {
        if (length % LENGTH_RGB_BYTES != 0) {
            throw new PngIntegrityException(String.format("Invalid palette data length: %d (not a multiple of 3)", length));
        }

        return new PngPalette(
                Arrays.copyOfRange(source, first, first + length),
                rgba8888From(source, first, length)
        );
    }

    private static int[] rgba8888From(byte[] source, int first, int length) {
        int last = first + length;
        int numColours = length / 3;
        int[] rgba8888 = new int[numColours];
        int j = 0;
        for (int i = first; i < last; i += LENGTH_RGB_BYTES) {
            rgba8888[j] = source[i] << 24 | source[i + 1] << 16 | source[i + 2] << 8 | BYTE_INITIAL_ALPHA;
            j++;
        }
        return rgba8888;
    }

}
