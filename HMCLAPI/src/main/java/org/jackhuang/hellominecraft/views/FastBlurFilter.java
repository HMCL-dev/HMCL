/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.views;

import java.awt.image.BufferedImage;

public class FastBlurFilter extends AbstractFilter {

    private final int radius;

    public FastBlurFilter() {
        this(3);
    }

    public FastBlurFilter(int radius) {
        if (radius < 1) {
            radius = 1;
        }

        this.radius = radius;
    }

    public int getRadius() {
        return this.radius;
    }

    public BufferedImage filter(BufferedImage src, BufferedImage dst) {
        int width = src.getWidth();
        int height = src.getHeight();

        if (dst == null) {
            dst = createCompatibleDestImage(src, null);
        }

        int[] srcPixels = new int[width * height];
        int[] dstPixels = new int[width * height];

        getPixels(src, 0, 0, width, height, srcPixels);

        blur(srcPixels, dstPixels, width, height, this.radius);

        blur(dstPixels, srcPixels, height, width, this.radius);

        setPixels(dst, 0, 0, width, height, srcPixels);

        return dst;
    }

    static void blur(int[] srcPixels, int[] dstPixels, int width, int height, int radius) {
        int windowSize = radius * 2 + 1;
        int radiusPlusOne = radius + 1;

        int srcIndex = 0;

        int[] sumLookupTable = new int[256 * windowSize];
        for (int i = 0; i < sumLookupTable.length; i++) {
            sumLookupTable[i] = (i / windowSize);
        }

        int[] indexLookupTable = new int[radiusPlusOne];
        if (radius < width) {
            for (int i = 0; i < indexLookupTable.length; i++) {
                indexLookupTable[i] = i;
            }
        } else {
            for (int i = 0; i < width; i++) {
                indexLookupTable[i] = i;
            }
            for (int i = width; i < indexLookupTable.length; i++) {
                indexLookupTable[i] = (width - 1);
            }
        }

        for (int y = 0; y < height; y++) {
            int sumBlue;
            int sumGreen;
            int sumRed;
            int sumAlpha = sumRed = sumGreen = sumBlue = 0;
            int dstIndex = y;

            int pixel = srcPixels[srcIndex];
            sumAlpha += radiusPlusOne * (pixel >> 24 & 0xFF);
            sumRed += radiusPlusOne * (pixel >> 16 & 0xFF);
            sumGreen += radiusPlusOne * (pixel >> 8 & 0xFF);
            sumBlue += radiusPlusOne * (pixel & 0xFF);

            for (int i = 1; i <= radius; i++) {
                pixel = srcPixels[(srcIndex + indexLookupTable[i])];
                sumAlpha += (pixel >> 24 & 0xFF);
                sumRed += (pixel >> 16 & 0xFF);
                sumGreen += (pixel >> 8 & 0xFF);
                sumBlue += (pixel & 0xFF);
            }

            for (int x = 0; x < width; x++) {
                dstPixels[dstIndex] = (sumLookupTable[sumAlpha] << 24 | sumLookupTable[sumRed] << 16 | sumLookupTable[sumGreen] << 8 | sumLookupTable[sumBlue]);

                dstIndex += height;

                int nextPixelIndex = x + radiusPlusOne;
                if (nextPixelIndex >= width) {
                    nextPixelIndex = width - 1;
                }

                int previousPixelIndex = x - radius;
                if (previousPixelIndex < 0) {
                    previousPixelIndex = 0;
                }

                int nextPixel = srcPixels[(srcIndex + nextPixelIndex)];
                int previousPixel = srcPixels[(srcIndex + previousPixelIndex)];

                sumAlpha += (nextPixel >> 24 & 0xFF);
                sumAlpha -= (previousPixel >> 24 & 0xFF);

                sumRed += (nextPixel >> 16 & 0xFF);
                sumRed -= (previousPixel >> 16 & 0xFF);

                sumGreen += (nextPixel >> 8 & 0xFF);
                sumGreen -= (previousPixel >> 8 & 0xFF);

                sumBlue += (nextPixel & 0xFF);
                sumBlue -= (previousPixel & 0xFF);
            }

            srcIndex += width;
        }
    }
}
