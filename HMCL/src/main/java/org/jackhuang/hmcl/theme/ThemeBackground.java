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
/// The sealed implementations describe the explicit background source when the manifest
/// contains a `type` field. [Patch] represents a partial background object without a
/// source type, so overrides can replace only the wallpaper path, paint, URL, or opacity.
@NotNullByDefault
public sealed interface ThemeBackground
        permits ThemeBackground.Builtin, ThemeBackground.Image,
        ThemeBackground.Network, ThemeBackground.Paint, ThemeBackground.Patch {
    /// JSON member name for the background type.
    String FIELD_TYPE = "type";

    /// JSON member name for the local theme-pack image path.
    String FIELD_PATH = "path";

    /// JSON member name for a remote background URL.
    String FIELD_URL = "url";

    /// JSON member name for a serialized paint value.
    String FIELD_PAINT = "paint";

    /// JSON member name for the background opacity.
    String FIELD_OPACITY = "opacity";

    /// Returns the background opacity override.
    ///
    /// @return the opacity override, or `null` when inherited
    @Nullable Double opacity();

    /// Converts this background patch to its JSON representation.
    ///
    /// @return the JSON object representing this background patch
    JsonObject toJsonObject();

    /// Returns whether this background object contains no concrete fields.
    ///
    /// @return `true` when every field is inherited
    boolean isEmpty();

    /// Returns a background that applies the given patch over this background.
    ///
    /// @param patch the patch to apply
    /// @return the merged background
    default ThemeBackground merge(ThemeBackground patch) {
        Objects.requireNonNull(patch);

        if (patch instanceof Patch partialPatch) {
            return partialPatch.applyOver(this);
        }

        @Nullable Double mergedOpacity = mergeOpacity(opacity(), patch.opacity());
        if (patch instanceof Builtin) {
            return new Builtin(mergedOpacity);
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
            return new Image(path, mergedOpacity);
        }
        if (patch instanceof Network patchNetwork) {
            @Nullable String url = patchNetwork.url;
            if (url == null) {
                if (this instanceof Network network) {
                    url = network.url;
                } else if (this instanceof Patch basePatch) {
                    url = basePatch.url;
                }
            }
            return new Network(url, mergedOpacity);
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
            return new Paint(paint, mergedOpacity);
        }
        return patch;
    }

    /// Parses a background patch from a JSON object.
    ///
    /// @param object the JSON object
    /// @return the parsed background patch
    /// @throws JsonParseException if a known field is malformed
    static ThemeBackground fromJson(JsonObject object) throws JsonParseException {
        Objects.requireNonNull(object);

        @Nullable String type = readString(object, FIELD_TYPE);
        @Nullable String path = readString(object, FIELD_PATH);
        @Nullable String url = readString(object, FIELD_URL);
        @Nullable String paint = readString(object, FIELD_PAINT);
        @Nullable Double opacity = readOpacity(object);

        if (type == null) {
            return new Patch(path, url, paint, opacity);
        }

        return switch (type.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "BUILTIN" -> new Builtin(opacity);
            case "IMAGE" -> new Image(path, opacity);
            case "NETWORK" -> new Network(url, opacity);
            case "PAINT" -> new Paint(paint, opacity);
            default -> throw new JsonParseException("Unsupported theme background type: " + type);
        };
    }

    /// Adds a background type member to a JSON object.
    private static void addType(JsonObject object, String type) {
        object.addProperty(FIELD_TYPE, type);
    }

    /// Adds the opacity member to a JSON object when it is present.
    private static void addOpacity(JsonObject object, @Nullable Double opacity) {
        if (opacity != null) {
            object.addProperty(FIELD_OPACITY, opacity);
        }
    }

    /// Returns the patch opacity when present, otherwise the base opacity.
    private static @Nullable Double mergeOpacity(@Nullable Double baseOpacity, @Nullable Double patchOpacity) {
        return patchOpacity != null ? patchOpacity : baseOpacity;
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

    /// Validates an opacity value.
    private static @Nullable Double validateOpacity(@Nullable Double opacity) {
        if (opacity != null && (opacity < 0.0 || opacity > 1.0 || !Double.isFinite(opacity))) {
            throw new IllegalArgumentException("Theme background opacity must be between 0 and 1: " + opacity);
        }
        return opacity;
    }

    /// A directive that uses the launcher's built-in default background.
    ///
    /// @param opacity the background opacity override, or `null` when inherited
    @NotNullByDefault
    record Builtin(@Nullable Double opacity) implements ThemeBackground {
        /// Creates a built-in-background directive.
        ///
        /// @param opacity the background opacity override, or `null` when inherited
        public Builtin {
            opacity = validateOpacity(opacity);
        }

        /// Converts this directive to its JSON representation.
        @Override
        public JsonObject toJsonObject() {
            JsonObject object = new JsonObject();
            addType(object, "builtin");
            addOpacity(object, opacity);
            return object;
        }

        /// Returns whether this directive is empty.
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /// A directive that uses an image stored inside the theme pack.
    ///
    /// @param path the theme-pack relative image path, or `null` when inherited
    /// @param opacity the background opacity override, or `null` when inherited
    @NotNullByDefault
    record Image(@Nullable String path, @Nullable Double opacity) implements ThemeBackground {
        /// Creates an image-background directive.
        ///
        /// @param path the theme-pack relative image path, or `null` when inherited
        /// @param opacity the background opacity override, or `null` when inherited
        public Image {
            if (path != null) {
                path = requireNonBlank(path, FIELD_PATH);
            }
            opacity = validateOpacity(opacity);
        }

        /// Converts this directive to its JSON representation.
        @Override
        public JsonObject toJsonObject() {
            JsonObject object = new JsonObject();
            addType(object, "image");
            if (path != null) {
                object.addProperty(FIELD_PATH, path);
            }
            addOpacity(object, opacity);
            return object;
        }

        /// Returns whether this directive is empty.
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /// A directive that uses a remote background image URL.
    ///
    /// @param url the remote image URL, or `null` when inherited
    /// @param opacity the background opacity override, or `null` when inherited
    @NotNullByDefault
    record Network(@Nullable String url, @Nullable Double opacity) implements ThemeBackground {
        /// Creates a network-background directive.
        ///
        /// @param url the remote image URL, or `null` when inherited
        /// @param opacity the background opacity override, or `null` when inherited
        public Network {
            if (url != null) {
                url = requireNonBlank(url, FIELD_URL);
            }
            opacity = validateOpacity(opacity);
        }

        /// Converts this directive to its JSON representation.
        @Override
        public JsonObject toJsonObject() {
            JsonObject object = new JsonObject();
            addType(object, "network");
            if (url != null) {
                object.addProperty(FIELD_URL, url);
            }
            addOpacity(object, opacity);
            return object;
        }

        /// Returns whether this directive is empty.
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /// A directive that uses a JavaFX paint string.
    ///
    /// @param paint the serialized paint value, or `null` when inherited
    /// @param opacity the background opacity override, or `null` when inherited
    @NotNullByDefault
    record Paint(@Nullable String paint, @Nullable Double opacity) implements ThemeBackground {
        /// Creates a paint-background directive.
        ///
        /// @param paint the serialized paint value, or `null` when inherited
        /// @param opacity the background opacity override, or `null` when inherited
        public Paint {
            if (paint != null) {
                paint = requireNonBlank(paint, FIELD_PAINT);
            }
            opacity = validateOpacity(opacity);
        }

        /// Converts this directive to its JSON representation.
        @Override
        public JsonObject toJsonObject() {
            JsonObject object = new JsonObject();
            addType(object, "paint");
            if (paint != null) {
                object.addProperty(FIELD_PAINT, paint);
            }
            addOpacity(object, opacity);
            return object;
        }

        /// Returns whether this directive is empty.
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /// A partial background object without an explicit source type.
    ///
    /// @param path the theme-pack relative image path, or `null` when inherited
    /// @param url the remote image URL, or `null` when inherited
    /// @param paint the serialized paint value, or `null` when inherited
    /// @param opacity the background opacity override, or `null` when inherited
    @NotNullByDefault
    record Patch(
            @Nullable String path,
            @Nullable String url,
            @Nullable String paint,
            @Nullable Double opacity) implements ThemeBackground {
        /// Creates a partial background object.
        ///
        /// @param path the theme-pack relative image path, or `null` when inherited
        /// @param url the remote image URL, or `null` when inherited
        /// @param paint the serialized paint value, or `null` when inherited
        /// @param opacity the background opacity override, or `null` when inherited
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
            opacity = validateOpacity(opacity);
        }

        /// Converts this patch to its JSON representation.
        @Override
        public JsonObject toJsonObject() {
            JsonObject object = new JsonObject();
            if (path != null) {
                object.addProperty(FIELD_PATH, path);
            }
            if (url != null) {
                object.addProperty(FIELD_URL, url);
            }
            if (paint != null) {
                object.addProperty(FIELD_PAINT, paint);
            }
            addOpacity(object, opacity);
            return object;
        }

        /// Returns whether this patch is empty.
        @Override
        public boolean isEmpty() {
            return path == null && url == null && paint == null && opacity == null;
        }

        /// Applies this partial patch over a base background.
        ///
        /// @param base the base background
        /// @return the merged background
        private ThemeBackground applyOver(ThemeBackground base) {
            if (base instanceof Builtin background) {
                return new Builtin(mergeOpacity(background.opacity, opacity));
            }
            if (base instanceof Image background) {
                return new Image(
                        path != null ? path : background.path,
                        mergeOpacity(background.opacity, opacity));
            }
            if (base instanceof Network background) {
                return new Network(
                        url != null ? url : background.url,
                        mergeOpacity(background.opacity, opacity));
            }
            if (base instanceof Paint background) {
                return new Paint(
                        paint != null ? paint : background.paint,
                        mergeOpacity(background.opacity, opacity));
            }
            if (base instanceof Patch background) {
                return new Patch(
                        path != null ? path : background.path,
                        url != null ? url : background.url,
                        paint != null ? paint : background.paint,
                        mergeOpacity(background.opacity, opacity));
            }
            throw new IllegalArgumentException("Unsupported theme background: " + base);
        }
    }
}
