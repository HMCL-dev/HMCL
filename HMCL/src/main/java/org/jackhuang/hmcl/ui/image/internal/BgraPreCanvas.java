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
package org.jackhuang.hmcl.ui.image.internal;

import org.jackhuang.hmcl.util.ByteArray;

import java.util.Arrays;
import java.util.Objects;

public class BgraPreCanvas {
    protected static int argbToPre(int argb) {
        int a = argb >> 24 & 0xff;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;

        if (a == 0) {
            r = g = b = 0;
        } else if (a < 255) {
            r = (r * a + 127) / 0xff;
            g = (g * a + 127) / 0xff;
            b = (b * a + 127) / 0xff;
        }

        return (a << 24) | (r << 16) | (g << 8) | b;
    }


    protected final byte[] pixels;
    protected final int width;
    protected final int height;

    public BgraPreCanvas(byte[] pixels, int width, int height) {
        if (pixels.length != 4 * width * height)
            throw new IllegalArgumentException("Pixel array length mismatch");

        this.pixels = pixels;
        this.width = width;
        this.height = height;
    }

    public BgraPreCanvas(int width, int height) {
        this.pixels = new byte[4 * width * height];
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte[] getPixels(int x, int y, int w, int h) {
        Objects.checkFromIndexSize(x, w, width);
        Objects.checkFromIndexSize(y, h, height);

        final int bytesForRow = 4 * w;

        byte[] pixels = new byte[4 * w * h];
        for (int row = 0; row < h; row++) {
            int sourceOffset = 4 * ((y + row) * width + x);
            int targetOffset = 4 * (row * w);
            System.arraycopy(this.pixels, sourceOffset, pixels, targetOffset, bytesForRow);
        }
        return pixels;
    }

    public void clear(int x, int y, int w, int h) {
        Objects.checkFromIndexSize(x, w, width);
        Objects.checkFromIndexSize(y, h, height);

        final int bytesForRow = 4 * w;

        for (int row = 0; row < h; row++) {
            int targetIndex = 4 * ((y + row) * width + x);
            Arrays.fill(pixels, targetIndex, targetIndex + bytesForRow, (byte) 0);
        }
    }

    public void setBgraPre(int x, int y, BgraPreCanvas canvas) {
        Objects.checkFromIndexSize(x, canvas.width, width);
        Objects.checkFromIndexSize(y, canvas.height, height);

        for (int row = 0; row < canvas.height; row++) {
            for (int col = 0; col < canvas.width; col++) {
                int sourceIndex = 4 * (row * canvas.width + col);
                int targetIndex = 4 * ((row + y) * width + x + col);
                ByteArray.setIntLE(pixels, targetIndex, ByteArray.getIntLE(canvas.pixels, sourceIndex));
            }
        }
    }

    public void setArgb(int x, int y, int argb) {
        Objects.checkIndex(x, width);
        Objects.checkIndex(y, height);

        int targetIndex = (y * width + x) * 4;
        ByteArray.setIntLE(pixels, targetIndex, argbToPre(argb));
    }

    public void setArgbPre(int x, int y, int argbPre) {
        Objects.checkIndex(x, width);
        Objects.checkIndex(y, height);

        int targetIndex = (y * width + x) * 4;
        ByteArray.setIntLE(pixels, targetIndex, argbPre);
    }

    public void setArgb(int x, int y, int w, int h,
                        int[] buffer, int offset, int scanlineStride) {
        Objects.checkFromIndexSize(x, w, width);
        Objects.checkFromIndexSize(y, h, width);

        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int sourceIndex = offset + (row * scanlineStride + col);
                int targetIndex = 4 * ((row + y) * width + x + col);
                ByteArray.setIntLE(pixels, targetIndex, argbToPre(buffer[sourceIndex]));
            }
        }
    }

    public void blendingWithArgb(int x, int y, int w, int h,
                                 int[] buffer, int offset, int scanlineStride) {
        Objects.checkFromIndexSize(x, w, width);
        Objects.checkFromIndexSize(y, h, width);

        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int sourceIndex = offset + (row * scanlineStride + col);

                int resultArgbPre;
                int srcArgb = buffer[sourceIndex];

                int srcA = (srcArgb >> 24) & 0xff;
                if (srcA == 0) {
                    continue;
                }

                int targetIndex = 4 * ((row + y) * width + x + col);
                if (srcA == 255) {
                    resultArgbPre = argbToPre(srcArgb);
                } else {
                    int srcArgbPre = argbToPre(srcArgb);

                    int srcRPre = (srcArgbPre >> 16) & 0xff;
                    int srcGPre = (srcArgbPre >> 8) & 0xff;
                    int srcBPre = (srcArgbPre) & 0xff;

                    int dstArgbPre = ByteArray.getIntLE(pixels, targetIndex);

                    int dstA = (dstArgbPre >> 24) & 0xff;
                    int dstRPre = (dstArgbPre >> 16) & 0xff;
                    int dstGPre = (dstArgbPre >> 8) & 0xff;
                    int dstBPre = (dstArgbPre) & 0xff;

                    int invSrcA = 255 - srcA;

                    int outAlpha = srcA + (dstA * invSrcA + 127) / 255;

                    if (outAlpha == 0) {
                        resultArgbPre = 0;
                    } else {
                        int outR, outG, outB;

                        outR = srcRPre + (dstRPre * invSrcA + 127) / 255;
                        outG = srcGPre + (dstGPre * invSrcA + 127) / 255;
                        outB = srcBPre + (dstBPre * invSrcA + 127) / 255;

                        resultArgbPre = (outAlpha << 24) | (outR << 16) | (outG << 8) | outB;
                    }
                }

                ByteArray.setIntLE(pixels, targetIndex, resultArgbPre);
            }
        }
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
