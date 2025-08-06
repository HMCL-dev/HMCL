// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng;

/**
 * Each "undo*" function reverses the effect of a filter on a given single scanline.
 */
public class PngFilter {

    public static void undoSubFilter(byte[] bytes, int pixelStart, int pixelEnd, int filterUnit, byte[] previousRow) {
//        int ai = 0;
//        for (int i=pixelStart+filterUnit; i<pixelEnd; i++) {
//
//            final int x = bytes[i];
//            final int a = bytes[pixelStart + ai];
        int ai = pixelStart;
        for (int i = pixelStart + filterUnit; i < pixelEnd; i++) {

            final int x = bytes[i];
            final int a = bytes[ai];

            //bytes[rowPosition] = (byte)((bytes[rowPosition] + left) & 0xff); // TODO & 0xff
            bytes[i] = (byte) ((x + a) & 0xff);

            ai++;
        }
    }

    public static void undoUpFilter(byte[] bytes, int pixelStart, int pixelEnd, int filterUnit, byte[] previousRow) {
//        for (int i=1; i<bytesPerLine; i++) {
//            rowPosition++; // before the first op to skip filter byte
//            bytes[rowPosition] = (byte)((bytes[rowPosition] + previousRow[i]) & 0xff);
//        }
        int bi = 0;
        for (int i = pixelStart; i < pixelEnd; i++) {
            final int x = bytes[i];
            final int b = previousRow[bi];
            bytes[i] = (byte) ((x + b) & 0xff);
            bi++;
        }

    }

    public static void undoAverageFilter(byte[] bytes, int pixelStart, int pixelEnd, int filterUnit, byte[] previousRow) {
        int ai = pixelStart - filterUnit;
        int bi = 0;
        for (int i = pixelStart; i < pixelEnd; i++) {
            final int x = bytes[i];
            final int a = (ai < pixelStart) ? 0 : (0xff & bytes[ai]);
            final int b = (0xff & previousRow[bi]);
//        int ai = pixelStart;
//        int bi = 0;
//        for (int i=pixelStart+filterUnit; i<pixelEnd; i++) {
//            final int x = bytes[i];
//            final int a = bytes[ai];
//            final int b = previousRow[bi];

            //bytes[i] = (byte)((x+((a+b)>>1))&0xff);
            //int z = x+(a+b)/2;
            int z = x + ((a + b) / 2);
            bytes[i] = (byte) (0xff & z);
            ai++;
            bi++;
        }

        /*
        int bi = 0;
        for (int i=pixelStart; i<pixelStart+filterUnit; i++) {
            final int x = bytes[i];
            final int b = previousRow[bi];
            bytes[i] = (byte)((x + (b >> 1)) & 0xff);
            bi++;
        }

        for (int i=pixelStart+filterUnit; i<pixelEnd; i++) {
            final int x = bytes[i];
            final int a = bytes[pixelStart+bi-filterUnit];
            final int b = previousRow[bi];
            bytes[i] = (byte)((x + ((a+b) >> 1)) & 0xff);
            bi++;
        }*/
    }

    /**
     * See http://www.libpng.org/pub/png/spec/1.2/PNG-Filters.html
     *
     */
    public static void undoPaethFilter(byte[] bytes, int pixelStart, int pixelEnd, int filterUnit, byte[] previousRow) {
        //int scratch;
        //int previousLeft = 0;
        //int left = 0;


        int ai = pixelStart - filterUnit;
        int bi = 0;
        int ci = -filterUnit;
        //for (int i=0; i<bytesPerLine-1; i++) {
        //for (int i=filterUnit; i<bytesPerLine-1; i++) {
        for (int i = pixelStart; i < pixelEnd; i++) {

            final int a, b, c, x;
            x = bytes[i];
            if (ai < pixelStart) {
                a = c = 0;
            } else {
                a = 0xff & bytes[ai];
                c = 0xff & previousRow[ci];
            }
            b = 0xff & previousRow[bi];
            final int p = a + b - c;
            final int pa = p >= a ? p - a : a - p;
            final int pb = p >= b ? p - b : b - p;
            final int pc = p >= c ? p - c : c - p;
            final int predicted = (pa <= pb && pa <= pc) ? a
                    : (pb <= pc) ? b
                    : c;

            bytes[i] = (byte) ((x + predicted) & 0xff);
            ai++;
            bi++;
            ci++;
        }
    }
}
