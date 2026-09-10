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

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import org.glavo.monetfx.ColorScheme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/// Extracts a MonetFX seed color from wallpaper images.
@NotNullByDefault
public final class WallpaperColorExtractor {
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

        Image image;
        try {
            image = FXUtils.loadImage(imageFile);
        } catch (Exception e) {
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to load wallpaper image: " + imageFile, e);
        }

        return extract(image, fallback);
    }

    /// Extracts a theme color from a theme-pack resource.
    ///
    /// @param resource the theme-pack resource
    /// @param fallback the fallback color used when extraction fails
    /// @return the extracted color, or `fallback` when no suitable color is found
    /// @throws IOException if the resource cannot be read
    static ThemeColor extract(ThemePackResource resource, ThemeColor fallback) throws IOException {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(fallback);

        Image image;
        try {
            image = FXUtils.loadImage(resource.openStream(), resource.name());
        } catch (Exception e) {
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to load wallpaper image: " + resource.name(), e);
        }

        return extract(image, fallback);
    }

    /// Extracts a theme color from a loaded image.
    ///
    /// @param image the loaded image
    /// @param fallback the fallback color used when extraction fails
    /// @return the extracted color, or `fallback` when no suitable color is found
    public static ThemeColor extract(Image image, ThemeColor fallback) {
        Objects.requireNonNull(image);
        Objects.requireNonNull(fallback);

        Color extracted = ColorScheme.extractColor(image, fallback.color());
        return ThemeColor.of(extracted);
    }
}
