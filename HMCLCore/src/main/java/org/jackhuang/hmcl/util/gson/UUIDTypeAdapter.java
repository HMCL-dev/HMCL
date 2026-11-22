// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
