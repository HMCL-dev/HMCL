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
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Locale;

/// Represents a background for the HMCL theme.
///
/// A background can be one of the following variants:
/// - {@link BuiltIn}: a built-in background identified by name.
/// - {@link Local}: a background loaded from a local file path.
/// - {@link Remote}: a background loaded from a remote URL.
/// - {@link Fill}: a background filled with a solid or gradient {@link Paint}.
///
/// Instances can be serialized to and deserialized from JSON via {@link #toJson()} and
/// {@link #fromJson(JsonElement)}.
///
/// @author Glavo
@NotNullByDefault
public sealed interface ThemeBackground {

    /// Deserializes a {@link ThemeBackground} from a JSON element.
    ///
    /// Accepted forms:
    /// - A JSON string matching a {@link BuiltIn} name (case-insensitive) → {@link BuiltIn}.
    /// - Any other JSON string → {@link Local} with that string as the path.
    /// - A JSON object with {@code "type": "local"} and a {@code "path"} string → {@link Local}.
    /// - A JSON object with {@code "type": "remote"} and a {@code "url"} string → {@link Remote}.
    /// - A JSON object with {@code "type": "fill"} and either a {@code "paint"} or {@code "color"}
    ///   string parseable by JavaFX → {@link Fill}.
    ///
    /// @param json the JSON element to deserialize
    /// @return the deserialized {@link ThemeBackground}
    /// @throws JsonParseException if the JSON element is not a valid background representation
    static ThemeBackground fromJson(JsonElement json) throws JsonParseException {
        if (json instanceof JsonPrimitive primitive) {
            String value = primitive.getAsString();

            try {
                return BuiltIn.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return new Local(value);
            }
        } else if (json instanceof JsonObject object) {
            String type;
            if (object.get("type") instanceof JsonPrimitive elementType)
                type = elementType.getAsString();
            else
                throw new JsonParseException("Invalid theme background: " + json);

            switch (type) {
                case "local" -> {
                    if (object.get("path") instanceof JsonPrimitive path)
                        return new Local(path.getAsString());
                    else
                        throw new JsonParseException("Invalid theme background: " + json);
                }
                case "remote" -> {
                    if (object.get("url") instanceof JsonPrimitive url)
                        return new Remote(url.getAsString());
                    else
                        throw new JsonParseException("Invalid theme background: " + json);
                }
                case "fill" -> {
                    Paint paint;

                    if (object.get("paint") instanceof JsonPrimitive paintJson) {
                        try {
                            paint = Paint.valueOf(paintJson.getAsString());
                        } catch (IllegalArgumentException ignored) {
                            paint = null;
                        }
                    } else if (object.get("color") instanceof JsonPrimitive colorJson) {
                        try {
                            paint = Color.web(colorJson.getAsString());
                        } catch (IllegalArgumentException ignored) {
                            paint = null;
                        }
                    } else {
                        paint = null;
                    }

                    if (paint != null)
                        return new Fill(paint);
                    else
                        throw new JsonParseException("Invalid theme background: " + json);
                }
                default -> throw new JsonParseException("Invalid theme background: " + json);
            }
        } else {
            throw new JsonParseException("Invalid theme background: " + json);
        }
    }

    /// Serializes this background to a JSON element.
    ///
    /// The returned element can be passed back to {@link #fromJson(JsonElement)} to reconstruct
    /// an equivalent instance.
    ///
    /// @return a JSON representation of this background
    JsonElement toJson();

    /// Built-in backgrounds provided by HMCL.
    ///
    /// Each constant is serialized as a plain JSON string equal to its {@link #name()}.
    enum BuiltIn implements ThemeBackground {
        /// The default built-in background.
        DEFAULT,
        /// The classic built-in background.
        CLASSIC;

        /// {@inheritDoc}
        ///
        /// Returns a {@link JsonPrimitive} containing the name of this constant.
        @Override
        public JsonPrimitive toJson() {
            return new JsonPrimitive(name());
        }
    }

    /// A background that is loaded from a local file path.
    ///
    /// @param path the local file-system path to the background image
    record Local(String path) implements ThemeBackground {
        /// {@inheritDoc}
        ///
        /// Returns a JSON object of the form {@code {"type": "local", "path": "<path>"}}.
        @Override
        public JsonObject toJson() {
            var object = new JsonObject();
            object.addProperty("type", "local");
            object.addProperty("path", path);
            return object;
        }
    }

    /// A background that is loaded from a remote URL.
    ///
    /// @param url the URL of the background image
    record Remote(String url) implements ThemeBackground {
        /// {@inheritDoc}
        ///
        /// Returns a JSON object of the form {@code {"type": "remote", "url": "<url>"}}.
        @Override
        public JsonObject toJson() {
            var object = new JsonObject();
            object.addProperty("type", "remote");
            object.addProperty("url", url);
            return object;
        }
    }

    /// A background that fills the entire area with a {@link Paint}.
    ///
    /// @param paint the {@link Paint} used to fill the background
    record Fill(Paint paint) implements ThemeBackground {
        /// {@inheritDoc}
        ///
        /// Returns a JSON object of the form {@code {"type": "fill", "paint": "<paint>"}},
        /// where {@code <paint>} is the string representation of the {@link Paint} as returned by
        /// {@link Paint#toString()}.
        @Override
        public JsonObject toJson() {
            var object = new JsonObject();
            object.addProperty("type", "fill");
            object.addProperty("paint", paint.toString());
            return object;
        }
    }
}
