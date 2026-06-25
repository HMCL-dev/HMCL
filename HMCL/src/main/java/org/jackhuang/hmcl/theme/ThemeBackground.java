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

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Background source contributed by a theme-pack appearance.
///
/// The sealed implementations describe where the wallpaper comes from. Rendering
/// parameters such as opacity are stored by [ThemeBackgroundSettings].
@NotNullByDefault
public sealed interface ThemeBackground
        permits ThemeBackground.Default, ThemeBackground.Builtin, ThemeBackground.Image,
        ThemeBackground.Network, ThemeBackground.Paint,
        ThemeBackground.ThemeColor {
    /// JSON member name for the background type.
    String FIELD_TYPE = "type";

    /// JSON member name for the built-in wallpaper ID.
    String FIELD_ID = "id";

    /// JSON member name for the local theme-pack image path.
    String FIELD_PATH = "path";

    /// JSON member name for a remote background URL.
    String FIELD_URL = "url";

    /// JSON member name for a serialized paint value.
    String FIELD_PAINT = "paint";

    /// JSON member name for the remote image cache policy.
    String FIELD_CACHE = "cache";

    /// Adds this background source's concrete fields to a JSON object.
    ///
    /// @param object the target JSON object
    void addToJsonObject(JsonObject object);

    /// Converts this background source to its JSON representation.
    ///
    /// @return the JSON object representing this background source
    default JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        addToJsonObject(object);
        return object;
    }

    /// Parses a background source from a JSON object.
    ///
    /// @param object the JSON object
    /// @return the parsed background source, or `null` when no source field is present
    /// @throws JsonParseException if a known field is malformed
    static @Nullable ThemeBackground fromJson(JsonObject object) throws JsonParseException {
        Objects.requireNonNull(object);

        @Nullable String type = readString(object, FIELD_TYPE);
        @Nullable String id = readString(object, FIELD_ID);
        @Nullable String path = readString(object, FIELD_PATH);
        @Nullable String url = readString(object, FIELD_URL);
        @Nullable String paint = readString(object, FIELD_PAINT);
        @Nullable NetworkBackgroundImageCachePolicy cache = null;
        JsonElement cacheElement = object.get(FIELD_CACHE);
        if (cacheElement != null) {
            if (!(cacheElement instanceof JsonPrimitive primitive) || !primitive.isString()) {
                LOG.warning("Ignored invalid theme background cache: expected a string, got " + cacheElement);
            } else {
                String cacheValue = primitive.getAsString();
                switch (cacheValue.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
                    case "ENABLED" -> cache = NetworkBackgroundImageCachePolicy.ENABLED;
                    case "DISABLED" -> cache = NetworkBackgroundImageCachePolicy.DISABLED;
                    default -> LOG.warning(
                            "Ignored invalid theme background cache: unsupported cache policy `" + cacheValue + "`");
                }
            }
        }

        if (type == null) {
            int sourceFields = 0;
            if (id != null) {
                sourceFields++;
            }
            if (path != null) {
                sourceFields++;
            }
            if (url != null) {
                sourceFields++;
            }
            if (paint != null) {
                sourceFields++;
            }
            if (sourceFields > 1) {
                throw new JsonParseException("Theme background without type must contain only one source field");
            }
            if (id != null) {
                return new Builtin(id);
            }
            if (path != null) {
                return new Image(path);
            }
            if (url != null) {
                return new Network(url, cache);
            }
            if (paint != null) {
                return new Paint(paint);
            }
            if (cache != null) {
                throw new JsonParseException("Theme background cache requires a network background URL");
            }
            return null;
        }

        return switch (type.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "DEFAULT" -> new Default();
            case "BUILTIN" -> new Builtin(id);
            case "IMAGE" -> new Image(path);
            case "NETWORK" -> new Network(url, cache);
            case "PAINT" -> new Paint(paint);
            case "THEME_COLOR" -> new ThemeColor();
            default -> throw new JsonParseException("Unsupported theme background type: " + type);
        };
    }

    /// Adds a background type member to a JSON object.
    private static void addType(JsonObject object, String type) {
        object.addProperty(FIELD_TYPE, type);
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

    /// Returns a non-blank string value.
    private static @Nullable String optionalNonBlank(@Nullable String value, String field) {
        return value != null ? requireNonBlank(value, field) : null;
    }

    /// Returns a required non-blank string value.
    private static String requireNonBlank(@Nullable String value, String field) {
        if (value == null) {
            throw new JsonParseException("Theme background field is missing: " + field);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new JsonParseException("Theme background field is blank: " + field);
        }
        return trimmed;
    }

    /// A source that delegates to HMCL's launcher default background resolution.
    @NotNullByDefault
    record Default() implements ThemeBackground {
        /// Adds this source to a JSON object.
        @Override
        public void addToJsonObject(JsonObject object) {
            addType(object, "default");
        }

    }

    /// A source that uses a launcher built-in wallpaper.
    ///
    /// @param id the built-in wallpaper ID, or `null` for the fallback built-in wallpaper
    @NotNullByDefault
    record Builtin(@Nullable String id) implements ThemeBackground {
        /// Creates a source that uses the fallback built-in wallpaper.
        public Builtin() {
            this(null);
        }

        /// Creates a built-in wallpaper source.
        ///
        /// @param id the built-in wallpaper ID, or `null` for the fallback built-in wallpaper
        public Builtin {
            if (id != null) {
                id = optionalNonBlank(id, FIELD_ID);
            }
        }

        /// Adds this source to a JSON object.
        @Override
        public void addToJsonObject(JsonObject object) {
            addType(object, "builtin");
            if (id != null) {
                object.addProperty(FIELD_ID, id);
            }
        }

    }

    /// A source that uses an image stored inside the theme pack.
    ///
    /// @param path the theme-pack relative image path
    @NotNullByDefault
    record Image(String path) implements ThemeBackground {
        /// Creates an image background source.
        ///
        /// @param path the theme-pack relative image path
        public Image {
            path = requireNonBlank(path, FIELD_PATH);
        }

        /// Adds this source to a JSON object.
        @Override
        public void addToJsonObject(JsonObject object) {
            addType(object, "image");
            object.addProperty(FIELD_PATH, path);
        }

    }

    /// A source that uses a remote background image URL.
    ///
    /// @param url the remote image URL
    /// @param cache the remote image cache policy, or `null` for the default policy
    @NotNullByDefault
    record Network(String url, @Nullable NetworkBackgroundImageCachePolicy cache) implements ThemeBackground {
        /// Creates a network background source.
        ///
        /// @param url the remote image URL
        /// @param cache the remote image cache policy, or `null` for the default policy
        public Network {
            url = requireNonBlank(url, FIELD_URL);
        }

        /// Adds this source to a JSON object.
        @Override
        public void addToJsonObject(JsonObject object) {
            addType(object, "network");
            object.addProperty(FIELD_URL, url);
            if (cache != null) {
                object.addProperty(FIELD_CACHE, switch (cache) {
                    case ENABLED -> "enabled";
                    case DISABLED -> "disabled";
                });
            }
        }

    }

    /// A source that uses a JavaFX paint string.
    ///
    /// @param paint the serialized paint value
    @NotNullByDefault
    record Paint(String paint) implements ThemeBackground {
        /// Creates a paint background source.
        ///
        /// @param paint the serialized paint value
        public Paint {
            paint = requireNonBlank(paint, FIELD_PAINT);
        }

        /// Adds this source to a JSON object.
        @Override
        public void addToJsonObject(JsonObject object) {
            addType(object, "paint");
            object.addProperty(FIELD_PAINT, paint);
        }

    }

    /// A source that follows the current launcher theme color.
    @NotNullByDefault
    record ThemeColor() implements ThemeBackground {
        /// Adds this source to a JSON object.
        @Override
        public void addToJsonObject(JsonObject object) {
            addType(object, "theme_color");
        }

    }
}
