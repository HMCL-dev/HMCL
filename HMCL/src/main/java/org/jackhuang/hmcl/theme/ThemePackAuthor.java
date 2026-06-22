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

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Author metadata declared by a theme pack.
///
/// @param name the localized author display name
@NotNullByDefault
public record ThemePackAuthor(LocalizedText name) {
    /// JSON member name for the author display name.
    static final String FIELD_NAME = "name";

    /// Creates theme-pack author metadata.
    ///
    /// @param name the localized author display name
    public ThemePackAuthor {
        name = ThemePackManifest.requireLocalizedText(name, FIELD_NAME);
    }

    /// Returns the author display name in the current locale.
    ///
    /// @return the localized author display name
    public String displayName() {
        return Objects.requireNonNullElse(name.getText(I18n.getLocale().getCandidateLocales()), "");
    }

    /// Converts this author to its JSON representation.
    ///
    /// @return the JSON object representing this author
    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        object.add(FIELD_NAME, JsonUtils.GSON.toJsonTree(name, LocalizedText.class));
        return object;
    }
}
