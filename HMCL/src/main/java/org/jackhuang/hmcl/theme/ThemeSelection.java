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

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Identifies one installed theme selected by the launcher.
///
/// @param packId the theme-pack identifier
/// @param version the theme-pack version
/// @param themeId the selected theme identifier inside the pack
@NotNullByDefault
public record ThemeSelection(
        @SerializedName("packId") String packId,
        @SerializedName("version") String version,
        @SerializedName("themeId") String themeId) {

    /// Creates a theme selection reference.
    ///
    /// @param packId the theme-pack identifier
    /// @param version the theme-pack version
    /// @param themeId the selected theme identifier inside the pack
    public ThemeSelection {
        packId = requireNonBlank(packId, "packId");
        version = requireNonBlank(version, "version");
        themeId = requireNonBlank(themeId, "themeId");
    }

    /// Returns a non-blank identifier value.
    private static String requireNonBlank(String value, String name) {
        String trimmed = Objects.requireNonNull(value).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Theme selection field is blank: " + name);
        }
        return trimmed;
    }
}
