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

/// Background settings contributed by a theme-pack appearance.
///
/// @param source the background source, or `null` when inherited
/// @param opacity the background opacity override, or `null` when inherited
@NotNullByDefault
public record ThemeBackgroundSettings(
        @Nullable ThemeBackground source,
        @Nullable Double opacity) {
    /// JSON member name for the background opacity.
    private static final String FIELD_OPACITY = "opacity";

    /// Creates background settings.
    ///
    /// @param source the background source, or `null` when inherited
    /// @param opacity the background opacity override, or `null` when inherited
    public ThemeBackgroundSettings {
        if (opacity != null && (opacity < 0.0 || opacity > 1.0 || !Double.isFinite(opacity))) {
            throw new IllegalArgumentException("Theme background opacity must be between 0 and 1: " + opacity);
        }
    }

    /// Parses background settings from a JSON object.
    ///
    /// @param object the JSON object
    /// @return the parsed background settings
    static ThemeBackgroundSettings fromJson(JsonObject object) throws JsonParseException {
        Objects.requireNonNull(object);

        return new ThemeBackgroundSettings(
                ThemeBackground.fromJson(object),
                readOpacity(object));
    }

    /// Converts these settings to their JSON representation.
    ///
    /// @return the JSON object representing these background settings
    public JsonObject toJsonObject() {
        JsonObject object = source != null ? source.toJsonObject() : new JsonObject();
        if (opacity != null) {
            object.addProperty(FIELD_OPACITY, opacity);
        }
        return object;
    }

    /// Returns whether these settings contain no concrete fields.
    ///
    /// @return `true` when every background setting is inherited
    public boolean isEmpty() {
        return source == null && opacity == null;
    }

    /// Returns settings that apply the given patch over these settings.
    ///
    /// @param patch the patch to apply
    /// @return the merged settings
    public ThemeBackgroundSettings merge(ThemeBackgroundSettings patch) {
        Objects.requireNonNull(patch);

        @Nullable ThemeBackground mergedSource = patch.source != null ? patch.source : source;
        @Nullable Double mergedOpacity = patch.opacity != null ? patch.opacity : opacity;
        return new ThemeBackgroundSettings(mergedSource, mergedOpacity);
    }

    /// Reads the optional opacity field.
    private static @Nullable Double readOpacity(JsonObject object) {
        JsonElement element = object.get(FIELD_OPACITY);
        if (element == null) {
            return null;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            LOG.warning("Ignored invalid theme background opacity: expected a number, got " + element);
            return null;
        }

        try {
            double opacity = primitive.getAsDouble();
            if (opacity < 0.0 || opacity > 1.0 || !Double.isFinite(opacity)) {
                LOG.warning("Ignored invalid theme background opacity: expected a number between 0 and 1, got "
                        + opacity);
                return null;
            }
            return opacity;
        } catch (NumberFormatException e) {
            LOG.warning("Ignored invalid theme background opacity: " + e.getMessage(), e);
            return null;
        }
    }
}
