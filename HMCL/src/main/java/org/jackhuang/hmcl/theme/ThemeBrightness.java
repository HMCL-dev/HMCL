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

import org.glavo.monetfx.Brightness;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Locale;
import java.util.Objects;

/// Describes a theme-pack appearance's explicit brightness control.
@NotNullByDefault
public enum ThemeBrightness {
    /// Forces the launcher to use the light color scheme.
    LIGHT,

    /// Forces the launcher to use the dark color scheme.
    DARK;

    /// Parses a serialized theme brightness value.
    ///
    /// @param value the serialized brightness value
    /// @return the parsed brightness value
    /// @throws IllegalArgumentException if the value is not supported
    public static ThemeBrightness parse(String value) {
        Objects.requireNonNull(value);

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "light" -> LIGHT;
            case "dark" -> DARK;
            default -> throw new IllegalArgumentException("Unsupported theme brightness: " + value);
        };
    }

    /// Converts this value to a MonetFX brightness.
    ///
    /// @return the MonetFX brightness
    public Brightness toMonetBrightness() {
        return switch (this) {
            case LIGHT -> Brightness.LIGHT;
            case DARK -> Brightness.DARK;
        };
    }

    /// Returns the canonical JSON value.
    ///
    /// @return the serialized JSON value
    public String toJsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
