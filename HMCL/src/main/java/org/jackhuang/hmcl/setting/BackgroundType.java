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

/// Selects the source used to render the launcher background.
@NotNullByDefault
public enum BackgroundType {
    /// Uses the built-in default background image or a local fallback image.
    DEFAULT,

    /// Uses the background resolved from the selected theme.
    THEME,

    /// Uses a user-selected local image file or a random image from a selected directory.
    CUSTOM,

    /// Uses the built-in classic background image.
    CLASSIC,

    /// Uses an image loaded from a user-provided URL.
    NETWORK,

    /// Uses the configured paint value as a flat background.
    PAINT,

    /// Uses the current theme color scheme surface container as a flat background.
    THEME_COLOR
}
