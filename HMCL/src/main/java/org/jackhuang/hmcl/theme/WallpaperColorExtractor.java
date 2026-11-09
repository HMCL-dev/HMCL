// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
