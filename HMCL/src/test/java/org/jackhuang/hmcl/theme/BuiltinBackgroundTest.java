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
