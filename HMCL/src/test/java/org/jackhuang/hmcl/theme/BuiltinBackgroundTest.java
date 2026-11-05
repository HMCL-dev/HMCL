// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.theme;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests for built-in launcher wallpapers.
@NotNullByDefault
public final class BuiltinBackgroundTest {
    /// Checks precomputed MonetFX seed colors against dynamic extraction.
    @Test
    public void precomputedThemeColorsMatchDynamicExtraction() {
        List<Executable> checks = Arrays.stream(BuiltinBackground.values())
                .<Executable>map(background -> () -> assertEquals(
                        WallpaperColorExtractor.extract(loadWallpaper(background), ThemeColor.DEFAULT),
                        background.themeColor(),
                        background.id()))
                .toList();
        assertAll(checks);
    }

    /// Loads the bundled wallpaper for a built-in background.
    private static Image loadWallpaper(BuiltinBackground background) throws Exception {
        String fileName = background.id() + ".jpg";
        @Nullable InputStream input = BuiltinBackgroundTest.class.getResourceAsStream(
                "/assets/img/wallpapers/" + fileName);
        if (input == null) {
            throw new AssertionError("Missing built-in wallpaper: " + fileName);
        }
        return FXUtils.loadImage(input, fileName);
    }
}
