/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Generates the local UI preview of a Minecraft cape from its raw UV texture.
///
/// The raw `capes[].url` texture is a UV unwrap (for example 64x32) and must
/// not be shown as-is. This class downloads and caches that raw texture through
/// [TexturesLoader], then extracts only the cape front face so the launcher
/// renders a correctly proportioned preview.
///
/// The raw texture itself is never modified: the returned image is only ever
/// used for local display.
@NotNullByDefault
public final class CapePreview {

    /// Display aspect ratio of a cape front face (10x16).
    public static final double ASPECT_RATIO = 10.0 / 16.0;

    /// Reference width, in pixels, of the standard 64x32 cape texture.
    private static final double BASE_WIDTH = 64.0;

    /// UV region of the cape front face in the standard 64x32 layout.
    private static final double FRONT_X = 1.0;
    private static final double FRONT_Y = 1.0;
    private static final double FRONT_WIDTH = 10.0;
    private static final double FRONT_HEIGHT = 16.0;

    private CapePreview() {
    }

    /// Downloads and caches the raw cape texture for `url`, then returns the
    /// front-face preview image.
    ///
    /// @param url the raw Minecraft cape texture URL
    /// @return the cropped preview, or `null` when the texture is unavailable
    public static @Nullable Image load(@Nullable String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }

        try {
            TexturesLoader.LoadedTexture texture = TexturesLoader.loadTexture(new Texture(url, null));
            return extractFrontFace(texture.image());
        } catch (Throwable e) {
            LOG.warning("Failed to load cape preview: " + url, e);
            return null;
        }
    }

    /// Extracts the cape front face from an already decoded raw cape texture.
    ///
    /// The front face is read from the region `(1, 1, 10, 16)` of the standard
    /// 64x32 UV layout, scaled by `width / 64` so that higher-resolution
    /// textures (128x64, 256x128, ...) are handled proportionally. The
    /// rectangle is clamped into the texture bounds, so unknown or irregular
    /// sizes degrade gracefully instead of failing.
    ///
    /// @param texture the decoded raw cape texture
    /// @return the cropped front-face preview, or `null` when the texture is unreadable
    public static @Nullable Image extractFrontFace(@Nullable Image texture) {
        if (texture == null) {
            return null;
        }

        int width = (int) texture.getWidth();
        int height = (int) texture.getHeight();
        int[] region = computeFrontRegion(width, height);
        if (region == null) {
            return null;
        }

        int x = region[0];
        int y = region[1];
        int w = region[2];
        int h = region[3];

        PixelReader reader = texture.getPixelReader();
        if (reader == null) {
            return null;
        }

        WritableImage preview = new WritableImage(w, h);
        PixelWriter writer = preview.getPixelWriter();
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                writer.setArgb(px, py, reader.getArgb(x + px, y + py));
            }
        }
        return preview;
    }

    /// Computes the integer pixel rectangle of the cape front face for a texture
    /// of the given size.
    ///
    /// @return `{x, y, width, height}`, or `null` when the size cannot be mapped
    static int @Nullable [] computeFrontRegion(int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }

        double scale = width / BASE_WIDTH;
        int x = Math.max(0, Math.min((int) Math.round(FRONT_X * scale), width - 1));
        int y = Math.max(0, Math.min((int) Math.round(FRONT_Y * scale), height - 1));
        int w = clamp((int) Math.round(FRONT_WIDTH * scale), 1, width - x);
        int h = clamp((int) Math.round(FRONT_HEIGHT * scale), 1, height - y);
        return new int[]{x, y, w, h};
    }

    /// Clamps `value` into `[min, max]`.
    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
