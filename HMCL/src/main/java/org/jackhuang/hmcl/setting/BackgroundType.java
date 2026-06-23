/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.setting;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Selects the source used to render the launcher background.
@NotNullByDefault
public enum BackgroundType {
    /// Uses the selected theme background when available, otherwise a built-in fallback wallpaper.
    DEFAULT,

    /// Uses one built-in wallpaper selected by launcher settings.
    BUILTIN,

    /// Uses a user-selected local image file or a random image from a selected directory.
    CUSTOM,

    /// Uses an image loaded from a user-provided URL.
    NETWORK,

    /// Uses the configured paint value as a flat background.
    PAINT,

    /// Uses the current theme color scheme surface container as a flat background.
    THEME_COLOR;

    /// ID of the built-in launcher wallpaper from 2015-06-22.
    public static final String BUILTIN_WALLPAPER_2015_06_22_ID = "2015-06-22";

    /// ID of the built-in launcher wallpaper from 2016-02-25.
    public static final String BUILTIN_WALLPAPER_2016_02_25_ID = "2016-02-25";

    /// ID of the built-in launcher wallpaper from 2021-08-26.
    public static final String BUILTIN_WALLPAPER_2021_08_26_ID = "2021-08-26";

    /// IDs of launcher built-in wallpapers that can be selected by users and theme packs.
    public static final @Unmodifiable List<String> BUILTIN_WALLPAPER_IDS = List.of(
            BUILTIN_WALLPAPER_2021_08_26_ID,
            BUILTIN_WALLPAPER_2016_02_25_ID,
            BUILTIN_WALLPAPER_2015_06_22_ID
    );

    /// ID used when a built-in wallpaper reference is missing or unsupported.
    public static final String FALLBACK_BUILTIN_WALLPAPER_ID = BUILTIN_WALLPAPER_2021_08_26_ID;
}
