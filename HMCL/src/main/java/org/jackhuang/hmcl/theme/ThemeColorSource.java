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

/// Describes how a theme-pack appearance chooses its Monet seed color.
@NotNullByDefault
public sealed interface ThemeColorSource permits ThemeColorSource.Default, ThemeColorSource.Custom, ThemeColorSource.Wallpaper {
    /// JSON member name for the source type.
    String FIELD_SOURCE = "source";

    /// The launcher default color source.
    ThemeColorSource DEFAULT = new Default();

    /// Creates a custom color source.
    ///
    /// @param color the custom color
    /// @return the custom color source
    static ThemeColorSource custom(ThemeColor color) {
        return new Custom(color);
    }

    /// Creates a wallpaper color source.
    ///
    /// @return the wallpaper color source
    static ThemeColorSource wallpaper() {
        return new Wallpaper();
    }

    /// Parses a color source from a manifest JSON value.
    ///
    /// @param element the JSON value
    /// @return the parsed color source
    /// @throws JsonParseException if the color source is malformed
    static ThemeColorSource fromJson(JsonElement element) throws JsonParseException {
        Objects.requireNonNull(element);
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            String value = primitive.getAsString();
            if ("default".equals(value.trim().replace('-', '_').toLowerCase(Locale.ROOT))) {
                return DEFAULT;
            }
            @Nullable ThemeColor color = ThemeColor.of(value);
            if (color == null) {
                throw new JsonParseException("Invalid theme color: " + value);
            }
            return custom(color);
        }
        if (!(element instanceof JsonObject object)) {
            throw new JsonParseException("Theme color must be a string or object");
        }

        JsonElement sourceElement = object.get(FIELD_SOURCE);
        if (sourceElement == null) {
            throw new JsonParseException("Theme color source is missing required field: " + FIELD_SOURCE);
        }
        if (!(sourceElement instanceof JsonPrimitive sourcePrimitive) || !sourcePrimitive.isString()) {
            throw new JsonParseException("Theme color source field must be a string: " + FIELD_SOURCE);
        }
        String source = sourcePrimitive.getAsString();
        String normalized = source.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if ("DEFAULT".equals(normalized)) {
            return DEFAULT;
        }
        if ("WALLPAPER".equals(normalized)) {
            return wallpaper();
        }
        throw new JsonParseException("Unsupported theme color source: " + source);
    }

    /// Converts this color source to its JSON representation.
    ///
    /// @return the color source JSON value
    JsonElement toJsonElement();

    /// Returns the best available color without accessing wallpaper pixels.
    ///
    /// @return the custom color or launcher default color
    ThemeColor resolveFallback();

    /// The launcher default color seed.
    @NotNullByDefault
    record Default() implements ThemeColorSource {
        /// Creates a default color source.
        public Default {
        }

        /// Converts this color source to its JSON representation.
        @Override
        public JsonElement toJsonElement() {
            JsonObject object = new JsonObject();
            object.addProperty(FIELD_SOURCE, "default");
            return object;
        }

        /// Returns the launcher default seed color.
        @Override
        public ThemeColor resolveFallback() {
            return ThemeColor.DEFAULT;
        }
    }

    /// A fixed color seed supplied by the manifest.
    ///
    /// @param color the custom seed color
    @NotNullByDefault
    record Custom(ThemeColor color) implements ThemeColorSource {
        /// Creates a custom color source.
        ///
        /// @param color the custom seed color
        public Custom {
            Objects.requireNonNull(color);
        }

        /// Converts this color source to its JSON representation.
        @Override
        public JsonElement toJsonElement() {
            return new JsonPrimitive(color.name());
        }

        /// Returns the custom seed color.
        @Override
        public ThemeColor resolveFallback() {
            return color;
        }
    }

    /// A dynamic seed color extracted from the effective wallpaper.
    @NotNullByDefault
    record Wallpaper() implements ThemeColorSource {
        /// Creates a wallpaper color source.
        public Wallpaper {
        }

        /// Converts this color source to its JSON representation.
        @Override
        public JsonElement toJsonElement() {
            JsonObject object = new JsonObject();
            object.addProperty(FIELD_SOURCE, "wallpaper");
            return object;
        }

        /// Returns the launcher default color used before wallpaper pixels are available.
        @Override
        public ThemeColor resolveFallback() {
            return ThemeColor.DEFAULT;
        }
    }
}
