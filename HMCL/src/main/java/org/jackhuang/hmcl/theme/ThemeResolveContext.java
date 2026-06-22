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
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/// Environment values used to resolve conditional theme-pack overrides.
///
/// @param brightness the effective light or dark mode after resolving launcher and system settings
/// @param os the normalized operating system name used by theme conditions
/// @param language the normalized UI language subtag used by theme conditions
@NotNullByDefault
public record ThemeResolveContext(
        Brightness brightness,
        String os,
        String language) {

    /// Creates a theme resolution context.
    ///
    /// @param brightness the effective light or dark mode after resolving launcher and system settings
    /// @param os the normalized operating system name used by theme conditions
    /// @param language the normalized UI language subtag used by theme conditions
    public ThemeResolveContext {
        Objects.requireNonNull(brightness);
        os = normalizeToken(os, "os");
        language = normalizeToken(language, "language");
    }

    /// Creates a context for the current process platform.
    ///
    /// @param brightness the effective light or dark mode after resolving launcher and system settings
    /// @return a context containing the current OS and default language
    public static ThemeResolveContext current(Brightness brightness) {
        return new ThemeResolveContext(
                brightness,
                normalizeOperatingSystem(OperatingSystem.CURRENT_OS),
                Locale.getDefault().getLanguage());
    }

    /// Returns the normalized value for one supported condition key.
    ///
    /// @param key the condition key
    /// @return the normalized context value, or `null` when the key is unsupported
    @Nullable String conditionValue(String key) {
        return switch (key) {
            case ThemeCondition.KEY_BRIGHTNESS -> brightness.name().toLowerCase(Locale.ROOT);
            case ThemeCondition.KEY_OS -> os;
            case ThemeCondition.KEY_LANGUAGE -> language;
            default -> null;
        };
    }

    /// Returns the normalized name for an operating system.
    ///
    /// @param operatingSystem the operating system to normalize
    /// @return the normalized operating system name
    static String normalizeOperatingSystem(OperatingSystem operatingSystem) {
        Objects.requireNonNull(operatingSystem);

        return operatingSystem == OperatingSystem.UNKNOWN ? "unknown" : operatingSystem.getCheckedName();
    }

    /// Normalizes one condition context token.
    private static String normalizeToken(String value, String name) {
        Objects.requireNonNull(value, name);

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Empty theme condition context value: " + name);
        }
        return normalized;
    }
}
