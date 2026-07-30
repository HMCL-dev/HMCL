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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Identifies a built-in launcher background wallpaper.
@NotNullByDefault
public enum BuiltinBackground {
    /// Built-in launcher wallpaper from 2021-08-26.
    WALLPAPER_2021_08_26("2021-08-26", "#3F6AA2"),

    /// Built-in launcher wallpaper from 2016-02-25.
    WALLPAPER_2016_02_25("2016-02-25", "#354264"),

    /// Built-in launcher wallpaper from 2015-06-22.
    WALLPAPER_2015_06_22("2015-06-22", "#FBC578"),
    ;

    /// Built-in wallpaper used when a wallpaper ID is missing or unsupported.
    public static final BuiltinBackground FALLBACK = WALLPAPER_2021_08_26;

    /// Built-in wallpapers keyed by serialized wallpaper ID.
    public static final @Unmodifiable Map<String, BuiltinBackground> BUILTIN_BACKGROUNDS;

    /// Serialized IDs of launcher built-in wallpapers in display order.
    public static final @Unmodifiable List<String> BUILTIN_BACKGROUND_IDS;

    static {
        var backgrounds = new LinkedHashMap<String, BuiltinBackground>();
        for (BuiltinBackground value : values()) {
            backgrounds.put(value.id(), value);
        }
        BUILTIN_BACKGROUNDS = Collections.unmodifiableMap(backgrounds);
        BUILTIN_BACKGROUND_IDS = List.copyOf(backgrounds.keySet());
    }

    /// Serialized wallpaper ID.
    private final String id;

    /// Precomputed MonetFX seed color extracted from this wallpaper.
    private final ThemeColor themeColor;

    /// Creates a built-in wallpaper entry.
    ///
    /// @param id         the serialized wallpaper ID
    /// @param themeColor the precomputed MonetFX seed color
    BuiltinBackground(String id, String themeColor) {
        this.id = id;
        this.themeColor = Objects.requireNonNull(ThemeColor.of(themeColor));
    }

    /// Returns the serialized wallpaper ID.
    public String id() {
        return id;
    }

    /// Returns the precomputed MonetFX seed color extracted from this wallpaper.
    public ThemeColor themeColor() {
        return themeColor;
    }

    /// Returns the built-in wallpaper for a serialized ID, or `null` when the ID is not supported.
    ///
    /// @param id the serialized wallpaper ID
    /// @return the matching built-in wallpaper, or `null`
    public static @Nullable BuiltinBackground fromId(@Nullable String id) {
        return BUILTIN_BACKGROUNDS.get(id);
    }

    /// Returns the built-in wallpaper for a serialized ID, or the fallback wallpaper when the ID is not supported.
    ///
    /// @param id the serialized wallpaper ID
    /// @return the matching built-in wallpaper, or the fallback wallpaper
    public static BuiltinBackground fromIdOrFallback(@Nullable String id) {
        BuiltinBackground background = fromId(id);
        return background != null ? background : FALLBACK;
    }
}
