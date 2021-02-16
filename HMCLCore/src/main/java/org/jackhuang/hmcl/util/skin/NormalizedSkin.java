/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.skin;

import java.awt.image.BufferedImage;

/**
 * Describes a Minecraft 1.8+ skin (64x64).
 * Old format skins are converted to the new format.
 *
 * @author yushijinhun
 */
public class NormalizedSkin {

    private static void copyImage(BufferedImage src, BufferedImage dst, int sx, int sy, int dx, int dy, int w, int h, boolean flipHorizontal) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = src.getRGB(sx + x, sy + y);
                dst.setRGB(dx + (flipHorizontal ? w - x - 1 : x), dy + y, pixel);
            }
        }
    }

    private final BufferedImage texture;
    private final BufferedImage normalizedTexture;
    private final int scale;
    private final boolean oldFormat;

    public NormalizedSkin(BufferedImage texture) throws InvalidSkinException {
        this.texture = texture;

        // check format
        int w = texture.getWidth();
        int h = texture.getHeight();
        if (w % 64 != 0) {
            throw new InvalidSkinException("Invalid size " + w + "x" + h);
        }
        if (w == h) {
            oldFormat = false;
        } else if (w == h * 2) {
            oldFormat = true;
        } else {
            throw new InvalidSkinException("Invalid size " + w + "x" + h);
        }

        // compute scale
        scale = w / 64;

        normalizedTexture = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
        copyImage(texture, normalizedTexture, 0, 0, 0, 0, w, h, false);
        if (oldFormat) {
            convertOldSkin();
        }
    }

    private void convertOldSkin() {
        copyImageRelative(4, 16, 20, 48, 4, 4, true); // Top Leg
        copyImageRelative(8, 16, 24, 48, 4, 4, true); // Bottom Leg
        copyImageRelative(0, 20, 24, 52, 4, 12, true); // Outer Leg
        copyImageRelative(4, 20, 20, 52, 4, 12, true); // Front Leg
        copyImageRelative(8, 20, 16, 52, 4, 12, true); // Inner Leg
        copyImageRelative(12, 20, 28, 52, 4, 12, true); // Back Leg
        copyImageRelative(44, 16, 36, 48, 4, 4, true); // Top Arm
        copyImageRelative(48, 16, 40, 48, 4, 4, true); // Bottom Arm
        copyImageRelative(40, 20, 40, 52, 4, 12, true); // Outer Arm
        copyImageRelative(44, 20, 36, 52, 4, 12, true); // Front Arm
        copyImageRelative(48, 20, 32, 52, 4, 12, true); // Inner Arm
        copyImageRelative(52, 20, 44, 52, 4, 12, true); // Back Arm
    }

    private void copyImageRelative(int sx, int sy, int dx, int dy, int w, int h, boolean flipHorizontal) {
        copyImage(normalizedTexture, normalizedTexture, sx * scale, sy * scale, dx * scale, dy * scale, w * scale, h * scale, flipHorizontal);
    }

    public BufferedImage getOriginalTexture() {
        return texture;
    }

    public BufferedImage getNormalizedTexture() {
        return normalizedTexture;
    }

    public int getScale() {
        return scale;
    }

    public boolean isOldFormat() {
        return oldFormat;
    }

    /**
     * Tests whether the skin is slim.
     * Note that this method doesn't guarantee the result is correct.
     */
    public boolean isSlim() {
        return (hasTransparencyRelative(50, 16, 2, 4) ||
                hasTransparencyRelative(54, 20, 2, 12) ||
                hasTransparencyRelative(42, 48, 2, 4) ||
                hasTransparencyRelative(46, 52, 2, 12)) ||
                (isAreaBlackRelative(50, 16, 2, 4) &&
                        isAreaBlackRelative(54, 20, 2, 12) &&
                        isAreaBlackRelative(42, 48, 2, 4) &&
                        isAreaBlackRelative(46, 52, 2, 12));
    }

    private boolean hasTransparencyRelative(int x0, int y0, int w, int h) {
        x0 *= scale;
        y0 *= scale;
        w *= scale;
        h *= scale;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = normalizedTexture.getRGB(x0 + x, y0 + y);
                if (pixel >>> 24 != 0xff) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAreaBlackRelative(int x0, int y0, int w, int h) {
        x0 *= scale;
        y0 *= scale;
        w *= scale;
        h *= scale;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = normalizedTexture.getRGB(x0 + x, y0 + y);
                if (pixel != 0xff000000) {
                    return false;
                }
            }
        }
        return true;
    }
}
