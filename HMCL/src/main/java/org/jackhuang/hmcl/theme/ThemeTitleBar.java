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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Title-bar settings contributed by a theme-pack appearance.
///
/// @param transparent whether the launcher title bar should be transparent, or `null` when inherited
@NotNullByDefault
public record ThemeTitleBar(@Nullable Boolean transparent) {
    /// JSON member name for title-bar transparency.
    private static final String FIELD_TRANSPARENT = "transparent";

    /// Creates a title-bar patch.
    ///
    /// @param transparent whether the launcher title bar should be transparent, or `null` when inherited
    public ThemeTitleBar {
    }

    /// Parses title-bar settings from a JSON object.
    ///
    /// @param object the title-bar JSON object
    /// @return the parsed title-bar patch
    static ThemeTitleBar fromJson(JsonObject object) throws JsonParseException {
        Objects.requireNonNull(object);

        return new ThemeTitleBar(readTransparent(object));
    }

    /// Converts this title-bar patch to its JSON representation.
    ///
    /// @return the JSON object representing this title-bar patch
    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        if (transparent != null) {
            object.addProperty(FIELD_TRANSPARENT, transparent);
        }
        return object;
    }

    /// Returns whether this title-bar object contains no concrete fields.
    ///
    /// @return `true` when every field is inherited
    public boolean isEmpty() {
        return transparent == null;
    }

    /// Returns a title-bar patch that applies the given patch over this title-bar patch.
    ///
    /// @param patch the patch to apply
    /// @return the merged title-bar patch
    public ThemeTitleBar merge(ThemeTitleBar patch) {
        Objects.requireNonNull(patch);

        return new ThemeTitleBar(patch.transparent != null ? patch.transparent : transparent);
    }

    /// Reads the optional transparent field.
    private static @Nullable Boolean readTransparent(JsonObject object) {
        JsonElement element = object.get(FIELD_TRANSPARENT);
        if (element == null) {
            return null;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            LOG.warning("Ignored invalid theme titleBar.transparent: expected a boolean, got " + element);
            return null;
        }
        return primitive.getAsBoolean();
    }
}
