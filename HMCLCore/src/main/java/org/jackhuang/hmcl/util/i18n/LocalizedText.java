/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.i18n;

import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

@JsonAdapter(LocalizedText.Adapter.class)
public final class LocalizedText {
    private final @Nullable String value;
    private final @Nullable Map<String, String> localizedValues;

    public LocalizedText(String value) {
        this.value = value;
        this.localizedValues = null;
    }

    public LocalizedText(@NotNull Map<String, String> localizedValues) {
        this.value = null;
        this.localizedValues = Objects.requireNonNull(localizedValues);
    }

    public String getText(@NotNull List<Locale> candidates) {
        if (localizedValues != null) {
            for (Locale locale : candidates) {
                String value = localizedValues.get(LocaleUtils.toLanguageKey(locale));
                if (value != null)
                    return value;
            }
            return null;
        } else
            return value;
    }

    static final class Adapter extends TypeAdapter<LocalizedText> {

        @Override
        public LocalizedText read(JsonReader jsonReader) throws IOException {
            JsonToken nextToken = jsonReader.peek();
            if (nextToken == JsonToken.NULL) {
                return null;
            } else if (nextToken == JsonToken.STRING) {
                return new LocalizedText(jsonReader.nextString());
            } else if (nextToken == JsonToken.BEGIN_OBJECT) {
                LinkedHashMap<String, String> localizedValues = new LinkedHashMap<>();

                jsonReader.beginObject();
                while (jsonReader.hasNext()) {
                    String name = jsonReader.nextName();
                    String value = jsonReader.nextString();

                    localizedValues.put(name, value);
                }
                jsonReader.endObject();

                return new LocalizedText(localizedValues);
            } else {
                throw new JsonSyntaxException("Unexpected token " + nextToken);
            }
        }

        @Override
        public void write(JsonWriter jsonWriter, LocalizedText localizedText) throws IOException {
            if (localizedText == null) {
                jsonWriter.nullValue();
            } else if (localizedText.localizedValues != null) {

                jsonWriter.beginObject();
                for (var entry : localizedText.localizedValues.entrySet()) {
                    jsonWriter.name(entry.getKey());
                    jsonWriter.value(entry.getValue());
                }
                jsonWriter.endObject();
            } else {
                jsonWriter.value(localizedText.value);
            }
        }
    }
}
