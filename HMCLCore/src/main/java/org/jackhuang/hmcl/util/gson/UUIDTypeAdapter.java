/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.gson;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.glavo.uuid.UUIDs;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.UUID;

/// Gson adapter for standard UUID strings.
@NotNullByDefault
public final class UUIDTypeAdapter extends TypeAdapter<@Nullable UUID> {
    /// Shared adapter instance.
    public static final UUIDTypeAdapter INSTANCE = new UUIDTypeAdapter();

    /// Writes a UUID as a standard lowercase string with hyphens.
    @Override
    public void write(JsonWriter writer, @Nullable UUID value) throws IOException {
        writer.value(value == null ? null : value.toString());
    }

    /// Reads a UUID from a standard or unhyphenated UUID string.
    @Override
    public @Nullable UUID read(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        try {
            return UUIDs.parse(reader.nextString());
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("UUID malformed", e);
        }
    }

    /// Prevents instantiation outside the shared instance.
    private UUIDTypeAdapter() {
    }
}
