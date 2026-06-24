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

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

/// Author metadata declared by a theme pack.
///
/// @param name the localized author display name
@NotNullByDefault
@JsonSerializable
@JsonAdapter(ThemePackAuthor.Adapter.class)
public record ThemePackAuthor(LocalizedText name) {

    static JsonArray toJson(List<ThemePackAuthor> authors) {
        JsonArray array = new JsonArray();
        for (ThemePackAuthor author : authors) {
            array.add(author.toJsonObject());
        }
        return array;
    }

    /// Creates theme-pack author metadata.
    ///
    /// @param name the localized author display name
    public ThemePackAuthor {
        if (name.mayBeEmpty()) {
            throw new IllegalArgumentException("The author name cannot be empty");
        }
    }

    /// Returns the author display name in the current locale.
    ///
    /// @return the localized author display name
    public String displayName() {
        return name.getText(I18n.getLocale().getCandidateLocales());
    }

    /// Converts this author to its JSON representation.
    ///
    /// @return the JSON object representing this author
    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        object.add("name", name.toJsonElement());
        return object;
    }

    static final class Adapter implements JsonSerializer<@Nullable ThemePackAuthor>, JsonDeserializer<@Nullable ThemePackAuthor> {
        @Override
        public @Nullable ThemePackAuthor deserialize(@Nullable JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json instanceof JsonNull)
                return null;

            if (json instanceof JsonObject jsonObject) {
                JsonElement nameJson = jsonObject.get("name");
                if (nameJson == null) {
                    throw new JsonParseException("Missing author name: " + json);
                }

                LocalizedText name = LocalizedText.parseJson(nameJson);
                if (name == null) {
                    throw new JsonParseException("The author name is null");
                }

                try {
                    return new ThemePackAuthor(name);
                } catch (IllegalArgumentException e) {
                    throw new JsonParseException(e);
                }
            }

            if (json instanceof JsonPrimitive jsonPrimitive) {
                return new ThemePackAuthor(LocalizedText.plain(jsonPrimitive.getAsString()));
            }

            throw new JsonParseException("Unexpected json element type: " + json.getClass());
        }

        @Override
        public JsonElement serialize(@Nullable ThemePackAuthor src, Type typeOfSrc, JsonSerializationContext context) {
            return src != null ? src.toJsonObject() : JsonNull.INSTANCE;
        }
    }
}
