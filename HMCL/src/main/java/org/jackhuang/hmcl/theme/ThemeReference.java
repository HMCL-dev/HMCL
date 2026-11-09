// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.theme;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Identifies one theme declared by an installed theme pack.
///
/// @param packId the theme-pack identifier
/// @param themeId the selected theme identifier inside the pack, or `null` for an unnamed single-theme pack
@NotNullByDefault
@JsonSerializable
public record ThemeReference(
        @SerializedName("packId") String packId,
        @SerializedName("themeId") @Nullable String themeId) {

    /// Creates a theme reference.
    ///
    /// @param packId the theme-pack identifier
    /// @param themeId the selected theme identifier inside the pack, or `null` for an unnamed single-theme pack
    public ThemeReference {
        packId = ThemePackManifest.requirePackageId(packId);
        if (themeId != null) {
            themeId = ThemePackManifest.requireThemeId(themeId);
        }
    }
}
