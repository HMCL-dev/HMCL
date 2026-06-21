/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.theme;

import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/// Extracts a Monet seed color from wallpaper images.
@NotNullByDefault
public final class WallpaperColorExtractor {
    /// Maximum number of pixels sampled from one image.
    private static final int MAX_SAMPLE_COUNT = 10_000;

    /// Number of low color bits discarded for histogram buckets.
    private static final int CHANNEL_SHIFT = 3;

    /// Center offset added to quantized RGB buckets.
    private static final int BUCKET_CENTER_OFFSET = 1 << (CHANNEL_SHIFT - 1);

    /// Minimum alpha accepted for sampled pixels.
    private static final int MIN_ALPHA = 128;

    /// Minimum saturation used to avoid gray seed colors when possible.
    private static final float MIN_SATURATION = 0.08f;

    /// Minimum brightness accepted for sampled pixels.
    private static final float MIN_BRIGHTNESS = 0.12f;

    /// Maximum brightness accepted for sampled pixels.
    private static final float MAX_BRIGHTNESS = 0.95f;

    /// Prevents instantiation.
    private WallpaperColorExtractor() {
    }

    /// Extracts a theme color from an image file.
    ///
    /// @param imageFile the image file
    /// @param fallback the fallback color used when extraction fails
    /// @return the extracted color, or `fallback` when no suitable color is found
    /// @throws IOException if the image file cannot be read
    public static ThemeColor extract(Path imageFile, ThemeColor fallback) throws IOException {
        Objects.requireNonNull(imageFile);
        Objects.requireNonNull(fallback);

        BufferedImage image = ImageIO.read(imageFile.toFile());
        if (image == null) {
            return fallback;
        }

        @Nullable ThemeColor extracted = extract(image);
        return extracted != null ? extracted : fallback;
    }

    /// Extracts a theme color from an in-memory image.
    private static @Nullable ThemeColor extract(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }

        Map<Integer, Integer> histogram = new HashMap<>();
        int sampleStep = Math.max(1, (int) Math.sqrt((double) width * height / MAX_SAMPLE_COUNT));
        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                addPixel(histogram, image.getRGB(x, y));
            }
        }

        int bestKey = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
            int red = red(entry.getKey());
            int green = green(entry.getKey());
            int blue = blue(entry.getKey());
            float[] hsb = java.awt.Color.RGBtoHSB(red, green, blue, null);
            double score = entry.getValue() * (0.5 + hsb[1]) * (1.0 - Math.abs(hsb[2] - 0.55) * 0.6);
            if (score > bestScore) {
                bestKey = entry.getKey();
                bestScore = score;
            }
        }

        if (histogram.isEmpty()) {
            return null;
        }
        return ThemeColor.of(Color.rgb(red(bestKey), green(bestKey), blue(bestKey)));
    }

    /// Adds one ARGB pixel to the histogram when it is suitable for a theme seed.
    private static void addPixel(Map<Integer, Integer> histogram, int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha < MIN_ALPHA) {
            return;
        }

        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        float[] hsb = java.awt.Color.RGBtoHSB(red, green, blue, null);
        if (hsb[1] < MIN_SATURATION || hsb[2] < MIN_BRIGHTNESS || hsb[2] > MAX_BRIGHTNESS) {
            return;
        }

        histogram.merge(key(red, green, blue), 1, Integer::sum);
    }

    /// Returns the histogram key for an RGB color.
    private static int key(int red, int green, int blue) {
        return (red >> CHANNEL_SHIFT) << 10
                | (green >> CHANNEL_SHIFT) << 5
                | (blue >> CHANNEL_SHIFT);
    }

    /// Returns the red channel represented by a histogram key.
    private static int red(int key) {
        return (((key >> 10) & 0x1F) << CHANNEL_SHIFT) + BUCKET_CENTER_OFFSET;
    }

    /// Returns the green channel represented by a histogram key.
    private static int green(int key) {
        return (((key >> 5) & 0x1F) << CHANNEL_SHIFT) + BUCKET_CENTER_OFFSET;
    }

    /// Returns the blue channel represented by a histogram key.
    private static int blue(int key) {
        return ((key & 0x1F) << CHANNEL_SHIFT) + BUCKET_CENTER_OFFSET;
    }
}
