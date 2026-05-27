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

import com.github.f4b6a3.uuid.alt.GUID;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/// Gson adapter for [GUID].
///
/// HMCL mainly uses [GUID] as a Gson-serialized replacement for [java.util.UUID] in
/// configuration models. A GUID is stored as the same 36-character canonical string
/// used by [java.util.UUID], while application code can keep the stronger [GUID] type.
///
/// This adapter also accepts the compact 32-character UUID string form supported by
/// [UUIDTypeAdapter] when reading existing JSON values.
///
/// @author Glavo
@JsonSerializable
@NotNullByDefault
public final class GUIDTypeAdapter extends TypeAdapter<@Nullable GUID> {
    /// Shared stateless adapter instance.
    public static final GUIDTypeAdapter INSTANCE = new GUIDTypeAdapter();

    /// Creates a GUID type adapter.
    private GUIDTypeAdapter() {
    }

    /// Writes a GUID as a canonical UUID string, or JSON null when the value is null.
    @Override
    public void write(JsonWriter writer, @Nullable GUID value) throws IOException {
        if (value == null) {
            writer.nullValue();
        } else {
            writer.value(value.toString());
        }
    }

    /// Reads a GUID from a canonical or compact UUID string, or returns null for JSON null.
    @Override
    public @Nullable GUID read(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        String value = reader.nextString();
        try {
            return new GUID(UUIDTypeAdapter.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("GUID malformed: " + value, e);
        }
    }

}
