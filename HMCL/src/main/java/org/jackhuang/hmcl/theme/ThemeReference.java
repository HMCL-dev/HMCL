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
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Identifies one theme declared by an installed theme pack.
///
/// @param packId the theme-pack identifier
/// @param themeId the selected theme identifier inside the pack, or `null` for an unnamed single-theme pack
@NotNullByDefault
public record ThemeReference(
        @SerializedName("packId") String packId,
        @SerializedName("themeId") @Nullable String themeId) {

    /// Creates a theme reference.
    ///
    /// @param packId the theme-pack identifier
    /// @param themeId the selected theme identifier inside the pack, or `null` for an unnamed single-theme pack
    public ThemeReference {
        packId = requireNonBlank(packId, "packId");
        if (themeId != null) {
            themeId = requireNonBlank(themeId, "themeId");
        }
    }

    /// Returns a non-blank identifier value.
    private static String requireNonBlank(String value, String name) {
        String trimmed = Objects.requireNonNull(value).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Theme reference field is blank: " + name);
        }
        return trimmed;
    }
}
