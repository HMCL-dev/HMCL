// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.argb8888;

import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.error.PngIntegrityException;

/**
 * A palette implementation representing colours in ARGB8888 format as 32-bit integers.
 * <p>
 * Note that the transparency of individual entries in the palette is modified in-place
 * when the trNS chunk is processed.
 *
 * @see BasicArgb8888Director
 */
public class Argb8888Palette {
    protected static Argb8888Palette monochromePalette = null;
    protected static Argb8888Palette greyPalette2 = null;
    protected static Argb8888Palette greyPalette4 = null;
    protected static Argb8888Palette greyPalette8 = null;

    /**
     * The colour array is public and mutable by design.
     *
     * @see this.get()
     */
    public final int[] argbArray;

    /**
     * Construct a new palette using the specific array passed as the argument as-is (not copied).
     *
     * @param argbArray array of colours to use as-is (it is not copied).
     */
    public Argb8888Palette(int[] argbArray) {
        this.argbArray = argbArray;
    }

    /**
     * Retrieve a colour using a method. Users can also just use the argbArray directly.
     *
     * @param index of colour to retrieve
     * @return integer representing the colour at that index.
     */
    public int get(int index) {
        return this.argbArray[index];
    }

    /**
     * @return the number of colours in this palette, which is the same thing as the size of the array.
     */
    public int size() {
        return argbArray.length;
    }

    /**
     * Build a new palette by reading the byte array (from the offset position and for length bytes)
     * as provided in a PLTE chunk.
     *
     * @param bytes    array to read data from
     * @param position offset into bytes array to begin reading from
     * @param length   number of bytes to read from bytes array
     * @return new palette formed by reading the bytes array.
     * @throws PngException
     */
    public static Argb8888Palette fromPaletteBytes(byte[] bytes, int position, int length) throws PngException {
        int numColours = length / 3; // guaranteed to be divisible by 3
        int[] argbArray = new int[numColours];
        int srcIndex = position;
        int alpha = 0xff << 24;
        for (int destIndex = 0; destIndex < numColours; destIndex++) {
            final int r = bytes[srcIndex++] & 0xff;
            final int g = bytes[srcIndex++] & 0xff;
            final int b = bytes[srcIndex++] & 0xff;
            argbArray[destIndex] = alpha | r << 16 | g << 8 | b;
        }
        return new Argb8888Palette(argbArray);
    }

    public static Argb8888Palette forGreyscale(int numEntries, int step) {
        int[] array = new int[numEntries];
        int alpha = 0xff << 24;
        int grey = 0;
        for (int i = 0; i < numEntries; i++) {
            array[i] = alpha | grey << 16 | grey << 8 | grey;
            grey = (grey + step) & 0xff;
        }
        return new Argb8888Palette(array);
    }

    public static Argb8888Palette forGreyscale(int bitDepth) throws PngException {
        switch (bitDepth) {
            case 1:
                if (null == monochromePalette) {
                    monochromePalette = forGreyscale(2, 0xff); // Worth it or not really?
                }
                return monochromePalette;
            case 2:
                if (null == greyPalette2) {
                    greyPalette2 = forGreyscale(4, 0x55);
                }
                return greyPalette2;
            case 4:
                if (null == greyPalette4) {
                    greyPalette4 = forGreyscale(16, 0x11);
                }
                return greyPalette4;
            case 8: // TODO: need??
                if (null == greyPalette8) {
                    greyPalette8 = forGreyscale(256, 0x01);
                }
                return greyPalette8;
            default:
                throw new PngIntegrityException(String.format("Valid greyscale bit depths are 1, 2, 4, 8, not %d", bitDepth));
        }
    }
}
