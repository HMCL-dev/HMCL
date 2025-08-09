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

public class BgraPreCanvas {
    private final byte[] pixels;
    private final int width;
    private final int height;

    public BgraPreCanvas(byte[] pixels, int width, int height) {
        if (pixels.length != 4 * width * height)
            throw new IllegalArgumentException("Pixel array length missmatch");

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

    public void setArgb(int row, int col, int argb) {
        if (row < 0 || row >= width || col < 0 || col >= height) {
            throw new IndexOutOfBoundsException("row or col out of bounds");
        }

        int targetIndex = (row * width + col) * 4;

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

        //noinspection PointlessArithmeticExpression
        pixels[targetIndex + 0] = (byte) b;
        pixels[targetIndex + 1] = (byte) g;
        pixels[targetIndex + 2] = (byte) r;
        pixels[targetIndex + 3] = (byte) a;
    }

    public void setArgbPre(int row, int col, int argbPre) {
        if (row < 0 || row >= width || col < 0 || col >= height) {
            throw new IndexOutOfBoundsException("row or col out of bounds");
        }

        int targetIndex = (row * width + col) * 4;
        ByteArray.setIntLE(pixels, targetIndex, argbPre);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
