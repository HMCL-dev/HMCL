// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.argb8888;

/**
 * A bitmap where each pixel is represented by 8-bits for alpha, red, green and blue
 * respectively, and each pixel stored in a single 32-bit integer. This is expressly designed
 * to be compatible with the input array used to build Android Bitmap objects, though of course
 * its use is not limited to that.
 */
public final class Argb8888Bitmap {
    public final int[] array;
    public final int width;
    public final int height;

    public Argb8888Bitmap(int width, int height) {
        this(new int[width * height], width, height);
    }

    public Argb8888Bitmap(int[] array, int width, int height) {//}, int x, int y) {
        this.array = array;
        this.width = width;
        this.height = height;
//        this.x = y;
    }

    /**
     * Create a new Bitmap that shares the byte array of this bitmap.
     * This is useful when rendering a number of animation frames from an APNG file
     * and wanting to minimise memory allocation, while still wanting a "new" bitmap
     * to work on.
     *
     * @param width  in pixels of "new" bitmap (actual pixel array is the exact array
     *               from the original bitmap).
     * @param height in pixels of "new" bitmap (actual pixel array is the exact array
     *               from the original bitmap).
     * @return new bitmap object sharing the same data array.
     */
    public Argb8888Bitmap makeView(int width, int height) {//}, int x, int y) {
        if ((width * height) > (this.width * this.height)) {
            throw new IllegalArgumentException(String.format(
                    "Requested width and height (%d x %d) exceeds maximum pixels allowed by host bitmap (%d x %d",
                    width, height, this.width, this.height));
        }
        return new Argb8888Bitmap(array, width, height);
    }
}
