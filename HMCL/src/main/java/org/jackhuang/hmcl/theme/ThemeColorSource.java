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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Describes how a theme-pack appearance chooses its Monet seed color.
///
/// @param type the color source type
/// @param fixedColor the fixed color for [Type#FIXED], or `null` otherwise
/// @param fallback the fallback color for [Type#WALLPAPER], or `null` to use the launcher default
@NotNullByDefault
public record ThemeColorSource(Type type, @Nullable ThemeColor fixedColor, @Nullable ThemeColor fallback) {
    /// JSON member name for the source type.
    private static final String FIELD_SOURCE = "source";

    /// JSON member name for the fallback color.
    private static final String FIELD_FALLBACK = "fallback";

    /// Fields accepted by the wallpaper color source object.
    private static final Set<String> FIELDS = Set.of(FIELD_SOURCE, FIELD_FALLBACK);

    /// Supported color source types.
    public enum Type {
        /// Use a fixed manifest color.
        FIXED,

        /// Extract the seed color from the effective wallpaper image.
        WALLPAPER
    }

    /// Creates a color source.
    ///
    /// @param type the color source type
    /// @param fixedColor the fixed color for [Type#FIXED], or `null` otherwise
    /// @param fallback the fallback color for [Type#WALLPAPER], or `null` to use the launcher default
    public ThemeColorSource {
        Objects.requireNonNull(type);
        if (type == Type.FIXED) {
            Objects.requireNonNull(fixedColor);
            fallback = null;
        } else {
            fixedColor = null;
        }
    }

    /// Creates a fixed color source.
    ///
    /// @param color the fixed color
    /// @return the fixed color source
    public static ThemeColorSource fixed(ThemeColor color) {
        return new ThemeColorSource(Type.FIXED, color, null);
    }

    /// Creates a wallpaper color source.
    ///
    /// @param fallback the fallback color, or `null` to use the launcher default
    /// @return the wallpaper color source
    public static ThemeColorSource wallpaper(@Nullable ThemeColor fallback) {
        return new ThemeColorSource(Type.WALLPAPER, null, fallback);
    }

    /// Parses a color source from a manifest JSON value.
    ///
    /// @param element the JSON value
    /// @return the parsed color source
    /// @throws JsonParseException if the color source is malformed
    static ThemeColorSource fromJson(JsonElement element) throws JsonParseException {
        Objects.requireNonNull(element);
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            return fixed(parseColor(primitive.getAsString(), "color"));
        }
        if (!(element instanceof JsonObject object)) {
            throw new JsonParseException("Theme color must be a string or object");
        }

        checkUnknownFields(object);
        String source = readRequiredString(object, FIELD_SOURCE);
        String normalized = source.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "WALLPAPER" -> wallpaper(readColor(object, FIELD_FALLBACK));
            default -> throw new JsonParseException("Unsupported theme color source: " + source);
        };
    }

    /// Converts this color source to its JSON representation.
    ///
    /// @return the color source JSON value
    public JsonElement toJsonElement() {
        if (type == Type.FIXED) {
            return new JsonPrimitive(Objects.requireNonNull(fixedColor).name());
        }

        JsonObject object = new JsonObject();
        object.addProperty(FIELD_SOURCE, "wallpaper");
        if (fallback != null) {
            object.addProperty(FIELD_FALLBACK, fallback.name());
        }
        return object;
    }

    /// Returns the best available color without accessing wallpaper pixels.
    ///
    /// @return the fixed color, fallback color, or launcher default color
    public ThemeColor resolveFallback() {
        return switch (type) {
            case FIXED -> Objects.requireNonNull(fixedColor);
            case WALLPAPER -> Objects.requireNonNullElse(fallback, ThemeColor.DEFAULT);
        };
    }

    /// Checks that no unsupported fields are present.
    private static void checkUnknownFields(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!FIELDS.contains(entry.getKey())) {
                throw new JsonParseException("Unsupported theme color source field: " + entry.getKey());
            }
        }
    }

    /// Reads a required string field.
    private static String readRequiredString(JsonObject object, String field) {
        @Nullable String value = readString(object, field);
        if (value == null) {
            throw new JsonParseException("Theme color source is missing required field: " + field);
        }
        return value;
    }

    /// Reads an optional color field.
    private static @Nullable ThemeColor readColor(JsonObject object, String field) {
        @Nullable String value = readString(object, field);
        return value != null ? parseColor(value, field) : null;
    }

    /// Reads an optional string field.
    private static @Nullable String readString(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null) {
            return null;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new JsonParseException("Theme color source field must be a string: " + field);
        }
        return primitive.getAsString();
    }

    /// Parses a theme color value.
    private static ThemeColor parseColor(String value, String field) {
        @Nullable ThemeColor color = ThemeColor.of(value);
        if (color == null) {
            throw new JsonParseException("Invalid theme color " + field + ": " + value);
        }
        return color;
    }
}
