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

import java.util.Locale;
import java.util.Objects;

/// Background settings contributed by a theme-pack appearance.
///
/// The object is intentionally allowed to be partial so an override can replace only
/// the wallpaper path or opacity while inheriting other background fields.
///
/// @param type the background source type, or `null` when inherited
/// @param path the theme-pack relative image path, or `null` when inherited
/// @param url the remote image URL, or `null` when inherited
/// @param paint the serialized paint value, or `null` when inherited
/// @param opacity the background opacity, or `null` when inherited
@NotNullByDefault
public record ThemeBackground(
        @Nullable Type type,
        @Nullable String path,
        @Nullable String url,
        @Nullable String paint,
        @Nullable Double opacity) {

    /// JSON member name for the background type.
    private static final String FIELD_TYPE = "type";

    /// JSON member name for the local theme-pack image path.
    private static final String FIELD_PATH = "path";

    /// JSON member name for a remote background URL.
    private static final String FIELD_URL = "url";

    /// JSON member name for a serialized paint value.
    private static final String FIELD_PAINT = "paint";

    /// JSON member name for the background opacity.
    private static final String FIELD_OPACITY = "opacity";

    /// Background source types supported by theme packs.
    public enum Type {
        /// Use the launcher default background.
        DEFAULT,

        /// Use the launcher classic background.
        CLASSIC,

        /// Use an image stored inside the theme pack.
        IMAGE,

        /// Use a remote image URL.
        NETWORK,

        /// Use a JavaFX paint string.
        PAINT
    }

    /// Creates a theme background patch.
    ///
    /// @param type the background source type, or `null` when inherited
    /// @param path the theme-pack relative image path, or `null` when inherited
    /// @param url the remote image URL, or `null` when inherited
    /// @param paint the serialized paint value, or `null` when inherited
    /// @param opacity the background opacity, or `null` when inherited
    public ThemeBackground {
        if (path != null) {
            path = requireNonBlank(path, FIELD_PATH);
        }
        if (url != null) {
            url = requireNonBlank(url, FIELD_URL);
        }
        if (paint != null) {
            paint = requireNonBlank(paint, FIELD_PAINT);
        }
        if (opacity != null && (opacity < 0.0 || opacity > 1.0 || !Double.isFinite(opacity))) {
            throw new IllegalArgumentException("Theme background opacity must be between 0 and 1: " + opacity);
        }
    }

    /// Parses a background patch from a JSON object.
    ///
    /// @param object the JSON object
    /// @return the parsed background patch
    /// @throws JsonParseException if a known field is malformed
    static ThemeBackground fromJson(JsonObject object) throws JsonParseException {
        Objects.requireNonNull(object);

        return new ThemeBackground(
                readType(object),
                readString(object, FIELD_PATH),
                readString(object, FIELD_URL),
                readString(object, FIELD_PAINT),
                readOpacity(object));
    }

    /// Returns the effective type after inferring it from concrete source fields when needed.
    ///
    /// @return the effective background type
    public Type effectiveType() {
        if (type != null) {
            return type;
        }
        if (path != null) {
            return Type.IMAGE;
        }
        if (url != null) {
            return Type.NETWORK;
        }
        if (paint != null) {
            return Type.PAINT;
        }
        return Type.DEFAULT;
    }

    /// Converts this background patch to its JSON representation.
    ///
    /// @return the JSON object representing this background patch
    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        if (type != null) {
            object.addProperty(FIELD_TYPE, type.name().toLowerCase(Locale.ROOT));
        }
        if (path != null) {
            object.addProperty(FIELD_PATH, path);
        }
        if (url != null) {
            object.addProperty(FIELD_URL, url);
        }
        if (paint != null) {
            object.addProperty(FIELD_PAINT, paint);
        }
        if (opacity != null) {
            object.addProperty(FIELD_OPACITY, opacity);
        }
        return object;
    }

    /// Returns whether this background object contains no concrete fields.
    ///
    /// @return `true` when every field is inherited
    public boolean isEmpty() {
        return type == null && path == null && url == null && paint == null && opacity == null;
    }

    /// Returns a background that applies the given patch over this background.
    ///
    /// @param patch the patch to apply
    /// @return the merged background
    public ThemeBackground merge(ThemeBackground patch) {
        Objects.requireNonNull(patch);

        return new ThemeBackground(
                patch.type != null ? patch.type : type,
                patch.path != null ? patch.path : path,
                patch.url != null ? patch.url : url,
                patch.paint != null ? patch.paint : paint,
                patch.opacity != null ? patch.opacity : opacity);
    }

    /// Reads the optional background type field.
    private static @Nullable Type readType(JsonObject object) {
        @Nullable String value = readString(object, FIELD_TYPE);
        if (value == null) {
            return null;
        }

        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return Type.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Unsupported theme background type: " + value, e);
        }
    }

    /// Reads an optional string field.
    private static @Nullable String readString(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null) {
            return null;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new JsonParseException("Theme background field must be a string: " + field);
        }
        return primitive.getAsString();
    }

    /// Reads the optional opacity field.
    private static @Nullable Double readOpacity(JsonObject object) {
        JsonElement element = object.get(FIELD_OPACITY);
        if (element == null) {
            return null;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new JsonParseException("Theme background opacity must be a number");
        }

        double opacity = primitive.getAsDouble();
        if (opacity < 0.0 || opacity > 1.0 || !Double.isFinite(opacity)) {
            throw new JsonParseException("Theme background opacity must be between 0 and 1: " + opacity);
        }
        return opacity;
    }

    /// Returns a non-blank string value.
    private static String requireNonBlank(String value, String field) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Theme background field is blank: " + field);
        }
        return trimmed;
    }
}
