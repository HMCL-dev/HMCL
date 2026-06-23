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
        permits ThemeBackground.Builtin, ThemeBackground.Image,
        ThemeBackground.Network, ThemeBackground.Paint, ThemeBackground.Patch {
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

    /// Returns whether this background source contains no concrete fields.
    ///
    /// @return `true` when every source field is inherited
    boolean isEmpty();

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

    /// Returns a background source that applies the given patch over this source.
    ///
    /// @param patch the patch to apply
    /// @return the merged background source
    default ThemeBackground merge(ThemeBackground patch) {
        Objects.requireNonNull(patch);

        if (patch instanceof Patch partialPatch) {
            return partialPatch.applyOver(this);
        }
        if (patch instanceof Builtin) {
            return patch;
        }
        if (patch instanceof Image patchImage) {
            @Nullable String path = patchImage.path;
            if (path == null) {
                if (this instanceof Image image) {
                    path = image.path;
                } else if (this instanceof Patch basePatch) {
                    path = basePatch.path;
                }
            }
            return new Image(path);
        }
        if (patch instanceof Network patchNetwork) {
            @Nullable String url = patchNetwork.url;
            @Nullable NetworkBackgroundImageCachePolicy cache = patchNetwork.cache;
            if (url == null) {
                if (this instanceof Network network) {
                    url = network.url;
                } else if (this instanceof Patch basePatch) {
                    url = basePatch.url;
                }
            }
            if (cache == null) {
                if (this instanceof Network network) {
                    cache = network.cache;
                } else if (this instanceof Patch basePatch) {
                    cache = basePatch.cache;
                }
            }
            return new Network(url, cache);
        }
        if (patch instanceof Paint patchPaint) {
            @Nullable String paint = patchPaint.paint;
            if (paint == null) {
                if (this instanceof Paint basePaint) {
                    paint = basePaint.paint;
                } else if (this instanceof Patch basePatch) {
                    paint = basePatch.paint;
                }
            }
            return new Paint(paint);
        }
        return patch;
    }

    /// Parses a background source from a JSON object.
    ///
    /// @param object the JSON object
    /// @return the parsed background source
    /// @throws JsonParseException if a known field is malformed
    static ThemeBackground fromJson(JsonObject object) throws JsonParseException {
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
            return new Patch(path, url, paint, cache);
        }

        return switch (type.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "BUILTIN" -> new Builtin(id);
            case "IMAGE" -> new Image(path);
            case "NETWORK" -> new Network(url, cache);
            case "PAINT" -> new Paint(paint);
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
    private static String requireNonBlank(String value, String field) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Theme background field is blank: " + field);
        }
        return trimmed;
    }

    /// A source that uses a launcher built-in wallpaper.
    ///
    /// @param id the built-in wallpaper ID, or `null` for the current default built-in wallpaper
    @NotNullByDefault
    record Builtin(@Nullable String id) implements ThemeBackground {
        /// Creates a source that uses the current default built-in wallpaper.
        public Builtin() {
            this(null);
        }

        /// Creates a built-in wallpaper source.
        ///
        /// @param id the built-in wallpaper ID, or `null` for the current default built-in wallpaper
        public Builtin {
            if (id != null) {
                id = requireNonBlank(id, FIELD_ID);
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

        /// Returns whether this source is empty.
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /// A source that uses an image stored inside the theme pack.
    ///
    /// @param path the theme-pack relative image path, or `null` when inherited
    @NotNullByDefault
    record Image(@Nullable String path) implements ThemeBackground {
        /// Creates an image background source.
        ///
        /// @param path the theme-pack relative image path, or `null` when inherited
        public Image {
            if (path != null) {
                path = requireNonBlank(path, FIELD_PATH);
            }
        }

        /// Adds this source to a JSON object.
        @Override
        public void addToJsonObject(JsonObject object) {
            addType(object, "image");
            if (path != null) {
                object.addProperty(FIELD_PATH, path);
            }
        }

        /// Returns whether this source is empty.
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /// A source that uses a remote background image URL.
    ///
    /// @param url the remote image URL, or `null` when inherited
    /// @param cache the remote image cache policy, or `null` when inherited
    @NotNullByDefault
    record Network(@Nullable String url, @Nullable NetworkBackgroundImageCachePolicy cache) implements ThemeBackground {
        /// Creates a network background source.
        ///
        /// @param url the remote image URL, or `null` when inherited
        /// @param cache the remote image cache policy, or `null` when inherited
        public Network {
            if (url != null) {
                url = requireNonBlank(url, FIELD_URL);
            }
        }

        /// Adds this source to a JSON object.
        @Override
        public void addToJsonObject(JsonObject object) {
            addType(object, "network");
            if (url != null) {
                object.addProperty(FIELD_URL, url);
            }
            if (cache != null) {
                object.addProperty(FIELD_CACHE, switch (cache) {
                    case ENABLED -> "enabled";
                    case DISABLED -> "disabled";
                });
            }
        }

        /// Returns whether this source is empty.
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /// A source that uses a JavaFX paint string.
    ///
    /// @param paint the serialized paint value, or `null` when inherited
    @NotNullByDefault
    record Paint(@Nullable String paint) implements ThemeBackground {
        /// Creates a paint background source.
        ///
        /// @param paint the serialized paint value, or `null` when inherited
        public Paint {
            if (paint != null) {
                paint = requireNonBlank(paint, FIELD_PAINT);
            }
        }

        /// Adds this source to a JSON object.
        @Override
        public void addToJsonObject(JsonObject object) {
            addType(object, "paint");
            if (paint != null) {
                object.addProperty(FIELD_PAINT, paint);
            }
        }

        /// Returns whether this source is empty.
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /// A partial background source without an explicit source type.
    ///
    /// @param path the theme-pack relative image path, or `null` when inherited
    /// @param url the remote image URL, or `null` when inherited
    /// @param paint the serialized paint value, or `null` when inherited
    /// @param cache the remote image cache policy, or `null` when inherited
    @NotNullByDefault
    record Patch(
            @Nullable String path,
            @Nullable String url,
            @Nullable String paint,
            @Nullable NetworkBackgroundImageCachePolicy cache) implements ThemeBackground {
        /// Creates a partial background source.
        ///
        /// @param path the theme-pack relative image path, or `null` when inherited
        /// @param url the remote image URL, or `null` when inherited
        /// @param paint the serialized paint value, or `null` when inherited
        /// @param cache the remote image cache policy, or `null` when inherited
        public Patch {
            if (path != null) {
                path = requireNonBlank(path, FIELD_PATH);
            }
            if (url != null) {
                url = requireNonBlank(url, FIELD_URL);
            }
            if (paint != null) {
                paint = requireNonBlank(paint, FIELD_PAINT);
            }
        }

        /// Adds this source to a JSON object.
        @Override
        public void addToJsonObject(JsonObject object) {
            if (path != null) {
                object.addProperty(FIELD_PATH, path);
            }
            if (url != null) {
                object.addProperty(FIELD_URL, url);
            }
            if (paint != null) {
                object.addProperty(FIELD_PAINT, paint);
            }
            if (cache != null) {
                object.addProperty(FIELD_CACHE, switch (cache) {
                    case ENABLED -> "enabled";
                    case DISABLED -> "disabled";
                });
            }
        }

        /// Returns whether this patch is empty.
        @Override
        public boolean isEmpty() {
            return path == null && url == null && paint == null && cache == null;
        }

        /// Applies this partial source over a base source.
        ///
        /// @param base the base source
        /// @return the merged source
        private ThemeBackground applyOver(ThemeBackground base) {
            if (base instanceof Builtin) {
                return base;
            }
            if (base instanceof Image background) {
                return new Image(path != null ? path : background.path);
            }
            if (base instanceof Network background) {
                return new Network(
                        url != null ? url : background.url,
                        cache != null ? cache : background.cache);
            }
            if (base instanceof Paint background) {
                return new Paint(paint != null ? paint : background.paint);
            }
            if (base instanceof Patch background) {
                return new Patch(
                        path != null ? path : background.path,
                        url != null ? url : background.url,
                        paint != null ? paint : background.paint,
                        cache != null ? cache : background.cache);
            }
            throw new IllegalArgumentException("Unsupported theme background: " + base);
        }
    }
}
