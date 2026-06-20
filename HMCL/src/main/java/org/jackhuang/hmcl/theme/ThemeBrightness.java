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

/// Describes how a theme-pack appearance chooses its effective MonetFX brightness.
@NotNullByDefault
public enum ThemeBrightness {
    /// Always resolves to the light color scheme.
    LIGHT,

    /// Always resolves to the dark color scheme.
    DARK,

    /// Resolves to the brightness provided by the current theme resolution context.
    ADAPTIVE;

    /// Parses a serialized theme brightness directive.
    ///
    /// @param value the serialized brightness value
    /// @return the parsed brightness directive
    /// @throws IllegalArgumentException if the value is not supported
    public static ThemeBrightness parse(String value) {
        Objects.requireNonNull(value);

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "light" -> LIGHT;
            case "dark" -> DARK;
            case "adaptive", "auto" -> ADAPTIVE;
            default -> throw new IllegalArgumentException("Unsupported theme brightness: " + value);
        };
    }

    /// Resolves this directive to a concrete MonetFX brightness.
    ///
    /// @param contextBrightness the brightness resolved from the current launcher or system mode
    /// @return the concrete brightness to use for a color scheme
    public Brightness resolve(Brightness contextBrightness) {
        Objects.requireNonNull(contextBrightness);

        return switch (this) {
            case LIGHT -> Brightness.LIGHT;
            case DARK -> Brightness.DARK;
            case ADAPTIVE -> contextBrightness;
        };
    }

    /// Returns the canonical JSON value for this brightness directive.
    ///
    /// @return the serialized brightness value
    public String toJsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
